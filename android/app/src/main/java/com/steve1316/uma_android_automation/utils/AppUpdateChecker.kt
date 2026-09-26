package com.steve1316.uma_android_automation.utils

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.util.DisplayMetrics
import android.util.Log
import android.util.Xml
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.steve1316.uma_android_automation.BuildConfig
import com.steve1316.uma_android_automation.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.net.URL

/**
 * Checks for app updates by fetching and parsing the remote update.xml hosted on GitHub. If a newer version is detected, a custom dialog is
 * shown with release notes; its Update button downloads the release for this device and hands it to Android's installer, with the
 * release page as the fallback.
 *
 * @property activity The [Activity] context used to display the update dialog.
 */
class AppUpdateChecker(private val activity: Activity) {
    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val UPDATE_XML_URL =
            "https://raw.githubusercontent.com/lhceist41/uma-auto-plus/refs/heads/main/android/app/update.xml"
        private const val MAX_SCROLL_HEIGHT_RATIO = 0.5
    }

    data class UpdateInfo(
        val latestVersion: String,
        val url: String,
        val releaseNotes: String,
    )

    /**
     * Fetches the remote update XML and shows the update dialog if a newer version is available.
     *
     * @param forceShow If true, always shows the dialog regardless of version comparison. Useful for testing the dialog UI.
     */
    fun checkForUpdate(forceShow: Boolean = false) {
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                val updateInfo =
                    withContext(Dispatchers.IO) {
                        clearUpdateFiles(activity)
                        URL(UPDATE_XML_URL).openStream().use { parseUpdateXml(it) }
                    } ?: return@launch

                if (forceShow || BuildConfig.UPDATE_TEST_TAG.isNotEmpty() || isNewerVersion(updateInfo.latestVersion, BuildConfig.VERSION_NAME)) {
                    showUpdateDialog(updateInfo)
                }
            } catch (_: Exception) {
                // Silently ignore network or parsing failures.
            }
        }
    }

    /**
     * Parses the update XML stream and extracts version, URL, and release notes.
     *
     * @param inputStream The raw XML input stream from the remote update file.
     * @return The parsed [UpdateInfo], or null if any required fields are missing.
     */
    private fun parseUpdateXml(inputStream: InputStream): UpdateInfo? {
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, null)

        var latestVersion: String? = null
        var url: String? = null
        var releaseNotes: String? = null
        var currentTag: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> currentTag = parser.name
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    when (currentTag) {
                        "latestVersion" -> latestVersion = text.trim()
                        "url" -> url = text.trim()
                        "releaseNotes" -> releaseNotes = text.trim()
                    }
                }
                XmlPullParser.END_TAG -> currentTag = null
            }
            eventType = parser.next()
        }

        return if (latestVersion != null && url != null && releaseNotes != null) {
            UpdateInfo(latestVersion, url, releaseNotes)
        } else {
            null
        }
    }

    /**
     * A [ReplacementSpan] that draws a rounded rectangle behind the text, similar to GitHub's inline code pill.
     *
     * @property backgroundColor The fill color for the rounded background.
     * @property textColor The color used to draw the text on top of the background.
     * @property cornerRadius The corner radius in pixels for the rounded rectangle.
     * @property horizontalPadding The horizontal padding in pixels inside the pill.
     */
    private class RoundedBackgroundSpan(
        private val backgroundColor: Int,
        private val textColor: Int,
        private val cornerRadius: Float = 8f,
        private val horizontalPadding: Float = 6f,
    ) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            val originalTypeface = paint.typeface
            paint.typeface = Typeface.MONOSPACE
            val width = (paint.measureText(text, start, end) + horizontalPadding * 2).toInt()
            paint.typeface = originalTypeface
            return width
        }

        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val originalTypeface = paint.typeface
            paint.typeface = Typeface.MONOSPACE
            val textWidth = paint.measureText(text, start, end)
            // Use font metrics for pill height so line spacing doesn't inflate it.
            val fm = paint.fontMetrics
            val pillTop = y + fm.ascent - 2f
            val pillBottom = y + fm.descent + 2f
            val rect = RectF(x, pillTop, x + textWidth + horizontalPadding * 2, pillBottom)
            val bgPaint = Paint(paint)
            bgPaint.color = backgroundColor
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
            paint.color = textColor
            canvas.drawText(text, start, end, x + horizontalPadding, y.toFloat(), paint)
            paint.typeface = originalTypeface
        }
    }

    /**
     * Formats release notes text by styling backtick-wrapped segments with a code-like appearance (monospace font, rounded tinted background
     * and text color), similar to GitHub's inline code rendering. Also replaces leading dashes with bullet points.
     *
     * @param text The raw release notes string potentially containing backtick-wrapped text.
     * @return A [SpannableStringBuilder] with styled inline code spans.
     */
    private fun formatReleaseNotes(text: String): SpannableStringBuilder {
        // Replace leading dashes with bullet points.
        val bulletText = text.replace(Regex("(?m)^- "), "\u2022 ")

        val builder = SpannableStringBuilder()
        val codeBgColor = ContextCompat.getColor(activity, R.color.dialog_code_background)
        val codeTextColor = ContextCompat.getColor(activity, R.color.dialog_code_text)
        val density = activity.resources.displayMetrics.density
        val cornerRadius = 6f * density
        val horizontalPadding = 4f * density

        var i = 0
        while (i < bulletText.length) {
            val backtickStart = bulletText.indexOf('`', i)
            if (backtickStart == -1) {
                builder.append(bulletText, i, bulletText.length)
                break
            }
            val backtickEnd = bulletText.indexOf('`', backtickStart + 1)
            if (backtickEnd == -1) {
                builder.append(bulletText, i, bulletText.length)
                break
            }

            // Append text before the backtick.
            builder.append(bulletText, i, backtickStart)

            // Append the code content with a rounded background span.
            val codeContent = bulletText.substring(backtickStart + 1, backtickEnd)
            val spanStart = builder.length
            builder.append(codeContent)
            val spanEnd = builder.length
            builder.setSpan(RoundedBackgroundSpan(codeBgColor, codeTextColor, cornerRadius, horizontalPadding), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            i = backtickEnd + 1
        }
        return builder
    }

    /**
     * Displays the custom update dialog with release notes and Update/Dismiss buttons.
     *
     * @param updateInfo The parsed update metadata to display in the dialog.
     */
    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_app_update)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tag = updateTag(updateInfo.latestVersion, BuildConfig.UPDATE_TEST_TAG)
        dialog.findViewById<TextView>(R.id.dialog_subtitle).text =
            "Version ${tag?.removePrefix("v") ?: updateInfo.latestVersion} is available"
        dialog.findViewById<TextView>(R.id.dialog_release_notes).text =
            formatReleaseNotes(updateInfo.releaseNotes)

        // Cap the ScrollView height to avoid the dialog filling the entire screen.
        val scrollView = dialog.findViewById<ScrollView>(R.id.dialog_scroll)
        scrollView.post {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(metrics)
            val maxHeight = (metrics.heightPixels * MAX_SCROLL_HEIGHT_RATIO).toInt()
            if (scrollView.height > maxHeight) {
                scrollView.layoutParams = scrollView.layoutParams.apply { height = maxHeight }
            }
        }

        UpdateFlow(dialog, updateInfo.url, tag).showAvailable()

        dialog.show()

        // Set the dialog width to 85% of the screen so the content isn't squished.
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getMetrics(metrics)
        dialog.window?.setLayout((metrics.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /**
     * The Update button's download and install, shown in the update dialog. Each state shows only the buttons that work in it, and
     * every text is a fixed one from [UpdateStop] or the plan file. Nothing says "updated": when Android installs, it closes the app.
     * A null [tag] (a version that is not digits and dots) leaves only the release page. The flow ends with the dialog or the
     * activity, whichever goes first, so nothing is handed to Android from a closed screen.
     */
    private inner class UpdateFlow(private val dialog: Dialog, private val releasePageUrl: String, private val tag: String?) {
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private val status = dialog.findViewById<TextView>(R.id.dialog_status)
        private val progress = dialog.findViewById<ProgressBar>(R.id.dialog_progress)
        private val left = dialog.findViewById<Button>(R.id.btn_dismiss)
        private val right = dialog.findViewById<Button>(R.id.btn_update)
        private var job: Job? = null
        private var receiverRegistered = false

        /** The installer session made but not yet showing Android's confirmation; abandoned if the flow ends first. */
        @Volatile private var openingSessionId: Int? = null

        private val activityEnd = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) end() }

        private val statusReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val installStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    if (installStatus == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        val confirm = confirmIntentOf(intent)
                        if (activity.isFinishing || activity.isDestroyed) return
                        openingSessionId = null
                        if (confirm == null) {
                            unregisterStatusReceiver()
                            showStop(UpdateStop.ANDROID_REFUSED)
                            return
                        }
                        activity.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        showStatus(HANDED_TO_ANDROID_TEXT)
                        buttons("Close" to { dialog.dismiss() }, null)
                        return
                    }
                    openingSessionId = null
                    unregisterStatusReceiver()
                    Log.i(TAG, "Installer finished with status $installStatus.")
                    installStatusStop(installStatus)?.let { showStop(it) }
                }
            }

        init {
            dialog.setOnDismissListener { end() }
            (activity as? LifecycleOwner)?.lifecycle?.addObserver(activityEnd)
        }

        /**
         * Stops the attempt when the dialog closes or the activity is destroyed: the download is cancelled, the status receiver is
         * dropped, and a session that has not reached Android's confirmation is abandoned rather than left for the system to expire.
         */
        private fun end() {
            job?.cancel()
            unregisterStatusReceiver()
            (activity as? LifecycleOwner)?.lifecycle?.removeObserver(activityEnd)
            openingSessionId?.let { id ->
                openingSessionId = null
                try {
                    activity.packageManager.packageInstaller.abandonSession(id)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not abandon the unfinished installer session: ${e.javaClass.simpleName}.")
                }
            }
        }

        fun showAvailable() {
            showStatus(null)
            buttons("Dismiss" to { dialog.dismiss() }, "Update" to { if (tag == null) openReleasePage() else start() })
        }

        private fun start() {
            val target = tag ?: return openReleasePage()
            val versionName = target.removePrefix("v")
            job?.cancel()
            job =
                scope.launch {
                    try {
                        showStatus(CHECKING_TEXT)
                        buttons("Cancel" to { cancel() }, null)
                        val block = withContext(Dispatchers.IO) { currentUpdateBlock(activity) }
                        if (block != null) return@launch showStop(block)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) return@launch showNeedsPermission()
                        val abi = chooseAbi(Build.SUPPORTED_ABIS.toList()) ?: return@launch showStop(UpdateStop.NO_DOWNLOAD_FOR_DEVICE)
                        val release =
                            when (val lookup = withContext(Dispatchers.IO) { fetchRelease(target) }) {
                                is ReleaseLookup.Found -> lookup.release
                                ReleaseLookup.NotPublished -> return@launch showStop(UpdateStop.NOT_READY)
                                ReleaseLookup.RateLimited -> return@launch showStop(UpdateStop.LOOKUP_RATE_LIMITED)
                                ReleaseLookup.Failed -> return@launch showStop(UpdateStop.DOWNLOAD_FAILED)
                            }
                        val asset = assetFor(release, target, versionName, abi) ?: return@launch showStop(UpdateStop.NOT_READY)
                        if (!withContext(Dispatchers.IO) { hasRoomForDownload(activity, asset.size) }) return@launch showStop(UpdateStop.NOT_ENOUGH_STORAGE)
                        showDownloading(0, asset.size)
                        val download =
                            withContext(Dispatchers.IO) {
                                var shown = -1L
                                downloadAsset(activity, asset, isCancelled = { !isActive }) { done ->
                                    val permille = done * 1000 / asset.size
                                    if (permille != shown) {
                                        shown = permille
                                        activity.runOnUiThread { if (isActive) showDownloading(done, asset.size) }
                                    }
                                }
                            } ?: return@launch showStop(UpdateStop.DOWNLOAD_FAILED)
                        val (check, apk) = withContext(Dispatchers.IO) { checkDownload(activity, asset, download, versionName) }
                        if (apk == null) {
                            Log.w(TAG, "The update download was rejected: ${check.name}.")
                            return@launch showStop(UpdateStop.VERIFY_FAILED)
                        }
                        if (activity.isFinishing || activity.isDestroyed) return@launch withContext(Dispatchers.IO) { clearUpdateFiles(activity) }
                        install(apk, download.sha256)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "The update attempt failed: ${e.javaClass.simpleName}.")
                        withContext(Dispatchers.IO) { clearUpdateFiles(activity) }
                        showStop(UpdateStop.DOWNLOAD_FAILED)
                    }
                }
        }

        /** Commits the verified file; the installer's own dialog follows through [statusReceiver]. */
        private suspend fun install(apk: File, verifiedSha256: String) {
            showStatus(OPENING_INSTALLER_TEXT)
            buttons(null, null)
            registerStatusReceiver()
            val blocked =
                try {
                    withContext(Dispatchers.IO) {
                        try {
                            commitInstall(activity, apk, verifiedSha256, onSessionCreated = { openingSessionId = it }) {
                                if (!isActive) UpdateStop.DOWNLOAD_CANCELLED else currentUpdateBlock(activity)
                            }
                        } finally {
                            clearUpdateFiles(activity)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "The installer session failed: ${e.javaClass.simpleName}.")
                    UpdateStop.ANDROID_REFUSED
                }
            if (blocked != null) {
                openingSessionId = null
                unregisterStatusReceiver()
                showStop(blocked)
            }
        }

        private fun cancel() {
            job?.cancel()
            showStop(UpdateStop.DOWNLOAD_CANCELLED)
        }

        @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
        private fun showNeedsPermission() {
            showStatus(NEEDS_PERMISSION_TEXT)
            buttons(
                "Cancel" to { dialog.dismiss() },
                "Open settings" to {
                    try {
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                        buttons("Cancel" to { dialog.dismiss() }, "Continue" to { start() })
                    } catch (e: ActivityNotFoundException) {
                        showStop(UpdateStop.ANDROID_REFUSED)
                    }
                },
            )
        }

        private fun showStop(stop: UpdateStop) {
            showStatus(stop.text)
            buttons(
                if (stop.offersRetry) "Retry" to { start() } else "Close" to { dialog.dismiss() },
                if (stop.offersReleasePage) "Open release page" to { openReleasePage() } else null,
            )
        }

        private fun showDownloading(done: Long, total: Long) {
            showStatus(downloadProgressText(done, total))
            progress.visibility = View.VISIBLE
            progress.progress = (done * 1000 / total).toInt().coerceIn(0, 1000)
        }

        private fun showStatus(text: String?) {
            status.text = text.orEmpty()
            status.visibility = if (text == null) View.GONE else View.VISIBLE
            progress.visibility = View.GONE
        }

        private fun buttons(leftAction: Pair<String, () -> Unit>?, rightAction: Pair<String, () -> Unit>?) {
            for ((button, action) in listOf(left to leftAction, right to rightAction)) {
                button.visibility = if (action == null) View.GONE else View.VISIBLE
                button.text = action?.first.orEmpty()
                button.setOnClickListener { action?.second?.invoke() }
            }
        }

        private fun openReleasePage() {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releasePageUrl)))
            dialog.dismiss()
        }

        private fun registerStatusReceiver() {
            if (receiverRegistered) return
            ContextCompat.registerReceiver(activity, statusReceiver, IntentFilter(INSTALL_STATUS_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }

        private fun unregisterStatusReceiver() {
            if (!receiverRegistered) return
            receiverRegistered = false
            activity.unregisterReceiver(statusReceiver)
        }

        private fun confirmIntentOf(intent: Intent): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
    }
}
