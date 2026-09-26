package com.steve1316.uma_android_automation.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.StatFs
import android.util.Log
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.MediaProjectionService
import com.steve1316.uma_android_automation.BuildConfig
import com.steve1316.uma_android_automation.StartModule
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val TAG = "AppUpdateInstaller"

/** The broadcast the installer session reports its status to; only this app can receive it. */
internal const val INSTALL_STATUS_ACTION = "com.steve1316.uma_android_automation.UPDATE_INSTALL_STATUS"

private const val UPDATES_DIR = "updates"

/** Downloads live in app storage, which the system does not clear mid-download the way it can clear the cache. */
internal fun updateFilesDir(context: Context): File = File(context.filesDir, UPDATES_DIR)

/** Removes every downloaded or partial update. Called before each attempt and after each hand-over. */
internal fun clearUpdateFiles(context: Context) {
    updateFilesDir(context).listFiles()?.forEach { if (!it.delete()) Log.w(TAG, "Could not delete an old update file.") }
}

/** Reads the four idle signals now. Reads settings.db, so call it off the main thread. */
internal fun currentUpdateBlock(context: Context): UpdateStop? =
    updateBlockReason(
        botRunning = BotService.isRunning,
        projectionRunning = MediaProjectionService.isRunning,
        sessionActive = StartModule.isSessionActive(),
        resumableQueue = StartModule.loadQueueState(context) != null,
    )

internal sealed interface ReleaseLookup {
    data class Found(val release: JSONObject) : ReleaseLookup

    data object NotPublished : ReleaseLookup

    data object RateLimited : ReleaseLookup

    data object Failed : ReleaseLookup
}

/** One unauthenticated GitHub API read of the release [tag]. GitHub refuses requests without a User-Agent. */
internal fun fetchRelease(tag: String): ReleaseLookup {
    val connection = URL(RELEASE_API_PREFIX + tag).openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "UMA-Auto-Plus/${BuildConfig.VERSION_NAME}")
        when (lookupOutcome(connection.responseCode, connection.getHeaderField("X-RateLimit-Remaining"), connection.getHeaderField("Retry-After"))) {
            LookupOutcome.FOUND -> ReleaseLookup.Found(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
            LookupOutcome.NOT_PUBLISHED -> ReleaseLookup.NotPublished
            LookupOutcome.RATE_LIMITED -> ReleaseLookup.RateLimited
            LookupOutcome.FAILED -> ReleaseLookup.Failed
        }
    } catch (e: Exception) {
        Log.w(TAG, "Release lookup failed: ${e.javaClass.simpleName}")
        ReleaseLookup.Failed
    } finally {
        connection.disconnect()
    }
}

internal fun hasRoomForDownload(context: Context, size: Long): Boolean = hasRoomFor(StatFs(context.filesDir.path).availableBytes, size)

/** A finished download: the file, its byte count and its sha256, computed while it was written. */
internal data class DownloadedFile(val file: File, val size: Long, val sha256: String)

/**
 * Streams [asset] into a part file of its own (`updates/<name>.<unique>.part`, so a retry never
 * shares a file with a cancelled attempt still unwinding) and returns it once the stream ends, or
 * null when the download failed or [isCancelled] turned true; a partial file is deleted either way.
 * github.com redirects to its HTTPS file host, which the connection follows because both are HTTPS.
 * Stops at one byte past the published size, since such a file can only fail the size check.
 */
internal fun downloadAsset(context: Context, asset: ReleaseAsset, isCancelled: () -> Boolean, onProgress: (Long) -> Unit): DownloadedFile? {
    val dir = updateFilesDir(context).apply { mkdirs() }
    val part = File.createTempFile("${asset.name}.", ".part", dir)
    val connection = URL(asset.url).openConnection() as HttpURLConnection
    var finished = false
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "UMA-Auto-Plus/${BuildConfig.VERSION_NAME}")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        connection.inputStream.use { input ->
            part.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (isCancelled()) return null
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    written += read
                    onProgress(written)
                    if (written > asset.size) break
                }
                output.fd.sync()
            }
        }
        finished = true
        return DownloadedFile(part, written, digest.digest().joinToString("") { "%02x".format(it) })
    } catch (e: IOException) {
        Log.w(TAG, "Update download failed: ${e.javaClass.simpleName}")
        return null
    } finally {
        connection.disconnect()
        if (!finished) part.delete()
    }
}

/** Checks a finished download against the release and this install; on success renames its part file to `.apk`. */
internal fun checkDownload(context: Context, asset: ReleaseAsset, download: DownloadedFile, expectedVersionName: String): Pair<DownloadCheck, File?> {
    val pm = context.packageManager
    val archive: PackageInfo? = if (download.size == asset.size) pm.getPackageArchiveInfo(download.file.path, 0) else null
    val installed = pm.getPackageInfo(context.packageName, 0)
    val check =
        verifyDownload(
            asset = asset,
            actualSize = download.size,
            actualSha256 = download.sha256,
            archivePackage = archive?.packageName,
            archiveVersionName = archive?.versionName,
            archiveVersionCode = archive?.let(::versionCodeOf),
            expectedPackage = context.packageName,
            expectedVersionName = expectedVersionName,
            installedVersionCode = versionCodeOf(installed),
        )
    if (check != DownloadCheck.OK) {
        download.file.delete()
        return check to null
    }
    val apk = File(download.file.parentFile, download.file.name.removeSuffix(".part") + ".apk")
    if (download.file.renameTo(apk)) return check to apk
    download.file.delete()
    return DownloadCheck.UNREADABLE_ARCHIVE to null
}

private fun versionCodeOf(info: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }

/**
 * Hands [apk] to Android's installer in a session that needs the player's confirmation, and
 * returns null once committed. The bytes written into the session are hashed as they are copied,
 * and the session is abandoned unless they are exactly the [verifiedSha256] bytes, so nothing that
 * changed after the check can reach the installer. [blockBeforeCommit] then re-reads the idle
 * signals right before the commit, because the player can press Start during a download of several
 * minutes; a block abandons the session and is returned. [onSessionCreated] gets the session id
 * first, so the dialog can abandon it if it closes before Android shows its confirmation. The
 * package source is left unset: on Android 13+ a downloaded-file source can restrict turning the
 * accessibility service back on.
 */
internal fun commitInstall(context: Context, apk: File, verifiedSha256: String, onSessionCreated: (Int) -> Unit, blockBeforeCommit: () -> UpdateStop?): UpdateStop? {
    val installer = context.packageManager.packageInstaller
    val params =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
    val sessionId = installer.createSession(params)
    onSessionCreated(sessionId)
    installer.openSession(sessionId).use { session ->
        try {
            val written =
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    val copy = apk.inputStream().use { copyWithSha256(it, out) }
                    session.fsync(out)
                    copy
                }
            if (written.second != verifiedSha256.lowercase()) {
                session.abandon()
                return UpdateStop.VERIFY_FAILED
            }
            val block = blockBeforeCommit()
            if (block != null) {
                session.abandon()
                return block
            }
            val statusIntent = Intent(INSTALL_STATUS_ACTION).setPackage(context.packageName)
            // Mutable so the system can add the status extras; explicit, as Android 14 requires of a mutable one.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            session.commit(PendingIntent.getBroadcast(context, sessionId, statusIntent, flags).intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }
    return null
}
