package com.example.aiassistant

import android.content.Context
import android.view.accessibility.AccessibilityEvent

/**
 * Observes user-initiated actions and records them as patterns.
 * This runs in both OBSERVE and ASSIST modes — always learning.
 */
object Observer {

    /**
     * Process a user-initiated action event.
     * Call this when TYPE_VIEW_CLICKED, TYPE_VIEW_SCROLLED, or TYPE_VIEW_TEXT_CHANGED
     * is detected — these represent what the *user* actually did.
     */
    suspend fun onUserAction(
        context: Context,
        event: AccessibilityEvent,
        currentState: String,
        packageName: String
    ) {
        val actionText = extractActionText(event) ?: return
        val actionType = mapEventType(event.eventType)

        DatabaseHelper.recordPattern(
            context = context,
            state = currentState,
            packageName = packageName,
            actionText = actionText,
            actionType = actionType
        )
    }

    private fun extractActionText(event: AccessibilityEvent): String? {
        // Try to get the text of the element the user interacted with
        event.text?.let { texts ->
            val joined = texts.joinToString(" ").trim()
            if (joined.isNotEmpty()) return joined
        }

        event.contentDescription?.toString()?.let {
            if (it.isNotEmpty()) return it
        }

        return null
    }

    private fun mapEventType(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICK"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "SCROLL"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE"
            else -> "CLICK"
        }
    }
}
