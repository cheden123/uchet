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
    fun parseCmInput_flagsMoreThan20m_butDoesNotReject() {
        assertFalse(parseCmInput("2000")!!.isSuspicious) // ровно 20 м — норма
        val big = parseCmInput("9630")!!                 // 96.3 м — лишняя цифра
        assertEquals(96.3, big.meters, 0.0)
        assertTrue(big.isSuspicious)
        assertFalse(parseCmInput("99", baseMeters = 10)!!.isSuspicious)
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
