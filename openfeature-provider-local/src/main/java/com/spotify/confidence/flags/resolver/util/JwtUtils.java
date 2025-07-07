package com.spotify.confidence.flags.resolver.util;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.spotify.confidence.commons.v1.AuthProto;
import com.spotify.confidence.commons.v1.Permission;
import io.grpc.Metadata;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class JwtUtils {
  public static final String AUTH0_ISSUER = "https://auth.confidence.dev/";
  public static final String[] AUTH0_ISSUERS =
      new String[] {"https://konfidens.eu.auth0.com/", AUTH0_ISSUER};

  public static final String CONFIDENCE_ISSUER = "https://iam.confidence.dev/";
  public static final String CONFIDENCE_AUDIENCE = "https://confidence.dev/";
  private static final Map<String, Permission> PERMISSION_MAP =
      Arrays.stream(Permission.values())
          .filter(
              permission ->
                  permission != Permission.UNKNOWN_PERMISSION
                      && permission != Permission.UNRECOGNIZED)
          .collect(
              Collectors.toMap(
                  e ->
                      Permission.getDescriptor()
                          .findValueByName(e.name())
                          .getOptions()
                          .getExtension(AuthProto.scope),
                  c -> c));

  public static final String BEARER_TYPE = "Bearer";
  public static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY =
      Metadata.Key.of("Authorization", ASCII_STRING_MARSHALLER);

  public static final String ACCOUNT_NAME_CLAIM = "https://confidence.dev/account_name";
  public static final String USER_NAME_CLAIM = "https://confidence.dev/user_name";
  public static final String SUB_CLAIM = "sub";
  public static final String LOGIN_ID_CLAIM = "https://confidence.dev/org_login_id";
  public static final String PERMISSIONS_CLAIM = "https://confidence.dev/permissions";

  public static Claim getClaimOrThrow(final DecodedJWT jwt, final String claim)
      throws JWTVerificationException {

    return getClaim(jwt, claim)
        .orElseThrow(
            () ->
                new JWTVerificationException(
                    String.format("Missing required claim '%s' in JWT.", claim)));
  }

  public static Optional<Claim> getClaim(final DecodedJWT jwt, final String claim)
      throws JWTVerificationException {
    if (!jwt.getClaims().containsKey(claim)) {
      return Optional.empty();
    } else {
      return Optional.of(jwt.getClaim(claim));
    }
  }

  public static Set<Permission> parsePermissions(DecodedJWT decodedJWT) {
    return Arrays.stream(getClaimOrThrow(decodedJWT, PERMISSIONS_CLAIM).asArray(String.class))
        .map(PERMISSION_MAP::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  public static boolean isValidToken(String token) {
    return token.startsWith(BEARER_TYPE);
  }

  public static String getTokenAsHeader(String rawToken) {
    return String.format("Bearer %s", rawToken);
  }
}
