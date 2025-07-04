package com.spotify.confidence.flags.resolver.domain;

import com.google.protobuf.Struct;
import com.spotify.confidence.flags.admin.v1.Flag;
import com.spotify.confidence.flags.admin.v1.Segment;
import java.util.Optional;

public record AssignmentMatch(
    String assignmentId,
    String targetingKey,
    Optional<String> variant,
    Optional<Struct> value,
    Segment segment,
    Flag.Rule matchedRule) {}
