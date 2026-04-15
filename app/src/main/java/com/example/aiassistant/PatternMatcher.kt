package com.example.aiassistant

import android.content.Context

/**
 * Finds the best action for a screen by looking up learned user patterns.
 * Falls back to rule-based matching if no confident pattern exists.
 */
object PatternMatcher {

    /**
     * Minimum number of times a pattern must have been observed
     * before the app will replay it automatically.
     */
    const val MIN_CONFIDENCE = 3

    /**
     * Find the best action for the given screen snapshot.
     * Returns null if no confident action is found.
     */
    suspend fun findBestAction(
        context: Context,
        snapshot: ScreenSnapshot
    ): ActionCommand? {
        // 1. Try learned patterns first
        val patternAction = findFromPatterns(context, snapshot)
        if (patternAction != null) return patternAction

        // 2. Fall back to rule-based matching
        return findFromRules(context, snapshot)
    }

    private suspend fun findFromPatterns(
        context: Context,
        snapshot: ScreenSnapshot
    ): ActionCommand? {
        val dao = DatabaseHelper.getDB(context).userPatternDao()
        val top = dao.getTopByState(snapshot.stableState) ?: return null

        // Only act on high-confidence patterns
        if (top.count < MIN_CONFIDENCE) return null

        // Verify the target text still exists on the current screen
        val targetExists = snapshot.textElements.any {
            it.equals(top.actionText, ignoreCase = true)
        }
        if (!targetExists) return null

        val type = parseActionType(top.actionType)
        return ActionCommand(type = type, target = top.actionText)
    }

    private suspend fun findFromRules(
        context: Context,
        snapshot: ScreenSnapshot
    ): ActionCommand? {
        val rules = DatabaseHelper.getDB(context).ruleDao().getEnabled()
        if (rules.isEmpty()) return null

        for (element in snapshot.textElements) {
            val lower = element.lowercase()
            for (rule in rules) {
                if (lower.contains(rule.keyword.lowercase())) {
                    val type = parseActionType(rule.actionType)
                    return ActionCommand(type = type, target = element)
                }
            }
        }
        return null
    }

    private fun parseActionType(actionType: String): ActionType {
        return when (actionType.uppercase()) {
            "CLICK" -> ActionType.CLICK
            "SCROLL", "SCROLL_FORWARD" -> ActionType.SCROLL_FORWARD
            "SCROLL_BACKWARD" -> ActionType.SCROLL_BACKWARD
            "SWIPE" -> ActionType.SWIPE
            "TYPE" -> ActionType.TYPE
            else -> ActionType.CLICK
        }
    }
}
