package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The in-app updater's wiring, pinned in source: the installer and dialog need a device, so these
 * guard the safety properties the plan tests cannot reach.
 */
@DisplayName("In-app update wiring")
class AppUpdateWiringTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).exists()) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun source(relative: String) = repoFile(relative).readText().replace("\r\n", "\n")

    private val main = "android/app/src/main"
    private val utils = "$main/java/com/steve1316/uma_android_automation/utils"
    private val installer by lazy { source("$utils/AppUpdateInstaller.kt") }
    private val checker by lazy { source("$utils/AppUpdateChecker.kt") }

    private fun body(text: String, signature: String): String {
        val start = text.indexOf(signature)
        assertTrue(start >= 0, "missing: $signature")
        return text.substring(start).substringBefore("\n}\n")
    }

    @Test
    fun `the manifest asks to request installs and never to update without the player`() {
        val manifest = source("$main/AndroidManifest.xml")
        assertTrue(manifest.contains("<uses-permission android:name=\"android.permission.REQUEST_INSTALL_PACKAGES\" />"))
        assertFalse(manifest.contains("UPDATE_PACKAGES_WITHOUT_USER_ACTION"))
        assertFalse(installer.contains("USER_ACTION_NOT_REQUIRED"))
        assertTrue(installer.contains("setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)"))
    }

    @Test
    fun `no code sets a package source`() {
        val offenders = repoFile("$main/java").walkTopDown().filter { it.isFile && it.readText().contains("setPackageSource(") }.map { it.name }.toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `the idle signals are read again after the copy and right before the commit`() {
        val commit = body(installer, "internal fun commitInstall(")
        val write = commit.indexOf("session.openWrite(\"base.apk\"")
        val hash = commit.indexOf("if (written.second != verifiedSha256.lowercase()) {\n                session.abandon()\n                return UpdateStop.VERIFY_FAILED")
        val recheck = commit.indexOf("val block = blockBeforeCommit()")
        val abandon = commit.indexOf("session.abandon()\n                return block")
        val commitCall = commit.indexOf("session.commit(")
        assertTrue(write in 0 until hash && hash < recheck && recheck < abandon && abandon < commitCall, "write $write, hash $hash, recheck $recheck, abandon $abandon, commit $commitCall")
        assertEquals(1, Regex("\\.commit\\(").findAll(installer + checker).count(), "the only commit")
        assertTrue(checker.contains("commitInstall(activity, apk, verifiedSha256, onSessionCreated = { openingSessionId = it }) {"))
        assertTrue(checker.contains("if (!isActive) UpdateStop.DOWNLOAD_CANCELLED else currentUpdateBlock(activity)"), "a cancelled flow never commits")
    }

    @Test
    fun `the bytes written into the session are the verified bytes`() {
        val commit = body(installer, "internal fun commitInstall(")
        assertTrue(commit.contains("val copy = apk.inputStream().use { copyWithSha256(it, out) }"))
        assertFalse(commit.contains("copyTo("), "no unhashed copy")
        assertTrue(checker.contains("install(apk, download.sha256)"), "the hash the download was verified with")
        val created = commit.indexOf("onSessionCreated(sessionId)")
        assertTrue(created in 0 until commit.indexOf("session.openWrite("), "the dialog knows the session before any byte is written")
    }

    @Test
    fun `the flow ends with the activity and never hands off from a closed screen`() {
        val flow = checker.substringAfter("private inner class UpdateFlow")
        assertTrue(flow.contains("private val activityEnd = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) end() }"))
        assertTrue(flow.contains("dialog.setOnDismissListener { end() }\n            (activity as? LifecycleOwner)?.lifecycle?.addObserver(activityEnd)"))
        val start = flow.substringAfter("private fun start() {")
        val alive = start.indexOf("if (activity.isFinishing || activity.isDestroyed) return@launch")
        assertTrue(alive in 0 until start.indexOf("install(apk, download.sha256)"))
        val pending = flow.substringAfter("if (installStatus == PackageInstaller.STATUS_PENDING_USER_ACTION) {")
        assertTrue(pending.indexOf("if (activity.isFinishing || activity.isDestroyed) return") in 0 until pending.indexOf("activity.startActivity(confirm"))
    }

    @Test
    fun `an unfinished session is abandoned when the flow ends, and kept once Android shows its confirmation`() {
        val end = checker.substringAfter("private fun end() {").substringBefore("\n        }\n\n")
        val steps = listOf("job?.cancel()", "unregisterStatusReceiver()", "removeObserver(activityEnd)", "activity.packageManager.packageInstaller.abandonSession(id)")
        for (step in steps) assertTrue(end.contains(step), step)
        val pending = checker.substringAfter("if (installStatus == PackageInstaller.STATUS_PENDING_USER_ACTION) {")
        assertTrue(pending.indexOf("openingSessionId = null") in 0 until pending.indexOf("activity.startActivity(confirm"))
    }

    @Test
    fun `a rate-limited lookup gets its own words`() {
        assertTrue(installer.contains("lookupOutcome(connection.responseCode, connection.getHeaderField(\"X-RateLimit-Remaining\"), connection.getHeaderField(\"Retry-After\"))"))
        assertTrue(installer.contains("LookupOutcome.RATE_LIMITED -> ReleaseLookup.RateLimited"))
        assertTrue(checker.contains("ReleaseLookup.RateLimited -> return@launch showStop(UpdateStop.LOOKUP_RATE_LIMITED)"))
    }

    @Test
    fun `a version that cannot go into a URL leaves only the release page`() {
        assertTrue(checker.contains("\"Update\" to { if (tag == null) openReleasePage() else start() }"))
        assertTrue(checker.contains("val target = tag ?: return openReleasePage()"))
        assertFalse(Regex("fetchRelease\\((?!target\\))").containsMatchIn(checker), "only the validated tag is fetched")
    }

    @Test
    fun `the update help uses the guide's own wording for menu paths`() {
        val section = source("TROUBLESHOOTING.md").substringAfter("## Updating from inside the app").substringBefore("## Reporting a bug")
        assertTrue(section.contains("Install unknown apps"))
        assertFalse(section.contains("→"))
    }

    @Test
    fun `the idle signals are read at the tap, before anything is fetched`() {
        val start = checker.substring(checker.indexOf("private fun start() {"))
        val tap = start.indexOf("withContext(Dispatchers.IO) { currentUpdateBlock(activity) }")
        assertTrue(tap in 0 until start.indexOf("fetchRelease(target)"))
        assertTrue(tap < start.indexOf("downloadAsset("))
        val signals = body(installer, "internal fun currentUpdateBlock(")
        for (signal in listOf("BotService.isRunning", "MediaProjectionService.isRunning", "StartModule.isSessionActive()", "StartModule.loadQueueState(context) != null")) {
            assertTrue(signals.contains(signal), signal)
        }
    }

    @Test
    fun `the player never sees Android's status message or raw error text`() {
        assertFalse((installer + checker).contains("EXTRA_STATUS_MESSAGE"))
        val shown = Regex("showStatus\\(([^)]*\\)?)\\)").findAll(checker).map { it.groupValues[1] }.toSet()
        val allowed =
            setOf(
                "null",
                "CHECKING_TEXT",
                "HANDED_TO_ANDROID_TEXT",
                "OPENING_INSTALLER_TEXT",
                "NEEDS_PERMISSION_TEXT",
                "stop.text",
                "downloadProgressText(done, total)",
                "text: String?",
            )
        assertEquals(allowed, shown)
        assertEquals(1, Regex("status\\.text = ").findAll(checker).count(), "only showStatus writes the status line")
        assertFalse(Regex("\\.message\\b").containsMatchIn(checker.substringAfter("private inner class UpdateFlow")), "no exception message reaches the dialog")
    }

    @Test
    fun `a device without the install-permission screen falls back instead of crashing`() {
        val permission = checker.substringAfter("private fun showNeedsPermission() {").substringBefore("\n        }\n")
        val open = permission.indexOf("activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES")
        val guard = permission.indexOf("} catch (e: ActivityNotFoundException) {\n                        showStop(UpdateStop.ANDROID_REFUSED)")
        assertTrue(permission.indexOf("try {") in 0 until open && open < guard, "try before $open, catch at $guard")
    }

    @Test
    fun `the status comes back only to this app`() {
        assertTrue(installer.contains("val statusIntent = Intent(INSTALL_STATUS_ACTION).setPackage(context.packageName)"))
        assertTrue(installer.contains("PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0"))
        assertTrue(checker.contains("ContextCompat.registerReceiver(activity, statusReceiver, IntentFilter(INSTALL_STATUS_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)"))
    }

    @Test
    fun `a download is written as a part file, kept only after it checks out, and cleaned up`() {
        assertTrue(installer.contains("val part = File.createTempFile(\"\${asset.name}.\", \".part\", dir)"), "a part file of its own per attempt")
        val check = body(installer, "internal fun checkDownload(")
        assertTrue(check.contains("val apk = File(download.file.parentFile, download.file.name.removeSuffix(\".part\") + \".apk\")"), "the kept file is as unique as its part")
        assertTrue(check.indexOf("verifyDownload(") in 0 until check.indexOf("download.file.renameTo(apk)"))
        assertTrue(check.contains("if (check != DownloadCheck.OK) {\n        download.file.delete()"))
        assertTrue(installer.contains("File(context.filesDir, UPDATES_DIR)"))
        assertTrue(checker.contains("clearUpdateFiles(activity)\n                        URL(UPDATE_XML_URL)"), "each check starts clean")
        assertTrue(checker.contains("} finally {\n                            clearUpdateFiles(activity)"), "the file goes once the session has its copy")
    }

    @Test
    fun `the test tag is empty unless the build asks for it`() {
        assertTrue(source("android/app/build.gradle").contains("buildConfigField \"String\", \"UPDATE_TEST_TAG\", \"\\\"\${findProperty('umaUpdateTestTag') ?: ''}\\\"\""))
        assertEquals(2, Regex("BuildConfig\\.UPDATE_TEST_TAG").findAll(checker).count())
    }

    @Test
    fun `update xml keeps its three elements`() {
        val xml = source("android/app/update.xml")
        assertEquals(listOf("latestVersion", "url", "releaseNotes"), Regex("<(\\w+)>").findAll(xml).map { it.groupValues[1] }.filter { it !in setOf("AppUpdater", "update") }.toList())
    }

    @Test
    fun `Home shows Stop from the same flags the refusal reads, after a re-created screen too`() {
        // "Press Stop in UMA Auto+" must point at a button that says Stop whenever the capture service is up.
        val bridge = source("$main/java/com/steve1316/uma_android_automation/StartModule.kt").substringAfter("fun getSessionState(promise: Promise) {").substringBefore("\n    }\n")
        assertTrue(bridge.contains("map.putBoolean(\"armed\", MediaProjectionService.isRunning)"))
        assertTrue(bridge.contains("map.putBoolean(\"botRunning\", BotService.isRunning)"))
        val home = source("src/pages/Home/index.tsx")
        assertEquals(2, Regex("refreshSessionState\\(\\)").findAll(home).count(), "read on mount and on every return to the app")
        assertTrue(home.contains("dispatchSession({ type: \"NATIVE_STATE\", armed: state.armed, botRunning: state.botRunning })"))
        // A live projection or bot event newer than the read wins over it.
        assertEquals(2, Regex("liveSessionEvents\\.current\\+\\+").findAll(home).count())
        assertTrue(home.contains("if (liveSessionEvents.current !== eventsBefore) return"))
        assertTrue(home.contains("if (phase === \"armed\") return \"Stop · Waiting for overlay\""))
        assertTrue(home.contains("if (phase === \"running\" || phase === \"ended\") return \"Stop\""))
    }
}
