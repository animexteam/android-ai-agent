package com.androidagent.aiagent.agent

/**
 * Detects when the agent is stuck in a loop and injects a warning
 * into the prompt so the model can change strategy.
 *
 * Two independent heuristics are tracked:
 *
 * 1. **Consecutive identical actions** – if the agent calls the same
 *    tool with the same arguments [maxConsecutiveSameActions] times in
 *    a row, a loop is declared.
 *
 * 2. **Stale screen** – if the observation fingerprint does not change
 *    over [maxUnchangedObservations] consecutive observations, a loop
 *    is declared.
 *
 * The observation fingerprint uses the package name + total node count +
 * first-10 node IDs to avoid false positives from trivial re-renders
 * while still catching genuine stagnation.
 */
class AgentLoopGuard(
    private val maxConsecutiveSameActions: Int = 3,
    private val maxUnchangedObservations: Int = 4
) {
    // -------------------------------------------------------------------
    // Internal data
    // -------------------------------------------------------------------

    private data class ActionSignature(
        val toolName: String,
        val argsHash: Int
    )

    /** We keep twice the detection window to avoid boundary effects. */
    private val recentActions: ArrayDeque<ActionSignature> = ArrayDeque()
    private val recentObservationHashes: ArrayDeque<Int> = ArrayDeque()

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    /** Record that a tool was executed with the given arguments. */
    fun recordAction(toolName: String, args: String) {
        recentActions.addLast(
            ActionSignature(
                toolName = toolName,
                argsHash = stableHash(args)
            )
        )
        trim(recentActions, maxConsecutiveSameActions * 2)
    }

    /** Record a new screen observation for stale-screen detection. */
    fun recordObservation(observation: AndroidObservation) {
        val fingerprint = computeObservationFingerprint(observation)
        recentObservationHashes.addLast(fingerprint)
        trim(recentObservationHashes, maxUnchangedObservations + 2)
    }

    /**
     * Check whether the agent is currently looping.
     *
     * @return `true` if either heuristic has tripped.
     */
    fun isLooping(): Boolean {
        return isActionLooping() || isScreenStale()
    }

    /**
     * Return a human-readable explanation of *why* the loop was detected,
     * suitable for injecting into the model's prompt.
     */
    fun getLoopMessage(): String {
        val actionCount = countConsecutiveSameActions()
        val screenStale = isScreenStale()

        return buildString {
            if (screenStale) {
                append("The screen has not changed after $maxUnchangedObservations consecutive observations. ")
            }
            append(
                "The same action has been repeated $actionCount time(s) consecutively. " +
                    "The current strategy is not working. " +
                    "Choose a fundamentally different approach (different tool, different target, " +
                    "navigate somewhere else), or use agent.finish/agent.stop to end gracefully."
            )
        }
    }

    /** Clear all tracked state. Called at the start of each new task. */
    fun reset() {
        recentActions.clear()
        recentObservationHashes.clear()
    }

    // -------------------------------------------------------------------
    // Internal heuristics
    // -------------------------------------------------------------------

    private fun isActionLooping(): Boolean {
        if (recentActions.size < maxConsecutiveSameActions) return false
        val tail = recentActions.takeLast(maxConsecutiveSameActions)
        val first = tail.first()
        return tail.all { it.toolName == first.toolName && it.argsHash == first.argsHash }
    }

    private fun isScreenStale(): Boolean {
        if (recentObservationHashes.size < maxUnchangedObservations) return false
        val tail = recentObservationHashes.takeLast(maxUnchangedObservations)
        val first = tail.first()
        return tail.all { it == first }
    }

    private fun countConsecutiveSameActions(): Int {
        val last = recentActions.lastOrNull() ?: return 0
        var count = 0
        for (i in recentActions.indices.reversed()) {
            val action = recentActions[i]
            if (action.toolName == last.toolName && action.argsHash == last.argsHash) {
                count++
            } else {
                break
            }
        }
        return count
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /**
     * Compute a fingerprint for an observation that captures the
     * essential structure of the screen without being overly sensitive
     * to minor layout shifts.
     *
     * Uses: package name + node count + first 10 node IDs (which encode
     * the view hierarchy structure via their hash-based naming).
     */
    private fun computeObservationFingerprint(observation: AndroidObservation): Int {
        val leadingNodeIds = observation.uiTree
            .take(10)
            .joinToString(",") { it.nodeId }
        val raw = "${observation.packageName}|${observation.uiTree.size}|$leadingNodeIds"
        return stableHash(raw)
    }

    /**
     * A deterministic string hash that avoids the platform-dependent
     * quirks of [String.hashCode] by mixing in the string length.
     *
     * This is still **not** cryptographically secure – it's just a
     * cheap way to get a reasonable fingerprint.
     */
    private fun stableHash(s: String): Int {
        var h = s.hashCode()
        h = h xor (h ushr 16)
        h = h * 0x85ebca6b.toInt()
        h = h xor (h ushr 13)
        h = h * 0xc2b2ae35.toInt()
        h = h xor (h ushr 16)
        return h
    }

    /** Remove oldest entries until the deque is at most [maxSize]. */
    private fun <T> trim(deque: ArrayDeque<T>, maxSize: Int) {
        while (deque.size > maxSize) {
            deque.removeFirst()
        }
    }
}
