@file:Suppress("ForbiddenImport")

package com.stellar.core.input

import com.mojang.blaze3d.platform.InputConstants
import com.stellar.core.StellarCore
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.Options
import net.minecraft.client.input.KeyEvent
import net.minecraft.resources.Identifier
import java.io.File
import java.lang.reflect.Field
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry for vanilla Minecraft [KeyMapping] bindings in Stellar.
 *
 * Ensures custom key mappings show up in the game's Controls / Key Binds menu,
 * prevents sorting crashes by registering categories into Minecraft's sort order map,
 * and maintains seamless synchronization with [Options.keyMappings].
 */
@Suppress("TooManyFunctions")
object KeyMappingRegistry {
    val CATEGORY_STELLAR: KeyMapping.Category = getOrCreateCategory(
        Identifier.fromNamespaceAndPath(StellarCore.NAMESPACE, "main"),
    )

    private val registeredMappings = CopyOnWriteArrayList<KeyMapping>()

    @Volatile
    var minecraftProvider: (() -> Minecraft?)? = null

    /**
     * Registers a vanilla [KeyMapping] instance.
     */
    fun register(mapping: KeyMapping): KeyMapping {
        if (!registeredMappings.contains(mapping)) {
            registeredMappings.removeIf { it.name == mapping.name }
            registeredMappings.add(mapping)
        }
        syncWithOptions()
        return mapping
    }

    /**
     * Creates and registers a new [KeyMapping].
     */
    fun register(
        name: String,
        keyCode: Int,
        category: KeyMapping.Category = CATEGORY_STELLAR,
        type: InputConstants.Type = InputConstants.Type.KEYSYM,
    ): KeyMapping {
        val mapping = KeyMapping(name, type, keyCode, category)
        return register(mapping)
    }

    /**
     * Registers a new [KeyMapping] with an [Identifier] category.
     */
    fun registerWithIdentifier(name: String, keyCode: Int, categoryIdentifier: Identifier): KeyMapping {
        return register(name, keyCode, getOrCreateCategory(categoryIdentifier))
    }

    /**
     * Registers a new [KeyMapping] with a category string ID.
     */
    fun registerWithCategoryName(name: String, keyCode: Int, categoryName: String): KeyMapping {
        val identifier = runCatching { Identifier.parse(categoryName) }
            .getOrDefault(Identifier.fromNamespaceAndPath(StellarCore.NAMESPACE, "main"))
        return register(name, keyCode, getOrCreateCategory(identifier))
    }

    /**
     * Unregisters a [KeyMapping] by name or ID.
     */
    fun unregister(name: String): Boolean {
        return registeredMappings.removeIf { it.name == name }
    }

    /**
     * Clears all registered mappings (useful for testing).
     */
    fun clear() {
        registeredMappings.clear()
        minecraftProvider = null
    }

    /**
     * Finds a registered [KeyMapping] by name.
     */
    fun get(name: String): KeyMapping? {
        return registeredMappings.firstOrNull { it.name == name }
    }

    /**
     * Returns an unmodifiable list of all registered key mappings.
     */
    fun getAll(): List<KeyMapping> = registeredMappings.toList()

    /**
     * Looks up an existing [KeyMapping.Category] by [Identifier] or creates and registers a new one.
     */
    fun getOrCreateCategory(id: Identifier): KeyMapping.Category {
        val existing = findCategory(id)
        if (existing != null) return existing
        return runCatching {
            KeyMapping.Category.register(id)
        }.getOrElse {
            findCategory(id) ?: KeyMapping.Category.MISC
        }
    }

    /**
     * Finds an existing [KeyMapping.Category] by [Identifier].
     */
    fun findCategory(id: Identifier): KeyMapping.Category? {
        return runCatching {
            val field = KeyMapping.Category::class.java.getDeclaredField("SORT_ORDER")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val list = field.get(null) as? List<KeyMapping.Category>
            list?.firstOrNull { it.id() == id }
        }.getOrNull()
    }

    /**
     * Merges registered mod key mappings into an existing [KeyMapping] array,
     * ensuring each registered mapping is present without duplicates.
     */
    fun process(originalKeys: Array<KeyMapping>): Array<KeyMapping> {
        val list = originalKeys.toMutableList()
        for (mapping in registeredMappings) {
            if (list.none { it.name == mapping.name }) {
                list.add(mapping)
            }
        }
        return list.toTypedArray()
    }

