package com.spotify.confidence;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.ImportFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.spotify.confidence.flags.resolver.v1.LogMessage;
import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyRequest;
import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.WriteFlagLogsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.WriteFlagLogsResponse;
import com.spotify.confidence.wasm.Messages;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import rust_guest.Types;

@FunctionalInterface
interface WasmFlagLogger {
  WriteFlagLogsResponse write(WriteFlagLogsRequest request);
}

class WasmResolveApi {

  private final FunctionType HOST_FN_TYPE =
      FunctionType.of(List.of(ValType.I32), List.of(ValType.I32));
  private final Instance instance;

  // interop
  private final ExportFunction wasmMsgAlloc;
  private final ExportFunction wasmMsgFree;
  private final WasmFlagLogger writeFlagLogs;

  // api
  private final ExportFunction wasmMsgGuestSetResolverState;
  private final ExportFunction wasmMsgFlushLogs;
  private final ExportFunction wasmMsgGuestResolve;
  private static final ScheduledExecutorService logPollExecutor =
      Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setDaemon(true).build());
  private final ExportFunction wasmMsgGuestResolveWithSticky;

  public WasmResolveApi(WasmFlagLogger flagLogger) {
    this.writeFlagLogs = flagLogger;
    logPollExecutor.scheduleAtFixedRate(
        this::flushLogs, 10, 10, java.util.concurrent.TimeUnit.SECONDS);
    try (InputStream wasmStream =
        getClass().getClassLoader().getResourceAsStream("wasm/confidence_resolver.wasm")) {
      if (wasmStream == null) {
        throw new RuntimeException("Could not find confidence_resolver.wasm in resources");
      }
      final WasmModule module = Parser.parse(wasmStream);
      instance =
          Instance.builder(module)
              .withImportValues(
                  ImportValues.builder()
                      .addFunction(
                          createImportFunction(
                              "current_time", Messages.Void::parseFrom, this::currentTime))
                      .addFunction(
                          createImportFunction("log_message", LogMessage::parseFrom, this::log))
                      .addFunction(
                          new ImportFunction(
                              "wasm_msg",
                              "wasm_msg_current_thread_id",
                              FunctionType.of(List.of(), List.of(ValType.I32)),
                              this::currentThreadId))
                      .addFunction(
                          createImportFunction(
                              "encrypt_resolve_token",
                              Types.EncryptionRequest::parseFrom,
                              this::encryptResolveToken))
                      .build())
              .withMachineFactory(MachineFactoryCompiler::compile)
              .build();
      wasmMsgAlloc = instance.export("wasm_msg_alloc");
      wasmMsgFree = instance.export("wasm_msg_free");
      wasmMsgGuestSetResolverState = instance.export("wasm_msg_guest_set_resolver_state");
      wasmMsgFlushLogs = instance.export("wasm_msg_guest_flush_logs");
      wasmMsgGuestResolve = instance.export("wasm_msg_guest_resolve");
      wasmMsgGuestResolveWithSticky = instance.export("wasm_msg_guest_resolve_with_sticky");
    } catch (IOException e) {
      throw new RuntimeException("Failed to load WASM module", e);
    }
  }

  private GeneratedMessageV3 log(LogMessage message) {
    System.out.println(message.getMessage());
    return Messages.Void.getDefaultInstance();
  }

  private GeneratedMessageV3 encryptResolveToken(Types.EncryptionRequest encryptionRequest) {
    try {
      final byte[] tokenData = encryptionRequest.getTokenData().toByteArray();
      final byte[] encryptionKey = encryptionRequest.getEncryptionKey().toByteArray();
      if (encryptionKey.length != 16) {
        throw new IllegalArgumentException("Encryption key must be exactly 16 bytes for AES-128");
      }
      final byte[] iv = new byte[16];
      new SecureRandom().nextBytes(iv);
      final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      final SecretKeySpec secretKey = new SecretKeySpec(encryptionKey, "AES");
      final IvParameterSpec ivSpec = new IvParameterSpec(iv);

      cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
      final byte[] encryptedData = cipher.doFinal(tokenData);

      final byte[] result = new byte[iv.length + encryptedData.length];
      System.arraycopy(iv, 0, result, 0, iv.length);
      System.arraycopy(encryptedData, 0, result, iv.length, encryptedData.length);

      return BytesValue.newBuilder().setValue(ByteString.copyFrom(result)).build();

    } catch (Exception e) {
      throw new RuntimeException("Failed to encrypt resolve token", e);
    }
  }

  private long[] currentThreadId(Instance instance, long... longs) {
    return new long[] {0};
  }

  private Timestamp currentTime(Messages.Void unused) {
    return Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build();
  }

  public void setResolverState(byte[] state, String accountId) {
    final var resolverStateRequest =
        Messages.SetResolverStateRequest.newBuilder()
            .setState(ByteString.copyFrom(state))
            .setAccountId(accountId)
            .build();
    final byte[] request =
        Messages.Request.newBuilder()
            .setData(ByteString.copyFrom(resolverStateRequest.toByteArray()))
            .build()
            .toByteArray();
    final int addr = transfer(request);
    final int respPtr = (int) wasmMsgGuestSetResolverState.apply(addr)[0];
    consumeResponse(respPtr, Messages.Void::parseFrom);
  }

  private void flushLogs() {
    final var respPtr = (int) wasmMsgFlushLogs.apply()[0];
    final var request = consumeResponse(respPtr, WriteFlagLogsRequest::parseFrom);
    final var ignore = writeFlagLogs.write(request);
  }

  public ResolveWithStickyResponse resolveWithSticky(ResolveWithStickyRequest request) {
    final int reqPtr = transferRequest(request);
    final int respPtr = (int) wasmMsgGuestResolveWithSticky.apply(reqPtr)[0];
    return consumeResponse(respPtr, ResolveWithStickyResponse::parseFrom);
  }

  public ResolveFlagsResponse resolve(ResolveFlagsRequest request) {
    final int reqPtr = transferRequest(request);
    final int respPtr = (int) wasmMsgGuestResolve.apply(reqPtr)[0];
    return consumeResponse(respPtr, ResolveFlagsResponse::parseFrom);
  }

  private <T extends GeneratedMessageV3> T consumeResponse(int addr, ParserFn<T> codec) {
    try {
      final Messages.Response response = Messages.Response.parseFrom(consume(addr));
      if (response.hasError()) {
        throw new RuntimeException(response.getError());
      } else {
        return codec.apply(response.getData().toByteArray());
      }
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  private <T extends GeneratedMessageV3> T consumeRequest(int addr, ParserFn<T> codec) {
    try {
      final Messages.Request request = Messages.Request.parseFrom(consume(addr));
      return codec.apply(request.getData().toByteArray());
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  private int transferRequest(GeneratedMessageV3 message) {
    final byte[] request =
        Messages.Request.newBuilder().setData(message.toByteString()).build().toByteArray();
    return transfer(request);
  }

  private int transferResponseSuccess(GeneratedMessageV3 response) {
    final byte[] wrapperBytes =
        Messages.Response.newBuilder().setData(response.toByteString()).build().toByteArray();
    return transfer(wrapperBytes);
  }

  private int transferResponseError(String error) {
    final byte[] wrapperBytes =
        Messages.Response.newBuilder().setError(error).build().toByteArray();
    return transfer(wrapperBytes);
  }

  private byte[] consume(int addr) {
    final Memory mem = instance.memory();
    final int len = (int) (mem.readU32(addr - 4) - 4L);
    final byte[] data = mem.readBytes(addr, len);
    wasmMsgFree.apply(addr);
    return data;
  }

  private int transfer(byte[] data) {
    final Memory mem = instance.memory();
    final int addr = (int) wasmMsgAlloc.apply(data.length)[0];
    mem.write(addr, data);
    return addr;
  }

  private <T extends GeneratedMessageV3> ImportFunction createImportFunction(
      String name, ParserFn<T> reqCodec, Function<T, GeneratedMessageV3> impl) {
    return new ImportFunction(
        "wasm_msg",
        "wasm_msg_host_" + name,
        HOST_FN_TYPE,
        (instance1, args) -> {
          try {
            final T message = consumeRequest((int) args[0], reqCodec);
            final GeneratedMessageV3 response = impl.apply(message);
            return new long[] {transferResponseSuccess(response)};
          } catch (Exception e) {
            return new long[] {transferResponseError(e.getMessage())};
          }
        });
  }

  private interface ParserFn<T> {

    T apply(byte[] data) throws InvalidProtocolBufferException;
  }
}
