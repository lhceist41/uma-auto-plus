package com.steve1316.uma_android_automation.bot

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.util.concurrent.atomic.AtomicReference

// The stall watchdog's recovery ladder. Every rung acts first and then logs with android.util.Log
// only: MessageLog's process-wide lock, Game.wait and SettingsHelper can be exactly what stalled
// (both watchdogs have deadlocked on their own MessageLog call before), so no rung touches them.

internal enum class WatchdogRung { NONE, RESET, RECOVERED, TOGGLE_ACCESSIBILITY, SKIP_TOGGLE, INTERRUPT_GAME_THREAD, KILL }

internal const val WATCHDOG_TOGGLE_AT_MS = 120_000L
internal const val WATCHDOG_INTERRUPT_AT_MS = 150_000L

/** 3 minutes, so popup animations and dialog chains do not false-trigger the kill. */
internal const val WATCHDOG_KILL_AT_MS = 180_000L

/**
 * An interrupted Game thread that has exited is a recovery (the queue owns the run). A stall with no
 * live Game thread (the queue thread itself stuck) still ends in the kill.
 */
internal fun decideWatchdogRung(ageMs: Long, rungsDone: Int, gameThreadAlive: Boolean, grantPresent: Boolean): WatchdogRung =
    when {
        rungsDone >= 2 && !gameThreadAlive -> WatchdogRung.RECOVERED
        ageMs < WATCHDOG_TOGGLE_AT_MS -> if (rungsDone > 0) WatchdogRung.RESET else WatchdogRung.NONE
        rungsDone == 0 -> if (grantPresent) WatchdogRung.TOGGLE_ACCESSIBILITY else WatchdogRung.SKIP_TOGGLE
        ageMs < WATCHDOG_INTERRUPT_AT_MS -> WatchdogRung.NONE
        rungsDone == 1 && gameThreadAlive -> WatchdogRung.INTERRUPT_GAME_THREAD
        ageMs >= WATCHDOG_KILL_AT_MS -> WatchdogRung.KILL
        else -> WatchdogRung.NONE
    }

internal fun rungsDoneAfter(rung: WatchdogRung, rungsDone: Int): Int =
    when (rung) {
        WatchdogRung.RESET, WatchdogRung.RECOVERED -> 0
        WatchdogRung.TOGGLE_ACCESSIBILITY, WatchdogRung.SKIP_TOGGLE -> 1
        WatchdogRung.INTERRUPT_GAME_THREAD -> 2
        WatchdogRung.NONE, WatchdogRung.KILL -> rungsDone
    }

/** Zero is never claimed, so a Game that never started a run (the navigator's) is never stale; a zombie thread from an older run stops at its next wait or tap. */
internal object GameGeneration {
    @Volatile
    private var current = 0

    @Synchronized
    fun claim(): Int = ++current

    fun isStale(token: Int): Boolean = token != 0 && token != current
}

internal object WatchdogReason {
    private val reason = AtomicReference<String?>(null)

    fun set(text: String) = reason.set(text)

    fun take(): String? = reason.getAndSet(null)

    fun clear() = reason.set(null)
}

internal fun watchdogInterruptReason(ageMs: Long): String = "No progress for ${ageMs / 1000} seconds, so the stall watchdog interrupted the run."

internal data class WatchdogBreadcrumb(val rung: String)

private const val BREADCRUMB_PREFIX = "uma-watchdog"

/** A breadcrumb that marks a stall as over, so a later death is not blamed on it. */
internal const val WATCHDOG_BREADCRUMB_CLEARED = "$BREADCRUMB_PREFIX:CLEARED"

private val BREADCRUMB_RUNGS = setOf(WatchdogRung.TOGGLE_ACCESSIBILITY, WatchdogRung.SKIP_TOGGLE, WatchdogRung.INTERRUPT_GAME_THREAD).map { it.name }.toSet()

