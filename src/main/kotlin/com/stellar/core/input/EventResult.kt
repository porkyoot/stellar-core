package com.stellar.core.input

/**
 * Indicates whether an input event was consumed by an input binding handler.
 */
enum class EventResult {
    /**
     * The input event was handled and consumed.
     * Prevents lower-priority handlers and cancels vanilla Minecraft processing.
     */
    CONSUMED,

    /**
     * The input event was ignored or passed through to other handlers and vanilla Minecraft.
     */
    PASS,
    ;

    val isConsumed: Boolean
        get() = this == CONSUMED
}
