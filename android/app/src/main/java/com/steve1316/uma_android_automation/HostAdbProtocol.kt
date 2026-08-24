package com.steve1316.uma_android_automation

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal const val HOST_PROTOCOL_VERSION = 1
internal const val HOST_COMPANION_VERSION = "1.0.0"
internal const val HOST_APP_PACKAGE = "com.lhceist41.uma_auto_plus"
internal const val HOST_DEVICE_PORT = 37183
internal const val HOST_MAX_FRAME_BYTES = 16 * 1024

internal data class HostPairing(
    val targetId: String,
    val secret: ByteArray,
)

internal data class HostInputConfiguration(
    val pairing: HostPairing,
) {
    companion object {
        fun fromSettings(modeRaw: String?, pairingCodeRaw: String?): HostInputConfiguration? {
            if (HostInputMode.parse(modeRaw) != HostInputMode.ACCESSIBILITY_WITH_HOST) return null
            return HostAdbProtocol.parsePairingCode(pairingCodeRaw)?.let(::HostInputConfiguration)
        }
    }
}

internal data class HostProtocolSession(
    val id: String,
    val key: ByteArray,
    val targetId: String,
    val capabilities: Set<InputCapability>,
    var nextSequence: Long = 1,
)

internal object HostAdbProtocol {
    private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val targetIdPattern = Regex("^[a-f0-9]{32}$")
    private val tokenPattern = Regex("^[A-Za-z0-9_-]{16,64}$")
    private val noncePattern = Regex("^[A-Za-z0-9_-]{24,64}$")
    private val secureRandom = SecureRandom()

    fun parsePairingCode(raw: String?): HostPairing? {
        val parts = raw?.trim()?.split('.') ?: return null
        if (parts.size != 3 || parts[0] != "v1" || !targetIdPattern.matches(parts[1])) return null
        val secret = decodeBase64Url(parts[2]) ?: return null
        if (secret.size != 32) return null
        return HostPairing(parts[1], secret)
    }

    fun newToken(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return encodeBase64Url(bytes)
    }

