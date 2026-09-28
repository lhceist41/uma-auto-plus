package com.steve1316.uma_android_automation.utils

import com.steve1316.automation_library.events.JSEvent
import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.bot.LaunchIdentityGate
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * The Remote Log Viewer's access code and Host allowlist, exercised against the real Ktor routes on
 * loopback with raw sockets (so the tests control the Host header and the first WebSocket frame).
 *
 * Loopback is reachable by every app on the device, and over adb forward by a DNS-rebinding page in the
 * PC's browser. So no history, live line, image, download or command may flow before the session's code,
 * and every route must refuse a Host that is not this server on loopback.
 */
@DisplayName("LogStreamServer access code and Host allowlist")
class LogStreamServerAccessTest {
    private var port = 0

    private val code: String get() = requireNotNull(LogStreamServer.accessCode) { "no access code while running" }

    @BeforeEach
    fun launch() {
        LaunchIdentityGate.clear()
        port = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        LogStreamServer.launchServer(port)
        awaitListening()
    }

    @AfterEach
    fun shutDown() {
        LogStreamServer.stop()
        LaunchIdentityGate.clear()
    }

    @Test
    fun `a client without the code gets no history, live line or command, while an authenticated one gets them`() {
        log("10:00:00.000 [INFO] history canary")

        WsClient(port).use { silent ->
            assertEquals(101, silent.status)
            assertNull(silent.next(700), "nothing is sent before the code")
            log("10:00:01.000 [INFO] live canary before auth")
            assertNull(silent.next(700), "a live line does not reach a client without the code")

            WsClient(port).use { refresh ->
                refresh.sendText("CMD:REFRESH_IMAGES")
                assertEquals("CLOSE:4401", refresh.next(), "a command as the first frame is refused and nothing else is sent")
            }

            WsClient(port).use { viewer ->
                viewer.sendText("AUTH:$code")
                assertEquals("AUTH_OK", viewer.next())
                val history = viewer.untilHistoryDone()
                assertTrue(history.contains("history canary") && history.contains("live canary before auth"), "the authenticated client gets the history")

                log("10:00:02.000 [INFO] live canary after auth")
                assertTrue(viewer.nextNonStatus()?.contains("live canary after auth") == true, "the authenticated client gets live lines")
                assertNull(silent.next(700), "the waiting client still gets nothing")
            }
        }
    }

    @Test
    fun `a wrong, partial or stale code is rejected with 4401 and nothing else`() {
        val current = code
        val wrong =
            listOf(
                "AUTH:wrong",
                "AUTH:",
                "AUTH:${current.dropLast(1)}",
                "AUTH:${current}x",
                "AUTH: $current",
                "AUTH:${current.uppercase()}",
                "auth:$current",
                current,
            )
        for (frame in wrong) {
            WsClient(port).use { client ->
                client.sendText(frame)
                assertEquals("CLOSE:4401", client.next(), "rejected: $frame")
            }
        }

        assertEquals(401, http("/logs/download", "127.0.0.1:$port").first, "no code header")
        assertEquals(401, http("/logs/download", "127.0.0.1:$port", "wrong").first, "wrong code header")
        assertEquals(401, http("/logs/download", "127.0.0.1:$port", current.dropLast(1)).first, "partial code header")
        assertEquals(200, http("/logs/download", "127.0.0.1:$port", current).first, "the right code downloads")

        // A new session rotates the code; the old one no longer opens anything.
        LogStreamServer.stop()
        assertNull(LogStreamServer.accessCode, "no code while stopped")
        LogStreamServer.launchServer(port)
        awaitListening()
        assertNotEquals(current, code)
        WsClient(port).use { client ->
            client.sendText("AUTH:$current")
            assertEquals("CLOSE:4401", client.next(), "the previous session's code is rejected")
        }
        assertEquals(401, http("/logs/download", "127.0.0.1:$port", current).first)
    }

    @Test
    fun `the launch-mismatch validation hook arms only for an authenticated session`() {
        WsClient(port).use { client ->
            client.sendText("CMD:ARM_LAUNCH_MISMATCH_TEST")
            assertEquals("CLOSE:4401", client.next())
        }
        LaunchIdentityGate.setExpected(1, "h")
        assertEquals(LaunchIdentityGate.Verdict.PASS, LaunchIdentityGate.verdict(1, "h"), "an unauthenticated command must not arm the gate")

        WsClient(port).use { client ->
            client.sendText("AUTH:$code")
            assertEquals("AUTH_OK", client.next())
            client.untilHistoryDone()
            client.sendText("CMD:ARM_LAUNCH_MISMATCH_TEST")
            assertEquals("ACK:ARM_LAUNCH_MISMATCH_TEST", client.nextNonStatus())
        }
        LaunchIdentityGate.setExpected(1, "h")
        assertEquals(LaunchIdentityGate.Verdict.MISMATCH, LaunchIdentityGate.verdict(1, "h"), "an authenticated command arms the gate")
    }

