package com.stellar.core.input

/**
 * Priority levels for input bindings. Higher priority bindings are evaluated first.
 */
object BindingPriority {
    const val HIGHEST: Int = 1000
    const val HIGH: Int = 100
    const val NORMAL: Int = 0
    const val LOW: Int = -100
    const val LOWEST: Int = -1000
}

/**
 * Base interface for all input bindings.
 */
sealed interface InputBinding {
    val id: String
    val priority: Int
    val contextFilter: InputContextFilter
    val isEnabled: Boolean
}

/**
 * Binding triggered by a keyboard key and modifier conditions.
 */
data class KeyBinding(
    override val id: String,
    val combo: KeyCombo,
    val action: KeyAction = KeyAction.PRESS,
    override val priority: Int = BindingPriority.NORMAL,
    override val contextFilter: InputContextFilter = InputContextFilter.ALWAYS,
    override val isEnabled: Boolean = true,
    val handler: (InputEventContext) -> EventResult,
) : InputBinding

/**
 * Binding triggered by a mouse button and modifier conditions.
 */
data class MouseBinding(
    override val id: String,
    val combo: MouseCombo,
    val action: KeyAction = KeyAction.PRESS,
    override val priority: Int = BindingPriority.NORMAL,
    override val contextFilter: InputContextFilter = InputContextFilter.ALWAYS,
    override val isEnabled: Boolean = true,
    val handler: (InputEventContext) -> EventResult,
) : InputBinding

/**
 * Binding triggered by mouse wheel scroll and modifier conditions.
 */
data class ScrollBinding(
    override val id: String,
    val combo: ScrollCombo,
    override val priority: Int = BindingPriority.NORMAL,
    override val contextFilter: InputContextFilter = InputContextFilter.ALWAYS,
    override val isEnabled: Boolean = true,
    val handler: (ScrollDelta, InputEventContext) -> EventResult,
) : InputBinding
