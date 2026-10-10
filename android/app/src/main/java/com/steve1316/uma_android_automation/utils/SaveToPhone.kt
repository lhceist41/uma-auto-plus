package com.steve1316.uma_android_automation.utils

import java.io.File
import java.io.OutputStream

/** The file side of "Save to phone": which files may leave the app folder, and the byte-for-byte copy. */
object SaveToPhone {
    /** Only a regular file inside [appDir] may be copied out; "..", links and other folders are refused. Plain path strings on purpose: the NIO path classes need API 26 and minSdk is 24. */
    fun isAppFile(file: File, appDir: File): Boolean = file.isFile && file.canonicalPath.startsWith(appDir.canonicalPath + File.separator)

    /** Copies [source] unchanged, so the saved file equals the one the share sheet gets. */
    fun copy(source: File, output: OutputStream): Long = source.inputStream().use { it.copyTo(output) }
}