    fun sign(secret: ByteArray, canonical: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return encodeBase64Url(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
    }

    fun signatureMatches(secret: ByteArray, canonical: String, supplied: String): Boolean {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val expected = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
        val actual = decodeBase64Url(supplied) ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    internal fun encodeBase64Url(bytes: ByteArray): String {
        val encoded = StringBuilder((bytes.size * 4 + 2) / 3)
        var index = 0
        while (index + 2 < bytes.size) {
            val value =
                ((bytes[index].toInt() and 0xff) shl 16) or
                    ((bytes[index + 1].toInt() and 0xff) shl 8) or
                    (bytes[index + 2].toInt() and 0xff)
            encoded.append(BASE64_URL_ALPHABET[value ushr 18])
            encoded.append(BASE64_URL_ALPHABET[(value ushr 12) and 0x3f])
            encoded.append(BASE64_URL_ALPHABET[(value ushr 6) and 0x3f])
            encoded.append(BASE64_URL_ALPHABET[value and 0x3f])
            index += 3
        }
        when (bytes.size - index) {
            1 -> {
                val value = (bytes[index].toInt() and 0xff) shl 4
                encoded.append(BASE64_URL_ALPHABET[value ushr 6])
                encoded.append(BASE64_URL_ALPHABET[value and 0x3f])
            }

            2 -> {
                val value = ((bytes[index].toInt() and 0xff) shl 10) or ((bytes[index + 1].toInt() and 0xff) shl 2)
                encoded.append(BASE64_URL_ALPHABET[value ushr 12])
                encoded.append(BASE64_URL_ALPHABET[(value ushr 6) and 0x3f])
                encoded.append(BASE64_URL_ALPHABET[value and 0x3f])
            }
        }
        return encoded.toString()
    }

    private fun decodeBase64Url(raw: String): ByteArray? {
        if (raw.length % 4 == 1) return null
        val decoded = ByteArray((raw.length * 3) / 4)
        var outputIndex = 0
        var buffer = 0
        var bits = 0
        for (character in raw) {
            val value = BASE64_URL_ALPHABET.indexOf(character)
            if (value < 0) return null
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                decoded[outputIndex++] = ((buffer ushr bits) and 0xff).toByte()
                buffer = if (bits == 0) 0 else buffer and ((1 shl bits) - 1)
            }
        }
        if (buffer != 0) return null
        return decoded.copyOf(outputIndex)
    }

    fun buildHello(
        pairing: HostPairing,
        appVersion: String,
        requestId: String,
        clientNonce: String,
    ): JSONObject {
        val message =
            JSONObject()
                .put("type", "HELLO")
                .put("protocolVersion", HOST_PROTOCOL_VERSION)
                .put("appVersion", appVersion)
                .put("requestId", requestId)
                .put("clientNonce", clientNonce)
                .put("targetId", pairing.targetId)
        message.put("signature", sign(pairing.secret, canonicalHello(message)))
        return message
    }

    fun acceptHelloResult(
        hello: JSONObject,
        result: JSONObject,
        pairing: HostPairing,
        appVersion: String,
    ): HostProtocolSession {
        require(result.getString("type") == "HELLO_RESULT")
        require(result.getInt("protocolVersion") == HOST_PROTOCOL_VERSION)
        require(result.getString("appVersion") == appVersion)
        require(result.getString("companionVersion") == HOST_COMPANION_VERSION)
        require(result.getString("requestId") == hello.getString("requestId"))
        require(result.getString("clientNonce") == hello.getString("clientNonce"))
        require(result.getString("targetId") == pairing.targetId)
        require(tokenPattern.matches(result.getString("sessionId")))
        require(noncePattern.matches(result.getString("serverNonce")))
        require(signatureMatches(pairing.secret, canonicalHelloResult(result), result.getString("signature")))
        val capabilities = parseCapabilities(result.getJSONArray("capabilities"))
        require(capabilities.containsAll(setOf(InputCapability.HEALTH, InputCapability.SWIPE)))
        return HostProtocolSession(
            id = result.getString("sessionId"),
            key = deriveSessionKey(pairing.secret, hello, result),
            targetId = pairing.targetId,
            capabilities = capabilities,
        )
    }

    fun buildHealthRequest(session: HostProtocolSession, requestId: String): JSONObject {
        val message = baseRequest("HEALTH", session, requestId)
        message.put("signature", sign(session.key, canonicalRequest(message)))
        return message
    }

    fun buildSwipeRequest(
        session: HostProtocolSession,
        requestId: String,
        request: BoundedSwipe,
    ): JSONObject {
        require(request.validationError() == null)
        val message =
            baseRequest("SWIPE_REQUEST", session, requestId)
                .put("targetPackage", HOST_APP_PACKAGE)
                .put("scope", request.scope.wire)
                .put("startX", request.startX)
                .put("startY", request.startY)
                .put("endX", request.endX)
                .put("endY", request.endY)
                .put("durationMs", request.durationMs)
        message.put("signature", sign(session.key, canonicalRequest(message)))
        return message
    }

    fun acceptResult(
        session: HostProtocolSession,
        request: JSONObject,
        result: JSONObject,
        expectedType: String,
    ): Pair<InputHealth, InputExecutionResult> {
        require(result.getString("type") == expectedType)
        require(result.getInt("protocolVersion") == HOST_PROTOCOL_VERSION)
        require(result.getString("sessionId") == session.id)
        require(result.getString("requestId") == request.getString("requestId"))
        require(result.getLong("sequence") == request.getLong("sequence"))
        require(result.getString("targetId") == session.targetId)
        require(signatureMatches(session.key, canonicalResult(result), result.getString("signature")))
        val status = InputExecutionStatus.parse(result.getString("status"))
        val foreground = result.getBoolean("foreground")
        val capabilities = parseCapabilities(result.getJSONArray("capabilities"))
        val detailCode = result.optString("detailCode", "")
        return InputHealth(status, foreground, capabilities, session.targetId) to InputExecutionResult(status, foreground, detailCode)
    }

    fun canonicalHello(message: JSONObject): String =
        listOf(
            "HELLO",
            message.getInt("protocolVersion").toString(),
            message.getString("appVersion"),
            message.getString("requestId"),
            message.getString("clientNonce"),
            message.getString("targetId"),
        ).joinToString("\n")

    fun canonicalHelloResult(message: JSONObject): String =
        listOf(
            "HELLO_RESULT",
            message.getInt("protocolVersion").toString(),
            message.getString("appVersion"),
            message.getString("companionVersion"),
            message.getString("requestId"),
            message.getString("clientNonce"),
            message.getString("serverNonce"),
            message.getString("sessionId"),
            message.getString("targetId"),
            canonicalCapabilities(message.getJSONArray("capabilities")),
        ).joinToString("\n")

    fun canonicalRequest(message: JSONObject): String {
        val fields =
            mutableListOf(
                message.getString("type"),
                message.getInt("protocolVersion").toString(),
                message.getString("sessionId"),
                message.getString("requestId"),
                message.getLong("sequence").toString(),
                message.getString("targetId"),
            )
        if (message.getString("type") == "SWIPE_REQUEST") {
            fields.addAll(
                listOf(
                    message.getString("targetPackage"),
                    message.getString("scope"),
                    message.getInt("startX").toString(),
                    message.getInt("startY").toString(),
                    message.getInt("endX").toString(),
                    message.getInt("endY").toString(),
                    message.getInt("durationMs").toString(),
                ),
            )
        }
        return fields.joinToString("\n")
    }

    fun canonicalResult(message: JSONObject): String =
        listOf(
            message.getString("type"),
            message.getInt("protocolVersion").toString(),
            message.getString("sessionId"),
            message.getString("requestId"),
            message.getLong("sequence").toString(),
            message.getString("targetId"),
            message.getString("status"),
            message.getBoolean("foreground").toString(),
            canonicalCapabilities(message.getJSONArray("capabilities")),
            message.optString("detailCode", ""),
        ).joinToString("\n")

    private fun baseRequest(type: String, session: HostProtocolSession, requestId: String): JSONObject {
        require(tokenPattern.matches(requestId))
        val sequence = session.nextSequence++
        return JSONObject()
            .put("type", type)
            .put("protocolVersion", HOST_PROTOCOL_VERSION)
            .put("sessionId", session.id)
            .put("requestId", requestId)
            .put("sequence", sequence)
            .put("targetId", session.targetId)
    }

    private fun canonicalCapabilities(array: JSONArray): String =
        (0 until array.length()).map { array.getString(it) }.sorted().joinToString(",")

    private fun parseCapabilities(array: JSONArray): Set<InputCapability> =
        (0 until array.length()).mapNotNull { raw -> InputCapability.entries.firstOrNull { it.name == array.getString(raw) } }.toSet()

    private fun deriveSessionKey(secret: ByteArray, hello: JSONObject, result: JSONObject): ByteArray {
        val canonical =
            listOf(
                "SESSION",
                hello.getString("clientNonce"),
                result.getString("serverNonce"),
                result.getString("sessionId"),
                hello.getString("targetId"),
                hello.getString("appVersion"),
                result.getString("companionVersion"),
            ).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
    }
}
