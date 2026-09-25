@file:Suppress("ForbiddenImport")

package com.stellar.core.input

import com.mojang.blaze3d.platform.InputConstants
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import net.minecraft.client.input.KeyEvent
import net.minecraft.resources.Identifier
import sun.misc.Unsafe
import java.io.File
import java.nio.file.Files

/**
 * Unit tests for [KeyMappingRegistry] in stellar-core.
 */
class KeyMappingRegistrySpec : FunSpec({
    beforeEach {
        KeyMappingRegistry.clear()
    }

    test("should register and retrieve a key mapping") {
        val mapping = KeyMappingRegistry.register("key.test.action", Key.KEY_P)
        mapping.name shouldBe "key.test.action"
        mapping.defaultKey.value shouldBe Key.KEY_P
        mapping.category shouldBe KeyMappingRegistry.CATEGORY_STELLAR

        KeyMappingRegistry.get("key.test.action") shouldBe mapping
        KeyMappingRegistry.getAll() shouldContain mapping
    }

    test("should register mapping with explicit category and type") {
        val customCat = KeyMappingRegistry.getOrCreateCategory(Identifier.fromNamespaceAndPath("test", "keys"))
        val mapping = KeyMappingRegistry.register(
            "key.test.custom",
            Key.KEY_O,
            customCat,
            InputConstants.Type.KEYSYM,
        )
        mapping.category shouldBe customCat
        KeyMappingRegistry.get("key.test.custom") shouldBe mapping
    }

    test("should register mapping using identifier and string category helpers") {
        val catId = Identifier.fromNamespaceAndPath("test", "sub")
        val mapping1 = KeyMappingRegistry.registerWithIdentifier("key.test.id", Key.KEY_I, catId)
        mapping1.category.id() shouldBe catId

        val mapping2 = KeyMappingRegistry.registerWithCategoryName("key.test.str", Key.KEY_U, "test:str")
        mapping2.category.id() shouldBe Identifier.fromNamespaceAndPath("test", "str")
    }

    test("should prevent duplicate registrations by ID") {
        val mapping1 = KeyMappingRegistry.register("key.test.dup", Key.KEY_A)
        val mapping2 = KeyMappingRegistry.register("key.test.dup", Key.KEY_B)

        KeyMappingRegistry.getAll() shouldHaveSize 1
        KeyMappingRegistry.get("key.test.dup") shouldBe mapping2
    }

    test("should unregister and clear mappings") {
        KeyMappingRegistry.register("key.test.remove", Key.KEY_R)
        KeyMappingRegistry.get("key.test.remove").shouldNotBeNull()

        KeyMappingRegistry.unregister("key.test.remove") shouldBe true
        KeyMappingRegistry.get("key.test.remove").shouldBeNull()

        KeyMappingRegistry.register("key.test.c1", Key.KEY_1)
        KeyMappingRegistry.register("key.test.c2", Key.KEY_2)
        KeyMappingRegistry.clear()
        KeyMappingRegistry.getAll() shouldHaveSize 0
    }

    test("should merge registered mappings into array with process") {
        val mapping1 = KeyMappingRegistry.register("key.test.m1", Key.KEY_1)
        val mapping2 = KeyMappingRegistry.register("key.test.m2", Key.KEY_2)

        val existing = arrayOf(mapping1)
        val processed = KeyMappingRegistry.process(existing)

        processed shouldHaveSize 2
        processed.map { it.name } shouldBe listOf("key.test.m1", "key.test.m2")
    }

    test("should check key matching via keyCode, Key, and KeyEvent") {
        val mapping = KeyMappingRegistry.register("key.test.match", Key.KEY_K)

        KeyMappingRegistry.matches(mapping, Key.KEY_K) shouldBe true
        KeyMappingRegistry.matches(mapping, Key.KEY_J) shouldBe false

        val keyObj = InputConstants.Type.KEYSYM.getOrCreate(Key.KEY_K)
        KeyMappingRegistry.matches(mapping, keyObj) shouldBe true

        val event = KeyEvent(Key.KEY_K, 0, 0)
        KeyMappingRegistry.matches(mapping, event) shouldBe true
    }

    test("should resolve bound key and bound key code") {
        val mapping = KeyMappingRegistry.register("key.test.bound", Key.KEY_L)
        KeyMappingRegistry.getBoundKeyCode(mapping) shouldBe Key.KEY_L
        KeyMappingRegistry.getBoundKey(mapping).value shouldBe Key.KEY_L
    }

    test("should read isDown and consumeClick") {
        val mapping = KeyMappingRegistry.register("key.test.state", Key.KEY_M)
        KeyMappingRegistry.isDown(mapping) shouldBe false
        KeyMappingRegistry.consumeClick(mapping) shouldBe false
    }

    test("should load saved keys from options file") {
        val tempDir = Files.createTempDirectory("stellar_core_test").toFile()
        val optionsFile = File(tempDir, "options.txt")
        optionsFile.writeText("key_key.test.saved:key.keyboard.x\n")

        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as Unsafe

        val options = unsafe.allocateInstance(net.minecraft.client.Options::class.java) as net.minecraft.client.Options
        val fileField = net.minecraft.client.Options::class.java.getDeclaredField("optionsFile")
        fileField.isAccessible = true
        fileField.set(options, optionsFile)

        val mapping = KeyMappingRegistry.register("key.test.saved", Key.KEY_Z)
        KeyMappingRegistry.loadSavedKeys(options, listOf(mapping))

        mapping.saveString() shouldBe "key.keyboard.x"
        KeyMappingRegistry.getBoundKeyCode(mapping) shouldBe Key.KEY_X

        tempDir.deleteRecursively()
    }
})
