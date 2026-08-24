package com.steve1316.uma_android_automation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class HostInputTransportTest {
    private val targetId = "a".repeat(32)
    private val secret = "s".repeat(32).toByteArray()
    private val pairing = HostPairing(targetId, secret)

    @Test
    fun `missing old or unknown settings stay accessibility-only`() {
        assertEquals(HostInputMode.ACCESSIBILITY_ONLY, HostInputMode.parse(null))
        assertEquals(HostInputMode.ACCESSIBILITY_ONLY, HostInputMode.parse("unexpected"))
        assertNull(HostInputConfiguration.fromSettings(null, null))
        assertNull(HostInputConfiguration.fromSettings("unexpected", validPairingCode()))
    }

    @Test
    fun `host mode remains unavailable until a valid pairing code is present`() {
        assertNull(HostInputConfiguration.fromSettings("accessibility_with_host", ""))
        assertNull(HostInputConfiguration.fromSettings("accessibility_with_host", "v1.$targetId.not-a-secret"))
        val configuration = HostInputConfiguration.fromSettings("accessibility_with_host", validPairingCode())
        assertNotNull(configuration)
        assertEquals(targetId, configuration?.pairing?.targetId)
        assertTrue(secret.contentEquals(configuration?.pairing?.secret))
    }

    @Test
    fun `pairing tokens use canonical unpadded base64url on every supported API level`() {
        assertEquals("", HostAdbProtocol.encodeBase64Url(byteArrayOf()))
        assertEquals("Zg", HostAdbProtocol.encodeBase64Url("f".toByteArray()))
        assertEquals("Zm8", HostAdbProtocol.encodeBase64Url("fo".toByteArray()))
        assertEquals("Zm9v", HostAdbProtocol.encodeBase64Url("foo".toByteArray()))

        val nonCanonical = validPairingCode().dropLast(1) + "B"
        assertNull(HostAdbProtocol.parsePairingCode(nonCanonical))
    }

    @Test
    fun `version target and authentication mismatches reject the handshake`() {
        val hello = HostAdbProtocol.buildHello(pairing, "1.2.3", "request_12345678", "client_nonce_123456789012")
        val valid = helloResult(hello)

        val wrongVersion = JSONObject(valid.toString()).put("companionVersion", "2.0.0")
        wrongVersion.put("signature", HostAdbProtocol.sign(secret, HostAdbProtocol.canonicalHelloResult(wrongVersion)))
        assertThrows(IllegalArgumentException::class.java) {
            HostAdbProtocol.acceptHelloResult(hello, wrongVersion, pairing, "1.2.3")
        }

        val wrongTarget = JSONObject(valid.toString()).put("targetId", "b".repeat(32))
        wrongTarget.put("signature", HostAdbProtocol.sign(secret, HostAdbProtocol.canonicalHelloResult(wrongTarget)))
        assertThrows(IllegalArgumentException::class.java) {
            HostAdbProtocol.acceptHelloResult(hello, wrongTarget, pairing, "1.2.3")
        }

        val wrongAuthentication = JSONObject(valid.toString()).put("signature", "invalid")
        assertThrows(IllegalArgumentException::class.java) {
            HostAdbProtocol.acceptHelloResult(hello, wrongAuthentication, pairing, "1.2.3")
        }
    }

    @Test
    fun `hello signature matches the companion wire vector`() {
        val hello = HostAdbProtocol.buildHello(pairing, "1.2.3", "request_12345678", "client_nonce_123456789012")
        assertEquals("vZF1EMkIIDB3g87-T2N4PqaOajd2c6m6CHwZ62I8sdY", hello.getString("signature"))
    }

    @Test
    fun `authenticated health response exposes capability and foreground state`() {
        val hello = HostAdbProtocol.buildHello(pairing, "1.2.3", "request_12345678", "client_nonce_123456789012")
        val session = HostAdbProtocol.acceptHelloResult(hello, helloResult(hello), pairing, "1.2.3")
        val request = HostAdbProtocol.buildHealthRequest(session, "request_87654321")
        val result =
            JSONObject()
                .put("type", "HEALTH_RESULT")
                .put("protocolVersion", HOST_PROTOCOL_VERSION)
                .put("sessionId", session.id)
                .put("requestId", request.getString("requestId"))
                .put("sequence", request.getLong("sequence"))
                .put("targetId", targetId)
                .put("status", "EXECUTED")
                .put("foreground", false)
                .put("capabilities", JSONArray(listOf("HEALTH", "SWIPE")))
                .put("detailCode", "READY")
        result.put("signature", HostAdbProtocol.sign(session.key, HostAdbProtocol.canonicalResult(result)))

        val health = HostAdbProtocol.acceptResult(session, request, result, "HEALTH_RESULT").first
        assertEquals(InputExecutionStatus.EXECUTED, health.status)
        assertFalse(health.foreground)
        assertTrue(InputCapability.SWIPE in health.capabilities)
    }

    @Test
    fun `swipe requests remain scoped and bounded`() {
        val borrow =
            BoundedSwipe.fromPixels(
                HostInputScope.BORROW_LIST_SCROLL,
                startX = 540f,
                startY = 1382f,
                endX = 540f,
                endY = 596f,
                frameWidth = 1080,
                frameHeight = 1920,
                durationMs = 900,
            )
        assertNotNull(borrow)
        assertNull(borrow?.validationError())
        assertEquals(HostInputScope.BORROW_LIST_SCROLL, borrow?.scope)

        assertEquals(
            "COORDINATES_OUT_OF_RANGE",
            BoundedSwipe(HostInputScope.BORROW_LIST_SCROLL, 5000, 7200, 5000, -1, 900).validationError(),
        )
        assertEquals(
            "DURATION_OUT_OF_RANGE",
            BoundedSwipe(HostInputScope.LEGACY_LIST_SCROLL, 5000, 7300, 5000, 3400, 1200).validationError(),
        )
        assertEquals(
            "GEOMETRY_NOT_ALLOWED",
            BoundedSwipe(HostInputScope.LEGACY_LIST_SCROLL, 5000, 7300, 5000, 7100, 900).validationError(),
        )
    }

    @Test
    fun `executed transport is not treated as movement evidence`() {
        val before = ScreenFingerprint(recognized = true, value = "same")
        val executed = InputExecutionResult(InputExecutionStatus.EXECUTED, foreground = true, detailCode = "ADB_EXIT_0")
        assertEquals(SwipeMovement.NO_EFFECT, verifySwipeMovement(before, executed, ScreenFingerprint(true, "same")))
        assertEquals(SwipeMovement.MOVED, verifySwipeMovement(before, executed, ScreenFingerprint(true, "different")))
        assertEquals(SwipeMovement.UNCERTAIN, verifySwipeMovement(before, executed, ScreenFingerprint(false, "different")))

        val ambiguous = InputExecutionResult(InputExecutionStatus.AMBIGUOUS, foreground = false, detailCode = "RESPONSE_LOST")
        assertEquals(SwipeMovement.UNCERTAIN, verifySwipeMovement(before, ambiguous, ScreenFingerprint(true, "different")))
    }

    @Test
    fun `diagnostic source contains one swipe boundary and no selection or launch action`() {
        val navigator = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt").readText()
        for (name in listOf("rehearseHostBorrowSwipe", "rehearseHostLegacySwipe")) {
            val body = functionBody(navigator, name)
            assertTrue(body.contains("runHostSwipeDiagnostic"), "$name must use the bounded diagnostic runner")
            assertFalse(body.contains("CoordinateTap"), "$name must not use coordinate taps")
            assertFalse(body.contains(".click("), "$name must not click a control")
            assertFalse(body.contains("ButtonStartCareer"), "$name must not reference the launch action")
            assertFalse(body.contains("gestureUtils"), "$name must not fall back to accessibility gestures")
        }
        val runner = functionBody(navigator, "runHostSwipeDiagnostic")
        assertEquals(1, Regex("transport\\.swipe\\(").findAll(runner).count(), "the diagnostic runner must issue exactly one transport swipe")
        assertFalse(runner.contains("while ("), "the diagnostic runner must not retry")

        val protocol = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/HostAdbProtocol.kt").readText()
        assertFalse(protocol.contains("TAP_REQUEST"))
        assertFalse(protocol.contains("KEY_REQUEST"))
        assertFalse(protocol.contains("TEXT_REQUEST"))
    }

    @Test
    fun `settings import and export keep the pairing code private`() {
        val manager = repoFile("src/hooks/useSettingsManager.tsx").readText()
        assertTrue(manager.contains("delete settingsForExport.debug.hostInputPairingCode"))
        assertTrue(manager.contains("importedSettings.debug.hostInputPairingCode = settingsRef.current.debug?.hostInputPairingCode"))
        assertTrue(Regex("delete profile\\.settings\\.debug\\.hostInputPairingCode").findAll(manager).count() >= 2)
    }

    private fun helloResult(hello: JSONObject): JSONObject {
        val result =
            JSONObject()
                .put("type", "HELLO_RESULT")
                .put("protocolVersion", HOST_PROTOCOL_VERSION)
                .put("appVersion", hello.getString("appVersion"))
                .put("companionVersion", HOST_COMPANION_VERSION)
                .put("requestId", hello.getString("requestId"))
                .put("clientNonce", hello.getString("clientNonce"))
                .put("serverNonce", "server_nonce_123456789012")
                .put("sessionId", "session_12345678")
                .put("targetId", targetId)
                .put("capabilities", JSONArray(listOf("HEALTH", "SWIPE")))
        result.put("signature", HostAdbProtocol.sign(secret, HostAdbProtocol.canonicalHelloResult(result)))
        return result
    }

    private fun validPairingCode(): String =
        "v1.$targetId.${HostAdbProtocol.encodeBase64Url(secret)}"

    private fun functionBody(source: String, name: String): String {
        val nameIndex = source.indexOf("fun $name")
        require(nameIndex >= 0) { "function $name not found" }
        val openingBrace = source.indexOf('{', nameIndex)
        require(openingBrace >= 0) { "function $name has no body" }
        var depth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(nameIndex, index + 1)
                }
            }
        }
        error("function $name has an unterminated body")
    }

    private fun repoFile(relative: String): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val candidate = File(directory, relative)
            if (candidate.isFile) return candidate
            directory = directory?.parentFile
        }
        error("repository file not found: $relative")
    }
}
