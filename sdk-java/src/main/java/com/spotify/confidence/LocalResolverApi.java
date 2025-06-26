package com.spotify.confidence;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.*;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.wasm.Messages;
import java.util.List;
import java.util.function.Function;

public class LocalResolverApi {

  private static final FunctionType HOST_FN_TYPE =
      FunctionType.of(List.of(ValType.I32), List.of(ValType.I32));
  private final Instance instance;

  // interop
  private final ExportFunction wasmMsgAlloc;
  private final ExportFunction wasmMsgFree;

  // api
  private final ExportFunction wasmMsgGuestSetResolverState;
  private final ExportFunction wasmMsgGuestResolve;

  public LocalResolverApi(WasmModule module) {

    instance =
        Instance.builder(module)
            .withImportValues(
                ImportValues.builder()
                    .addFunction(
                        createImportFunction(
                            "current_time", Messages.Void::parseFrom, this::currentTime))
                    .build())
            .withMachineFactory(MachineFactoryCompiler::compile)
            .build();
    wasmMsgAlloc = instance.export("wasm_msg_alloc");
    wasmMsgFree = instance.export("wasm_msg_free");
    wasmMsgGuestSetResolverState = instance.export("wasm_msg_guest_set_resolver_state");
    wasmMsgGuestResolve = instance.export("wasm_msg_guest_resolve");
  }

  private Timestamp currentTime(Messages.Void unused) {
    return Timestamp.getDefaultInstance();
  }

  public void setResolverState(byte[] state) {
    final byte[] request =
        Messages.Request.newBuilder().setData(ByteString.copyFrom(state)).build().toByteArray();
    int addr = transfer(request);
    int respPtr = (int) wasmMsgGuestSetResolverState.apply(addr)[0];
    consumeResponse(respPtr, Messages.Void::parseFrom);
  }

  public ResolveFlagsResponse resolve(ResolveFlagsRequest request) {
    int reqPtr = transferRequest(request);
    int respPtr = (int) wasmMsgGuestResolve.apply(reqPtr)[0];
    return consumeResponse(respPtr, ResolveFlagsResponse::parseFrom);
  }

  private <T extends GeneratedMessageV3> T consumeResponse(int addr, ParserFn<T> codec) {
    try {
      Messages.Response response = Messages.Response.parseFrom(consume(addr));
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
      Messages.Request request = Messages.Request.parseFrom(consume(addr));
      return codec.apply(request.toByteArray());
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
    int addr = (int) wasmMsgAlloc.apply(data.length)[0];
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
