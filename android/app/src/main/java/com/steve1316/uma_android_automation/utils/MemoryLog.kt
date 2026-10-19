package com.steve1316.uma_android_automation.utils

import android.os.Debug
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
import java.util.concurrent.atomic.AtomicInteger

/** One reading for a [MemoryLog] line: native heap in use, Java heap in use, live Java threads. */
data class MemorySample(val nativeKb: Long, val javaKb: Long, val threads: Int)

/**
 * Writes one "[MEM]" line per career turn and at each run's start and end, so a long queue shows which memory grows.
 * Every reading is a counter the runtime already keeps: no GC, no file read.
 */
class MemoryLog(
    private val gameId: Int,
    private val scenario: String,
    private val sample: () -> MemorySample = ::readMemory,
    private val emit: (String) -> Unit = { MessageLog.i(TAG, it) },
) {
    private var lastTurn: Int? = null

    fun gameStart() = emit(formatLine("game_start", gameId, null, null, scenario, null, sample()))

    fun gameEnd() = emit(formatLine("game_end", gameId, lastTurn, null, scenario, null, sample()))

    /** Logs the turn once; the turn's later main-screen ticks stay silent. Returns true when it logged. */
    fun turnEnd(turn: Int, phase: String, step: String?): Boolean {
        if (turn == lastTurn) return false
        lastTurn = turn
        emit(formatLine("turn", gameId, turn, phase, scenario, step, sample()))
        return true
    }

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]MemoryLog"

        private val gameIds = AtomicInteger(0)

        fun nextGameId(): Int = gameIds.incrementAndGet()

        fun formatLine(event: String, gameId: Int, turn: Int?, phase: String?, scenario: String, step: String?, s: MemorySample): String =
            "[MEM] event=$event game=$gameId turn=${turn ?: "-"} phase=${token(phase)} scenario=${token(scenario)} step=${token(step)} " +
                "nativeKB=${s.nativeKb} javaKB=${s.javaKb} threads=${s.threads}"

        private fun token(value: String?): String = value?.trim()?.takeIf { it.isNotEmpty() }?.replace(Regex("\\s+"), "_") ?: "-"

        fun readMemory(): MemorySample {
            val runtime = Runtime.getRuntime()
            // The root group's count covers every Java thread, including the bot's unnamed worker threads.
            var group: ThreadGroup? = Thread.currentThread().threadGroup
            while (group?.parent != null) group = group.parent
            return MemorySample(Debug.getNativeHeapAllocatedSize() / 1024, (runtime.totalMemory() - runtime.freeMemory()) / 1024, group?.activeCount() ?: 0)
        }
    }
}

/**
 * Says when the Tesseract engines are due for re-creation: every [everyTurns] turns while [enabled].
 * A re-creation skipped because OCR was busy stays due, so the next turn boundary tries again.
 */
class TesseractReinitSchedule(private val enabled: Boolean, everyTurns: Int) {
    private val everyTurns = everyTurns.coerceAtLeast(1)
    private var turnsSinceReinit = 0
    private var stopped = false

    fun turnEnded(): Boolean {
        if (!enabled || stopped) return false
        turnsSinceReinit++
        return turnsSinceReinit >= everyTurns
    }

    fun reinitDone() {
        turnsSinceReinit = 0
    }

    /** After a failed re-creation the run keeps its old engines and tries no more. */
    fun stop() {
        stopped = true
    }
}