    @Test
    fun `every route and the WebSocket upgrade refuse a Host other than loopback with this port`() {
        val badHosts =
            listOf(
                "evil.example:$port",
                "localhost.evil.example:$port",
                "127.0.0.1.nip.io:$port",
                "localhost:${port + 1}",
                "127.0.0.1:${port + 1}",
                "localhost",
                "127.0.0.1",
                "[::1]:$port",
                "0.0.0.0:$port",
            )
        for (host in badHosts) {
            for (path in listOf("/", "/index.html", "/health", "/logs/download", "/log", "/status", "/dashboard/app.js", "/dashboard/fonts/jetbrains-mono/jetbrains-mono-latin-wght-normal.woff2")) {
                assertEquals(403, http(path, host, code).first, "$path with Host $host")
            }
            WsClient(port, host).use { assertEquals(403, it.status, "WebSocket upgrade with Host $host") }
        }
        assertTrue(http("/health", null).first >= 400, "a request with no Host is refused")

        for (host in listOf("localhost:$port", "127.0.0.1:$port", "LOCALHOST:$port")) {
            assertEquals(200, http("/health", host).first, "health with Host $host")
            assertEquals(200, http("/logs/download", host, code).first, "download with Host $host")
            val page = http("/", host).first
            assertTrue(page != 401 && page != 403, "the page needs no code with Host $host (got $page)")
            WsClient(port, host).use { assertEquals(101, it.status, "WebSocket upgrade with Host $host") }
        }
    }

    @Test
    fun `the access code is at least 16 characters from a secure random source and new each session`() {
        val codes = List(200) { LogStreamServer.newAccessCode() }
        assertEquals(codes.size, codes.toSet().size, "codes repeat")
        for (c in codes + code) {
            assertTrue(c.length >= 16 && c.all { it in "abcdefghijkmnpqrstuvwxyz23456789" }, "code shape: $c")
        }
        val server = sourceFile("utils/LogStreamServer.kt").readText().replace("\r\n", "\n")
        val generator = server.substring(server.indexOf("internal fun newAccessCode()"), server.indexOf("internal fun accessCodeMatches("))
        assertTrue(generator.contains("SecureRandom()"), "the code comes from SecureRandom")
    }

