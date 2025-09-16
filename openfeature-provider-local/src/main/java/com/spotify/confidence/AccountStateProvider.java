package com.spotify.confidence;

/**
 * Functional interface for providing AccountState instances.
 *
 * <p>This interface allows custom implementations to provide AccountState data instead of using the
 * default FlagsAdminStateFetcher. This is useful for scenarios where flag data should be sourced
 * from custom locations or caching mechanisms.
 *
 * @since 0.2.4
 */
@FunctionalInterface
public interface AccountStateProvider {

  /**
   * Provides an AccountState instance.
   *
   * @return the AccountState containing flag configurations and metadata
   * @throws RuntimeException if the AccountState cannot be provided
   */
  AccountState provide();
}
