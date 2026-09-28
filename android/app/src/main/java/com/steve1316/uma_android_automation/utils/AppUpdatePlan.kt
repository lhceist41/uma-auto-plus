package com.steve1316.uma_android_automation.utils

import android.content.pm.PackageInstaller
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** The ABIs each release publishes an APK for (`build.gradle` splits; there is no universal APK). */
internal val PUBLISHED_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

/** Every release APK sits under this prefix; a lookup result pointing anywhere else is not used. */
internal const val RELEASE_DOWNLOAD_PREFIX = "https://github.com/lhceist41/uma-auto-plus/releases/download/"

internal const val RELEASE_API_PREFIX = "https://api.github.com/repos/lhceist41/uma-auto-plus/releases/tags/"

/**
 * Compares two dot-separated versions segment by segment ("5.4.8" > "5.4.7"); a part that is not a
 * number counts as 0.
 *
 * @return True if [latest] is strictly newer than [current].
 */
internal fun isNewerVersion(latest: String, current: String): Boolean {
    val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
    val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(latestParts.size, currentParts.size)
    for (i in 0 until maxLen) {
        val l = latestParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
    }
    return false
}

private val PLAIN_VERSION = Regex("\\d{1,4}(\\.\\d{1,4}){0,3}")

/**
 * The release tag the update installs: the one `update.xml` names, or the build's test tag when it
 * has one (empty unless the build was made with `-PumaUpdateTestTag`). Null when the version is
 * anything but digits and dots, since it goes into a URL; the dialog then offers only the release page.
 */
internal fun updateTag(latestVersion: String, testTag: String): String? {
    if (testTag.isNotEmpty()) return testTag.takeIf { it.startsWith("v") && PLAIN_VERSION.matches(it.substring(1)) }
    return "v$latestVersion".takeIf { PLAIN_VERSION.matches(latestVersion) }
}

/** The device's preferred ABI that has a published APK, or null when none does. */
internal fun chooseAbi(supportedAbis: List<String>, published: List<String> = PUBLISHED_ABIS): String? = supportedAbis.firstOrNull { it in published }

internal fun apkAssetName(versionName: String, abi: String) = "UMA-Auto-Plus-v$versionName-$abi-release.apk"

internal enum class LookupOutcome { FOUND, NOT_PUBLISHED, RATE_LIMITED, FAILED }

/**
 * What a release API response means. GitHub answers an exhausted unauthenticated quota with 429, or
 * with 403 plus `X-RateLimit-Remaining: 0` or a `Retry-After`; any other 403 is a plain failure.
 */
internal fun lookupOutcome(responseCode: Int, rateLimitRemaining: String?, retryAfter: String?): LookupOutcome =
    when {
        responseCode == 200 -> LookupOutcome.FOUND
        responseCode == 404 -> LookupOutcome.NOT_PUBLISHED
        responseCode == 429 -> LookupOutcome.RATE_LIMITED
        responseCode == 403 && (rateLimitRemaining?.trim() == "0" || retryAfter != null) -> LookupOutcome.RATE_LIMITED
        else -> LookupOutcome.FAILED
    }

/** Copies [input] to [output] and returns the byte count and the sha256 (lowercase hex) of what was copied. */
internal fun copyWithSha256(input: InputStream, output: OutputStream): Pair<Long, String> {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        digest.update(buffer, 0, read)
        copied += read
    }
    return copied to digest.digest().joinToString("") { "%02x".format(it) }
}

/** A release APK as the GitHub API describes it. [sha256] is null when GitHub published no digest. */
internal data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String?)

/**
 * The published, uploaded APK named for [versionName] and [abi] in the release API response for
 * [tag], or null when the release is a draft, is another tag, or has no such asset yet.
 */
internal fun assetFor(release: JSONObject, tag: String, versionName: String, abi: String): ReleaseAsset? {
    if (release.optBoolean("draft") || release.optString("tag_name") != tag) return null
    val assets = release.optJSONArray("assets") ?: return null
    val name = apkAssetName(versionName, abi)
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        if (asset.optString("name") != name || asset.optString("state") != "uploaded") continue
        val url = asset.optString("browser_download_url")
        val size = asset.optLong("size")
        if (!url.startsWith(RELEASE_DOWNLOAD_PREFIX) || size <= 0) return null
        val sha256 = asset.optString("digest").removePrefix("sha256:").lowercase().takeIf { Regex("[0-9a-f]{64}").matches(it) }
        return ReleaseAsset(name, url, size, sha256)
    }
    return null
}

/** The installer session keeps its own copy of the APK, so the download needs room for about two. */
internal fun hasRoomFor(availableBytes: Long, size: Long): Boolean = availableBytes >= size * 22 / 10

/** Decimal megabytes, as the release page lists them. */
internal fun downloadProgressText(doneBytes: Long, totalBytes: Long): String = "Downloading: ${(doneBytes + 500_000) / 1_000_000} of ${(totalBytes + 500_000) / 1_000_000} MB"

