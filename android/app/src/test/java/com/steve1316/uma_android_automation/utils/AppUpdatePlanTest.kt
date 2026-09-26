package com.steve1316.uma_android_automation.utils

import android.content.pm.PackageInstaller
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** The in-app updater's decisions: which file, whether the bot is idle, whether the download is the release, and what the player reads. */
@DisplayName("In-app update plan")
class AppUpdatePlanTest {
    @Nested
    @DisplayName("versions and tags")
    inner class Versions {
        @Test
        fun `a strictly newer version is newer, segment by segment`() {
            assertTrue(isNewerVersion("1.6.0", "1.5.0"))
            assertTrue(isNewerVersion("1.5.10", "1.5.9"))
            assertTrue(isNewerVersion("2.0", "1.9.9"))
            assertTrue(isNewerVersion("1.5.0.1", "1.5.0"))
            assertFalse(isNewerVersion("1.5.0", "1.5.0"))
            assertFalse(isNewerVersion("1.5", "1.5.0"))
            assertFalse(isNewerVersion("1.4.9", "1.5.0"))
        }

        @Test
        fun `a part that is not a number counts as zero`() {
            assertFalse(isNewerVersion("1.x", "1.0"))
            assertTrue(isNewerVersion("1.1", "1.x"))
        }

        @Test
        fun `the update installs the tag update xml names, unless the build carries a test tag`() {
            assertEquals("v1.6.0", updateTag("1.6.0", ""))
            assertEquals("v1.5.0", updateTag("1.6.0", "v1.5.0"))
            assertEquals("v2", updateTag("2", ""))
        }

        @Test
        fun `a version that is not digits and dots never reaches a URL`() {
            for (version in listOf("", "1.6.0-beta", "1.6.0/../x", "1..2", " 1.6", "1.6.", ".1", "v1.6.0", "1.6.0?x=1", "12345.0", "1.2.3.4.5", "1.6.0\n")) {
                assertEquals(null, updateTag(version, ""), "version '$version'")
            }
        }

        @Test
        fun `a test tag must be v and a plain version`() {
            for (tag in listOf("1.5.0", "v", "v1.5.0/../x", "latest", "vv1.5.0", "v1.5.0-rc")) assertEquals(null, updateTag("1.6.0", tag), "tag '$tag'")
        }
    }

    @Nested
    @DisplayName("the release lookup")
    inner class Lookup {
        @Test
        fun `a quota refusal reads as rate limited, not as a failed download`() {
            assertEquals(LookupOutcome.FOUND, lookupOutcome(200, null, null))
            assertEquals(LookupOutcome.NOT_PUBLISHED, lookupOutcome(404, null, null))
            assertEquals(LookupOutcome.RATE_LIMITED, lookupOutcome(429, null, null))
            assertEquals(LookupOutcome.RATE_LIMITED, lookupOutcome(403, "0", null))
            assertEquals(LookupOutcome.RATE_LIMITED, lookupOutcome(403, " 0 ", null))
            assertEquals(LookupOutcome.RATE_LIMITED, lookupOutcome(403, null, "3600"))
            assertEquals(LookupOutcome.FAILED, lookupOutcome(403, "12", null), "a 403 with quota left is not a rate limit")
            assertEquals(LookupOutcome.FAILED, lookupOutcome(403, null, null))
            for (code in listOf(301, 401, 500, 502, 503, -1)) assertEquals(LookupOutcome.FAILED, lookupOutcome(code, "0", null), "code $code")
        }

        @Test
        fun `the rate limit has its own text and still offers the release page`() {
            assertEquals("GitHub is limiting update checks right now. Try again in an hour, or open the release page.", UpdateStop.LOOKUP_RATE_LIMITED.text)
            assertTrue(UpdateStop.LOOKUP_RATE_LIMITED.offersReleasePage)
            assertFalse(UpdateStop.LOOKUP_RATE_LIMITED.text.contains("download failed"))
        }
    }

