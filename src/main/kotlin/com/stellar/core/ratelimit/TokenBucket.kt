package com.stellar.core.ratelimit

import kotlinx.coroutines.delay
import kotlin.math.ceil

/**
 * Generic Token Bucket rate limiter implementing [RateLimiter].
 *
 * Applicable across translation API pacing, click frequency regulation, and packet rate governance.
 * Supports burst allowance up to [capacity], sustained refill rates, coroutine suspension ([acquire]),
 * and blocking thread synchronization ([acquireBlocking]).
 *
 * @property capacity Maximum number of tokens the bucket can accumulate (burst allowance).
 * @property refillRatePerSecond Number of tokens regenerated per second.
 * @property timeSource Monotonic nanosecond time provider, injectable for deterministic testing.
 */
class TokenBucket(
    val capacity: Double = DEFAULT_CAPACITY,
    val refillRatePerSecond: Double = DEFAULT_REFILL_RATE,
    private val timeSource: () -> Long = System::nanoTime,
) : RateLimiter {
    private var availableTokens: Double = capacity
    private var lastRefillNanos: Long = timeSource()
    private val lock = Any()

    override val currentTokens: Double
        get() = synchronized(lock) {
            refillInternal()
            availableTokens
        }

    fun refill() {
        synchronized(lock) {
            refillInternal()
        }
    }

    override fun tryAcquire(tokens: Double): Boolean = synchronized(lock) {
        refillInternal()
        if (availableTokens >= tokens) {
            availableTokens -= tokens
            true
        } else {
            false
        }
    }

    /**
     * Backward-compatible alias for [tryAcquire].
     */
    fun tryConsume(tokens: Double = RateLimiter.DEFAULT_ACQUIRE_AMOUNT): Boolean = tryAcquire(tokens)

    override suspend fun acquire(tokens: Double) {
        require(tokens <= capacity) {
            "Requested tokens ($tokens) exceeds bucket capacity ($capacity)"
        }
        while (true) {
            val waitMs = synchronized(lock) {
                refillInternal()
                if (availableTokens >= tokens) {
                    availableTokens -= tokens
                    return
                }
                calculateWaitMillis(tokens)
            }
            delay(waitMs)
        }
    }

    override fun acquireBlocking(tokens: Double, timeoutMillis: Long): Boolean {
        require(tokens <= capacity) {
            "Requested tokens ($tokens) exceeds bucket capacity ($capacity)"
        }
        val startTime = System.currentTimeMillis()

        while (true) {
            val waitMs = synchronized(lock) {
                refillInternal()
                if (availableTokens >= tokens) {
                    availableTokens -= tokens
                    return true
                }
                calculateWaitMillis(tokens)
            }

            val elapsed = System.currentTimeMillis() - startTime
            val remaining = timeoutMillis - elapsed
            if (remaining <= 0) {
                return false
            }

            val sleepTime = waitMs.coerceAtMost(remaining)
            if (!sleepQuietly(sleepTime)) {
                return false
            }
        }
    }

    override fun timeUntilAvailable(tokens: Double): Long = synchronized(lock) {
        refillInternal()
        calculateWaitMillis(tokens)
    }

    override fun reset() {
        synchronized(lock) {
            availableTokens = capacity
            lastRefillNanos = timeSource()
        }
    }

    /**
     * Executes the suspending [block] once [tokens] have been acquired.
     */
    suspend fun <T> execute(tokens: Double = RateLimiter.DEFAULT_ACQUIRE_AMOUNT, block: suspend () -> T): T {
        acquire(tokens)
        return block()
    }

    /**
     * Executes the blocking [block] once [tokens] have been acquired within [timeoutMillis].
     */
    fun <T> executeBlocking(
        tokens: Double = RateLimiter.DEFAULT_ACQUIRE_AMOUNT,
        timeoutMillis: Long = Long.MAX_VALUE,
        block: () -> T,
    ): T {
        val acquired = acquireBlocking(tokens, timeoutMillis)
        check(acquired) { "Rate limit timeout of ${timeoutMillis}ms exceeded" }
        return block()
    }

    private fun calculateWaitMillis(tokens: Double): Long {
        val deficit = tokens - availableTokens
        if (deficit <= 0.0) return 0L
        val seconds = deficit / refillRatePerSecond
        return ceil(seconds * MILLIS_PER_SECOND).toLong().coerceAtLeast(MIN_WAIT_MILLIS)
    }

    private fun refillInternal() {
        val now = timeSource()
        val elapsedNanos = now - lastRefillNanos
        if (elapsedNanos > 0) {
            val elapsedSeconds = elapsedNanos.toDouble() / NANOS_PER_SECOND
            val added = elapsedSeconds * refillRatePerSecond
            availableTokens = (availableTokens + added).coerceAtMost(capacity)
            lastRefillNanos = now
        }
    }

    private fun sleepQuietly(millis: Long): Boolean {
        if (millis <= 0) return true
        return try {
            Thread.sleep(millis)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    companion object {
        const val DEFAULT_CAPACITY: Double = 10.0
        const val DEFAULT_REFILL_RATE: Double = 8.0
        private const val MIN_WAIT_MILLIS: Long = 1L
        private const val MILLIS_PER_SECOND: Double = 1000.0
        private const val NANOS_PER_SECOND: Double = 1_000_000_000.0

        /**
         * Factory function creating a rate limiter based on requests per second.
         *
         * @param requestsPerSecond Sustained request rate.
         * @param burstSize Maximum simultaneous burst allowed (defaults to requestsPerSecond).
         */
        fun forRequestsPerSecond(requestsPerSecond: Double, burstSize: Double = requestsPerSecond): TokenBucket =
            TokenBucket(capacity = burstSize, refillRatePerSecond = requestsPerSecond)
    }
}
