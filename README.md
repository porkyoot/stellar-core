<div align="center">

# 🌌 Stellarcore

**Central foundation library and technical core of the Stellar mod suite.**

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2+%20%2F%2026.2-blue.svg)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Loader-Quilt-purple.svg)](https://quiltmc.org/)
[![Language](https://img.shields.io/badge/Kotlin-2.4-orange.svg)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-21-red.svg)](https://adoptium.net/)
[![Role](https://img.shields.io/badge/Type-Internal%20Fat%20Jar%20Library-yellow.svg)](#-about)

</div>

---

## 📖 About

**Stellarcore** is the central library and technical core powering all mods within the Stellar ecosystem ([**Stellarlaw**](../stellar-law/), [**Stellarops**](../stellar-ops/), and [**Stellartweak**](../stellar-tweak/)).

This library is **not published independently** on public mod download platforms (such as Modrinth or CurseForge). Instead, it is engineered as a shared foundation that is directly bundled into each mod in the series as an embedded **Fat Jar** (or *Shadow Jar*).

Players and server administrators **never need to download, install, or update Stellarcore manually**: it is packaged and loaded transparently inside each companion mod's archive.

---

## ⚙️ Core Features

Stellarcore aggregates all shared components to eliminate code duplication and maintain seamless consistency across the suite:

* 🌐 **Global Variables & Constants:** Central definitions and constants used across the ecosystem, including the shared `stellar` namespace (`StellarCore.NAMESPACE`).
* 🏷️ **Identifier Formatting:** Common helper methods (`StellarCore.identifier(path)`) producing canonical namespaced keys (`stellar:<path>`).
* 📋 **Unified Logging:** Centralized SLF4J logging (`StellarCore.logInfo(message)`) ensuring standardized diagnostic output across all modules.
* 📦 **Shared Assets & Common Logic:** Common visual resources, data definitions, and utility methods required for moderation, networking, inventory handling, and build helpers.

---

## 🏛️ Architectural Isolation & Boundaries

To preserve strict separation of concerns, Stellarcore enforces strict boundary constraints via [Detekt](../config/detekt/detekt-stellar-core.yml):

- **No Client Dependencies:** Must never import client-side Minecraft classes (`net.minecraft.client.*`) or Blaze3D rendering engines (`com.mojang.blaze3d.*`).
- **No Dedicated Server Dependencies:** Must never import dedicated server classes (`net.minecraft.server.dedicated.*`).
- **No Downstream Module Coupling:** Must never import or depend on `com.stellar.law.*`, `com.stellar.ops.*`, or `com.stellar.tweak.*`.

This ensures that Stellarcore remains a pure, neutral foundation that can be safely embedded into both client-side and server-side modules without side effects.

---

## 🛠️ For Developers

Stellarcore is built for the **Quilt** ecosystem, targeting **Java 21** and **Kotlin 2.4**.

> [!WARNING]
> Because Stellarcore is embedded into all suite mods, any breaking change made here will directly impact every dependent mod in the ecosystem.

### Gradle Fat-Jar Packaging

When compiling a parent mod, Gradle is configured to pull Stellarcore's compiled source set into the final mod archive:

```kotlin
// In parent module's build.gradle.kts (stellar-law, stellar-ops, stellar-tweak)
dependencies {
    implementation(project(":stellar-core"))
}

tasks.named<Jar>("jar") {
    from(project(":stellar-core").the<SourceSetContainer>()["main"].output)
}
```

### Testing & Verification

* **Unit Tests (Kotest):**
  ```bash
  ./gradlew :stellar-core:test
  ```
  Executes unit tests in [`StellarCoreSpec.kt`](file:///home/etoile/50-59_Code/50_Minecraft_Mods/stellar-mods/stellar-core/src/test/kotlin/com/stellar/core/StellarCoreSpec.kt).

* **Detekt Static Analysis:**
  ```bash
  ./gradlew :stellar-core:detekt
  ```
