package com.stellar.core.action

/**
 * Sealed outcome of executing a [ModAction].
 *
 * Strict modeling ensures exhaustive pattern matching at call sites without requiring
 * arbitrary fallback branches.
 */
sealed interface ActionResult {
    /**
     * The action executed successfully and has completed its full lifecycle.
     */
    data object Success : ActionResult

    /**
     * The action encountered an unrecoverable failure or constraint violation.
     *
     * @property reason Human-readable diagnostic explaining why execution failed.
     * @property cause Optional underlying exception or root cause.
     */
    data class Failure(
        val reason: String,
        val cause: Throwable? = null,
    ) : ActionResult

    /**
     * The action could not complete during the current execution cycle and requests
     * to be deferred or yielded back to the scheduler for re-evaluation in subsequent ticks.
     */
    data object Deferred : ActionResult
}

val ActionResult.isSuccess: Boolean
    get() = this is ActionResult.Success

val ActionResult.isFailure: Boolean
    get() = this is ActionResult.Failure

val ActionResult.isDeferred: Boolean
    get() = this is ActionResult.Deferred
