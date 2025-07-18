package com.spotify.confidence;

import com.spotify.confidence.shaded.flags.admin.v1.Flag;

record FallthroughRule(Flag.Rule rule, String assignmentId, String targetingKey) {}
