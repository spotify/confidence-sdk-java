package com.spotify.confidence.flags.resolver;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.spotify.confidence.flags.admin.v1.Flag.Rule.Assignment;
import com.spotify.confidence.flags.admin.v1.Flag.Rule.BucketRange;
import com.spotify.confidence.flags.resolver.domain.AccountState;
import java.util.BitSet;

public class Randomizer {

  public static final String MEGA_SALT = "MegaSalt";
  public static final int BR_BUCKET_COUNT = 1_000_000;
  public static final BitSet FULL_BITSET = new BitSet(BR_BUCKET_COUNT);

  static {
    for (int i = 0; i < BR_BUCKET_COUNT; i++) {
      FULL_BITSET.set(i);
    }
  }

  private static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();
  private static final Joiner PIPE_JOINER = Joiner.on('|');
  private static final long LONG_SCALE = 0x0FFF_FFFF_FFFF_FFFFL;

  public static boolean inBitset(AccountState state, String segmentName, String unit) {
    if (!state.bitsets().containsKey(segmentName) || state.bitsets().get(segmentName).isEmpty()) {
      return false;
    }

    final BitSet bitset = state.bitsets().get(segmentName);
    final long bucket = getBucket(unit, state.saltForAccount(), BR_BUCKET_COUNT);

    return bitset.get((int) bucket);
  }

  public static long getBucket(String unit, String salt, int bucketCount) {
    final String[] data = new String[] {salt, unit};
    final String unitString = PIPE_JOINER.join(data);
    final HashCode hashCode = HASH_FUNCTION.hashString(unitString, UTF_8);
    return ((hashCode.asLong() >> 4) & LONG_SCALE) % bucketCount;
  }

  public static boolean coversBucket(Assignment assignment, long bucket) {
    for (BucketRange range : assignment.getBucketRangesList()) {
      if (range.getLower() <= bucket && bucket < range.getUpper()) {
        return true;
      }
    }
    return false;
  }
}
