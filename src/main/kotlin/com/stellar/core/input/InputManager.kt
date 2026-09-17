package com.stellar.core.input

import org.lwjgl.glfw.GLFW
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central input management and dispatching engine.
 */
class InputManager(
    var screenContextProvider: ScreenContextProvider = ScreenContextProvider.NONE,
) {
    val stateTracker: InputStateTracker = InputStateTracker()

    var actionResolver: GameActionKeyResolver
        get() = stateTracker.actionResolver
        set(value) {
            stateTracker.actionResolver = value
        }

    private val keyBindings = CopyOnWriteArrayList<KeyBinding>()
    private val mouseBindings = CopyOnWriteArrayList<MouseBinding>()
    private val scrollBindings = CopyOnWriteArrayList<ScrollBinding>()

    fun register(binding: KeyBinding) {
        unregister(binding.id)
        keyBindings.add(binding)
    }

    fun register(binding: MouseBinding) {
        unregister(binding.id)
        mouseBindings.add(binding)
    }

    fun register(binding: ScrollBinding) {
        unregister(binding.id)
        scrollBindings.add(binding)
    }

    fun unregister(id: String): Boolean {
        val removedKey = keyBindings.removeIf { it.id == id }
        val removedMouse = mouseBindings.removeIf { it.id == id }
        val removedScroll = scrollBindings.removeIf { it.id == id }
        return removedKey || removedMouse || removedScroll
    }

    fun clear() {
        keyBindings.clear()
        mouseBindings.clear()
        scrollBindings.clear()
        stateTracker.reset()
    }

    fun getBinding(id: String): InputBinding? {
        return keyBindings.firstOrNull { it.id == id }
            ?: mouseBindings.firstOrNull { it.id == id }
            ?: scrollBindings.firstOrNull { it.id == id }
    }

    fun onKey(key: Int, action: Int, glfwMods: Int, timestamp: Long = System.currentTimeMillis()): EventResult {
        updateKeyState(key, action, glfwMods, timestamp)
        val activeMods = stateTracker.activeModifiers
        val isScreenOpen = screenContextProvider.isScreenOpen()
        val keyAction = KeyAction.fromGlfw(action)

        val candidates = keyBindings.filter { binding ->
            binding.isEnabled &&
                binding.action == keyAction &&
                binding.contextFilter.allows(isScreenOpen) &&
                binding.combo.matches(key, activeMods, actionResolver)
        }.sortedWith(KEY_BINDING_COMPARATOR)

        val context = InputEventContext(timestamp, activeMods, isScreenOpen, stateTracker)
        for (binding in candidates) {
            if (binding.handler(context).isConsumed) {
                return EventResult.CONSUMED
            }
        }
        return EventResult.PASS
    }

    fun onMouseButton(button: Int, action: Int, mods: Int, timestamp: Long = System.currentTimeMillis()): EventResult {
        updateMouseState(button, action, mods, timestamp)
        val activeMods = stateTracker.activeModifiers
        val isScreenOpen = screenContextProvider.isScreenOpen()
        val keyAction = KeyAction.fromGlfw(action)

        val candidates = mouseBindings.filter { binding ->
            binding.isEnabled &&
                binding.action == keyAction &&
                binding.contextFilter.allows(isScreenOpen) &&
                binding.combo.matches(button, activeMods)
        }.sortedWith(MOUSE_BINDING_COMPARATOR)

        val context = InputEventContext(timestamp, activeMods, isScreenOpen, stateTracker)
        for (binding in candidates) {
            if (binding.handler(context).isConsumed) {
                return EventResult.CONSUMED
            }
        }
        return EventResult.PASS
    }

    fun onMouseScroll(horizontal: Double, vertical: Double, timestamp: Long = System.currentTimeMillis()): EventResult {
        val delta = ScrollDelta.fromDelta(horizontal, vertical)
        if (delta.direction == ScrollDirection.NONE) return EventResult.PASS

        val activeMods = stateTracker.activeModifiers
        val isScreenOpen = screenContextProvider.isScreenOpen()

        val candidates = scrollBindings.filter { binding ->
            binding.isEnabled &&
                binding.contextFilter.allows(isScreenOpen) &&
                binding.combo.matches(delta.direction, activeMods)
        }.sortedWith(SCROLL_BINDING_COMPARATOR)

        val context = InputEventContext(timestamp, activeMods, isScreenOpen, stateTracker)
        for (binding in candidates) {
            if (binding.handler(delta, context).isConsumed) {
                return EventResult.CONSUMED
            }
        }
        return EventResult.PASS
    }

    fun onWindowBlur() {
        stateTracker.reset()
    }

    private fun updateKeyState(key: Int, action: Int, glfwModifiers: Int, timestamp: Long) {
        when (action) {
            GLFW.GLFW_PRESS -> stateTracker.onKeyPressed(key, timestamp)
            GLFW.GLFW_RELEASE -> stateTracker.onKeyReleased(key)
        }
        stateTracker.updateModifiers(glfwModifiers)
    }

    private fun updateMouseState(button: Int, action: Int, glfwModifiers: Int, timestamp: Long) {
        when (action) {
            GLFW.GLFW_PRESS -> stateTracker.onMouseButtonPressed(button, timestamp)
            GLFW.GLFW_RELEASE -> stateTracker.onMouseButtonReleased(button)
        }
        stateTracker.updateModifiers(glfwModifiers)
    }

    companion object {
        private val KEY_BINDING_COMPARATOR = Comparator<KeyBinding> { a, b ->
            val priorityDiff = b.priority.compareTo(a.priority)
            if (priorityDiff != 0) priorityDiff else b.combo.modifiers.count.compareTo(a.combo.modifiers.count)
        }

        private val MOUSE_BINDING_COMPARATOR = Comparator<MouseBinding> { a, b ->
            val priorityDiff = b.priority.compareTo(a.priority)
            if (priorityDiff != 0) priorityDiff else b.combo.modifiers.count.compareTo(a.combo.modifiers.count)
        }

        private val SCROLL_BINDING_COMPARATOR = Comparator<ScrollBinding> { a, b ->
            val priorityDiff = b.priority.compareTo(a.priority)
            if (priorityDiff != 0) priorityDiff else b.combo.modifiers.count.compareTo(a.combo.modifiers.count)
        }
    }
}
