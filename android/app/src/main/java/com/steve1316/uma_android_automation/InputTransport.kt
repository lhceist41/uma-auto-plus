package com.steve1316.uma_android_automation

import java.io.Closeable

internal enum class InputTransportAuthority {
    ACCESSIBILITY,
    HOST_ADB,
}

internal enum class InputCapability {
    HEALTH,
    SWIPE,
}

internal enum class HostInputMode(val wire: String) {
    ACCESSIBILITY_ONLY("accessibility_only"),
    ACCESSIBILITY_WITH_HOST("accessibility_with_host"),
    ;

    companion object {
        fun parse(raw: String?): HostInputMode =
            entries.firstOrNull { it.wire == raw } ?: ACCESSIBILITY_ONLY
    }
}

internal enum class HostInputScope(val wire: String) {
    BORROW_LIST_SCROLL("BORROW_LIST_SCROLL"),
    LEGACY_LIST_SCROLL("LEGACY_LIST_SCROLL"),
}

internal enum class InputExecutionStatus(val wire: String) {
    EXECUTED("EXECUTED"),
    REJECTED("REJECTED"),
    TIMEOUT("TIMEOUT"),
    AMBIGUOUS("AMBIGUOUS"),
    UNAVAILABLE("UNAVAILABLE"),
    ;

    companion object {
        fun parse(raw: String?): InputExecutionStatus =
            entries.firstOrNull { it.wire == raw } ?: UNAVAILABLE
    }
}

internal data class InputHealth(
    val status: InputExecutionStatus,
    val foreground: Boolean,
    val capabilities: Set<InputCapability>,
    val targetId: String,
)

internal data class BoundedSwipe(
    val scope: HostInputScope,
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val durationMs: Int,
) {
    fun validationError(): String? {
        if (listOf(startX, startY, endX, endY).any { it !in 0..10_000 }) return "COORDINATES_OUT_OF_RANGE"
        if (durationMs !in 700..1100) return "DURATION_OUT_OF_RANGE"
        if (startX !in 4400..5600 || endX !in 4400..5600 || kotlin.math.abs(startX - endX) > 300) return "GEOMETRY_NOT_ALLOWED"
        if (endY >= startY || startY - endY < 2200) return "GEOMETRY_NOT_ALLOWED"
        return when (scope) {
            HostInputScope.BORROW_LIST_SCROLL ->
                if (startY !in 6800..8500 || endY !in 2500..4500) "GEOMETRY_NOT_ALLOWED" else null
            HostInputScope.LEGACY_LIST_SCROLL ->
                if (startY !in 5500..8000 || endY !in 2000..4500) "GEOMETRY_NOT_ALLOWED" else null
        }
    }

    companion object {
        fun fromPixels(
            scope: HostInputScope,
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            frameWidth: Int,
            frameHeight: Int,
            durationMs: Int,
        ): BoundedSwipe? {
            if (frameWidth <= 1 || frameHeight <= 1) return null
            fun normalized(value: Float, extent: Int): Int =
                ((value.coerceIn(0f, (extent - 1).toFloat()) * 10_000f) / (extent - 1).toFloat()).toInt()
            return BoundedSwipe(
                scope = scope,
                startX = normalized(startX, frameWidth),
                startY = normalized(startY, frameHeight),
                endX = normalized(endX, frameWidth),
                endY = normalized(endY, frameHeight),
                durationMs = durationMs,
            ).takeIf { it.validationError() == null }
        }
    }
}

internal data class InputExecutionResult(
    val status: InputExecutionStatus,
    val foreground: Boolean,
    val detailCode: String,
)

internal interface InputTransport : Closeable {
    val authority: InputTransportAuthority

    fun health(): InputHealth

    fun swipe(request: BoundedSwipe): InputExecutionResult
}

internal class AccessibilityInputTransport(
    private val dispatchSwipe: (BoundedSwipe) -> Boolean,
) : InputTransport {
    override val authority: InputTransportAuthority = InputTransportAuthority.ACCESSIBILITY

    override fun health(): InputHealth =
        InputHealth(
            status = InputExecutionStatus.EXECUTED,
            foreground = true,
            capabilities = setOf(InputCapability.HEALTH, InputCapability.SWIPE),
            targetId = "local",
        )

    override fun swipe(request: BoundedSwipe): InputExecutionResult {
        if (request.validationError() != null) {
            return InputExecutionResult(InputExecutionStatus.REJECTED, foreground = true, detailCode = "INVALID_REQUEST")
        }
        val dispatched = dispatchSwipe(request)
        return InputExecutionResult(
            status = if (dispatched) InputExecutionStatus.EXECUTED else InputExecutionStatus.REJECTED,
            foreground = true,
            detailCode = if (dispatched) "DISPATCHED" else "DISPATCH_REJECTED",
        )
    }

    override fun close() = Unit
}

