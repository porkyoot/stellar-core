package com.stellar.core.action

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class ActionDispatcherSpec : FunSpec({

    test("CRITICAL and HIGH actions bypass token bucket and execute immediately") {
        var mockNanos = 0L
        val bucket = TokenBucket(
            capacity = 0.0,
            refillRatePerSecond = 0.0,
            timeSource = { mockNanos },
        )
        val dispatcher = ActionDispatcher(tokenBucket = bucket)

        val executed = mutableListOf<String>()

        dispatcher.enqueue(object : ModAction {
            override val priority = ActionPriority.CRITICAL
            override suspend fun execute(context: ActionContext): ActionResult {
                executed.add("CRITICAL")
                return ActionResult.Success
            }
        })

        dispatcher.enqueue(object : ModAction {
            override val priority = ActionPriority.HIGH
            override suspend fun execute(context: ActionContext): ActionResult {
                executed.add("HIGH")
                return ActionResult.Success
            }
        })

        dispatcher.enqueue(object : ModAction {
            override val priority = ActionPriority.NORMAL
            override suspend fun execute(context: ActionContext): ActionResult {
                executed.add("NORMAL")
                return ActionResult.Success
            }
        })

        dispatcher.tick()

        // Critical and High should execute despite empty token bucket; Normal should wait
        executed shouldContainExactly listOf("CRITICAL", "HIGH")
        dispatcher.pendingCount shouldBe 1
    }

    test("NORMAL and LOW actions consume tokens and respect burst capacity") {
        var mockNanos = 0L
        val bucket = TokenBucket(
            capacity = 2.0,
            refillRatePerSecond = 0.0,
            timeSource = { mockNanos },
        )
        val dispatcher = ActionDispatcher(tokenBucket = bucket, maxActionsPerTick = 10)

        val executed = mutableListOf<String>()

        dispatcher.enqueue(object : ModAction {
            override val priority = ActionPriority.NORMAL
            override suspend fun execute(context: ActionContext): ActionResult {
                executed.add("N1")
                return ActionResult.Success
            }
        })

        dispatcher.enqueue(object : ModAction {
            override val priority = ActionPriority.NORMAL
            override suspend fun execute(context: ActionContext): ActionResult {
                executed.add("N2")
                return ActionResult.Success
            }
        })

        dispatcher.enqueue(object : ModAction {
            override val priority = ActionPriority.LOW
            override suspend fun execute(context: ActionContext): ActionResult {
                executed.add("L1")
                return ActionResult.Success
            }
        })

        dispatcher.tick()

        // Only 2 tokens were available, so 2 actions execute; L1 remains queued
        executed shouldContainExactly listOf("N1", "N2")
        dispatcher.pendingCount shouldBe 1

        // Refill 1 token by advancing time
        mockNanos += 1_000_000_000L
        bucket.reset()

        dispatcher.tick()
        executed shouldContainExactly listOf("N1", "N2", "L1")
        dispatcher.pendingCount shouldBe 0
    }

    test("max-actions-per-tick threshold bounds rate-limited execution") {
        val bucket = TokenBucket(capacity = 10.0, refillRatePerSecond = 10.0)
        val dispatcher = ActionDispatcher(tokenBucket = bucket, maxActionsPerTick = 2)

        val executed = mutableListOf<String>()

        for (i in 1..5) {
            dispatcher.enqueue(object : ModAction {
                override val priority = ActionPriority.NORMAL
                override suspend fun execute(context: ActionContext): ActionResult {
                    executed.add("N$i")
                    return ActionResult.Success
                }
            })
        }

        dispatcher.tick()

        // Although 10 tokens exist, maxActionsPerTick = 2 limits execution
        executed shouldContainExactly listOf("N1", "N2")
        dispatcher.pendingCount shouldBe 3
    }

    test("actions within the same priority tier preserve FIFO ordering") {
        val bucket = TokenBucket(capacity = 10.0, refillRatePerSecond = 10.0)
        val dispatcher = ActionDispatcher(tokenBucket = bucket, maxActionsPerTick = 10)

        val order = mutableListOf<Int>()

        for (i in 1..4) {
            dispatcher.enqueue(object : ModAction {
                override val priority = ActionPriority.NORMAL
                override suspend fun execute(context: ActionContext): ActionResult {
                    order.add(i)
                    return ActionResult.Success
                }
            })
        }

        dispatcher.tick()
        order shouldContainExactly listOf(1, 2, 3, 4)
    }

    test("Failure result emits ActionFailureEvent to SharedFlow") {
        val dispatcher = ActionDispatcher()

        val failingAction = object : ModAction {
            override val priority = ActionPriority.HIGH
            override suspend fun execute(context: ActionContext): ActionResult {
                return ActionResult.Failure("Simulated network timeout")
            }
        }

        dispatcher.enqueue(failingAction)

        val context = ActionContext.createMock(currentTick = 42L)
        dispatcher.tick(context)

        val emittedEvent = dispatcher.failures.first()
        emittedEvent.action shouldBe failingAction
        emittedEvent.result.reason shouldBe "Simulated network timeout"
        emittedEvent.tick shouldBe 42L
    }

    test("Deferred result is safely re-enqueued for next tick without spin-looping") {
        val dispatcher = ActionDispatcher()

        var callCount = 0
        val deferringAction = object : ModAction {
            override val priority = ActionPriority.CRITICAL
            override suspend fun execute(context: ActionContext): ActionResult {
                callCount++
                return if (callCount == 1) {
                    ActionResult.Deferred
                } else {
                    ActionResult.Success
                }
            }
        }

        dispatcher.enqueue(deferringAction)

        // First tick: calls once, defers, re-enqueues
        dispatcher.tick()
        callCount shouldBe 1
        dispatcher.pendingCount shouldBe 1

        // Second tick: calls second time, succeeds, queue becomes empty
        dispatcher.tick()
        callCount shouldBe 2
        dispatcher.pendingCount shouldBe 0
    }

    test("cancelAll drains queue and triggers cancel on pending actions") {
        val dispatcher = ActionDispatcher()
        var cancelledCount = 0

        for (i in 1..3) {
            dispatcher.enqueue(object : ModAction {
                override val priority = ActionPriority.LOW
                override suspend fun execute(context: ActionContext) = ActionResult.Success
                override fun cancel() {
                    cancelledCount++
                }
            })
        }

        dispatcher.pendingCount shouldBe 3
        dispatcher.cancelAll()

        dispatcher.pendingCount shouldBe 0
        cancelledCount shouldBe 3
    }
})