    @Test
    fun `the access code never reaches MessageLog, logcat, settings or files`() {
        val server = sourceFile("utils/LogStreamServer.kt").readText().replace("\r\n", "\n")
        val start = sourceFile("StartModule.kt").readText().replace("\r\n", "\n")

        // Every use of the code is one of these; a log, a settings write or a frame carrying it would add a new one.
        val allowed =
            setOf(
                "var accessCode: String? = null",
                "accessCode = newAccessCode()",
                "accessCode = null",
                "val expected = accessCode ?: return false",
            )
        val uses = server.lines().filter { Regex("\\baccessCode\\b").containsMatchIn(it) && !it.trimStart().startsWith("*") }.map { it.trim() }
        assertTrue(uses.isNotEmpty() && uses.all { it in allowed }, "unexpected use of the access code in LogStreamServer: ${uses.filterNot { it in allowed }}")
        assertEquals(listOf("promise.resolve(LogStreamServer.accessCode)"), start.lines().filter { it.contains("accessCode") && !it.trimStart().startsWith("*") }.map { it.trim() })

        // The received frame and header hold a candidate code, so the code-checking paths log nothing at all.
        for (fn in listOf("private suspend fun authenticate(", "internal fun accessCodeMatches(")) {
            val at = server.indexOf(fn)
            assertTrue(at >= 0, "missing $fn")
            val body = server.substring(at, server.indexOf("\n    }\n", at))
            assertFalse(Regex("\\b(MessageLog|Log)\\.[a-z]+\\(").containsMatchIn(body), "$fn must not log")
        }

        val others = kotlinRoot().walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "LogStreamServer.kt" && it.name != "StartModule.kt" }
        assertEquals(emptyList<String>(), others.filter { it.readText().contains("LogStreamServer.accessCode") }.map { it.name }.toList())
    }

    @Test
    fun `the bundled viewer sends the code first, on the download too, and never stores it`() {
        val page = File(kotlinRoot().parentFile.parentFile.parentFile.parentFile, "assets/log_viewer.html").readText().replace("\r\n", "\n")
        val onOpen = page.substring(page.indexOf("ws.onopen = function"), page.indexOf("ws.onmessage = function"))
        assertTrue(onOpen.contains("ws.send(\"AUTH:\" + accessCode)"), "the first frame is AUTH:<code>")
        assertTrue(page.contains("\"X-Access-Code\": accessCode"), "the download sends the code header")
        assertTrue(page.contains("event.code === 4401"), "a rejected code is asked for again")
        for (store in listOf("localStorage", "sessionStorage", "document.cookie")) {
            assertFalse(page.lines().any { it.contains(store) && it.contains("accessCode") }, "the code must not be written to $store")
        }
    }

    @Test
    fun `the viewer makes no request off the device and inserts no server text as HTML`() {
        val page = File(kotlinRoot().parentFile.parentFile.parentFile.parentFile, "assets/log_viewer.html").readText().replace("\r\n", "\n")
        assertFalse(page.contains("http://") || page.contains("https://"), "every asset comes from the device")
        assertTrue(page.contains("/dashboard/fonts/jetbrains-mono/jetbrains-mono-latin-wght-normal.woff2"), "the monospace font is the one the dashboard ships")
        for (name in listOf("showTooltip", "createLogEntry")) {
            val body = page.substringAfter("function $name(").substringBefore("\n            /**")
            assertTrue(body.isNotBlank() && !body.contains("innerHTML") && !body.contains("insertAdjacentHTML"), "$name writes text nodes only")
        }
    }

    @Test
    fun `a client that sends nothing is closed with 4401 when the auth time runs out`() {
        LogStreamServer.authTimeoutMs = 300
        try {
            WsClient(port).use { silent ->
                assertEquals(101, silent.status)
                assertEquals("CLOSE:4401", silent.next(3000), "closed without a frame, having sent nothing")
            }
        } finally {
            LogStreamServer.authTimeoutMs = 10_000L
        }
    }

    @Test
    fun `STATUS follows AUTH_OK, and GET status needs the code`() {
        StatusBoard.reset()
        StatusBoard.queueProgress(2, 4, "navigating", "{\"message\":\"raw text\"}")
        WsClient(port).use { client ->
            client.sendText("AUTH:$code")
            assertEquals("AUTH_OK", client.next())
            val first = JSONObject(client.next())
            assertEquals("status", first.getString("type"), "STATUS is the first frame after AUTH_OK")
            assertEquals(2, first.getJSONObject("run").getInt("current"))
        }
        assertEquals(401, http("/status", "127.0.0.1:$port").first)
        assertEquals(401, http("/status", "127.0.0.1:$port", "wrong").first)
        val (ok, body) = http("/status", "127.0.0.1:$port", code)
        assertEquals(200, ok)
        assertEquals("status", JSONObject(body).getString("type"))
        assertFalse(body.contains("raw text") || body.contains(code), "no queue message and no access code in STATUS")
        StatusBoard.reset()
    }

    @Test
    fun `the dashboard route serves only its shipped files and never a path from the request`() {
        // 500 here means the name passed the allowlist and only the Android assets are missing from the JVM.
        val served =
            listOf(
                "/",
                "/index.html",
                "/log",
                "/dashboard/dashboard.css",
                "/dashboard/logic.js",
                "/dashboard/app.js",
                "/dashboard/fonts/barlow-condensed/barlow-condensed-latin-700-normal.woff2",
            )
        for (path in served) {
            assertEquals(500, http(path, "localhost:$port").first, "served: $path")
        }
        val refused =
            listOf(
                "/dashboard/../log_viewer.html",
                "/dashboard/..%2flog_viewer.html",
                "/dashboard/%2e%2e/%2e%2e/log_viewer.html",
                "/dashboard/fonts/barlow-condensed/OFL.txt",
                "/dashboard/fonts/PROVENANCE.md",
                "/dashboard/index.html",
                "/dashboard/",
                "/dashboard/app.js/",
                "/dashboard/APP.JS",
                "/dashboard/fonts",
                "/log_viewer.html",
                "/dashboard/settings.db",
            )
        for (path in refused) {
            val status = http(path, "localhost:$port").first
            assertTrue(status == 404 || status == 400, "refused: $path (got $status)")
        }
        val server = sourceFile("utils/LogStreamServer.kt").readText().replace("\r\n", "\n")
        val serve = server.substring(server.indexOf("private suspend fun serveDashboardFile("), server.indexOf("internal fun statusJson()"))
        assertTrue(serve.contains("open(\"dashboard/\$name\")"), "the asset path is built from the allowlisted name only")
        val route = server.substring(server.indexOf("get(\"/dashboard/{name...}\")"), server.indexOf("get(\"/log\")"))
        assertTrue(route.indexOf("DASHBOARD_FILES[name]") in 0 until route.indexOf("serveDashboardFile("), "the name is looked up before anything is served")
    }

    @Test
    fun `STATUS is pushed only when it changed, at most once per second`() {
        StatusBoard.reset()
        WsClient(port).use { client ->
            client.sendText("AUTH:$code")
            assertEquals("AUTH_OK", client.next())
            client.untilHistoryDone()
            assertTrue(countStatusFrames(client, 2600) <= 1, "a quiet status is not resent (at most the poller's first look)")

            val publisher =
                Thread {
                    var n = 0
                    while (!Thread.currentThread().isInterrupted) {
                        StatusBoard.queueProgress(++n, 1000, "navigating", "{}")
                        try {
                            Thread.sleep(40)
                        } catch (_: InterruptedException) {
                            return@Thread
                        }
                    }
                }
            publisher.start()
            try {
                val pushed = countStatusFrames(client, 2600)
                assertTrue(pushed in 2..3, "changes every 40 ms go out once per second, not per change (got $pushed)")
            } finally {
                publisher.interrupt()
                publisher.join()
            }
        }
        StatusBoard.reset()
    }

    @Test
    fun `STATUS leaves only through the authenticated WebSocket or the code-gated status route`() {
        val server = sourceFile("utils/LogStreamServer.kt").readText().replace("\r\n", "\n")
        val uses = Regex("statusJson\\(\\)").findAll(server).map { it.range.first }.filter { !server.startsWith("internal fun statusJson()", it - "internal fun ".length) }.toList()
        assertEquals(3, uses.size, "the frame after AUTH_OK, the change push, and GET /status")

        val session = server.substring(server.indexOf("private suspend fun handleWebSocketSession("), server.indexOf("private suspend fun handleNewClientAction("))
        assertTrue(session.indexOf("if (!authenticate(session))") in 0 until session.indexOf("statusJson()"), "after the access code only")
        val push = server.substring(server.indexOf("private suspend fun pushStatusWhileRunning("), server.indexOf("private suspend fun serveLogViewerHtml("))
        assertTrue(push.contains("for (client in clients)") && !push.contains("session"), "pushed only to authenticated, synced clients")
        val route = server.substring(server.indexOf("get(\"/status\")"), server.indexOf("get(\"/health\")"))
        assertTrue(route.indexOf("accessCodeMatches(") in 0 until route.indexOf("statusJson()"), "GET /status checks the code first")
        assertTrue(server.contains("private val clients") && Regex("clients\\.add\\(").findAll(server).count() == 1, "one place adds a client, after its history sync")
    }

    private fun countStatusFrames(
        client: WsClient,
        windowMs: Long,
    ): Int {
        val end = System.currentTimeMillis() + windowMs
        var count = 0
        while (true) {
            val left = end - System.currentTimeMillis()
            if (left <= 0) return count
            val frame = client.next(left.toInt()) ?: return count
            if (isStatus(frame)) count++
        }
    }

    /** A queue on run 2 of 4, with the session probe set as given; everything restored after [body]. */
    private fun withQueue(
        active: Boolean = true,
        key: String = "starting",
        body: () -> Unit,
    ) {
        val probe = LogStreamServer.sessionActive
        StartModule.stopAfterCareerRequested = false
        StatusBoard.reset(900L)
        StatusBoard.queueProgress(2, 4, key, "{}", 1_000L)
        LogStreamServer.sessionActive = { active }
        try {
            body()
        } finally {
            LogStreamServer.sessionActive = probe
            StartModule.stopAfterCareerRequested = false
            StatusBoard.reset()
        }
    }

    /** Sends [frames] on an authenticated socket, then waits for the ARM command's ack, so every frame before it was handled. */
    private fun sendAuthenticated(vararg frames: String) {
        WsClient(port).use { client ->
            client.sendText("AUTH:$code")
            assertEquals("AUTH_OK", client.next())
            client.untilHistoryDone()
            for (frame in frames) client.sendText(frame)
            // A new second of the per-connection command budget, so the barrier is never the dropped frame.
            Thread.sleep(2_100)
            client.sendText("CMD:ARM_LAUNCH_MISMATCH_TEST")
            assertEquals("ACK:ARM_LAUNCH_MISMATCH_TEST", client.nextNonStatus())
        }
    }

    @Test
    fun `stop after this career is taken only on an authenticated socket`() =
        withQueue {
            WsClient(port).use { client ->
                client.sendText(LogStreamServer.CMD_STOP_AFTER_CAREER)
                assertEquals("CLOSE:4401", client.next(), "a command before the code closes the socket")
            }
            WsClient(port).use { client ->
                client.sendText("AUTH:wrong")
                assertEquals("CLOSE:4401", client.next())
            }
            assertEquals(401, http("/status?cmd=${LogStreamServer.CMD_STOP_AFTER_CAREER}", "127.0.0.1:$port").first)
            assertEquals(200, http("/status", "127.0.0.1:$port", code).first)
            assertFalse(StartModule.stopAfterCareerRequested, "nothing before auth, with a wrong code or over HTTP sets it")
            sendAuthenticated(LogStreamServer.CMD_STOP_AFTER_CAREER)
            assertTrue(StartModule.stopAfterCareerRequested, "accepted after AUTH_OK")
            sendAuthenticated(LogStreamServer.CMD_CANCEL_STOP_AFTER_CAREER)
            assertFalse(StartModule.stopAfterCareerRequested, "and cancelled")
        }

    @Test
    fun `a malformed stop command changes nothing`() =
        withQueue {
            sendAuthenticated("CMD:STOP_AFTER_CAREER ", "cmd:stop_after_career", "CMD:STOP_AFTER_CAREER:1", "STOP_AFTER_CAREER", "CMD:STOP")
            assertFalse(StartModule.stopAfterCareerRequested)
        }

    @Test
    fun `a stop command while it is not offered changes nothing`() {
        withQueue(active = false) {
            sendAuthenticated(LogStreamServer.CMD_STOP_AFTER_CAREER)
            assertFalse(StartModule.stopAfterCareerRequested, "no session")
        }
        for (key in listOf("navigating", "waiting")) {
            withQueue(key = key) {
                sendAuthenticated(LogStreamServer.CMD_STOP_AFTER_CAREER)
                assertFalse(StartModule.stopAfterCareerRequested, key)
            }
        }
        withQueue(key = "navigating") {
            StartModule.stopAfterCareerRequested = true
            sendAuthenticated(LogStreamServer.CMD_CANCEL_STOP_AFTER_CAREER)
            assertTrue(StartModule.stopAfterCareerRequested, "a cancel is also refused past the stop point, as Home hides it")
        }
    }

    @Test
    fun `the request made on the dashboard is the one Home reads, and STATUS shows it`() =
        withQueue {
            WsClient(port).use { client ->
                client.sendText("AUTH:$code")
                assertEquals("AUTH_OK", client.next())
                val first = JSONObject(client.next()).getJSONObject("session").getJSONObject("stopAfterCareer")
                assertTrue(first.getBoolean("offered") && !first.getBoolean("requested"))
                client.untilHistoryDone()
                client.sendText(LogStreamServer.CMD_STOP_AFTER_CAREER)
                var shown = false
                repeat(10) {
                    val frame = client.next(3000) ?: return@repeat
                    if (isStatus(frame) && JSONObject(frame).getJSONObject("session").getJSONObject("stopAfterCareer").getBoolean("requested")) shown = true
                    if (shown) return@repeat
                }
                assertTrue(shown, "the next STATUS says it is requested")
            }
            assertTrue(StartModule.stopAfterCareerRequested)
            val start = sourceFile("StartModule.kt").readText().replace("\r\n", "\n")
            assertTrue(start.contains("map.putBoolean(\"stopAfterCareer\", stopAfterCareerRequested)"), "Home reads this flag")
            assertTrue(start.contains("fun setStopAfterCareer(requested: Boolean, promise: Promise) {\n        stopAfterCareerRequested = requested"), "Home sets this flag")
        }

    @Test
    fun `an accepted command is logged once, after the request changed, naming the dashboard`() {
        val server = sourceFile("utils/LogStreamServer.kt").readText().replace("\r\n", "\n")
        val branch = server.substringAfter("val now = stopAfterCareerCommand(text, offered, StartModule.stopAfterCareerRequested)").substringBefore("\n                    }\n                }")
        val set = branch.indexOf("StartModule.stopAfterCareerRequested = now")
        val logged = branch.indexOf("MessageLog.i(TAG,")
        assertTrue(set in 0 until logged, "the state changes first, then one line")
        assertEquals(1, Regex("MessageLog\\.").findAll(branch).count())
        assertTrue(branch.contains("from the dashboard"))
        // The commands are read only inside the frame loop after authenticate(), never in a route.
        val afterAuth = server.substringAfter("private suspend fun handleWebSocketSession(").substringBefore("private suspend fun handleNewClientAction(")
        assertTrue(afterAuth.indexOf("if (!authenticate(session))") in 0 until afterAuth.indexOf("stopAfterCareerCommand(text,"))
        val routes = server.substringAfter("routing {")
        assertFalse(routes.contains("stopAfterCareer"), "no route reads or sets it")
    }

    @Test
    fun `the command decision takes only the exact frames while offered, and only a change`() {
        val stop = LogStreamServer.CMD_STOP_AFTER_CAREER
        val cancel = LogStreamServer.CMD_CANCEL_STOP_AFTER_CAREER
        assertEquals(true, LogStreamServer.stopAfterCareerCommand(stop, offered = true, requested = false))
        assertEquals(false, LogStreamServer.stopAfterCareerCommand(cancel, offered = true, requested = true))
        assertNull(LogStreamServer.stopAfterCareerCommand(stop, offered = false, requested = false))
        assertNull(LogStreamServer.stopAfterCareerCommand(stop, offered = true, requested = true), "no change, no log")
        assertNull(LogStreamServer.stopAfterCareerCommand("CMD:REFRESH_IMAGES", offered = true, requested = false))
    }

    /** The `[QUEUE]` lines the dashboard's stop commands wrote, from the app's own log. */
    private fun stopCommandLogLines() = com.steve1316.automation_library.utils.MessageLog.getMessageLogCopy().count { it.contains("Stop after this career") && it.contains("from the dashboard") }

    @Test
    fun `an oversized frame closes only its own socket, before or after the code`() {
        WsClient(port).use { client ->
            client.sendText("AUTH:" + "x".repeat(2_000))
            assertEquals("CLOSE:1009", client.next(), "an oversized first frame is refused as too big")
        }
        WsClient(port).use { bystander ->
            bystander.sendText("AUTH:$code")
            assertEquals("AUTH_OK", bystander.next())
            bystander.untilHistoryDone()
            WsClient(port).use { client ->
                client.sendText("AUTH:$code")
                assertEquals("AUTH_OK", client.next())
                client.untilHistoryDone()
                client.sendText("CMD:" + "y".repeat(LogStreamServer.MAX_INCOMING_FRAME_BYTES.toInt()))
                assertEquals("CLOSE:1009", client.nextNonStatus(), "an oversized frame after the code closes that socket")
            }
            log("10:00:03.000 [INFO] still streaming")
            assertTrue(bystander.nextNonStatus()?.contains("still streaming") == true, "another client is unaffected")
        }
    }

    @Test
    fun `the largest frames the server sends are not limited by the incoming frame size`() {
        val long = "10:00:04.000 [INFO] " + "z".repeat(20_000)
        log(long)
        WsClient(port).use { client ->
            client.sendText("AUTH:$code")
            assertEquals("AUTH_OK", client.next())
            assertTrue(client.untilHistoryDone().contains("z".repeat(20_000)), "a 20 KB history frame arrives whole")
            log("10:00:05.000 [INFO] " + "w".repeat(20_000))
            assertTrue(client.nextNonStatus()?.contains("w".repeat(20_000)) == true, "and so does a 20 KB live line")
        }
    }

    @Test
    fun `a flood of stop commands changes the state once and logs once`() =
        withQueue {
            val before = stopCommandLogLines()
            WsClient(port).use { client ->
                client.sendText("AUTH:$code")
                assertEquals("AUTH_OK", client.next())
                client.untilHistoryDone()
                repeat(40) { client.sendText(LogStreamServer.CMD_STOP_AFTER_CAREER) }
                Thread.sleep(2_100)
                client.sendText("CMD:ARM_LAUNCH_MISMATCH_TEST")
                assertEquals("ACK:ARM_LAUNCH_MISMATCH_TEST", client.nextNonStatus(), "the flood was handled")
            }
            assertTrue(StartModule.stopAfterCareerRequested)
            assertEquals(1, stopCommandLogLines() - before)
        }

    @Test
    fun `an alternating flood is cut to the per-second budget, with nothing logged for the dropped frames`() =
        withQueue {
            val before = stopCommandLogLines()
            WsClient(port).use { client ->
                client.sendText("AUTH:$code")
                assertEquals("AUTH_OK", client.next())
                client.untilHistoryDone()
                repeat(20) { client.sendText(if (it % 2 == 0) LogStreamServer.CMD_STOP_AFTER_CAREER else LogStreamServer.CMD_CANCEL_STOP_AFTER_CAREER) }
                Thread.sleep(2_100)
                client.sendText("CMD:ARM_LAUNCH_MISMATCH_TEST")
                assertEquals("ACK:ARM_LAUNCH_MISMATCH_TEST", client.nextNonStatus())
            }
            val logged = stopCommandLogLines() - before
            assertTrue(logged in 1..LogStreamServer.COMMAND_FRAMES_PER_SECOND, "at most the budget in the flood's second: $logged")
        }

    @Test
    fun `the command budget allows a few frames a second per connection`() {
        val second = 1_000_000_000L
        val budget = LogStreamServer.CommandBudget(perSecond = 3)
        assertEquals(listOf(true, true, true, false, false), (0 until 5).map { budget.allow(second + it) })
        assertTrue(budget.allow(2 * second - 1).not(), "the window lasts a second")
        assertTrue(budget.allow(2 * second), "a new second, a new budget")
        // nanoTime has an arbitrary origin: any value, even the smallest, starts a window.
        val early = LogStreamServer.CommandBudget(perSecond = 1)
        assertTrue(early.allow(Long.MIN_VALUE))
        assertFalse(early.allow(Long.MIN_VALUE + 1))
        assertTrue(early.allow(Long.MIN_VALUE + second))
        val atZero = LogStreamServer.CommandBudget(perSecond = 1)
        assertTrue(atZero.allow(0L))
        assertFalse(atZero.allow(1L), "a window that starts at 0 is still a window")
        assertEquals(5, LogStreamServer.COMMAND_FRAMES_PER_SECOND)
        assertTrue(LogStreamServer.MAX_INCOMING_FRAME_BYTES >= ("AUTH:" + "a".repeat(16)).length * 10, "wide room over the largest legitimate frame")
        val server = sourceFile("utils/LogStreamServer.kt").readText().replace("\r\n", "\n")
        assertTrue(server.contains("install(WebSockets) {\n                    maxFrameSize = MAX_INCOMING_FRAME_BYTES\n                }"))
        assertTrue(server.contains("if (frame is Frame.Text) {\n                    if (!budget.allow(System.nanoTime())) continue\n"), "monotonic; a dropped frame gets no reply and no log")
    }

    @Test
    fun `each connection has its own budget, so one busy client cannot starve another`() {
        WsClient(port).use { first ->
            WsClient(port).use { second ->
                for (client in listOf(first, second)) {
                    client.sendText("AUTH:$code")
                    assertEquals("AUTH_OK", client.next())
                    client.untilHistoryDone()
                }
                // Both clients use their whole budget inside the same second: 4 ignored frames, then the ARM.
                for (client in listOf(first, second)) {
                    repeat(LogStreamServer.COMMAND_FRAMES_PER_SECOND - 1) { client.sendText("CMD:NOT_A_COMMAND") }
                    client.sendText("CMD:ARM_LAUNCH_MISMATCH_TEST")
                }
                assertEquals("ACK:ARM_LAUNCH_MISMATCH_TEST", first.nextNonStatus(), "the first connection's fifth frame is within its own budget")
                assertEquals("ACK:ARM_LAUNCH_MISMATCH_TEST", second.nextNonStatus(), "and so is the second's")
            }
        }
    }

    private fun log(line: String) = LogStreamServer.onMessageLogEvent(JSEvent("MessageLog", line))

    private fun awaitListening() {
        val deadline = System.currentTimeMillis() + 5000
        while (true) {
            try {
                Socket("127.0.0.1", port).close()
                return
            } catch (e: Exception) {
                if (System.currentTimeMillis() > deadline) throw e
                Thread.sleep(50)
            }
        }
    }

    /** A raw HTTP/1.1 GET, so the Host header is exactly what the test says (or absent). Returns status and body. */
    private fun http(
        path: String,
        host: String?,
        accessCode: String? = null,
    ): Pair<Int, String> =
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5000
            val request = StringBuilder("GET $path HTTP/1.1\r\n")
            host?.let { request.append("Host: $it\r\n") }
            accessCode?.let { request.append("X-Access-Code: $it\r\n") }
            request.append("Connection: close\r\n\r\n")
            socket.getOutputStream().write(request.toString().toByteArray())
            val response = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            response.substringBefore("\r\n").split(" ")[1].toInt() to response.substringAfter("\r\n\r\n")
        }

    /** A minimal RFC 6455 client over a raw socket: masked text frames out, text and close frames in. */
    private class WsClient(
        port: Int,
        host: String = "127.0.0.1:$port",
    ) : Closeable {
        private val socket = Socket("127.0.0.1", port)
        private val input = DataInputStream(socket.getInputStream())
        val status: Int

        init {
            socket.soTimeout = 5000
            socket.getOutputStream().write(
                (
                    "GET / HTTP/1.1\r\nHost: $host\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n"
                ).toByteArray(),
            )
            val head = ByteArrayOutputStream()
            while (!head.toString(Charsets.UTF_8.name()).endsWith("\r\n\r\n")) {
                val b = input.read()
                if (b < 0) break
                head.write(b)
            }
            status = head.toString(Charsets.UTF_8.name()).substringBefore("\r\n").split(" ").getOrNull(1)?.toIntOrNull() ?: -1
        }

        fun sendText(text: String) {
            val payload = text.toByteArray()
            val mask = byteArrayOf(0x1b, 0x2c, 0x3d, 0x4e)
            val frame = ByteArrayOutputStream()
            frame.write(0x81)
            if (payload.size < 126) {
                frame.write(0x80 or payload.size)
            } else {
                frame.write(0x80 or 126)
                frame.write(payload.size shr 8)
                frame.write(payload.size and 0xff)
            }
            frame.write(mask)
            payload.forEachIndexed { i, b -> frame.write(b.toInt() xor mask[i % 4].toInt()) }
            socket.getOutputStream().write(frame.toByteArray())
        }

        /** The next text frame, `CLOSE:<code>` for a close frame, `EOF`, or null when nothing arrives within [timeoutMs]. */
        fun next(timeoutMs: Int = 5000): String? {
            socket.soTimeout = timeoutMs
            try {
                while (true) {
                    val b0 = input.read()
                    if (b0 < 0) return "EOF"
                    var length = (input.readUnsignedByte() and 0x7f).toLong()
                    if (length == 126L) {
                        length = input.readUnsignedShort().toLong()
                    } else if (length == 127L) {
                        length = input.readLong()
                    }
                    val payload = ByteArray(length.toInt())
                    input.readFully(payload)
                    when (b0 and 0x0f) {
                        0x1 -> return String(payload, Charsets.UTF_8)
                        0x8 -> return "CLOSE:" + if (payload.size >= 2) ((payload[0].toInt() and 0xff) shl 8) or (payload[1].toInt() and 0xff) else -1
                    }
                }
            } catch (_: SocketTimeoutException) {
                return null
            }
        }

        /** The next frame that is not a STATUS push, which the server may interleave at any time. */
        fun nextNonStatus(timeoutMs: Int = 5000): String? {
            while (true) {
                val frame = next(timeoutMs) ?: return null
                if (!isStatus(frame)) return frame
            }
        }

        /** Reads up to `HISTORY_DONE` and returns everything received on the way. */
        fun untilHistoryDone(): String {
            val seen = StringBuilder()
            while (true) {
                val frame = next() ?: throw AssertionError("no HISTORY_DONE; got: $seen")
                if (frame == "HISTORY_DONE") return seen.toString()
                if (frame.startsWith("CLOSE:") || frame == "EOF") throw AssertionError("closed before HISTORY_DONE: $frame")
                seen.append(frame).append('\n')
            }
        }

        override fun close() = socket.close()
    }

    private fun sourceFile(relative: String): File = File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}

/** A STATUS frame; parsed, because the JVM's org.json does not keep key order. */
private fun isStatus(frame: String) = frame.startsWith("{") && runCatching { JSONObject(frame).optString("type") }.getOrNull() == "status"
