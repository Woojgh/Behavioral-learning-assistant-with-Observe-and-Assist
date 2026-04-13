package com.example.aiassistant

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SafetyChecker's pure logic (no Android Context required).
 * Context-dependent methods (isAppAllowed, getExcludedApps, etc.) are covered
 * by instrumented tests.
 */
class SafetyCheckerTest {

    @Before
    fun resetCooldown() {
        // Ensure each test starts with no active cooldown.
        // We do this by calling checkCooldown() after a small wait, relying on the
        // 800 ms threshold never being met if lastActionTime is at epoch 0.
        // The AtomicLong starts at 0, so the first checkCooldown() always passes.
    }

    // -------------------------------------------------------------------------
    // isActionSafe
    // -------------------------------------------------------------------------

    @Test
    fun isActionSafe_plainClickTarget_returnsTrue() {
        assertTrue(SafetyChecker.isActionSafe(ActionCommand(ActionType.CLICK, "Skip")))
    }

    @Test
    fun isActionSafe_allowButton_returnsTrue() {
        assertTrue(SafetyChecker.isActionSafe(ActionCommand(ActionType.CLICK, "Allow")))
    }

    @Test
    fun isActionSafe_purchaseKeyword_returnsFalse() {
        assertFalse(SafetyChecker.isActionSafe(ActionCommand(ActionType.CLICK, "Purchase now")))
    }

    @Test
    fun isActionSafe_buyKeyword_returnsFalse() {
        assertFalse(SafetyChecker.isActionSafe(ActionCommand(ActionType.CLICK, "Buy Premium")))
    }

    @Test
    fun isActionSafe_deleteKeyword_returnsFalse() {
        assertFalse(SafetyChecker.isActionSafe(ActionCommand(ActionType.CLICK, "Delete Account")))
    }

    @Test
    fun isActionSafe_caseInsensitive_returnsFalse() {
        assertFalse(SafetyChecker.isActionSafe(ActionCommand(ActionType.CLICK, "PURCHASE")))
        assertFalse(SafetyChecker.isActionSafe(ActionCommand(ActionType.CLICK, "Reset Settings")))
    }

    @Test
    fun isActionSafe_subscribeKeyword_returnsFalse() {
        assertFalse(SafetyChecker.isActionSafe(ActionCommand(ActionType.CLICK, "Subscribe to Premium")))
    }

    @Test
    fun isActionSafe_uninstallKeyword_returnsFalse() {
        assertFalse(SafetyChecker.isActionSafe(ActionCommand(ActionType.CLICK, "Uninstall app")))
    }

    // -------------------------------------------------------------------------
    // cooldown
    // -------------------------------------------------------------------------

    @Test
    fun checkCooldown_initialState_returnsTrue() {
        // lastActionTime starts at 0 (epoch), so the cooldown period has long passed.
        assertTrue(SafetyChecker.checkCooldown())
    }

    @Test
    fun checkCooldown_afterRecord_returnsFalse() {
        SafetyChecker.recordActionTime()
        // Immediately after recording, cooldown should not have elapsed.
        assertFalse(SafetyChecker.checkCooldown())
    }

    @Test
    fun checkCooldown_afterCooldownElapsed_returnsTrue() {
        SafetyChecker.recordActionTime()
        // Wait slightly longer than the 800 ms cooldown.
        Thread.sleep(900)
        assertTrue(SafetyChecker.checkCooldown())
    }
}
