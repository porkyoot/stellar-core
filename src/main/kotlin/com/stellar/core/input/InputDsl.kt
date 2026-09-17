package com.stellar.core.input

@DslMarker
annotation class InputDslMarker

@InputDslMarker
class KeyBindingBuilder(val id: String) {
    var key: Int = Key.UNKNOWN
    var gameAction: GameAction? = null
    var modifiers: ModifierSet = ModifierSet.NONE
    var match: ModifierMatch = ModifierMatch.EXACT
    var action: KeyAction = KeyAction.PRESS
    var priority: Int = BindingPriority.NORMAL
    var context: InputContextFilter = InputContextFilter.ALWAYS
    var isEnabled: Boolean = true
    private var handler: ((InputEventContext) -> EventResult)? = null

    fun modifiers(vararg mods: Modifier) {
        modifiers = ModifierSet.combine(*mods)
    }

    fun gameAction(action: GameAction) {
        this.gameAction = action
    }

    fun onPress(block: (InputEventContext) -> EventResult) {
        action = KeyAction.PRESS
        handler = block
    }

    fun onRelease(block: (InputEventContext) -> EventResult) {
        action = KeyAction.RELEASE
        handler = block
    }

    fun onRepeat(block: (InputEventContext) -> EventResult) {
        action = KeyAction.REPEAT
        handler = block
    }

    fun build(): KeyBinding {
        val activeHandler = handler ?: { EventResult.PASS }
        return KeyBinding(
            id = id,
            combo = KeyCombo(key, modifiers, match, gameAction),
            action = action,
            priority = priority,
            contextFilter = context,
            isEnabled = isEnabled,
            handler = activeHandler,
        )
    }
}

@InputDslMarker
class MouseBindingBuilder(val id: String) {
    var button: Int = Key.MOUSE_BUTTON_LEFT
    var modifiers: ModifierSet = ModifierSet.NONE
    var match: ModifierMatch = ModifierMatch.EXACT
    var action: KeyAction = KeyAction.PRESS
    var priority: Int = BindingPriority.NORMAL
    var context: InputContextFilter = InputContextFilter.ALWAYS
    var isEnabled: Boolean = true
    private var handler: ((InputEventContext) -> EventResult)? = null

    fun modifiers(vararg mods: Modifier) {
        modifiers = ModifierSet.combine(*mods)
    }

    fun onPress(block: (InputEventContext) -> EventResult) {
        action = KeyAction.PRESS
        handler = block
    }

    fun onRelease(block: (InputEventContext) -> EventResult) {
        action = KeyAction.RELEASE
        handler = block
    }

    fun build(): MouseBinding {
        val activeHandler = handler ?: { EventResult.PASS }
        return MouseBinding(
            id = id,
            combo = MouseCombo(button, modifiers, match),
            action = action,
            priority = priority,
            contextFilter = context,
            isEnabled = isEnabled,
            handler = activeHandler,
        )
    }
}

@InputDslMarker
class ScrollBindingBuilder(val id: String) {
    var direction: ScrollDirection = ScrollDirection.NONE
    var modifiers: ModifierSet = ModifierSet.NONE
    var match: ModifierMatch = ModifierMatch.EXACT
    var priority: Int = BindingPriority.NORMAL
    var context: InputContextFilter = InputContextFilter.ALWAYS
    var isEnabled: Boolean = true
    private var handler: ((ScrollDelta, InputEventContext) -> EventResult)? = null

    fun modifiers(vararg mods: Modifier) {
        modifiers = ModifierSet.combine(*mods)
    }

    fun onScroll(block: (ScrollDelta, InputEventContext) -> EventResult) {
        handler = block
    }

    fun build(): ScrollBinding {
        val activeHandler = handler ?: { _, _ -> EventResult.PASS }
        return ScrollBinding(
            id = id,
            combo = ScrollCombo(direction, modifiers, match),
            priority = priority,
            contextFilter = context,
            isEnabled = isEnabled,
            handler = activeHandler,
        )
    }
}

fun InputManager.registerKey(id: String, block: KeyBindingBuilder.() -> Unit): KeyBinding {
    val builder = KeyBindingBuilder(id).apply(block)
    val binding = builder.build()
    register(binding)
    return binding
}

fun InputManager.registerMouse(id: String, block: MouseBindingBuilder.() -> Unit): MouseBinding {
    val builder = MouseBindingBuilder(id).apply(block)
    val binding = builder.build()
    register(binding)
    return binding
}

fun InputManager.registerScroll(id: String, block: ScrollBindingBuilder.() -> Unit): ScrollBinding {
    val builder = ScrollBindingBuilder(id).apply(block)
    val binding = builder.build()
    register(binding)
    return binding
}
