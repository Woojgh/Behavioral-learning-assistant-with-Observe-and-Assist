package com.example.aiassistant

import android.content.Context

/**
 * On each screen change, checks if any learned system-setting patterns
 * match the current screen context. In ASSIST mode, applies the setting
 * change automatically if confidence is high enough.
 */
object ScreenReactor {

    /** Minimum observations before auto-applying a system change. */
    private const val MIN_CONFIDENCE = 3

    /**
     * Check the current screen against learned system patterns.
     * Only takes action in ASSIST mode.
     */
    suspend fun checkAndReact(
        context: Context,
        snapshot: ScreenSnapshot,
        mode: AgentMode
    ) {
        // Only react in ASSIST mode
        if (mode != AgentMode.ASSIST) return

        // Respect app exclusions
        if (!SafetyChecker.isAppAllowed(context, snapshot.packageName)) return

        val dao = DatabaseHelper.getDB(context).systemPatternDao()
        val patterns = dao.getByScreenState(snapshot.stableState)

        for (pattern in patterns) {
            // Need enough confidence
            if (pattern.count < MIN_CONFIDENCE) continue

            // Verify the keywords that were present during learning are still on screen
            if (!keywordsMatch(pattern.matchedKeywords, snapshot.textElements)) continue

            // Apply the system change
            val success = SystemActionExecutor.apply(context, pattern.settingName, pattern.newValue)

            // Log it
            DatabaseHelper.logAction(
                context = context,
                packageName = snapshot.packageName,
                state = snapshot.stableState,
                actionType = "SYSTEM:${pattern.settingName}",
                actionDetail = "${pattern.oldValue} → ${pattern.newValue}",
                success = success
            )

            if (success) {
                OverlayService.updateStatus("${pattern.settingName}: ${pattern.newValue}")
            }
        }
    }

    /**
     * Check that at least half of the learned keywords are present on the current screen.
     * This handles minor screen changes while still requiring meaningful context match.
     */
    private fun keywordsMatch(storedKeywords: String, screenText: List<String>): Boolean {
        val keywords = storedKeywords.split(",").filter { it.isNotBlank() }
        if (keywords.isEmpty()) return false

        val screenLower = screenText.map { it.lowercase() }
        val matchCount = keywords.count { kw ->
            screenLower.any { it.contains(kw.lowercase()) }
        }

        return matchCount >= (keywords.size / 2).coerceAtLeast(1)
    }
}
