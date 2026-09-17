package com.stellar.core.input

import org.lwjgl.glfw.GLFW

/**
 * Modifier key flags.
 */
enum class Modifier(val mask: Int) {
    SHIFT(GLFW.GLFW_MOD_SHIFT),
    CTRL(GLFW.GLFW_MOD_CONTROL),
    ALT(GLFW.GLFW_MOD_ALT),
    SUPER(GLFW.GLFW_MOD_SUPER),
    ;

    companion object {
        fun fromKey(keyCode: Int): Modifier? = when (keyCode) {
            Key.KEY_LEFT_CONTROL, Key.KEY_RIGHT_CONTROL -> CTRL
            Key.KEY_LEFT_SHIFT, Key.KEY_RIGHT_SHIFT -> SHIFT
            Key.KEY_LEFT_ALT, Key.KEY_RIGHT_ALT -> ALT
            Key.KEY_LEFT_SUPER, Key.KEY_RIGHT_SUPER -> SUPER
            else -> null
        }
    }
}

/**
 * Type-safe bitmask representation for combinations of modifier keys.
 */
@JvmInline
value class ModifierSet(val mask: Int) {
    val hasControl: Boolean
        get() = contains(Modifier.CTRL)

    val hasShift: Boolean
        get() = contains(Modifier.SHIFT)

    val hasAlt: Boolean
        get() = contains(Modifier.ALT)

    val hasSuper: Boolean
        get() = contains(Modifier.SUPER)

    val isEmpty: Boolean
        get() = mask == 0

    val isNotEmpty: Boolean
        get() = mask != 0

    val count: Int
        get() = Integer.bitCount(mask)

    operator fun contains(modifier: Modifier): Boolean = mask and modifier.mask != 0

    operator fun plus(modifier: Modifier): ModifierSet = ModifierSet(mask or modifier.mask)

    operator fun plus(other: ModifierSet): ModifierSet = ModifierSet(mask or other.mask)

    operator fun minus(modifier: Modifier): ModifierSet = ModifierSet(mask and modifier.mask.inv())

    operator fun minus(other: ModifierSet): ModifierSet = ModifierSet(mask and other.mask.inv())

    override fun toString(): String {
        if (isEmpty) return "None"
        val parts = ArrayList<String>(INITIAL_PARTS_CAPACITY)
        if (hasControl) parts.add("Ctrl")
        if (hasAlt) parts.add("Alt")
        if (hasShift) parts.add("Shift")
        if (hasSuper) parts.add("Super")
        return parts.joinToString("+")
    }

    companion object {
        private const val INITIAL_PARTS_CAPACITY = 4
        private const val MODIFIER_MASK = 0x000F

        val NONE: ModifierSet = ModifierSet(0)
        val CTRL: ModifierSet = ModifierSet(Modifier.CTRL.mask)
        val SHIFT: ModifierSet = ModifierSet(Modifier.SHIFT.mask)
        val ALT: ModifierSet = ModifierSet(Modifier.ALT.mask)
        val SUPER: ModifierSet = ModifierSet(Modifier.SUPER.mask)

        fun combine(vararg modifiers: Modifier): ModifierSet {
            var combined = 0
            for (m in modifiers) {
                combined = combined or m.mask
            }
            return ModifierSet(combined)
        }

        fun fromGlfwModifiers(glfwMods: Int): ModifierSet {
            return ModifierSet(glfwMods and MODIFIER_MASK)
        }
    }
}
