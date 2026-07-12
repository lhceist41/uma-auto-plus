package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TraineePositionStoreTest {
    @Test
    fun `parse and serialize round-trip`() {
        val body = "daiwascarlet=1,2,0\nsupercreek=0,4,1\nairgroove=2,0,1"
        val parsed = TraineePositionStore.parse(body)
        assertEquals(3, parsed.size)
        assertEquals("1,2,0", parsed["daiwascarlet"])
        assertEquals(TraineePositionStore.parse(TraineePositionStore.serialize(parsed)), parsed)
    }

    @Test
    fun `malformed lines are skipped without poisoning the rest`() {
        val body = "=1,2,0\njunk\nsupercreek=\n \nricesho wer=0,0,0\nsymbolirudolf=1,0,0"
        val parsed = TraineePositionStore.parse(body)
        // "ricesho wer" keeps its literal key (parse does not re-normalize), rudolf survives.
        assertEquals("1,0,0", parsed["symbolirudolf"])
        assertNull(parsed["supercreek"])
        assertNull(parsed[""])
    }

    @Test
    fun `later duplicate lines win`() {
        val parsed = TraineePositionStore.parse("supercreek=0,0,0\nsupercreek=1,3,1")
        assertEquals("1,3,1", parsed["supercreek"])
    }

    @Test
    fun `parseCell validates ranges against the grid bounds`() {
        assertEquals(TraineePositionStore.Cell(1, 2, 0), TraineePositionStore.parseCell("1,2,0", 8, 5, 2))
        assertEquals(TraineePositionStore.Cell(0, 0, 1), TraineePositionStore.parseCell(" 0 , 0 , 1 ", 8, 5, 2))
        assertNull(TraineePositionStore.parseCell(null, 8, 5, 2))
        assertNull(TraineePositionStore.parseCell("", 8, 5, 2))
        assertNull(TraineePositionStore.parseCell("1,2", 8, 5, 2))
        assertNull(TraineePositionStore.parseCell("1,2,0,4", 8, 5, 2))
        assertNull(TraineePositionStore.parseCell("9,0,0", 8, 5, 2)) // page past maxPage
        assertNull(TraineePositionStore.parseCell("0,5,0", 8, 5, 2)) // col == colCount
        assertNull(TraineePositionStore.parseCell("0,0,2", 8, 5, 2)) // row == rowCount
        assertNull(TraineePositionStore.parseCell("a,b,c", 8, 5, 2))
        assertNull(TraineePositionStore.parseCell("-1,0,0", 8, 5, 2))
    }

    @Test
    fun `serialize is sorted and stable`() {
        val s = TraineePositionStore.serialize(mapOf("b" to "1,1,1", "a" to "0,0,0"))
        assertEquals("a=0,0,0\nb=1,1,1", s)
    }
}
