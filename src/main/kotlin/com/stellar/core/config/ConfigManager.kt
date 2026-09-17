package com.stellar.core.config

import org.quiltmc.config.api.ReflectiveConfig
import org.quiltmc.config.api.serializers.Json5Serializer
import org.quiltmc.config.api.serializers.TomlSerializer
import org.quiltmc.config.implementor_api.ConfigEnvironment
import org.quiltmc.config.implementor_api.ConfigFactory
import org.quiltmc.loader.impl.config.QuiltConfigImpl
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Common configuration manager wrapping Quilt Config.
 */
object ConfigManager {
    private val configs = ConcurrentHashMap<String, ReflectiveConfig>()
    private var customEnvironment: ConfigEnvironment? = null

    fun setEnvironment(env: ConfigEnvironment?) {
        this.customEnvironment = env
    }

    fun getEnvironment(): ConfigEnvironment {
        customEnvironment?.let { return it }
        val loaderEnv = runCatching { QuiltConfigImpl.getConfigEnvironment() }.getOrNull()
        if (loaderEnv != null) {
            return loaderEnv
        }
        val fallback = ConfigEnvironment(
            Paths.get("build/tmp/config"),
            TomlSerializer.INSTANCE,
            Json5Serializer.INSTANCE,
        )
        customEnvironment = fallback
        return fallback
    }

    fun <C : ReflectiveConfig> register(
        family: String,
        id: String,
        configClass: Class<C>,
        path: Path = Paths.get(""),
    ): C {
        val configKey = configKey(family, id)
        val existing = get<C>(family, id)
        if (existing != null) {
            return existing
        }
        val env = getEnvironment()
        val config = ConfigFactory.create(env, family, id, path, configClass)
        configs[configKey] = config
        return config
    }

    @Suppress("UNCHECKED_CAST")
    fun <C : ReflectiveConfig> get(family: String, id: String): C? {
        val key = configKey(family, id)
        return configs[key] as? C
    }

    fun unregister(family: String, id: String): Boolean {
        return configs.remove(configKey(family, id)) != null
    }

    fun getAll(): Collection<ReflectiveConfig> {
        return configs.values
    }

    fun clear() {
        configs.clear()
    }

    private fun configKey(family: String, id: String): String = "$family:$id"
}
