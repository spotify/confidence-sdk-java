# Java Confidence SDK

Java library for [Confidence](https://confidence.spotify.com/). This library is designed to work in a java backend environment. For Android, please visit [Confidence-SDK-Android](https://github.com/spotify/confidence-sdk-android).

## Install

### Maven
 
<!-- x-release-please-start-version -->
```xml
<dependency>
    <groupId>com.spotify.confidence</groupId>
    <artifactId>sdk-java</artifactId>
    <version>0.1.7-SNAPSHOT</version>
</dependency>
```
<!---x-release-please-end-->

_Note: we strongly recommend to adopt the latest non-SNAPSHOT release [available here](https://github.com/spotify/confidence-sdk-java/releases/)._

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
### Testing with ConfidenceStub

For testing code that uses Confidence, we provide `ConfidenceStub` - a stub implementation that allows configuring predefined values and evaluation results for flags. It also tracks method calls and provides access to the call history for verification.

Basic usage with predefined values:

```java
// Create a ConfidenceStub instance
ConfidenceStub stub = ConfidenceStub.createStub();

// Configure a predefined value for a flag
stub.configureValue("flag-name.property-name", "predefinedValue");

// Retrieve the value using the stub
String value = stub.getValue("flag-name.property-name", "defaultValue");
System.out.println("Retrieved value: " + value);

// Verify the call history
List<String> callHistory = stub.getCallHistory();
System.out.println("Call history: " + callHistory);
```

## OpenFeature
The library includes a `Provider` for
the [OpenFeature Java SDK](https://openfeature.dev/docs/tutorials/getting-started/java), that can be
used to resolve feature flag values from the Confidence platform.

The constructor for creating an OpenFeature `ConfidenceProvider` takes a `Confidence` instance as a parameter.
We kindly ask you to use the `Confidence.Builder.buildForProvider()` function when creating a `Confidence` instance that 
will be used with OpenFeature.

To learn more about the basic concepts (flags, targeting key, evaluation contexts),
the [OpenFeature reference documentation](https://openfeature.dev/docs/reference/intro) can be
useful.

## Telemetry

In order to improve the services provided by Confidence, the SDK collects a very limited amount of telemetry data. 
This data is sent in the form of an additional gRPC header with each resolve request. The data does not contain any 
information that can be used to link the data to a specific end user.

Please refer to the [Telemetry class](sdk-java/src/main/java/com/spotify/confidence/telemetry/Telemetry.java) to understand the data that is collected.

To opt out of this behavior, you can disable telemetry by setting the `disableTelemetry` flag to `true` when building the `Confidence` instance.

```java
final Confidence confidence = Confidence.Builder("<CLIENT_SECRET>")
        .disableTelemetry(true)
        .build();
```