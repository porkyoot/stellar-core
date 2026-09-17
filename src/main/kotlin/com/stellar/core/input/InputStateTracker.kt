package com.stellar.core.input

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the live state of keyboard keys, mouse buttons, modifiers, and game action keys.
 */
class InputStateTracker : InputStateReader {
    private val pressedKeys = ConcurrentHashMap.newKeySet<Int>()
    private val pressedButtons = ConcurrentHashMap.newKeySet<Int>()
    private val lastPressTime = ConcurrentHashMap<Int, Long>()

    @Volatile
    private var explicitModifiers: ModifierSet = ModifierSet.NONE

    var actionResolver: GameActionKeyResolver = GameActionKeyResolver.NONE

    override val activeModifiers: ModifierSet
        get() {
            var mask = explicitModifiers.mask
            if (isKeyDown(Key.KEY_LEFT_CONTROL) || isKeyDown(Key.KEY_RIGHT_CONTROL)) {
                mask = mask or Modifier.CTRL.mask
            }
            if (isKeyDown(Key.KEY_LEFT_SHIFT) || isKeyDown(Key.KEY_RIGHT_SHIFT)) {
                mask = mask or Modifier.SHIFT.mask
            }
            if (isKeyDown(Key.KEY_LEFT_ALT) || isKeyDown(Key.KEY_RIGHT_ALT)) {
                mask = mask or Modifier.ALT.mask
            }
            if (isKeyDown(Key.KEY_LEFT_SUPER) || isKeyDown(Key.KEY_RIGHT_SUPER)) {
                mask = mask or Modifier.SUPER.mask
            }
            return ModifierSet(mask)
        }

    override fun isKeyDown(keyCode: Int): Boolean = pressedKeys.contains(keyCode)

    override fun isMouseButtonDown(button: Int): Boolean = pressedButtons.contains(button)

    override fun isModifierActive(modifier: Modifier): Boolean {
        return activeModifiers.contains(modifier)
    }

    override fun isActionKeyDown(action: GameAction): Boolean {
        if (actionResolver.isActionKeyDown(action)) return true
        val resolved = actionResolver.resolveKey(action)
        if (resolved != null) {
            return isKeyDown(resolved) || isMouseButtonDown(resolved)
        }
        return defaultActionCheck(action)
    }

    fun onKeyPressed(keyCode: Int, timestamp: Long = System.currentTimeMillis()): Boolean {
        val wasPressed = pressedKeys.contains(keyCode)
        pressedKeys.add(keyCode)
        lastPressTime[keyCode] = timestamp
        return !wasPressed
    }

    fun onKeyReleased(keyCode: Int) {
        pressedKeys.remove(keyCode)
    }

    fun onMouseButtonPressed(button: Int, timestamp: Long = System.currentTimeMillis()): Boolean {
        val wasPressed = pressedButtons.contains(button)
        pressedButtons.add(button)
        lastPressTime[button] = timestamp
        return !wasPressed
    }

    fun onMouseButtonReleased(button: Int) {
        pressedButtons.remove(button)
    }

    fun updateModifiers(glfwModifiers: Int) {
        explicitModifiers = ModifierSet.fromGlfwModifiers(glfwModifiers)
    }

    fun isDoublePress(code: Int, currentTimestamp: Long, thresholdMs: Long = DEFAULT_DOUBLE_PRESS_MS): Boolean {
        val previous = lastPressTime[code] ?: return false
        val diff = currentTimestamp - previous
        return diff in 1..thresholdMs
    }

    /**
     * Resets all pressed key and button states to prevent stuck keys on focus lost.
     */
    fun reset() {
        pressedKeys.clear()
        pressedButtons.clear()
        lastPressTime.clear()
        explicitModifiers = ModifierSet.NONE
    }

    private fun defaultActionCheck(action: GameAction): Boolean = when (action) {
        GameAction.CROUCH -> isKeyDown(Key.KEY_LEFT_SHIFT) || isKeyDown(Key.KEY_RIGHT_SHIFT)
        GameAction.SPRINT -> isKeyDown(Key.KEY_LEFT_CONTROL) || isKeyDown(Key.KEY_RIGHT_CONTROL)
        GameAction.JUMP -> isKeyDown(Key.KEY_SPACE)
        else -> false
    }

    companion object {
        const val DEFAULT_DOUBLE_PRESS_MS: Long = 250L
    }
}
