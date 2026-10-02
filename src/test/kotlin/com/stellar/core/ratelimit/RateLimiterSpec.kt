package com.stellar.core.ratelimit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class RateLimiterSpec : FunSpec({

    test("TokenBucket forRequestsPerSecond sets correct capacity and refill rate") {
        val limiter = TokenBucket.forRequestsPerSecond(requestsPerSecond = 5.0, burstSize = 10.0)
        limiter.capacity shouldBe 10.0
        limiter.refillRatePerSecond shouldBe 5.0
        limiter.currentTokens shouldBe (10.0 plusOrMinus 0.01)
    }

    test("acquireBlocking successfully consumes tokens and respects timeout") {
        var mockNanos = 0L
        val bucket = TokenBucket(
            capacity = 1.0,
            refillRatePerSecond = 10.0,
            timeSource = { mockNanos },
        )

        bucket.tryAcquire(1.0) shouldBe true
        bucket.tryAcquire(1.0) shouldBe false

        // Advance 100ms (100_000_000 nanos) -> 1 token
        mockNanos += 100_000_000L
        bucket.acquireBlocking(1.0, timeoutMillis = 50L) shouldBe true
    }

    test("executeBlocking wraps action execution within rate limits") {
        val bucket = TokenBucket.forRequestsPerSecond(requestsPerSecond = 100.0, burstSize = 2.0)
        val result = bucket.executeBlocking(1.0) {
            "translated: hello"
        }
        result shouldBe "translated: hello"
    }

    test("KeyedTokenBucket isolates rate limits across different keys") {
        val keyedLimiter = KeyedTokenBucket<String> { provider ->
            when (provider) {
                "google" -> TokenBucket(capacity = 2.0, refillRatePerSecond = 1.0)
                "deepl" -> TokenBucket(capacity = 5.0, refillRatePerSecond = 2.0)
                else -> TokenBucket(capacity = 1.0, refillRatePerSecond = 0.5)
            }
        }

        // Google bucket allows 2, then runs out
        keyedLimiter.tryAcquire("google", 1.0) shouldBe true
        keyedLimiter.tryAcquire("google", 1.0) shouldBe true
        keyedLimiter.tryAcquire("google", 1.0) shouldBe false

        // DeepL bucket is completely independent and still has full capacity
        keyedLimiter.getBucket("deepl").currentTokens shouldBe (5.0 plusOrMinus 0.01)
        keyedLimiter.tryAcquire("deepl", 2.0) shouldBe true
        keyedLimiter.tryAcquire("deepl", 2.0) shouldBe true
        keyedLimiter.tryAcquire("deepl", 1.0) shouldBe true
        keyedLimiter.tryAcquire("deepl", 1.0) shouldBe false

        // Resetting Google does not alter DeepL
        keyedLimiter.reset("google")
        keyedLimiter.tryAcquire("google", 1.0) shouldBe true
        keyedLimiter.tryAcquire("deepl", 1.0) shouldBe false
    }

    test("timeUntilAvailable returns correct estimation") {
        var mockNanos = 0L
        val bucket = TokenBucket(
            capacity = 10.0,
            refillRatePerSecond = 2.0, // 2 tokens per second = 1 token per 500ms
            timeSource = { mockNanos },
        )

        bucket.tryAcquire(10.0) shouldBe true // 0 left
        // Request 1 token -> deficit = 1.0 / 2.0 = 0.5 sec = 500 ms
        val waitMs = bucket.timeUntilAvailable(1.0)
        waitMs shouldBe 500L
    }
})
