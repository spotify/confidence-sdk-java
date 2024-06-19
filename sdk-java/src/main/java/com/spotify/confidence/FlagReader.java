package com.spotify.confidence;

import java.io.Closeable;

public interface FlagReader extends Closeable {

  <T> T getValue(String key, T defaultValue);

  <T> FlagEvaluation<T> getEvaluation(String key, T defaultValue);
}
