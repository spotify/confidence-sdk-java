package com.spotify.confidence;

import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.admin.v1.Flag;
import com.spotify.confidence.shaded.flags.admin.v1.Segment;
import java.util.Optional;

record AssignmentMatch(
    String assignmentId,
    String targetingKey,
    Optional<String> variant,
    Optional<Struct> value,
    Segment segment,
    Flag.Rule matchedRule) {}
