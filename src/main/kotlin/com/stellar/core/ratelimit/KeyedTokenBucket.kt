package com.stellar.core.ratelimit

import java.util.concurrent.ConcurrentHashMap

/**
 * Multitenant / generic keyed rate limiter maintaining independent [TokenBucket] instances per key [K].
 *
 * Enables fine-grained rate limiting for translation providers (e.g. "google", "deepl", "libretranslate"),
 * per-language pairs (e.g. "en->es"), or per-client session.
 *
 * @param K Key identifying the rate-limited entity.
 * @param bucketFactory Factory generating a new [TokenBucket] instance for unseen keys.
 */
class KeyedTokenBucket<K>(
    private val bucketFactory: (key: K) -> TokenBucket,
) {
    private val buckets = ConcurrentHashMap<K, TokenBucket>()

    /**
     * Retrieves or creates the [TokenBucket] associated with [key].
     */
    fun getBucket(key: K): TokenBucket = buckets.computeIfAbsent(key, bucketFactory)

    /**
     * Immediately checks and consumes [tokens] for [key] if available.
     */
    fun tryAcquire(key: K, tokens: Double = RateLimiter.DEFAULT_ACQUIRE_AMOUNT): Boolean =
        getBucket(key).tryAcquire(tokens)

    /**
     * Suspends until [tokens] are available in the bucket corresponding to [key].
     */
    suspend fun acquire(key: K, tokens: Double = RateLimiter.DEFAULT_ACQUIRE_AMOUNT) {
        getBucket(key).acquire(tokens)
    }

    /**
     * Blocks until [tokens] are available in the bucket corresponding to [key] or timeout expires.
     */
    fun acquireBlocking(
        key: K,
        tokens: Double = RateLimiter.DEFAULT_ACQUIRE_AMOUNT,
        timeoutMillis: Long = Long.MAX_VALUE,
    ): Boolean = getBucket(key).acquireBlocking(tokens, timeoutMillis)

    /**
     * Executes the suspending [block] with rate limiting under [key].
     */
    suspend fun <T> execute(key: K, tokens: Double = RateLimiter.DEFAULT_ACQUIRE_AMOUNT, block: suspend () -> T): T =
        getBucket(key).execute(tokens, block)

    /**
     * Executes the blocking [block] with rate limiting under [key].
     */
    fun <T> executeBlocking(
        key: K,
        tokens: Double = RateLimiter.DEFAULT_ACQUIRE_AMOUNT,
        timeoutMillis: Long = Long.MAX_VALUE,
        block: () -> T,
    ): T = getBucket(key).executeBlocking(tokens, timeoutMillis, block)

    /**
     * Resets the bucket for [key].
     */
    fun reset(key: K) {
        buckets[key]?.reset()
    }

    /**
     * Resets all tracked buckets.
     */
    fun resetAll() {
        for (bucket in buckets.values) {
            bucket.reset()
        }
    }
}