    /**
     * Ensures all registered key mappings are present in the provided [Options] instance.
     */
    fun injectIntoOptions(options: Options): Boolean {
        return runCatching {
            val existing = options.keyMappings
            val updated = process(existing)
            if (updated.size != existing.size) {
                setKeyMappingsArray(options, updated)
                loadSavedKeys(options, registeredMappings)
                KeyMapping.resetMapping()
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Synchronizes registered key mappings with the running game's options if available.
     */
    private fun syncWithOptions() {
        val mc = minecraftProvider?.invoke() ?: runCatching { Minecraft.getInstance() }.getOrNull()
        val options = mc?.options ?: return
        injectIntoOptions(options)
    }

    private fun setKeyMappingsArray(options: Options, newMappings: Array<KeyMapping>) {
        runCatching {
            val field = Options::class.java.getDeclaredField("keyMappings")
            field.isAccessible = true
            field.set(options, newMappings)
        }.onFailure {
            setKeyMappingsViaUnsafe(options, newMappings)
        }
    }

    private fun setKeyMappingsViaUnsafe(options: Options, newMappings: Array<KeyMapping>) {
        runCatching {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
            unsafeField.isAccessible = true
            val unsafe = unsafeField.get(null)
            val field = Options::class.java.getDeclaredField("keyMappings")
            val offsetMethod = unsafeClass.getMethod("objectFieldOffset", Field::class.java)
            val offset = offsetMethod.invoke(unsafe, field) as Long
            val putMethod = unsafeClass.getMethod(
                "putReference",
                Any::class.java,
                Long::class.javaPrimitiveType,
                Any::class.java,
            )
            putMethod.invoke(unsafe, options, offset, newMappings)
        }
    }

    /**
     * Reads saved key assignments from options.txt if present for the given mappings.
     */
    fun loadSavedKeys(options: Options, mappings: List<KeyMapping>) {
        val file = getOptionsFile(options) ?: return
        if (!file.exists()) return

        val lines = runCatching { file.readLines() }.getOrNull() ?: return
        for (mapping in mappings) {
            applySavedKeyLine(mapping, lines)
        }
    }

    private fun getOptionsFile(options: Options): File? {
        return runCatching {
            val fileField = Options::class.java.getDeclaredField("optionsFile")
            fileField.isAccessible = true
            fileField.get(options) as? File
        }.getOrNull()
    }

    private fun applySavedKeyLine(mapping: KeyMapping, lines: List<String>) {
        val prefix = "key_${mapping.name}:"
        val match = lines.firstOrNull { it.startsWith(prefix) } ?: return
        val value = match.removePrefix(prefix).trim()
        if (value.isNotEmpty()) {
            runCatching {
                mapping.setKey(InputConstants.getKey(value))
            }
        }
    }

    /**
     * Resolves the bound [InputConstants.Key] of a [KeyMapping].
     */
    fun getBoundKey(mapping: KeyMapping): InputConstants.Key {
        return runCatching {
            val field = KeyMapping::class.java.getDeclaredField("key")
            field.isAccessible = true
            field.get(mapping) as InputConstants.Key
        }.getOrDefault(mapping.defaultKey)
    }

    /**
     * Resolves the bound key code integer of a [KeyMapping].
     */
    fun getBoundKeyCode(mapping: KeyMapping): Int {
        return getBoundKey(mapping).value
    }

    /**
     * Returns true if the key mapping is currently pressed.
     */
    fun isDown(mapping: KeyMapping): Boolean = mapping.isDown

    /**
     * Consumes a click if the key mapping was pressed, returning true if so.
     */
    fun consumeClick(mapping: KeyMapping): Boolean = mapping.consumeClick()

    /**
     * Checks if a GLFW key code matches this key mapping.
     */
    fun matches(mapping: KeyMapping, keyCode: Int): Boolean {
        val key = InputConstants.Type.KEYSYM.getOrCreate(keyCode)
        return mapping.matches(key)
    }

    /**
     * Checks if an [InputConstants.Key] matches this key mapping.
     */
    fun matches(mapping: KeyMapping, key: InputConstants.Key): Boolean {
        return mapping.matches(key)
    }

    /**
     * Checks if a [KeyEvent] matches this key mapping.
     */
    fun matches(mapping: KeyMapping, event: KeyEvent): Boolean {
        return mapping.matches(event)
    }
}
