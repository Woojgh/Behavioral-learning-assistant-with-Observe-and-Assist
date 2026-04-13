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
    private const val TEXT_MATCH_THRESHOLD = 0.85

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

        // Verify the target text still exists on the current screen.
        // Fuzzy matching (Jaro-Winkler ≥ 0.85) handles minor dynamic text changes
        // (e.g. "Allow" vs "Allow Once") without breaking screen recognition.
        val matchedTarget = findBestMatchingTextElement(
            elements = snapshot.textElements,
            targetText = top.actionText
        ) ?: return null

        val type = parseActionType(top.actionType)
        // Use the live element text (not the stored text) so the click target is accurate.
        return ActionCommand(type = type, target = matchedTarget)
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

    /**
     * Picks the best screen text candidate for a learned target.
     * Returns null when nothing clears the similarity threshold.
     */
    internal fun findBestMatchingTextElement(
        elements: List<String>,
        targetText: String,
        threshold: Double = TEXT_MATCH_THRESHOLD
    ): String? {
        return elements
            .asSequence()
            .map { candidate ->
                val score = StringSimilarity.jaroWinkler(
                    candidate.lowercase(),
                    targetText.lowercase()
                )
                candidate to score
            }
            .filter { (_, score) -> score >= threshold }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }
}