/** Under the 128 bytes `setProcessStateSummary` keeps. */
internal fun encodeWatchdogBreadcrumb(rung: WatchdogRung, ageMs: Long): String = "$BREADCRUMB_PREFIX:${rung.name}:${ageMs / 1000}"

internal fun decodeWatchdogBreadcrumb(text: String?): WatchdogBreadcrumb? {
    val parts = text?.split(':') ?: return null
    if (parts.size != 3 || parts[0] != BREADCRUMB_PREFIX || parts[1] !in BREADCRUMB_RUNGS || parts[2].toLongOrNull() == null) return null
    return WatchdogBreadcrumb(parts[1])
}

internal const val WATCHDOG_BREADCRUMB_FILE = "watchdog_breadcrumb"

/** Temp file renamed over the old one, as the queue heartbeat does. */
internal fun writeWatchdogBreadcrumbFile(dir: File, pid: Int, now: Long, text: String): Boolean {
    val target = File(dir, WATCHDOG_BREADCRUMB_FILE)
    val temp = File(dir, "$WATCHDOG_BREADCRUMB_FILE.tmp")
    temp.writeText("$pid:$now:$text")
    if (temp.renameTo(target)) return true
    target.delete()
    return temp.renameTo(target)
}

internal fun readWatchdogBreadcrumbFile(dir: File, pid: Int, since: Long): WatchdogBreadcrumb? =
    try {
        val parts = File(dir, WATCHDOG_BREADCRUMB_FILE).readText().split(':', limit = 3)
        if (parts.size == 3 && parts[0].toIntOrNull() == pid && (parts[1].toLongOrNull() ?: -1L) >= since) decodeWatchdogBreadcrumb(parts[2]) else null
    } catch (_: Exception) {
        null
    }

/**
 * Runs on a detached daemon thread nothing waits for, and only after the rung it describes has
 * acted, so a slow binder call or disk can never delay a recovery or the kill.
 */
internal fun recordWatchdogBreadcrumb(context: Context?, text: String) {
    val app = context ?: return
    try {
        Thread {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    setProcessStateSummary(app, text)
                } else {
                    writeWatchdogBreadcrumbFile(app.filesDir, android.os.Process.myPid(), System.currentTimeMillis(), text)
                }
            } catch (_: Throwable) {
            }
        }.apply {
            isDaemon = true
            start()
        }
    } catch (_: Throwable) {
    }
}

@RequiresApi(30)
private fun setProcessStateSummary(context: Context, text: String) {
    context.getSystemService(ActivityManager::class.java)?.setProcessStateSummary(text.toByteArray(Charsets.US_ASCII))
}

/** Whether the accessibility rung can run. Read on the Game thread as a run starts, never on the watchdog's. */
internal fun hasSecureSettingsGrant(context: Context): Boolean =
    try {
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

/**
 * Removes and re-adds this app's accessibility service like Game.forceRebindAccessibilityService, but with only the
 * application Context and Thread.sleep: that one waits through Game.wait and logs through MessageLog.
 */
internal fun toggleAccessibilityForWatchdog(context: Context): Boolean {
    val expected = "${context.packageName}/com.steve1316.automation_library.utils.MyAccessibilityService"
    return try {
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val others = current.split(':').filter { it.isNotEmpty() && !it.equals(expected, ignoreCase = true) }
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.joinToString(":"))
        Thread.sleep(1_000L)
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (others + expected).joinToString(":"))
        Settings.Secure.putString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
        true
    } catch (_: SecurityException) {
        false
    } catch (_: InterruptedException) {
        false
    }
}

/** Runs [block] on a new daemon thread, so a rung that blocks never holds up the watchdog's own loop or the kill. */
internal fun runWatchdogRung(name: String, block: () -> Unit) {
    try {
        Thread({
            try {
                block()
            } catch (e: Throwable) {
                Log.e("StallWatchdog", "[WATCHDOG] The $name rung failed: ${e.javaClass.simpleName}")
            }
        }, "watchdog-$name").apply {
            isDaemon = true
            start()
        }
    } catch (_: Throwable) {
    }
}
