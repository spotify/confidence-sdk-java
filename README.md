# Confidence OpenFeature Java Provider

Java library for the [Confidence](https://confidence.spotify.com/) feature flag provider.

The library includes a `Provider` for
the [OpenFeature Java SDK](https://openfeature.dev/docs/tutorials/getting-started/java), that can be
used to resolve feature flag values from the Confidence platform.

To learn more about the basic concepts (flags, targeting key, evaluation contexts),
the [OpenFeature reference documentation](https://openfeature.dev/docs/reference/intro) can be
useful.

## Install

### Maven
 
<!-- x-release-please-start-version -->
```xml
<dependency>
    <groupId>com.spotify.confidence</groupId>
    <artifactId>openfeature-provider</artifactId>
    <version>0.0.9</version>
</dependency>
```
<!---x-release-please-end-->

## Usage

The provider is instantiated using a client token that is configured in the Confidence UI or via the
management API. After that all interaction with the feature flags happens using the OpenFeature client APIs. 

```java
package com.spotify.confidence.openfeature;

import com.spotify.confidence.ConfidenceFeatureProvider;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import java.util.Map;

public final class ResolveFlags {

  public static final String CLIENT_TOKEN = "<>";

  public static void main(String[] args) {
    final OpenFeatureAPI api = OpenFeatureAPI.getInstance();
    api.setProvider(new ConfidenceFeatureProvider(CLIENT_TOKEN));
    final Client client = api.getClient();

    final String targetingKey = "userId";
    final Map<String, Value> context = Map.of("country", new Value("SE"));
    final MutableContext ctx = new MutableContext(targetingKey, context);
    final String propertyValue = client.getStringValue("flagName.propertyName", "defaultValue",
        ctx);
    System.out.println(propertyValue);
  }
}
```
