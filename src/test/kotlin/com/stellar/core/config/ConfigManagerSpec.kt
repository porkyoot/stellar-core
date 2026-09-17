package com.stellar.core.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.quiltmc.config.api.ReflectiveConfig
import org.quiltmc.config.api.values.TrackedValue

class TestConfig : ReflectiveConfig() {
    val enabled: TrackedValue<Boolean> = value(true)
    val testCount: TrackedValue<Int> = value(42)
}

class ConfigManagerSpec : FunSpec({
    test("ConfigManager can register and retrieve config") {
        val config = ConfigManager.register("stellar_test", "test", TestConfig::class.java)
        config shouldNotBe null
        config.enabled.value() shouldBe true
        config.testCount.value() shouldBe 42

        val retrieved = ConfigManager.get<TestConfig>("stellar_test", "test")
        retrieved shouldBe config
    }
})