internal data class ScreenFingerprint(
    val recognized: Boolean,
    val value: String,
)

internal enum class SwipeMovement {
    MOVED,
    NO_EFFECT,
    UNCERTAIN,
}

internal data class HostSwipeDiagnosticReport(
    val scope: HostInputScope,
    val execution: InputExecutionResult,
    val movement: SwipeMovement,
    val beforeRecognized: Boolean,
    val afterRecognized: Boolean,
)

internal const val HOST_SWIPE_INITIAL_SETTLE_SECONDS = 1.3
internal const val HOST_SWIPE_SETTLE_POLL_SECONDS = 0.35
internal const val HOST_SWIPE_SETTLE_MAX_SAMPLES = 8

internal data class HostSwipeSettleResult(
    val execution: InputExecutionResult,
    val stableAfter: ScreenFingerprint,
    val movement: SwipeMovement,
    val samplesTaken: Int,
    val recognizedSamples: Int,
    val stopped: Boolean,
)

internal fun executeHostSwipeWithSettleVerification(
    before: ScreenFingerprint,
    executeSwipe: () -> InputExecutionResult,
    shouldStop: () -> Boolean,
    waitBeforeSample: (Double) -> Unit,
    captureAfter: () -> ScreenFingerprint,
    initialDelaySeconds: Double = HOST_SWIPE_INITIAL_SETTLE_SECONDS,
    pollIntervalSeconds: Double = HOST_SWIPE_SETTLE_POLL_SECONDS,
    maxSamples: Int = HOST_SWIPE_SETTLE_MAX_SAMPLES,
): HostSwipeSettleResult {
    require(initialDelaySeconds >= 0.0)
    require(pollIntervalSeconds >= 0.0)
    require(maxSamples >= 2)

    val execution = executeSwipe()
    var samplesTaken = 0
    var recognizedSamples = 0

    fun noStableAfter(stopped: Boolean): HostSwipeSettleResult {
        val stableAfter = ScreenFingerprint(recognized = false, value = "")
        return HostSwipeSettleResult(
            execution = execution,
            stableAfter = stableAfter,
            movement = verifySwipeMovement(before, execution, stableAfter),
            samplesTaken = samplesTaken,
            recognizedSamples = recognizedSamples,
            stopped = stopped,
        )
    }

    if (execution.status != InputExecutionStatus.EXECUTED) return noStableAfter(stopped = false)

    var previousRecognized: ScreenFingerprint? = null
    for (sampleIndex in 0 until maxSamples) {
        if (shouldStop()) return noStableAfter(stopped = true)
        waitBeforeSample(if (sampleIndex == 0) initialDelaySeconds else pollIntervalSeconds)
        if (shouldStop()) return noStableAfter(stopped = true)

        val current = captureAfter()
        samplesTaken++
        if (!current.recognized || current.value.isBlank()) {
            previousRecognized = null
            continue
        }
        if (previousRecognized === current) {
            previousRecognized = null
            continue
        }
        recognizedSamples++
        if (previousRecognized?.value == current.value) {
            return HostSwipeSettleResult(
                execution = execution,
                stableAfter = current,
                movement = verifySwipeMovement(before, execution, current),
                samplesTaken = samplesTaken,
                recognizedSamples = recognizedSamples,
                stopped = false,
            )
        }
        previousRecognized = current
    }
    return noStableAfter(stopped = false)
}

internal fun verifySwipeMovement(
    before: ScreenFingerprint,
    execution: InputExecutionResult,
    after: ScreenFingerprint,
): SwipeMovement {
    if (execution.status != InputExecutionStatus.EXECUTED) return SwipeMovement.UNCERTAIN
    if (!before.recognized || !after.recognized || before.value.isBlank() || after.value.isBlank()) return SwipeMovement.UNCERTAIN
    return if (before.value == after.value) SwipeMovement.NO_EFFECT else SwipeMovement.MOVED
}
