package com.stellar.core.action

/**
 * Three-dimensional coordinates in framework-agnostic space.
 */
data class Position3d(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    companion object {
        val ZERO = Position3d(x = 0.0, y = 0.0, z = 0.0)
    }
}

/**
 * Two-dimensional angular rotation representing yaw and pitch.
 */
data class Rotation2d(
    val yaw: Float,
    val pitch: Float,
) {
    companion object {
        val ZERO = Rotation2d(yaw = 0.0f, pitch = 0.0f)
    }
}

/**
 * Three-dimensional vector representing directional velocity or movement delta.
 */
data class Vector3d(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    companion object {
        val ZERO = Vector3d(x = 0.0, y = 0.0, z = 0.0)
    }
}

/**
 * Mockable interface exposing read-only client player state without Minecraft engine coupling.
 */
interface PlayerState {
    val position: Position3d
    val look: Rotation2d
    val velocity: Vector3d
    val isOnGround: Boolean
    val isSneaking: Boolean
    val isSprinting: Boolean
    val health: Float
    val selectedSlot: Int
}

/**
 * Immutable default implementation of [PlayerState].
 */
data class ImmutablePlayerState(
    override val position: Position3d = Position3d.ZERO,
    override val look: Rotation2d = Rotation2d.ZERO,
    override val velocity: Vector3d = Vector3d.ZERO,
    override val isOnGround: Boolean = true,
    override val isSneaking: Boolean = false,
    override val isSprinting: Boolean = false,
    override val health: Float = DEFAULT_MAX_HEALTH,
    override val selectedSlot: Int = 0,
) : PlayerState {
    companion object {
        private const val DEFAULT_MAX_HEALTH = 20.0f
    }
}

/**
 * Mockable interface exposing read-only world and environmental state.
 */
interface WorldState {
    val dimensionId: String
    val isDay: Boolean
    val isRaining: Boolean
}

/**
 * Immutable default implementation of [WorldState].
 */
data class ImmutableWorldState(
    override val dimensionId: String = "minecraft:overworld",
    override val isDay: Boolean = true,
    override val isRaining: Boolean = false,
) : WorldState

/**
 * Immutable context injected into a [ModAction] during execution.
 *
 * This context is framework-agnostic, carrying zero Minecraft engine imports to preserve
 * Clean Architecture boundaries. All nested game state is represented via interfaces and
 * immutable data classes, ensuring straightforward mocking in unit tests.
 *
 * @property currentTick The monotonically increasing client game tick count.
 * @property partialTicks Fractional sub-tick interpolation factor (0.0 to 1.0).
 * @property timestampMs Monotonic or wall-clock epoch timestamp in milliseconds.
 * @property player Mockable snapshot of player spatial, physical, and vital state.
 * @property world Mockable snapshot of the world and environment.
 * @property attributes Extensible dictionary for ad-hoc or feature-specific metadata.
 */
data class ActionContext(
    val currentTick: Long,
    val partialTicks: Float = 0.0f,
    val timestampMs: Long = System.currentTimeMillis(),
    val player: PlayerState = ImmutablePlayerState(),
    val world: WorldState = ImmutableWorldState(),
    val attributes: Map<String, Any> = emptyMap(),
) {
    companion object {
        /**
         * Convenience factory creating an [ActionContext] with sane defaults for fast unit testing.
         */
        fun createMock(
            currentTick: Long = 0L,
            partialTicks: Float = 0.0f,
            timestampMs: Long = 0L,
            player: PlayerState = ImmutablePlayerState(),
            world: WorldState = ImmutableWorldState(),
            attributes: Map<String, Any> = emptyMap(),
        ): ActionContext = ActionContext(
            currentTick = currentTick,
            partialTicks = partialTicks,
            timestampMs = timestampMs,
            player = player,
            world = world,
            attributes = attributes,
        )
    }
}
