# Confidence OpenFeature Local Provider

![Status: Experimental](https://img.shields.io/badge/status-experimental-orange)

A high-performance OpenFeature provider for [Confidence](https://confidence.spotify.com/) feature flags that evaluates flags locally for minimal latency.

## Features

- **Local Resolution**: Evaluates feature flags locally using WebAssembly (WASM) or pure Java
- **Low Latency**: No network calls during flag evaluation
- **Automatic Sync**: Periodically syncs flag configurations from Confidence
- **Exposure Logging**: Fully supported exposure logging (and other resolve analytics)
- **OpenFeature Compatible**: Works with the standard OpenFeature SDK

## Installation

Add this dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.spotify.confidence</groupId>
    <artifactId>openfeature-provider-local</artifactId>
    <version>0.2.4</version>
</dependency>
```

## Quick Start

```java
import com.spotify.confidence.ApiSecret;
import com.spotify.confidence.OpenFeatureLocalResolveProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Client;

// Create API credentials
ApiSecret apiSecret = new ApiSecret("your-client-id", "your-client-secret");
String clientSecret = "your-application-client-secret";

// Create and register the provider
OpenFeatureLocalResolveProvider provider = 
    new OpenFeatureLocalResolveProvider(apiSecret, clientSecret);
OpenFeatureAPI.getInstance().setProvider(provider);

// Use OpenFeature client
Client client = OpenFeatureAPI.getInstance().getClient();
String value = client.getStringValue("my-flag", "default-value");
```

## Configuration

### Resolution Modes

The provider supports two resolution modes:

- **WASM mode** (default): Uses WebAssembly resolver
- **Java mode**: Pure Java implementation of the resolver

Control the mode with the `LOCAL_RESOLVE_MODE` environment variable:

```bash
# Force WASM mode
export LOCAL_RESOLVE_MODE=WASM

# Force Java mode  
export LOCAL_RESOLVE_MODE=JAVA
```

### Console Logging

Configure console logging levels using `ProviderOptions`:

```java
import com.spotify.confidence.ProviderOptions;

// Default console logging (INFO level - all levels above debug)
new OpenFeatureLocalResolveProvider(apiSecret, clientSecret);

// Custom console logging level
ProviderOptions options = ProviderOptions.withLoggingLevel(ProviderOptions.LoggingLevel.DEBUG);
new OpenFeatureLocalResolveProvider(apiSecret, clientSecret, options);

// Available logging levels: ALL, TRACE, DEBUG, INFO, WARN, ERROR, OFF
```

**Note**: This setting only affects console logging output from the provider and its components. It does not impact the assign and resolve logs that are sent as network requests to the Confidence service for telemetry purposes.

## Credentials

You need two types of credentials:

1. **API Secret** (`ApiSecret`): For authenticating with the Confidence API
   - Contains `clientId` and `clientSecret` for your Confidence application
   
2. **Client Secret** (`String`): For flag resolution authentication
   - Application-specific secret for flag evaluation

Both can be obtained from your Confidence dashboard.

## Requirements

- Java 17+
- OpenFeature SDK 1.6.1+