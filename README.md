# Java Confidence SDK

Java library for [Confidence](https://confidence.spotify.com/). This library is designed to work in a java backend environment. For Android, please visit [Confidence-SDK-Android](https://github.com/spotify/confidence-sdk-android).

## Install

### Maven
 
<!-- x-release-please-start-version -->
```xml
<dependency>
    <groupId>com.spotify.confidence</groupId>
    <artifactId>sdk-java</artifactId>
    <version>0.1.3-SNAPSHOT</version>
</dependency>
```
<!---x-release-please-end-->

#### Depending on a development snapshot
We deploy snapshots from the `main` branch to [Sonatype OSSRH](https://oss.sonatype.org/content/repositories/snapshots/com/spotify/confidence/sdk-java/).
To use a snapshot, add the following repository to your `pom.xml`:
```xml
<distributionManagement>
    <snapshotRepository>
        <id>oss.snapshots</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
    </snapshotRepository>
</distributionManagement>
```

## Usage

The SDK is instantiated using a client secret that is configured in the Confidence UI or via the
management console.

### Resolving flags
Flag values are evaluated remotely and returned to the application:
```java
final Confidence confidence = Confidence.builder("<CLIENT_TOKEN>").build();
confidence.setContext(Map.of("country", ConfidenceValue.of("SE")));
final String propertyValue =
    confidence
        .withContext(
            Map.of(
                "user_id", ConfidenceValue.of("<some-user-id>"),
                "country", ConfidenceValue.of("SE")))
        .getValue("flag-name.property-name", "defaultValue");
```

### Tracking events
Events are emitted to the Confidence backend:
```java
confidence.track("my-event", ConfidenceValue.of(Map.of("field", ConfidenceValue.of("data"))));
```

## OpenFeature
The library includes a `Provider` for
the [OpenFeature Java SDK](https://openfeature.dev/docs/tutorials/getting-started/java), that can be
used to resolve feature flag values from the Confidence platform.

To learn more about the basic concepts (flags, targeting key, evaluation contexts),
the [OpenFeature reference documentation](https://openfeature.dev/docs/reference/intro) can be
useful.