    @Nested
    @DisplayName("hashing the copy")
    inner class Copy {
        private fun copy(bytes: ByteArray): Pair<Pair<Long, String>, ByteArray> {
            val out = java.io.ByteArrayOutputStream()
            return copyWithSha256(bytes.inputStream(), out) to out.toByteArray()
        }

        @Test
        fun `the copy is byte for byte and hashed as it goes`() {
            assertEquals(3L to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", copy("abc".toByteArray()).first)
            assertEquals(0L to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", copy(ByteArray(0)).first)
            val big = ByteArray(200_003) { (it * 31 % 251).toByte() }
            val (result, written) = copy(big)
            val expected = java.security.MessageDigest.getInstance("SHA-256").digest(big).joinToString("") { "%02x".format(it) }
            assertEquals(big.size.toLong() to expected, result)
            assertTrue(big.contentEquals(written))
        }
    }

    @Nested
    @DisplayName("the file for this device")
    inner class Abi {
        @Test
        fun `the first supported ABI with a published APK wins`() {
            assertEquals("x86_64", chooseAbi(listOf("x86_64", "arm64-v8a", "x86", "armeabi-v7a", "armeabi")), "MuMu")
            assertEquals("arm64-v8a", chooseAbi(listOf("arm64-v8a", "armeabi-v7a", "armeabi")))
            assertEquals("armeabi-v7a", chooseAbi(listOf("armeabi-v7a", "armeabi")))
            assertEquals("armeabi-v7a", chooseAbi(listOf("armeabi-v7a", "arm64-v8a")), "device order, not list order")
        }

        @Test
        fun `a device with no published ABI has no file`() {
            assertNull(chooseAbi(listOf("x86", "armeabi")))
            assertNull(chooseAbi(emptyList()))
        }

        @Test
        fun `the asset name follows the release build's file name`() {
            assertEquals("UMA-Auto-Plus-v1.5.0-x86_64-release.apk", apkAssetName("1.5.0", "x86_64"))
        }
    }

    @Nested
    @DisplayName("finding the asset")
    inner class Asset {
        private val digest = "BEB83DAA" + "0".repeat(56)

        private fun asset(abi: String, overrides: Map<String, Any?> = emptyMap()): JSONObject {
            val name = apkAssetName("1.5.0", abi)
            val base =
                mapOf<String, Any?>(
                    "name" to name,
                    "state" to "uploaded",
                    "size" to 94_296_284L,
                    "digest" to "sha256:$digest",
                    "browser_download_url" to "${RELEASE_DOWNLOAD_PREFIX}v1.5.0/$name",
                ) + overrides
            return JSONObject().also { o -> base.forEach { (k, v) -> if (v != null) o.put(k, v) } }
        }

        private fun release(vararg assets: JSONObject, tag: String = "v1.5.0", draft: Boolean = false) =
            JSONObject().put("tag_name", tag).put("draft", draft).put("assets", JSONArray().also { a -> assets.forEach { a.put(it) } })

        @Test
        fun `the exact asset comes back with its size, url and lowercase digest`() {
            val found = assetFor(release(asset("arm64-v8a"), asset("x86_64")), "v1.5.0", "1.5.0", "x86_64")
            val name = apkAssetName("1.5.0", "x86_64")
            assertEquals(ReleaseAsset(name, "${RELEASE_DOWNLOAD_PREFIX}v1.5.0/$name", 94_296_284L, digest.lowercase()), found)
        }

        @Test
        fun `a missing or unusable digest leaves only the size and archive checks`() {
            for (d in listOf(null, "", "sha512:$digest", "sha256:xyz", "sha256:${digest.dropLast(1)}")) {
                assertNull(assetFor(release(asset("x86_64", mapOf("digest" to d))), "v1.5.0", "1.5.0", "x86_64")!!.sha256, "digest $d")
            }
        }

        @Test
        fun `a draft, another tag, or a release without this asset has none`() {
            assertNull(assetFor(release(asset("x86_64"), draft = true), "v1.5.0", "1.5.0", "x86_64"))
            assertNull(assetFor(release(asset("x86_64"), tag = "v1.4.0"), "v1.5.0", "1.5.0", "x86_64"))
            assertNull(assetFor(release(asset("arm64-v8a")), "v1.5.0", "1.5.0", "x86_64"))
            assertNull(assetFor(release(asset("x86_64")), "v1.5.0", "1.6.0", "x86_64"), "a file named for another version")
            assertNull(assetFor(JSONObject().put("tag_name", "v1.5.0"), "v1.5.0", "1.5.0", "x86_64"))
        }

        @Test
        fun `an asset still uploading, empty, or served from anywhere else is not used`() {
            assertNull(assetFor(release(asset("x86_64", mapOf("state" to "open"))), "v1.5.0", "1.5.0", "x86_64"))
            assertNull(assetFor(release(asset("x86_64", mapOf("size" to 0L))), "v1.5.0", "1.5.0", "x86_64"))
            assertNull(assetFor(release(asset("x86_64", mapOf("browser_download_url" to "https://example.com/UMA.apk"))), "v1.5.0", "1.5.0", "x86_64"))
            val cleartext = "http://github.com/lhceist41/uma-auto-plus/releases/download/v1.5.0/x.apk"
            assertNull(assetFor(release(asset("x86_64", mapOf("browser_download_url" to cleartext))), "v1.5.0", "1.5.0", "x86_64"))
        }
    }

    @Nested
    @DisplayName("space and progress")
    inner class Space {
        @Test
        fun `the download needs room for about two copies`() {
            assertTrue(hasRoomFor(220, 100))
            assertFalse(hasRoomFor(219, 100))
        }

        @Test
        fun `progress is real bytes in decimal megabytes`() {
            assertEquals("Downloading: 34 of 94 MB", downloadProgressText(34_000_000, 94_296_284))
            assertEquals("Downloading: 0 of 94 MB", downloadProgressText(0, 94_296_284))
            assertEquals("Downloading: 94 of 94 MB", downloadProgressText(94_296_284, 94_296_284))
        }
    }

    @Nested
    @DisplayName("the idle guard")
    inner class Idle {
        @Test
        fun `each signal alone blocks with its own reason`() {
            assertEquals(UpdateStop.BOT_RUNNING, updateBlockReason(botRunning = true, projectionRunning = false, sessionActive = false, resumableQueue = false))
            assertEquals(UpdateStop.ARMED, updateBlockReason(botRunning = false, projectionRunning = true, sessionActive = false, resumableQueue = false))
            assertEquals(UpdateStop.SESSION_ACTIVE, updateBlockReason(botRunning = false, projectionRunning = false, sessionActive = true, resumableQueue = false))
            assertEquals(UpdateStop.SAVED_QUEUE, updateBlockReason(botRunning = false, projectionRunning = false, sessionActive = false, resumableQueue = true))
        }

        @Test
        fun `an idle bot is not blocked, and any combination is`() {
            assertNull(updateBlockReason(botRunning = false, projectionRunning = false, sessionActive = false, resumableQueue = false))
            for (mask in 1 until 16) {
                assertTrue(updateBlockReason(mask and 1 != 0, mask and 2 != 0, mask and 4 != 0, mask and 8 != 0) != null, "mask $mask")
            }
        }

        @Test
        fun `a saved queue points to Home, and the others say installing closes the app`() {
            assertTrue(UpdateStop.SAVED_QUEUE.text.contains("Resume or discard it on Home"))
            for (stop in listOf(UpdateStop.BOT_RUNNING, UpdateStop.SESSION_ACTIVE, UpdateStop.ARMED)) assertTrue(stop.text.contains("closes UMA Auto+"), stop.name)
        }
    }

    @Nested
    @DisplayName("checking the download")
    inner class Verify {
        private val asset = ReleaseAsset("a.apk", "${RELEASE_DOWNLOAD_PREFIX}v1.5.0/a.apk", 100, "ab".repeat(32))

        private fun check(
            size: Long = 100,
            sha: String = "ab".repeat(32),
            pkg: String? = "com.lhceist41.uma_auto_plus",
            name: String? = "1.5.0",
            code: Long? = 150,
            installed: Long = 150,
            release: ReleaseAsset = asset,
        ) = verifyDownload(release, size, sha, pkg, name, code, "com.lhceist41.uma_auto_plus", "1.5.0", installed)

        @Test
        fun `the release's file passes, including a reinstall of the same version code`() {
            assertEquals(DownloadCheck.OK, check())
            assertEquals(DownloadCheck.OK, check(code = 160, installed = 150))
            assertEquals(DownloadCheck.OK, check(sha = "AB".repeat(32)), "digest case")
        }

        @Test
        fun `each mismatch is its own result`() {
            assertEquals(DownloadCheck.SIZE_MISMATCH, check(size = 99))
            assertEquals(DownloadCheck.SIZE_MISMATCH, check(size = 101))
            assertEquals(DownloadCheck.DIGEST_MISMATCH, check(sha = "cd".repeat(32)))
            assertEquals(DownloadCheck.UNREADABLE_ARCHIVE, check(pkg = null))
            assertEquals(DownloadCheck.UNREADABLE_ARCHIVE, check(name = null))
            assertEquals(DownloadCheck.UNREADABLE_ARCHIVE, check(code = null))
            assertEquals(DownloadCheck.WRONG_PACKAGE, check(pkg = "com.example.other"))
            assertEquals(DownloadCheck.WRONG_VERSION, check(name = "1.4.0"))
            assertEquals(DownloadCheck.OLDER_THAN_INSTALLED, check(code = 149))
        }

        @Test
        fun `without a published digest the other checks still decide`() {
            val noDigest = asset.copy(sha256 = null)
            assertEquals(DownloadCheck.OK, check(sha = "cd".repeat(32), release = noDigest))
            assertEquals(DownloadCheck.SIZE_MISMATCH, check(size = 1, release = noDigest))
            assertEquals(DownloadCheck.WRONG_PACKAGE, check(pkg = "x", release = noDigest))
        }
    }

    @Nested
    @DisplayName("what the player reads")
    inner class Texts {
        @Test
        fun `every installer status has fixed words, and an unknown one reads as a refusal`() {
            assertNull(installStatusStop(PackageInstaller.STATUS_SUCCESS))
            assertNull(installStatusStop(PackageInstaller.STATUS_PENDING_USER_ACTION))
            assertEquals(UpdateStop.ANDROID_CANCELLED, installStatusStop(PackageInstaller.STATUS_FAILURE_ABORTED))
            assertEquals(UpdateStop.ANDROID_NO_STORAGE, installStatusStop(PackageInstaller.STATUS_FAILURE_STORAGE))
            val refusals =
                listOf(
                    PackageInstaller.STATUS_FAILURE,
                    PackageInstaller.STATUS_FAILURE_BLOCKED,
                    PackageInstaller.STATUS_FAILURE_INVALID,
                    PackageInstaller.STATUS_FAILURE_CONFLICT,
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                    PackageInstaller.STATUS_FAILURE_TIMEOUT,
                    99,
                    -7,
                )
            for (status in refusals) assertEquals(UpdateStop.ANDROID_REFUSED, installStatusStop(status), "status $status")
        }

        @Test
        fun `every failure offers the release page, and only the player's own cancels do not`() {
            for (stop in UpdateStop.entries) {
                val cancelled = stop == UpdateStop.DOWNLOAD_CANCELLED || stop == UpdateStop.ANDROID_CANCELLED
                assertEquals(!cancelled, stop.offersReleasePage, stop.name)
            }
            assertEquals(setOf(UpdateStop.DOWNLOAD_FAILED), UpdateStop.entries.filter { it.offersRetry }.toSet())
        }

        @Test
        fun `the words are distinct, plain, and never claim an update happened`() {
            val texts = UpdateStop.entries.map { it.text } + listOf(CHECKING_TEXT, OPENING_INSTALLER_TEXT, NEEDS_PERMISSION_TEXT, HANDED_TO_ANDROID_TEXT)
            assertEquals(texts.size, texts.toSet().size)
            val claim = Regex("\\b(updated|was installed|installed the update)\\b", RegexOption.IGNORE_CASE)
            for (text in texts) {
                assertFalse(Regex("[A-Z]{2,}_[A-Z]|Exception|\\bnull\\b|\\u2014").containsMatchIn(text), text)
                val affirmative = text.replace("didn't install the update", "").replace("Nothing was installed", "")
                assertFalse(claim.containsMatchIn(affirmative), text)
            }
        }

        @Test
        fun `every stop after the download began says nothing changed`() {
            val beforeDownload =
                setOf(
                    UpdateStop.BOT_RUNNING,
                    UpdateStop.SESSION_ACTIVE,
                    UpdateStop.ARMED,
                    UpdateStop.SAVED_QUEUE,
                    UpdateStop.NO_DOWNLOAD_FOR_DEVICE,
                    UpdateStop.NOT_READY,
                    UpdateStop.LOOKUP_RATE_LIMITED,
                )
            for (stop in UpdateStop.entries - beforeDownload) assertTrue(stop.text.contains("Nothing was installed") || stop.text.contains("unchanged"), stop.name)
        }
    }
}
