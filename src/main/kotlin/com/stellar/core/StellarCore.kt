package com.stellar.core

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Common core utilities and constants shared across all Stellar modules.
 */
object StellarCore {
    const val NAMESPACE: String = "stellar"
    private val logger: Logger = LoggerFactory.getLogger(NAMESPACE)

    /**
     * Formats an identifier under the Stellar namespace.
     */
    fun identifier(path: String): String {
        return "$NAMESPACE:$path"
    }

    /**
     * Logs an informational core message.
     */
    fun logInfo(message: String) {
        logger.info("[StellarCore] {}", message)
    }
}
