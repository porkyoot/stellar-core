package com.stellar.core.input

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.lwjgl.glfw.GLFW

class InputManagerSpec : FunSpec({

    test("single key press triggers and consumes event") {
        val manager = InputManager()
        var triggered = false

        manager.registerKey("test:space") {
            key = Key.KEY_SPACE
            onPress {
                triggered = true
                EventResult.CONSUMED
            }
        }

        val result = manager.onKey(Key.KEY_SPACE, GLFW.GLFW_PRESS, 0)
        result shouldBe EventResult.CONSUMED
        triggered shouldBe true

        // Passing key that has no binding
        val unhandled = manager.onKey(Key.KEY_A, GLFW.GLFW_PRESS, 0)
        unhandled shouldBe EventResult.PASS
    }

    test("Ctrl modifier key combination triggers accurately") {
        val manager = InputManager()
        var ctrlCPressed = false

        manager.registerKey("test:ctrl_c") {
            key = Key.KEY_C
            modifiers(Modifier.CTRL)
            match = ModifierMatch.EXACT
            onPress {
                ctrlCPressed = true
                EventResult.CONSUMED
            }
        }

        // Press C without Ctrl -> should pass
        manager.onKey(Key.KEY_C, GLFW.GLFW_PRESS, 0) shouldBe EventResult.PASS
        ctrlCPressed shouldBe false

        // Press C with Shift (no Ctrl) -> should pass
        manager.onKey(Key.KEY_C, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_SHIFT) shouldBe EventResult.PASS
        ctrlCPressed shouldBe false

        // Press C with Ctrl -> should trigger and consume
        manager.onKey(Key.KEY_C, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_CONTROL) shouldBe EventResult.CONSUMED
        ctrlCPressed shouldBe true
    }

    test("multi-modifier specificity prioritizes more specific chords") {
        val manager = InputManager()
        var triggered = ""

        manager.registerKey("test:ctrl_c") {
            key = Key.KEY_C
            modifiers(Modifier.CTRL)
            match = ModifierMatch.EXACT
            onPress {
                triggered = "CTRL"
                EventResult.CONSUMED
            }
        }

        manager.registerKey("test:ctrl_shift_c") {
            key = Key.KEY_C
            modifiers(Modifier.CTRL, Modifier.SHIFT)
            match = ModifierMatch.EXACT
            onPress {
                triggered = "CTRL_SHIFT"
                EventResult.CONSUMED
            }
        }

        val mods = GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_SHIFT
        val result = manager.onKey(Key.KEY_C, GLFW.GLFW_PRESS, mods)

        result shouldBe EventResult.CONSUMED
        triggered shouldBe "CTRL_SHIFT"
    }

    test("mouse button with Shift modifier") {
        val manager = InputManager()
        var clicked = false

        manager.registerMouse("test:shift_click") {
            button = Key.MOUSE_BUTTON_LEFT
            modifiers(Modifier.SHIFT)
            onPress {
                clicked = true
                EventResult.CONSUMED
            }
        }

        // Left click without Shift -> PASS
        manager.onMouseButton(Key.MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0) shouldBe EventResult.PASS
        clicked shouldBe false

        // Left click with Shift -> CONSUMED
        val res = manager.onMouseButton(Key.MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_SHIFT)
        res shouldBe EventResult.CONSUMED
        clicked shouldBe true
    }

    test("mouse scroll with Ctrl and Shift modifiers") {
        val manager = InputManager()
        var zoomDelta = 0.0
        var cycleDirection: ScrollDirection = ScrollDirection.NONE

        // Ctrl + Scroll (any vertical) -> Zoom
        manager.registerScroll("test:ctrl_scroll_zoom") {
            modifiers(Modifier.CTRL)
            onScroll { delta, _ ->
                zoomDelta += delta.vertical
                EventResult.CONSUMED
            }
        }

        // Shift + Scroll UP -> Cycle Forward
        manager.registerScroll("test:shift_scroll_up") {
            direction = ScrollDirection.UP
            modifiers(Modifier.SHIFT)
            onScroll { delta, _ ->
                cycleDirection = delta.direction
                EventResult.CONSUMED
            }
        }

        // Scroll without modifiers -> PASS
        manager.onMouseScroll(0.0, 1.0) shouldBe EventResult.PASS

        // Simulate Ctrl key held down via state tracker
        manager.onKey(Key.KEY_LEFT_CONTROL, GLFW.GLFW_PRESS, 0)
        manager.stateTracker.isCtrlDown shouldBe true

        val zoomRes = manager.onMouseScroll(0.0, 1.0)
        zoomRes shouldBe EventResult.CONSUMED
        zoomDelta shouldBe 1.0

        // Release Ctrl, hold Shift
        manager.onKey(Key.KEY_LEFT_CONTROL, GLFW.GLFW_RELEASE, 0)
        manager.onKey(Key.KEY_LEFT_SHIFT, GLFW.GLFW_PRESS, 0)
        manager.stateTracker.isShiftDown shouldBe true

        val shiftRes = manager.onMouseScroll(0.0, 1.0)
        shiftRes shouldBe EventResult.CONSUMED
        cycleDirection shouldBe ScrollDirection.UP

        // Scroll DOWN with Shift (no binding for DOWN) -> PASS
        manager.onMouseScroll(0.0, -1.0) shouldBe EventResult.PASS
    }

    test("context filter obeys screen state") {
        var screenOpen = false
        val manager = InputManager(screenContextProvider = { screenOpen })
        var inGameFired = false
        var inGuiFired = false

        manager.registerKey("test:in_game") {
            key = Key.KEY_B
            context = InputContextFilter.IN_GAME_ONLY
            onPress {
                inGameFired = true
                EventResult.CONSUMED
            }
        }

        manager.registerKey("test:in_gui") {
            key = Key.KEY_B
            context = InputContextFilter.IN_GUI_ONLY
            onPress {
                inGuiFired = true
                EventResult.CONSUMED
            }
        }

        // When in-game (screenOpen = false)
        screenOpen = false
        manager.onKey(Key.KEY_B, GLFW.GLFW_PRESS, 0) shouldBe EventResult.CONSUMED
        inGameFired shouldBe true
        inGuiFired shouldBe false

        // When in GUI (screenOpen = true)
        screenOpen = true
        inGameFired = false
        manager.onKey(Key.KEY_B, GLFW.GLFW_PRESS, 0) shouldBe EventResult.CONSUMED
        inGameFired shouldBe false
        inGuiFired shouldBe true
    }

    test("window blur resets all key and button states") {
        val manager = InputManager()
        manager.onKey(Key.KEY_W, GLFW.GLFW_PRESS, 0)
        manager.onKey(Key.KEY_LEFT_CONTROL, GLFW.GLFW_PRESS, 0)
        manager.onMouseButton(Key.MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0)

        manager.stateTracker.isKeyDown(Key.KEY_W) shouldBe true
        manager.stateTracker.isCtrlDown shouldBe true
        manager.stateTracker.isMouseButtonDown(Key.MOUSE_BUTTON_LEFT) shouldBe true

        manager.onWindowBlur()

        manager.stateTracker.isKeyDown(Key.KEY_W) shouldBe false
        manager.stateTracker.isCtrlDown shouldBe false
        manager.stateTracker.isMouseButtonDown(Key.MOUSE_BUTTON_LEFT) shouldBe false
    }

    test("double press detection tracks rapid taps") {
        val tracker = InputStateTracker()
        val t0 = 1000L
        val t1 = 1150L // 150ms later (within 250ms default)
        val t2 = 1500L // 350ms later (outside 250ms threshold)

        tracker.onKeyPressed(Key.KEY_W, t0)
        tracker.isDoublePress(Key.KEY_W, t1) shouldBe true
        tracker.isDoublePress(Key.KEY_W, t2) shouldBe false
    }

    test("crouch and sprint key pressed tracking with default and custom resolver") {
        val manager = InputManager()

        // Default: Shift is crouch, Ctrl is sprint
        manager.onKey(Key.KEY_LEFT_SHIFT, GLFW.GLFW_PRESS, 0)
        manager.stateTracker.isCrouchKeyDown shouldBe true
        manager.onKey(Key.KEY_LEFT_CONTROL, GLFW.GLFW_PRESS, 0)
        manager.stateTracker.isSprintKeyDown shouldBe true

        manager.onKey(Key.KEY_LEFT_SHIFT, GLFW.GLFW_RELEASE, 0)
        manager.stateTracker.isCrouchKeyDown shouldBe false

        manager.onKey(Key.KEY_LEFT_CONTROL, GLFW.GLFW_RELEASE, 0)
        manager.stateTracker.isSprintKeyDown shouldBe false

        // Custom action resolver (e.g. player remapped crouch to C and sprint to R)
        val customResolver = GameActionKeyResolver { action ->
            when (action) {
                GameAction.CROUCH -> Key.KEY_C
                GameAction.SPRINT -> Key.KEY_R
                else -> null
            }
        }
        manager.actionResolver = customResolver

        manager.onKey(Key.KEY_C, GLFW.GLFW_PRESS, 0)
        manager.stateTracker.isCrouchKeyDown shouldBe true
        manager.onKey(Key.KEY_C, GLFW.GLFW_RELEASE, 0)
        manager.stateTracker.isCrouchKeyDown shouldBe false

        var crouchActionTriggered = false
        manager.registerKey("test:action_crouch") {
            gameAction(GameAction.CROUCH)
            onPress {
                crouchActionTriggered = true
                EventResult.CONSUMED
            }
        }

        manager.onKey(Key.KEY_C, GLFW.GLFW_PRESS, 0) shouldBe EventResult.CONSUMED
        crouchActionTriggered shouldBe true
    }
})
