package com.spotify.confidence.flags.resolver.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.spotify.confidence.flags.resolver.ResolveTokenConverter;
import com.spotify.confidence.flags.resolver.exceptions.BadRequestException;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlainResolveTokenConverter extends ResolveTokenConverter {

  private static final Logger logger = LoggerFactory.getLogger(PlainResolveTokenConverter.class);

  @Override
  public ByteString convertResolveToken(ResolveToken resolveToken) {
    return resolveToken.toByteString();
  }

  @Override
  public ResolveToken readResolveToken(ByteString tokenBytes) {
    try {
      return ResolveToken.parseFrom(tokenBytes);
    } catch (InvalidProtocolBufferException e) {
      logger.warn("Got InvalidProtocolBufferException when reading resolve token", e);
      throw new BadRequestException("Unable to parse resolve token");
    }
  }
}
