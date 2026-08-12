package com.androidagent.aiagent.agent

class AgentLoopGuard(
    private val maxConsecutiveSameActions: Int = 3,
    private val maxUnchangedObservations: Int = 4
) {

    private data class ActionSignature(
        val toolName: String,
        val argsHash: Int
    )

    private val recentActions: MutableList<ActionSignature> = ArrayDeque()
    private val recentObservationHashes: MutableList<Int> = ArrayDeque()

    fun recordAction(toolName: String, args: String) {
        val signature = ActionSignature(
            toolName = toolName,
            argsHash = args.hashCode()
        )
        recentActions.add(signature)
        // Keep only the last N*2 entries to bound memory
        val maxActions = maxConsecutiveSameActions * 2
        while (recentActions.size > maxActions) {
            (recentActions as ArrayDeque).removeFirst()
        }
    }

    fun recordObservation(observation: AndroidObservation) {
        val nodeIds = observation.uiTree.take(5).map { it.nodeId }
        val hash = (observation.packageName + observation.uiTree.size + nodeIds.joinToString(",")).hashCode()
        recentObservationHashes.add(hash)
        // Keep only the last maxUnchangedObservations + 2 entries
        while (recentObservationHashes.size > maxUnchangedObservations + 2) {
            (recentObservationHashes as ArrayDeque).removeFirst()
        }
    }

    fun isLooping(): Boolean {
        // Check consecutive identical actions
        if (recentActions.size >= maxConsecutiveSameActions) {
            val lastN = recentActions.takeLast(maxConsecutiveSameActions)
            val first = lastN.first()
            if (lastN.all { it.toolName == first.toolName && it.argsHash == first.argsHash }) {
                return true
            }
        }

        // Check unchanged observations
        if (recentObservationHashes.size >= maxUnchangedObservations) {
            val lastN = recentObservationHashes.takeLast(maxUnchangedObservations)
            val first = lastN.first()
            if (lastN.all { it == first }) {
                return true
            }
        }

        return false
    }

    fun getLoopMessage(): String {
        val actionRepeatCount = countConsecutiveSameActions()
        return if (recentObservationHashes.size >= maxUnchangedObservations &&
            recentObservationHashes.takeLast(maxUnchangedObservations).distinct().size == 1
        ) {
            "The screen state has not changed after $maxUnchangedObservations consecutive observations. " +
                "The same action has been repeated $actionRepeatCount time(s). " +
                "Choose a different strategy, try a different approach, or stop."
        } else {
            "The same action (with identical arguments) has been repeated $actionRepeatCount times consecutively. " +
                "The state has not changed. Choose a different strategy or stop."
        }
    }

    private fun countConsecutiveSameActions(): Int {
        if (recentActions.isEmpty()) return 0
        val last = recentActions.last()
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

    fun reset() {
        recentActions.clear()
        recentObservationHashes.clear()
    }
}
