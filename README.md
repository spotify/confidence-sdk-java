# Java Confidence SDK

Java library for [Confidence](https://confidence.spotify.com/). This library is designed to work in a java backend environment. For Android, please visit [Confidence-SDK-Android](https://github.com/spotify/confidence-sdk-android).

We suggest you to use the OpenFeature SDK to consume the Confidence feature flags.

## Install

### OpenFeature provider (Maven)

Add the OpenFeature provider dependency if you want to use Confidence via the OpenFeature Java SDK:

<!-- x-release-please-start-version -->
```xml
<dependency>
    <groupId>com.spotify.confidence</groupId>
    <artifactId>openfeature-provider</artifactId>
    <version>0.2.6</version>
</dependency>
```
<!---x-release-please-end-->

_Note: we strongly recommend to adopt the latest non-SNAPSHOT release [available here](https://github.com/spotify/confidence-sdk-java/releases/)._


## Usage

OpenFeature providers connect the OpenFeature SDK to your flag system. Use the Confidence OpenFeature provider to evaluate flags with the Confidence platform.

### Quickstart: OpenFeature provider (Java)

The Provider is instantiated using a client secret configured in the Confidence UI or via the management console.

```java
import com.spotify.confidence.Confidence;
import com.spotify.confidence.ConfidenceFeatureProvider;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;

// 1) Configure and register the provider
ConfidenceFeatureProvider provider = new ConfidenceFeatureProvider(
    Confidence.builder("<CLIENT_SECRET>")
);
OpenFeatureAPI api = OpenFeatureAPI.getInstance();
api.setProvider(provider);

// 2) Get an OpenFeature client
Client client = api.getClient();

// 3) Provide an evaluation context (targeting key + attributes)
Map<String, Value> requestAttrs = new HashMap<>();
requestAttrs.put("email", new Value(session.getAttribute("email")));
requestAttrs.put("product", new Value("productId"));
String targetingKey = session.getId();
EvaluationContext reqCtx = new ImmutableContext(targetingKey, requestAttrs);

// 4) Evaluate flags
boolean enabled = client.getBooleanValue("my-flag.enabled", false, reqCtx);
String group = client.getStringValue("my-flag.group", "group1", reqCtx);
```

#### Advanced configuration (optional)

```java
ConfidenceFeatureProvider provider = new ConfidenceFeatureProvider(
    Confidence.builder("<CLIENT_SECRET>")
        .resolveDeadlineMs(250) // timeout for flag resolution
        .flagResolverManagedChannel("localhost", 8080) // e.g., local sidecar
);
```

To learn more about providers and evaluation contexts, see the [OpenFeature reference docs](https://github.com/open-feature/java-sdk).

## Direct SDK usage (optional)

Use the Confidence SDK directly if you are not using OpenFeature. 

### Vanilla SDK (Maven)

<!-- x-release-please-start-version -->
```xml
<dependency>
    <groupId>com.spotify.confidence</groupId>
    <artifactId>sdk-java</artifactId>
    <version>0.2.6</version>
</dependency>
```
<!---x-release-please-end-->

### Resolving flags
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
```java
confidence.track("my-event", ConfidenceValue.of(Map.of("field", ConfidenceValue.of("data"))));
```

### Testing with ConfidenceStub

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