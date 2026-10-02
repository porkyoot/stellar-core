package com.stellar.core.action

/**
 * Priority classification for scheduled client actions.
 *
 * Lower ordinal values denote higher urgency, ensuring that [CRITICAL] actions
 * are evaluated and dispatched before [HIGH], [NORMAL], and [LOW] in scheduling queues.
 */
enum class ActionPriority {
    /**
     * Immediate, non-deferrable actions (e.g. emergency fall mitigation, auto-totem, life-saving maneuvers).
     */
    CRITICAL,

    /**
     * High-urgency actions (e.g. combat counters, emergency weapon swaps, quick healing).
     */
    HIGH,

    /**
     * Standard gameplay actions (e.g. block placement, pathfinding navigation, routine interactions).
     */
    NORMAL,

    /**
     * Background maintenance or non-urgent tasks (e.g. inventory sorting, cosmetic visual tweaks).
     */
    LOW,
}
