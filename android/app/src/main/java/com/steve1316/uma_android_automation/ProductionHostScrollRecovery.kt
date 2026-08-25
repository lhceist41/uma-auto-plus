package com.steve1316.uma_android_automation

/** A fresh, recognized production screen and the one audited swipe allowed from it. */
internal data class PreparedHostSwipe(
    val before: ScreenFingerprint,
    val request: BoundedSwipe?,
)

/** Truthful result of the optional production host recovery rung. */
internal data class HostScrollRecoveryReport(
    val scope: HostInputScope,
    val execution: InputExecutionResult,
    val movement: SwipeMovement,
    val detailCode: String,
    val swipeAttempts: Int,
    val stopped: Boolean,
) {
    val moved: Boolean
        get() = !stopped && execution.status == InputExecutionStatus.EXECUTED && movement == SwipeMovement.MOVED
}

/**
 * Executes at most one host swipe after the caller has exhausted its Accessibility recovery.
 * Production preconditions are checked here so no list walker can accidentally bypass mode,
 * pairing, health, target, foreground, capability, geometry, fresh-screen, or stop gates.
 */
internal fun executeProductionHostScrollRecovery(
    scope: HostInputScope,
    modeRaw: String?,
    pairingCodeRaw: String?,
    openTransport: (HostInputConfiguration) -> InputTransport,
    prepareSwipe: () -> PreparedHostSwipe,
    shouldStop: () -> Boolean,
    waitBeforeSample: (Double) -> Unit,
    captureAfter: () -> ScreenFingerprint,
): HostScrollRecoveryReport {
    fun blocked(
        status: InputExecutionStatus,
        foreground: Boolean,
        detailCode: String,
        stopped: Boolean = false,
    ): HostScrollRecoveryReport =
        HostScrollRecoveryReport(
            scope = scope,
            execution = InputExecutionResult(status, foreground, detailCode),
            movement = SwipeMovement.UNCERTAIN,
            detailCode = detailCode,
            swipeAttempts = 0,
            stopped = stopped,
        )

    if (shouldStop()) return blocked(InputExecutionStatus.REJECTED, false, "STOP_REQUESTED", stopped = true)
    if (HostInputMode.parse(modeRaw) != HostInputMode.ACCESSIBILITY_WITH_HOST) {
        return blocked(InputExecutionStatus.UNAVAILABLE, false, "HOST_INPUT_DISABLED")
    }
    val pairing = HostAdbProtocol.parsePairingCode(pairingCodeRaw)
        ?: return blocked(InputExecutionStatus.UNAVAILABLE, false, "HOST_INPUT_UNPAIRED")
    val configuration = HostInputConfiguration(pairing)
    val transport =
        try {
            openTransport(configuration)
        } catch (_: Exception) {
            return blocked(InputExecutionStatus.UNAVAILABLE, false, "TRANSPORT_UNAVAILABLE")
        }

    transport.use { activeTransport ->
        val health =
            try {
                activeTransport.health()
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
                return blocked(InputExecutionStatus.UNAVAILABLE, false, "HEALTH_UNAVAILABLE")
            }
        if (health.status != InputExecutionStatus.EXECUTED) {
            return blocked(health.status, health.foreground, "HEALTH_${health.status.wire}")
        }
        if (health.targetId != pairing.targetId) {
            return blocked(InputExecutionStatus.REJECTED, health.foreground, "TARGET_MISMATCH")
        }
        if (!health.foreground) {
            return blocked(InputExecutionStatus.REJECTED, false, "FOREGROUND_REJECTED")
        }
        if (InputCapability.SWIPE !in health.capabilities) {
            return blocked(InputExecutionStatus.UNAVAILABLE, true, "SWIPE_UNAVAILABLE")
        }
        if (shouldStop()) return blocked(InputExecutionStatus.REJECTED, true, "STOP_REQUESTED", stopped = true)

        // Capture after HEALTH and immediately before input. The caller must prove it is still on
        // the audited list and derive geometry from this fresh frame.
        val prepared =
            try {
                prepareSwipe()
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
                return blocked(InputExecutionStatus.UNAVAILABLE, true, "SCREEN_CAPTURE_UNAVAILABLE")
            }
        if (!prepared.before.recognized || prepared.before.value.isBlank()) {
            return blocked(InputExecutionStatus.REJECTED, true, "SCREEN_NOT_RECOGNIZED")
        }
        val request = prepared.request
        if (request == null || request.scope != scope || request.validationError() != null) {
            return blocked(InputExecutionStatus.REJECTED, true, "INVALID_GEOMETRY")
        }
        if (shouldStop()) return blocked(InputExecutionStatus.REJECTED, true, "STOP_REQUESTED", stopped = true)

        var swipeAttempts = 0
        var stoppedBeforeDispatch = false
        val settled =
            try {
                executeHostSwipeWithSettleVerification(
                    before = prepared.before,
                    executeSwipe = {
                        if (shouldStop()) {
                            stoppedBeforeDispatch = true
                            InputExecutionResult(InputExecutionStatus.REJECTED, foreground = true, detailCode = "STOP_REQUESTED")
                        } else {
                            swipeAttempts++
                            activeTransport.swipe(request)
                        }
                    },
                    shouldStop = shouldStop,
                    waitBeforeSample = waitBeforeSample,
                    captureAfter = captureAfter,
                )
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
                val status = if (swipeAttempts > 0) InputExecutionStatus.AMBIGUOUS else InputExecutionStatus.UNAVAILABLE
                return HostScrollRecoveryReport(
                    scope = scope,
                    execution = InputExecutionResult(status, foreground = false, detailCode = "SETTLE_UNAVAILABLE"),
                    movement = SwipeMovement.UNCERTAIN,
                    detailCode = "SETTLE_UNAVAILABLE",
                    swipeAttempts = swipeAttempts,
                    stopped = shouldStop(),
                )
            }
        val stopped = stoppedBeforeDispatch || settled.stopped
        val detailCode =
            when {
                stopped -> "STOP_REQUESTED"
                settled.movement == SwipeMovement.MOVED -> "MOVED"
                settled.movement == SwipeMovement.NO_EFFECT -> "NO_EFFECT"
                settled.execution.status != InputExecutionStatus.EXECUTED -> settled.execution.detailCode
                else -> "UNCERTAIN"
            }
        return HostScrollRecoveryReport(
            scope = scope,
            execution = settled.execution,
            movement = settled.movement,
            detailCode = detailCode,
            swipeAttempts = swipeAttempts,
            stopped = stopped,
        )
    }
}
