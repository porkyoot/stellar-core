package com.stellar.core.action

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class TokenBucketSpec : FunSpec({

    test("initial tokens equal full capacity") {
        val bucket = TokenBucket(capacity = 10.0, refillRatePerSecond = 5.0)
        bucket.currentTokens shouldBe (10.0 plusOrMinus 0.001)
    }

    test("tryConsume consumes available tokens and rejects when empty") {
        var mockNanos = 0L
        val bucket = TokenBucket(
            capacity = 2.0,
            refillRatePerSecond = 1.0,
            timeSource = { mockNanos },
        )

        bucket.tryConsume(1.0) shouldBe true
        bucket.tryConsume(1.0) shouldBe true
        bucket.tryConsume(1.0) shouldBe false
    }

    test("tokens regenerate over simulated elapsed time without exceeding capacity") {
        var mockNanos = 0L
        val bucket = TokenBucket(
            capacity = 5.0,
            refillRatePerSecond = 2.0, // 2 tokens per second
            timeSource = { mockNanos },
        )

        // Drain completely
        bucket.tryConsume(5.0) shouldBe true
        bucket.tryConsume(1.0) shouldBe false

        // Advance time by 1.5 seconds (1_500_000_000 nanos) -> +3.0 tokens
        mockNanos += 1_500_000_000L

        bucket.currentTokens shouldBe (3.0 plusOrMinus 0.05)
        bucket.tryConsume(2.0) shouldBe true
        bucket.tryConsume(2.0) shouldBe false

        // Advance time by 10 seconds -> should cap at capacity (5.0)
        mockNanos += 10_000_000_000L
        bucket.currentTokens shouldBe (5.0 plusOrMinus 0.001)
    }

    test("reset restores token count to capacity") {
        var mockNanos = 0L
        val bucket = TokenBucket(
            capacity = 4.0,
            refillRatePerSecond = 1.0,
            timeSource = { mockNanos },
        )

        bucket.tryConsume(4.0) shouldBe true
        bucket.tryConsume(1.0) shouldBe false

        bucket.reset()
        bucket.currentTokens shouldBe (4.0 plusOrMinus 0.001)
    }
})
