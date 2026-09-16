package com.stellar.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

/**
 * Unit tests for Stellar Core using Kotest.
 */
class StellarCoreSpec : FunSpec({
    test("identifier should format with namespace") {
        val id = StellarCore.identifier("test_item")
        id shouldStartWith "stellar:"
        id shouldBe "stellar:test_item"
    }

    test("namespace constant is stellar") {
        StellarCore.NAMESPACE shouldBe "stellar"
    }
})
