# Sticky Resolve Documentation

## Overview

Sticky Resolve ensures users receive the same variant throughout an experiment, even if their targeting attributes change or you pause new assignments.

**Two main use cases:**
1. **Consistent experience** - User moves countries but keeps the same variant
2. **Pause intake** - Stop new assignments while maintaining existing ones

**Default behavior:** Sticky assignments are managed by Confidence servers with automatic 90-day TTL. When needed, the provider makes a network call to Confidence. No setup required.

## How It Works

### Default: Server-Side Storage (RemoteResolverFallback)

**Flow:**
1. Local WASM resolver attempts to resolve
2. If sticky data needed → network call to Confidence
3. Confidence checks its sticky repository, returns variant
4. Assignment stored server-side with 90-day TTL (auto-renewed on access)

**Server-side configuration (in Confidence UI):**
- Optionally skip targeting criteria for sticky assignments
- Pause/resume new entity intake
- Automatic TTL management

### Custom: Local Storage (MaterializationRepository)

Implement `MaterializationRepository` to store assignments locally and eliminate network calls.

**Interface:**
```java
public interface MaterializationRepository extends StickyResolveStrategy {
  // Load assignments for a unit (e.g., user ID)
  CompletableFuture<Map<String, MaterializationInfo>> loadMaterializedAssignmentsForUnit(
      String unit, String materialization);

  // Store new assignments
  CompletableFuture<Void> storeAssignment(
      String unit, Map<String, MaterializationInfo> assignments);
}
```

**MaterializationInfo structure:**
```java
record MaterializationInfo(
    boolean isUnitInMaterialization,
    Map<String, String> ruleToVariant  // rule ID -> variant name
)
```

## Implementation Examples

### In-Memory (Testing/Development)

```java
public class InMemoryMaterializationRepository implements MaterializationRepository {
  private final Map<String, Map<String, MaterializationInfo>> storage = new ConcurrentHashMap<>();

  @Override
  public CompletableFuture<Map<String, MaterializationInfo>> loadMaterializedAssignmentsForUnit(
      String unit, String materialization) {
    return CompletableFuture.supplyAsync(() -> {
      Map<String, MaterializationInfo> unitAssignments = storage.get(unit);
      if (unitAssignments == null || !unitAssignments.containsKey(materialization)) {
        return Map.of();
      }
      return Map.of(materialization, unitAssignments.get(materialization));
    });
  }

  @Override
  public CompletableFuture<Void> storeAssignment(
      String unit, Map<String, MaterializationInfo> assignments) {
    return CompletableFuture.runAsync(() -> {
      storage.compute(unit, (key, existing) -> {
        if (existing == null) {
          return new ConcurrentHashMap<>(assignments);
        }
        existing.putAll(assignments);
        return existing;
      });
    });
  }

  @Override
  public void close() {
    storage.clear();
  }
}
```

### Redis (Production)

```java
public class RedisMaterializationRepository implements MaterializationRepository {
  private final JedisPool jedisPool;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final int TTL_SECONDS = 90 * 24 * 60 * 60; // 90 days

  public RedisMaterializationRepository(JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  @Override
  public CompletableFuture<Map<String, MaterializationInfo>> loadMaterializedAssignmentsForUnit(
      String unit, String materialization) {
    return CompletableFuture.supplyAsync(() -> {
      try (var jedis = jedisPool.getResource()) {
        String key = "sticky:" + unit + ":" + materialization;
        String value = jedis.get(key);

        if (value == null) return Map.of();

        // Renew TTL on read (matching Confidence behavior)
        jedis.expire(key, TTL_SECONDS);

        MaterializationInfo info = objectMapper.readValue(value, MaterializationInfo.class);
        return Map.of(materialization, info);
      } catch (Exception e) {
        throw new RuntimeException("Failed to load from Redis", e);
      }
    });
  }

  @Override
  public CompletableFuture<Void> storeAssignment(
      String unit, Map<String, MaterializationInfo> assignments) {
    return CompletableFuture.runAsync(() -> {
      try (var jedis = jedisPool.getResource()) {
        for (var entry : assignments.entrySet()) {
          String key = "sticky:" + unit + ":" + entry.getKey();
          String value = objectMapper.writeValueAsString(entry.getValue());
          jedis.setex(key, TTL_SECONDS, value);
        }
      } catch (Exception e) {
        // Don't fail resolve on storage errors
        System.err.println("Failed to store to Redis: " + e.getMessage());
      }
    });
  }

  @Override
  public void close() {
    jedisPool.close();
  }
}
```

### Usage

```java
MaterializationRepository repository = new RedisMaterializationRepository(jedisPool);

OpenFeatureLocalResolveProvider provider = new OpenFeatureLocalResolveProvider(
    apiSecret,
    clientSecret,
    repository
);
```

## Best Practices

1. **Fail gracefully** - Storage errors shouldn't fail flag resolution
2. **Use 90-day TTL** - Match Confidence's default behavior, renew on read
3. **Connection pooling** - Use pools for Redis/DB connections
4. **Monitor metrics** - Track cache hit rate, storage latency, errors
5. **Test both paths** - Missing assignments (cold start) and existing assignments

## When to Use Custom Storage

| Strategy | Best For | Trade-offs |
|----------|----------|------------|
| **RemoteResolverFallback** (default) | Most apps | Simple, managed by Confidence. Network calls when needed. |
| **MaterializationRepository** (in-memory) | Single-instance apps, testing | Fast, no network. Lost on restart. |
| **MaterializationRepository** (Redis/DB) | High-traffic apps, offline scenarios | No network calls. Requires storage infra. |

**Start with the default.** Only implement custom storage if you need to eliminate network calls or work offline.

## Additional Resources

- [Confidence Sticky Assignments Documentation](https://confidence.spotify.com/docs/flags/audience#sticky-assignments)
