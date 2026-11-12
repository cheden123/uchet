package com.uchet.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun parseCmInput_normalAndBaseModes() {
        assertEquals(9.63, parseCmInput("963")!!.meters, 0.0)
        assertEquals(9.63, parseCmInput("63", baseMeters = 9)!!.meters, 0.0)
        assertEquals(10.05, parseCmInput("05", baseMeters = 10)!!.meters, 0.0)
        assertEquals(0.35, parseCmInput("35")!!.meters, 0.0)
    }

    @Test
    fun parseCmInput_rejectsGarbageAndZero() {
        assertNull(parseCmInput(""))
        assertNull(parseCmInput("abc"))
        assertNull(parseCmInput("0"))
        assertNull(parseCmInput("99999999999")) // переполнение Int
    }

    @Test
    fun parseCmInput_flagsMoreThan15m_butDoesNotReject() {
        assertFalse(parseCmInput("1500")!!.isSuspicious) // ровно 15 м — норма
        assertTrue(parseCmInput("1501")!!.isSuspicious)
        val big = parseCmInput("9630")!!                 // 96.3 м — лишняя цифра
        assertEquals(96.3, big.meters, 0.0)
        assertTrue(big.isSuspicious)
        assertFalse(parseCmInput("99", baseMeters = 14)!!.isSuspicious)
        assertEquals(7.05, parseCmInput("05", baseMeters = 7)!!.meters, 0.0)
        assertEquals(12.4, parseCmInput("40", baseMeters = 12)!!.meters, 0.0)
    }

    @Test
    fun isCompleteCmInput_autoAddWhenUnambiguous() {
        assertTrue(isCompleteCmInput("638"))   // 6.38 м — сразу
        assertTrue(isCompleteCmInput("963"))
        assertTrue(isCompleteCmInput("1063"))  // 10.63 м — на 4-й цифре
        assertTrue(isCompleteCmInput("1634"))  // 16.34 м — добавится через подтверждение
        assertFalse(isCompleteCmInput("105"))  // 1.05 или 10.5x — ждём
        assertFalse(isCompleteCmInput("63"))
        assertFalse(isCompleteCmInput("063"))
        assertFalse(isCompleteCmInput("0638"))
        assertFalse(isCompleteCmInput(""))
    }

    @Test
    fun formatMeters_valueOf_roundsDecimalHalfUp() {
        assertEquals("9.63", formatMeters(9.63))
        assertEquals("10", formatMeters(10.0))
        assertEquals("0.3", formatMeters(0.1 + 0.2))
        // BigDecimal(1.0005) = 1.000499999… -> было "1"; valueOf -> "1.001".
        assertEquals("1.001", formatMeters(1.0005))
        assertEquals("0", formatMeters(Double.NaN))
    }
}
