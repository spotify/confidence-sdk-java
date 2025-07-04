package com.spotify.confidence.flags.resolver.domain;

import com.spotify.confidence.flags.resolver.v1.ResolveTokenV1;
import java.time.Instant;

public record FlagToApply(
    Instant skewAdjustedAppliedTime, ResolveTokenV1.AssignedFlag assignment) {}
