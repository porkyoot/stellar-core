package com.stellar.core.input

/**
 * High-level game actions whose physical key mappings can be configured by players.
 */
enum class GameAction {
    CROUCH,
    SPRINT,
    JUMP,
    ATTACK,
    USE,
    FORWARD,
    BACK,
    LEFT,
    RIGHT,
    DROP,
    INVENTORY,
}

/**
 * Resolves a high-level game action into its currently mapped physical key or button code.
 */
fun interface GameActionKeyResolver {
    fun resolveKey(action: GameAction): Int?

    fun isActionKeyDown(action: GameAction): Boolean = false

    companion object {
        val NONE: GameActionKeyResolver = GameActionKeyResolver { null }
    }
}
