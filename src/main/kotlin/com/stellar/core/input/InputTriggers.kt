package com.stellar.core.input

import org.lwjgl.glfw.GLFW

/**
 * Strategy for matching active modifiers against a combo's expected modifiers.
 */
enum class ModifierMatch {
    /** Active modifiers must match the expected modifiers exactly. */
    EXACT,

    /** Active modifiers must contain all expected modifiers, but may contain extra modifiers. */
    ALLOW_EXTRA,

    /** Modifiers are ignored entirely. */
    ANY,
    ;

    fun matches(active: ModifierSet, expected: ModifierSet): Boolean = when (this) {
        EXACT -> active == expected
        ALLOW_EXTRA -> active.mask and expected.mask == expected.mask
        ANY -> true
    }
}

/**
 * Key transition actions mirroring GLFW actions.
 */
enum class KeyAction(val glfwAction: Int) {
    PRESS(GLFW.GLFW_PRESS),
    RELEASE(GLFW.GLFW_RELEASE),
    REPEAT(GLFW.GLFW_REPEAT),
    ;

    companion object {
        fun fromGlfw(action: Int): KeyAction = when (action) {
            GLFW.GLFW_PRESS -> PRESS
            GLFW.GLFW_RELEASE -> RELEASE
            GLFW.GLFW_REPEAT -> REPEAT
            else -> PRESS
        }
    }
}

/**
 * Represents mouse wheel scroll displacement and direction.
 */
data class ScrollDelta(
    val horizontal: Double,
    val vertical: Double,
    val direction: ScrollDirection,
) {
    companion object {
        fun fromDelta(horizontal: Double, vertical: Double): ScrollDelta {
            val direction = when {
                vertical > 0.0 -> ScrollDirection.UP
                vertical < 0.0 -> ScrollDirection.DOWN
                horizontal > 0.0 -> ScrollDirection.RIGHT
                horizontal < 0.0 -> ScrollDirection.LEFT
                else -> ScrollDirection.NONE
            }
            return ScrollDelta(horizontal, vertical, direction)
        }
    }
}

/**
 * Combination of a keyboard key or high-level game action and modifier conditions.
 */
data class KeyCombo(
    val key: Int = Key.UNKNOWN,
    val modifiers: ModifierSet = ModifierSet.NONE,
    val match: ModifierMatch = ModifierMatch.EXACT,
    val gameAction: GameAction? = null,
)

fun KeyCombo.matches(
    activeKey: Int,
    activeMods: ModifierSet,
    resolver: GameActionKeyResolver = GameActionKeyResolver.NONE,
): Boolean {
    val targetKey = if (gameAction != null) {
        resolver.resolveKey(gameAction) ?: key
    } else {
        key
    }
    return targetKey == activeKey && match.matches(activeMods, modifiers)
}

/**
 * Combination of a mouse button and modifier conditions.
 */
data class MouseCombo(
    val button: Int,
    val modifiers: ModifierSet = ModifierSet.NONE,
    val match: ModifierMatch = ModifierMatch.EXACT,
)

fun MouseCombo.matches(activeButton: Int, activeMods: ModifierSet): Boolean {
    return button == activeButton && match.matches(activeMods, modifiers)
}

/**
 * Combination of a mouse scroll direction and modifier conditions.
 */
data class ScrollCombo(
    val direction: ScrollDirection,
    val modifiers: ModifierSet = ModifierSet.NONE,
    val match: ModifierMatch = ModifierMatch.EXACT,
)

fun ScrollCombo.matches(scrollDir: ScrollDirection, activeMods: ModifierSet): Boolean {
    val dirMatches = direction == ScrollDirection.NONE || direction == scrollDir
    return dirMatches && match.matches(activeMods, modifiers)
}
