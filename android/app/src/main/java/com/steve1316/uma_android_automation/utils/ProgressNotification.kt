package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.postsProgressLine
import com.steve1316.uma_android_automation.progressLineDue
import com.steve1316.uma_android_automation.progressLineText

/**
 * The live progress line in the library's ongoing notification, built from the same [StatusBoard]
 * snapshot the dashboard shows. Called from the session's own thread after a queue event or a turn's
 * reads; never from a MessageLog or EventBus subscriber, and it never logs.
 *
 * [end] runs first in the session's end notifier, so no line can land after the end text: the end
 * text is posted only after the session thread has finished, and every refresh after [end] is a no-op.
 */
internal object ProgressNotification {
    private var post: ((String) -> Unit)? = null
    private var captureRunning: () -> Boolean = { false }
    private var lastText: String? = null
    private var lastPostAt: Long? = null

    @Synchronized
    fun begin(
        captureRunning: () -> Boolean,
        post: (String) -> Unit,
    ) {
        this.captureRunning = captureRunning
        this.post = post
        lastText = null
        lastPostAt = null
    }

    @Synchronized
    fun end() {
        post = null
    }

    @Synchronized
    fun refresh(now: Long = System.currentTimeMillis()) {
        val post = post ?: return
        val s = StatusBoard.snapshot()
        val status = s.statusKey ?: "running"
        if (!postsProgressLine(status) || !captureRunning()) return
        val date = s.career?.let { c -> listOfNotNull(c.year, c.dateLabel).joinToString(" ").ifEmpty { null } }
        val text =
            progressLineText(
                status,
                s.runCurrent,
                s.runTotal,
                date,
                runRecorded = s.runs.any { it.run == s.runCurrent },
                pausePending = StartModule.stopAfterCareerRequested,
            )
        if (!progressLineDue(text, lastText, now, lastPostAt)) return
        lastText = text
        lastPostAt = now
        runCatching { post(text) }
    }
}
