package com.stellar.core.ratelimit

/**
 * Generic contract for rate-limiting operations to prevent API throttling and anti-cheat bans.
 */
interface RateLimiter {
    /**
     * Current token balance available for consumption.
     */
    val currentTokens: Double

    /**
     * Checks and consumes [tokens] immediately if available without waiting.
     *
     * @param tokens Number of tokens required.
     * @return `true` if tokens were acquired, `false` otherwise.
     */
    fun tryAcquire(tokens: Double = DEFAULT_ACQUIRE_AMOUNT): Boolean

    /**
     * Suspends the calling coroutine until [tokens] are replenished and consumed.
     *
     * @param tokens Number of tokens required.
     */
    suspend fun acquire(tokens: Double = DEFAULT_ACQUIRE_AMOUNT)

    /**
     * Blocks the current thread until [tokens] are replenished and consumed or [timeoutMillis] elapses.
     *
     * @param tokens Number of tokens required.
     * @param timeoutMillis Maximum milliseconds to wait.
     * @return `true` if acquired before timeout, `false` otherwise.
     */
    fun acquireBlocking(tokens: Double = DEFAULT_ACQUIRE_AMOUNT, timeoutMillis: Long = Long.MAX_VALUE): Boolean

    /**
     * Calculates the estimated milliseconds until [tokens] will become available.
     *
     * @param tokens Number of tokens requested.
     * @return Milliseconds until sufficient tokens exist (0 if already available).
     */
    fun timeUntilAvailable(tokens: Double = DEFAULT_ACQUIRE_AMOUNT): Long

    /**
     * Resets the rate limiter to its initial capacity.
     */
    fun reset()

    companion object {
        const val DEFAULT_ACQUIRE_AMOUNT: Double = 1.0
    }
}
