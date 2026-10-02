package com.stellar.core.action

/**
 * Encapsulates an executable client-side command according to Clean Architecture and the Command Pattern.
 *
 * Implementations represent discrete gameplay maneuvers (e.g. block placement, item swapping,
 * emergency totem activation, or movement inputs).
 *
 * Actions are inherently [Comparable], sorted strictly by [priority] so that [ActionPriority.CRITICAL]
 * actions are dispatched first.
 */
interface ModAction : Comparable<ModAction> {
    /**
     * The priority classification assigned to this action.
     */
    val priority: ActionPriority

    /**
     * Executes this action given the provided [context].
     *
     * As a suspending function, execution can asynchronously yield, await game state transitions,
     * or handle cooperative coroutine cancellation without blocking the client game loop.
     *
     * @param context Immutable snapshot of the current game, player, and world state.
     * @return [ActionResult] indicating whether the execution succeeded, failed, or was deferred.
     */
    suspend fun execute(context: ActionContext): ActionResult

    /**
     * Cancels any active or pending execution of this action, performing teardown and releasing resources.
     *
     * Default implementation is a no-op for simple or atomic actions.
     */
    fun cancel() {}

    /**
     * Compares actions by priority. Actions with higher urgency ([ActionPriority.CRITICAL])
     * sort before lower urgency ones ([ActionPriority.LOW]).
     */
    override fun compareTo(other: ModAction): Int = this.priority.compareTo(other.priority)
}
