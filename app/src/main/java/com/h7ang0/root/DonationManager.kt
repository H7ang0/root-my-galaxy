package com.h7ang0.root

import android.content.Context
import android.content.SharedPreferences

class DonationManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val successCount: Int
        get() = prefs.getInt(KEY_SUCCESS_COUNT, 0)

    fun recordSuccess(): Boolean {
        val count = successCount + 1
        prefs.edit().putInt(KEY_SUCCESS_COUNT, count).apply()
        val milestone = currentMilestone(count)
        if (milestone > 0 && !wasMilestoneNotified(milestone)) {
            prefs.edit().putInt(KEY_LAST_NOTIFIED_MILESTONE, milestone).apply()
            return true
        }
        return false
    }

    private fun currentMilestone(count: Int): Int {
        if (count >= 100) return 100
        if (count >= 75) return 75
        if (count >= 50) return 50
        if (count >= 25) return 25
        if (count >= 10) return 10
        return 0
    }

    private fun wasMilestoneNotified(milestone: Int): Boolean =
        prefs.getInt(KEY_LAST_NOTIFIED_MILESTONE, 0) >= milestone

    companion object {
        private const val PREFS_NAME = "donation"
        private const val KEY_SUCCESS_COUNT = "root_success_count"
        private const val KEY_LAST_NOTIFIED_MILESTONE = "last_notified_milestone"
    }
}
