package com.stellar.core.input

/**
 * Functional interface to query whether a GUI/Screen is currently open without importing client classes.
 */
fun interface ScreenContextProvider {
    fun isScreenOpen(): Boolean

    companion object {
        val NONE: ScreenContextProvider = ScreenContextProvider { false }
    }
}

/**
 * Filter determining under which screen states a binding can trigger.
 */
enum class InputContextFilter {
    /** Trigger anytime, whether in-game or inside a screen. */
    ALWAYS,

    /** Trigger only during gameplay when no screen/GUI is open. */
    IN_GAME_ONLY,

    /** Trigger only when a screen or GUI is actively open. */
    IN_GUI_ONLY,
    ;

    fun allows(isScreenOpen: Boolean): Boolean = when (this) {
        ALWAYS -> true
        IN_GAME_ONLY -> !isScreenOpen
        IN_GUI_ONLY -> isScreenOpen
    }
}

/**
 * Read-only view of keyboard, mouse, and game action input state.
 */
interface InputStateReader {
    val activeModifiers: ModifierSet

    fun isKeyDown(keyCode: Int): Boolean

    fun isMouseButtonDown(button: Int): Boolean

    fun isModifierActive(modifier: Modifier): Boolean

    fun isActionKeyDown(action: GameAction): Boolean
}

val InputStateReader.isCtrlDown: Boolean
    get() = isModifierActive(Modifier.CTRL)

val InputStateReader.isShiftDown: Boolean
    get() = isModifierActive(Modifier.SHIFT)

val InputStateReader.isAltDown: Boolean
    get() = isModifierActive(Modifier.ALT)

val InputStateReader.isSuperDown: Boolean
    get() = isModifierActive(Modifier.SUPER)

val InputStateReader.isCrouchKeyDown: Boolean
    get() = isActionKeyDown(GameAction.CROUCH)

val InputStateReader.isSprintKeyDown: Boolean
    get() = isActionKeyDown(GameAction.SPRINT)

val InputStateReader.isJumpKeyDown: Boolean
    get() = isActionKeyDown(GameAction.JUMP)

/**
 * Context payload provided to input binding callbacks.
 */
data class InputEventContext(
    val timestamp: Long,
    val modifiers: ModifierSet,
    val isScreenOpen: Boolean,
    val state: InputStateReader,
)
