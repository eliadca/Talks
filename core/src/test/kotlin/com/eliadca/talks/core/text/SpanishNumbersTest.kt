package com.eliadca.talks.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class SpanishNumbersTest {
    private fun say(n: Long) = SpanishNumbers.toWords(n).joinToString(" ")

    @Test fun smallNumbers() {
        assertEquals("cero", say(0))
        assertEquals("uno", say(1))
        assertEquals("quince", say(15))
        assertEquals("veintiuno", say(21))
        assertEquals("veintinueve", say(29))
        assertEquals("treinta", say(30))
        assertEquals("treinta y uno", say(31))
        assertEquals("noventa y nueve", say(99))
    }

    @Test fun hundreds() {
        assertEquals("cien", say(100))
        assertEquals("ciento uno", say(101))
        assertEquals("doscientos quince", say(215))
        assertEquals("quinientos", say(500))
        assertEquals("novecientos noventa y nueve", say(999))
    }

    @Test fun thousandsAndMillions() {
        assertEquals("mil", say(1000))
        assertEquals("mil quinientos", say(1500))
        assertEquals("dos mil veinticuatro", say(2024))
        assertEquals("treinta y cinco mil", say(35000))
        assertEquals("un millon", say(1_000_000))
        assertEquals("dos millones quinientos mil", say(2_500_000))
        assertEquals("mil novecientos ochenta y cinco", say(1985))
    }
}
