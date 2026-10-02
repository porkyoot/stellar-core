package com.stellar.core.action

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ModActionSpec : FunSpec({

    test("ActionPriority natural ordering places CRITICAL before HIGH, NORMAL, and LOW") {
        val priorities = listOf(
            ActionPriority.LOW,
            ActionPriority.CRITICAL,
            ActionPriority.NORMAL,
            ActionPriority.HIGH,
        )

        val sorted = priorities.sorted()

        sorted shouldContainExactly listOf(
            ActionPriority.CRITICAL,
            ActionPriority.HIGH,
            ActionPriority.NORMAL,
            ActionPriority.LOW,
        )
    }

    test("ModActions sort inherently by priority with CRITICAL first") {
        val lowAction = object : ModAction {
            override val priority = ActionPriority.LOW
            override suspend fun execute(context: ActionContext) = ActionResult.Success
        }
        val criticalAction = object : ModAction {
            override val priority = ActionPriority.CRITICAL
            override suspend fun execute(context: ActionContext) = ActionResult.Success
        }
        val normalAction = object : ModAction {
            override val priority = ActionPriority.NORMAL
            override suspend fun execute(context: ActionContext) = ActionResult.Success
        }
        val highAction = object : ModAction {
            override val priority = ActionPriority.HIGH
            override suspend fun execute(context: ActionContext) = ActionResult.Success
        }

        val queue = listOf(normalAction, lowAction, criticalAction, highAction).sorted()

        queue shouldContainExactly listOf(criticalAction, highAction, normalAction, lowAction)
    }

    test("ActionResult sealed hierarchy models Success, Failure, and Deferred strictly") {
        val success: ActionResult = ActionResult.Success
        val failure: ActionResult = ActionResult.Failure("Item count exhausted")
        val deferred: ActionResult = ActionResult.Deferred

        success.isSuccess shouldBe true
        success.isFailure shouldBe false
        success.isDeferred shouldBe false

        failure.isSuccess shouldBe false
        failure.isFailure shouldBe true
        (failure as ActionResult.Failure).reason shouldBe "Item count exhausted"

        deferred.isDeferred shouldBe true

        // Exhaustive pattern matching validation
        val message = when (success) {
            is ActionResult.Success -> "OK"
            is ActionResult.Failure -> "FAIL: ${success.reason}"
            is ActionResult.Deferred -> "DEFERRED"
        }
        message shouldBe "OK"
    }

    test("ActionContext is immutable, mockable, and carries framework-agnostic state") {
        val customPlayer = object : PlayerState {
            override val position = Position3d(x = 100.0, y = 64.0, z = -200.0)
            override val look = Rotation2d(yaw = 90.0f, pitch = 0.0f)
            override val velocity = Vector3d(x = 0.1, y = 0.0, z = 0.0)
            override val isOnGround = true
            override val isSneaking = true
            override val isSprinting = false
            override val health = 18.5f
            override val selectedSlot = 2
        }

        val customWorld = object : WorldState {
            override val dimensionId = "minecraft:the_nether"
            override val isDay = false
            override val isRaining = false
        }

        val context = ActionContext.createMock(
            currentTick = 1200L,
            player = customPlayer,
            world = customWorld,
            attributes = mapOf("targetBlock" to "minecraft:ancient_debris"),
        )

        context.currentTick shouldBe 1200L
        context.player.position.x shouldBe 100.0
        context.player.isSneaking shouldBe true
        context.player.health shouldBe 18.5f
        context.world.dimensionId shouldBe "minecraft:the_nether"
        context.attributes["targetBlock"] shouldBe "minecraft:ancient_debris"
    }

    test("ModAction executes and handles cancel lifecycle") {
        var cancelled = false
        val action = object : ModAction {
            override val priority = ActionPriority.HIGH
            override suspend fun execute(context: ActionContext): ActionResult {
                return if (context.player.health < 5.0f) {
                    ActionResult.Deferred
                } else {
                    ActionResult.Success
                }
            }

            override fun cancel() {
                cancelled = true
            }
        }

        val lowHealthContext = ActionContext.createMock(
            player = ImmutablePlayerState(health = 3.0f),
        )
        val highHealthContext = ActionContext.createMock(
            player = ImmutablePlayerState(health = 20.0f),
        )

        action.execute(lowHealthContext) shouldBe ActionResult.Deferred
        action.execute(highHealthContext) shouldBe ActionResult.Success

        action.cancel()
        cancelled shouldBe true
    }
})
