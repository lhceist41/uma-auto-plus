package com.steve1316.uma_android_automation

/**
 * Whether an EventBus event is the foundation library's "screen capture stopped" signal. The
 * notification's STOP action stops only the capture service (its receiver never calls
 * [StartModule.stop]), and the library does not interrupt the bot thread when its services are
 * destroyed, so this event is the only notice the running session gets.
 */
internal fun isCaptureStoppedEvent(eventName: String, message: String): Boolean = eventName == "MediaProjectionService" && message == "Not Running"

/** Whether losing screen capture must request a stop: only while a session runs and no stop is pending yet. */
internal fun shouldStopForLostCapture(sessionActive: Boolean, stopAlreadyRequested: Boolean): Boolean = sessionActive && !stopAlreadyRequested
