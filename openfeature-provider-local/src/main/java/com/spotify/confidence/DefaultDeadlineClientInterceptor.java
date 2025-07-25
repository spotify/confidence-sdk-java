/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
// Imported from https://github.com/salesforce/grpc-java-contrib

package com.spotify.confidence;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ForwardingClientCall;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

class DefaultDeadlineClientInterceptor implements ClientInterceptor {

  private Duration duration;

  DefaultDeadlineClientInterceptor(Duration duration) {
    checkNotNull(duration, "duration");
    checkArgument(!duration.isNegative(), "duration must be greater than zero");

    this.duration = duration;
  }

  /**
   * Get the current default deadline duration.
   *
   * @return the current default deadline duration
   */
  Duration getDuration() {
    return duration;
  }

  /**
   * Set a new default deadline duration.
   *
   * @param duration the new default deadline duration
   */
  void setDuration(Duration duration) {
    this.duration = duration;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    // Only add a deadline if no other deadline has been set.
    if (callOptions.getDeadline() == null && Context.current().getDeadline() == null) {
      callOptions = callOptions.withDeadlineAfter(duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        next.newCall(method, callOptions)) {};
  }
}
