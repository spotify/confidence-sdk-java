package com.spotify.confidence.flags.resolver.domain;

import com.spotify.confidence.flags.admin.v1.Flag;

public record FallthroughRule(Flag.Rule rule, String assignmentId, String targetingKey) {}