/**
 * Why an update attempt ended without installing, in player words. Every one leaves the current
 * version in place; [offersReleasePage] is whether the browser download is still a way forward.
 */
internal enum class UpdateStop(val text: String, val offersReleasePage: Boolean, val offersRetry: Boolean = false) {
    BOT_RUNNING("Stop the bot before updating. Installing closes UMA Auto+ and would end the current run.", true),
    SESSION_ACTIVE("A bot session is still ending. Wait until it has stopped, then try again. Installing closes UMA Auto+.", true),
    ARMED("Press Stop in UMA Auto+ before updating. Installing closes UMA Auto+ and its overlay button.", true),
    SAVED_QUEUE("A paused or interrupted queue is saved. Resume or discard it on Home before updating.", true),
    NO_DOWNLOAD_FOR_DEVICE("There is no download for this device's processor type. The release page lists every file.", true),
    NOT_READY("The download isn't ready yet. Try again in a few minutes.", true),
    LOOKUP_RATE_LIMITED("GitHub is limiting update checks right now. Try again in an hour, or open the release page.", true),
    DOWNLOAD_FAILED("The download failed. Nothing was installed.", true, offersRetry = true),
    NOT_ENOUGH_STORAGE("There isn't enough free storage for the download. Nothing was installed.", true),
    VERIFY_FAILED("The download didn't match the release. Nothing was installed.", true),
    DOWNLOAD_CANCELLED("Download cancelled. Nothing was installed.", false),
    ANDROID_CANCELLED("Update cancelled. Your current version is unchanged.", false),
    ANDROID_NO_STORAGE("Android didn't install the update because there isn't enough free storage. Your current version is unchanged.", true),
    ANDROID_REFUSED("Android didn't install the update. Your current version is unchanged.", true),
}

/**
 * Why the bot is not idle enough to update, or null when it is. Installing force-stops UMA Auto+,
 * which ends a run, the armed overlay and a session still ending; and a saved queue is refused
 * because resuming it across versions is not a supported contract.
 */
internal fun updateBlockReason(botRunning: Boolean, projectionRunning: Boolean, sessionActive: Boolean, resumableQueue: Boolean): UpdateStop? =
    when {
        botRunning -> UpdateStop.BOT_RUNNING
        sessionActive -> UpdateStop.SESSION_ACTIVE
        projectionRunning -> UpdateStop.ARMED
        resumableQueue -> UpdateStop.SAVED_QUEUE
        else -> null
    }

internal enum class DownloadCheck { OK, SIZE_MISMATCH, DIGEST_MISMATCH, UNREADABLE_ARCHIVE, WRONG_PACKAGE, WRONG_VERSION, OLDER_THAN_INSTALLED }

/**
 * Whether the downloaded file is the release's APK for this app: the published size, the published
 * sha256 when GitHub has one, then the archive's own package, version name, and a version code not
 * older than the installed one (the same code is a reinstall). Android still enforces the signature.
 */
internal fun verifyDownload(
    asset: ReleaseAsset,
    actualSize: Long,
    actualSha256: String,
    archivePackage: String?,
    archiveVersionName: String?,
    archiveVersionCode: Long?,
    expectedPackage: String,
    expectedVersionName: String,
    installedVersionCode: Long,
): DownloadCheck =
    when {
        actualSize != asset.size -> DownloadCheck.SIZE_MISMATCH
        asset.sha256 != null && !asset.sha256.equals(actualSha256, ignoreCase = true) -> DownloadCheck.DIGEST_MISMATCH
        archivePackage == null || archiveVersionName == null || archiveVersionCode == null -> DownloadCheck.UNREADABLE_ARCHIVE
        archivePackage != expectedPackage -> DownloadCheck.WRONG_PACKAGE
        archiveVersionName != expectedVersionName -> DownloadCheck.WRONG_VERSION
        archiveVersionCode < installedVersionCode -> DownloadCheck.OLDER_THAN_INSTALLED
        else -> DownloadCheck.OK
    }

/**
 * What an installer session's final status means for the player, or null for the two that need no
 * text: success (Android closes UMA Auto+ to replace it) and pending user action (Android's own
 * dialog is showing). Android's status message is never shown; an unknown status reads as a refusal.
 */
internal fun installStatusStop(status: Int): UpdateStop? =
    when (status) {
        PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_PENDING_USER_ACTION -> null
        PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateStop.ANDROID_CANCELLED
        PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateStop.ANDROID_NO_STORAGE
        else -> UpdateStop.ANDROID_REFUSED
    }

internal const val CHECKING_TEXT = "Finding the download for your device..."
internal const val OPENING_INSTALLER_TEXT = "Opening Android's installer..."
internal const val NEEDS_PERMISSION_TEXT =
    "UMA Auto+ needs your permission to install updates. Tap Open settings, turn on \"Allow from this source\" on Android's Install unknown apps screen, " +
        "then come back and tap Continue. Nothing is downloaded until you do."
internal const val HANDED_TO_ANDROID_TEXT = "Confirm the update in Android's dialog. UMA Auto+ closes while it updates; open it again afterwards."
