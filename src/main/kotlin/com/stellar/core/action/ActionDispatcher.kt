package com.stellar.core.action

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

/**
 * Event emitted across [ActionDispatcher.failures] whenever a [ModAction] yields [ActionResult.Failure].
 *
 * @property action The action instance that failed.
 * @property result The failure outcome containing the reason and optional cause.
 * @property tick The game tick when the failure occurred.
 */
data class ActionFailureEvent(
    val action: ModAction,
    val result: ActionResult.Failure,
    val tick: Long,
)

/**
 * Central action execution engine designed for 20 TPS game loops.
 *
 * Features:
 * - Thread-safe priority queue with FIFO stability for actions with matching priorities.
 * - [TokenBucket] CPS rate limiting to avoid anti-cheat infractions while allowing micro-bursts.
 * - High/Critical priority preemption: [ActionPriority.CRITICAL] and [ActionPriority.HIGH]
 *   bypass rate limiting and execute immediately.
 * - Reactive failure broadcasting via [SharedFlow].
 * - Safe deferral re-enqueueing for [ActionResult.Deferred] outcomes across tick boundaries.
 *
 * @property tokenBucket Rate limiter instance regulating click/action volume.
 * @property maxActionsPerTick Upper bound of rate-limited actions processed within a single tick.
 * @property contextProvider Supplier providing current game context when none is supplied to [tick].
 */
class ActionDispatcher(
    val tokenBucket: TokenBucket = TokenBucket(),
    val maxActionsPerTick: Int = DEFAULT_MAX_ACTIONS_PER_TICK,
    private val contextProvider: () -> ActionContext = { ActionContext.createMock() },
) {
    private val queue = PriorityBlockingQueue<QueuedAction>()
    private val sequenceGenerator = AtomicLong(0L)
    private val tickMutex = Mutex()

    private val _failures = MutableSharedFlow<ActionFailureEvent>(
        replay = 1,
        extraBufferCapacity = DEFAULT_FAILURE_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Shared flow emitting failure events whenever an action returns [ActionResult.Failure]
     * or throws an unhandled exception.
     */
    val failures: SharedFlow<ActionFailureEvent> = _failures.asSharedFlow()

    /**
     * Observable stream emitting failed action events, supporting reactive failure monitoring.
     */
    val failureFlow: SharedFlow<ActionFailureEvent>
        get() = failures

    /**
     * Number of actions currently pending in the execution queue.
     */
    val pendingCount: Int
        get() = queue.size

    /**
     * Whether the scheduling queue has no pending actions.
     */
    val isEmpty: Boolean
        get() = queue.isEmpty()

    /**
     * Submits a [ModAction] to the priority queue in a thread-safe manner.
     *
     * @param action The action to enqueue.
     * @return Unique monotonically increasing sequence ID assigned to the action.
     */
    fun enqueue(action: ModAction): Long {
        val sequenceId = sequenceGenerator.incrementAndGet()
        queue.offer(QueuedAction(action = action, sequenceId = sequenceId))
        return sequenceId
    }

    /**
     * Processes pending actions for the current game tick.
     *
     * In accordance with Clean Architecture and anti-cheat constraints:
     * - [ActionPriority.CRITICAL] and [ActionPriority.HIGH] bypass the token bucket and execute immediately.
     * - [ActionPriority.NORMAL] and [ActionPriority.LOW] consume tokens and halt if tokens run out
     *   or [maxActionsPerTick] is reached.
     * - Actions returning [ActionResult.Deferred] are safely preserved and re-enqueued for next ticks.
     *
     * @param context Immutable snapshot of the current game state for this tick.
     */
    suspend fun tick(context: ActionContext = contextProvider()) {
        tickMutex.withLock {
            tokenBucket.refill()
            var normalLowProcessed = 0
            val deferredActions = mutableListOf<ModAction>()

            while (canExecuteNext(normalLowProcessed)) {
                val queued = queue.poll() ?: break
                val action = queued.action
                if (action.priority == ActionPriority.NORMAL || action.priority == ActionPriority.LOW) {
                    normalLowProcessed++
                }
                dispatchAction(action = action, context = context, deferredActions = deferredActions)
            }

            for (deferred in deferredActions) {
                enqueue(deferred)
            }
        }
    }

    /**
     * Selectively clears and cancels queued actions matching [predicate].
     *
     * @param predicate Filter determining which queued actions to evict and cancel.
     * @return List of evicted actions.
     */
    fun clearQueue(predicate: (ModAction) -> Boolean = { true }): List<ModAction> {
        val removed = mutableListOf<ModAction>()
        synchronized(queue) {
            val toRemove = queue.filter { predicate(it.action) }
            for (queued in toRemove) {
                if (queue.remove(queued)) {
                    queued.action.cancel()
                    removed.add(queued.action)
                }
            }
        }
        return removed
    }

    /**
     * Clears all pending actions from the queue, invoking [ModAction.cancel] on each.
     */
    fun cancelAll(): List<ModAction> = clearQueue { true }

    private fun canExecuteNext(normalLowProcessed: Int): Boolean {
        val next = queue.peek() ?: return false
        return when (next.action.priority) {
            ActionPriority.CRITICAL, ActionPriority.HIGH -> true
            ActionPriority.NORMAL, ActionPriority.LOW ->
                normalLowProcessed < maxActionsPerTick && tokenBucket.tryConsume()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun dispatchAction(
        action: ModAction,
        context: ActionContext,
        deferredActions: MutableList<ModAction>,
    ) {
        try {
            when (val result = action.execute(context)) {
                is ActionResult.Success -> Unit
                is ActionResult.Failure -> {
                    _failures.emit(
                        ActionFailureEvent(
                            action = action,
                            result = result,
                            tick = context.currentTick,
                        ),
                    )
                }
                is ActionResult.Deferred -> {
                    deferredActions.add(action)
                }
            }
        } catch (e: CancellationException) {
            action.cancel()
            throw e
        } catch (e: Exception) {
            val failure = ActionResult.Failure(
                reason = "Unhandled action exception: ${e.message}",
                cause = e,
            )
            _failures.emit(
                ActionFailureEvent(
                    action = action,
                    result = failure,
                    tick = context.currentTick,
                ),
            )
        }
    }

    /**
     * Internal priority queue node guaranteeing FIFO order for actions sharing the same priority tier.
     */
    private class QueuedAction(
        val action: ModAction,
        val sequenceId: Long,
    ) : Comparable<QueuedAction> {
        override fun compareTo(other: QueuedAction): Int {
            val priorityComparison = this.action.compareTo(other.action)
            return if (priorityComparison != 0) {
                priorityComparison
            } else {
                this.sequenceId.compareTo(other.sequenceId)
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_ACTIONS_PER_TICK: Int = 5
        private const val DEFAULT_FAILURE_BUFFER_CAPACITY: Int = 64
    }
}
