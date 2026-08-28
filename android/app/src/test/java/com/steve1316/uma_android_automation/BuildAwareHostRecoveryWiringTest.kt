package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Host-recovery wiring guard: the three production build-aware Borrow walkers (locate, select/revalidate,
 * selected-marker verification) must reuse the already-proven [recoverBorrowScrollWithHost] rung, the
 * census/read-only pool walker must not gain host authority, and the default production walker must be
 * untouched. The movement/freshness/budget behaviour of the shared walker itself is exercised behaviorally
 * in [SmartBorrowScanTest] and is not re-tested here.
 */
@DisplayName("Build-aware host recovery wiring")
class BuildAwareHostRecoveryWiringTest {
    private val nav by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt") }

    private fun slice(signature: String, next: String): String {
        val start = nav.indexOf(signature)
        assertTrue(start >= 0, "$signature exists")
        val end = nav.indexOf(next, start + signature.length)
        assertTrue(end > start, "$next follows $signature")
        return nav.substring(start, end)
    }

    private fun defaultWalkerBody() = slice("private fun borrowWalker(): BorrowListWalker =", "private fun recoverBorrowScrollWithHost(): HostScrollRecoveryReport =")

    private fun censusBody() = slice("internal fun scanBorrowPoolReadOnly(", "private fun nameKeyOf(obs: BorrowRowObservation):")

    private fun locateBody() = slice("internal fun locateSmartBorrowIntentReadOnly(", "private fun persistSmartBorrowLocate(")

    private fun selectBody() = slice("private fun selectBorrowByIdentityRevalidated(", "private fun revalidateAndTapBorrow(")

    private fun selectedSlotRowsBody() = slice("private fun readSelectedSlotRows(", "private fun readSelectedSlotVerification(")

    @Nested
    @DisplayName("the three production build-aware walkers reuse the existing recovery rung")
    inner class BuildAwareWiring {
        @Test
        fun `the locate walker wires recoverHost to the existing production recovery function`() {
            val body = locateBody()
            assertTrue(body.contains("recoverHost ="), "locateSmartBorrowIntentReadOnly must wire recoverHost")
            assertTrue(body.contains("recoverBorrowScrollWithHost()"), "must reuse the existing helper, not a new implementation")
        }

        @Test
        fun `the select-revalidate walker wires recoverHost to the existing production recovery function`() {
            val body = selectBody()
            assertTrue(body.contains("recoverHost ="), "selectBorrowByIdentityRevalidated must wire recoverHost")
            assertTrue(body.contains("recoverBorrowScrollWithHost()"), "must reuse the existing helper, not a new implementation")
        }

        @Test
        fun `the selected-marker verification walker wires recoverHost to the existing production recovery function`() {
            val body = selectedSlotRowsBody()
            assertTrue(body.contains("recoverHost ="), "readSelectedSlotRows must wire recoverHost")
            assertTrue(body.contains("recoverBorrowScrollWithHost()"), "must reuse the existing helper, not a new implementation")
        }

        @Test
        fun `none of the three walkers introduce a host tap`() {
            for (body in listOf(locateBody(), selectBody(), selectedSlotRowsBody())) {
                assertFalse(body.contains("CoordinateTap"), "no host TAP may be introduced by this wiring")
                assertFalse(body.contains("ButtonStartCareer"), "no walker wiring may reference Start Career")
            }
        }
    }

    @Nested
    @DisplayName("excluded paths gain no host authority")
    inner class ExcludedPaths {
        @Test
        fun `the census read-only pool walker is unchanged and has no host recovery`() {
            val body = censusBody()
            assertFalse(body.contains("recoverHost"), "the census/read-only pool walker must not gain host authority")
        }

        @Test
        fun `the default production walker keeps its own existing host recovery unchanged`() {
            val body = defaultWalkerBody()
            assertTrue(body.contains("recoverHost ="), "the already-shipped default production walker must keep its recovery rung")
            assertTrue(body.contains("recoverBorrowScrollWithHost()"), "the default walker's recovery call is unchanged")
        }
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val f = File(dir, relative)
            if (f.isFile) return f.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }
}
