package com.eliadca.talks.notes

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.*
import com.eliadca.talks.ui.notes.CitationPosition
import org.junit.Assert.*
import org.junit.Test

class CitationPositionTest {
    @Test fun aPassageAppearsBesideItsCitationAndStaysInsideThePhoneOrTabletWindow() {
        val tablet = CitationPosition(Rect(100f, 120f, 180f, 145f)).calculatePosition(IntRect.Zero,
            IntSize(1200, 800), LayoutDirection.Ltr, IntSize(400, 300))
        assertEquals(IntOffset(198, 163), tablet)
        val phone = CitationPosition(Rect(350f, 720f, 375f, 740f)).calculatePosition(IntRect.Zero,
            IntSize(400, 800), LayoutDirection.Ltr, IntSize(360, 300))
        assertTrue(phone.x >= 0 && phone.x + 360 <= 400)
        assertTrue(phone.y >= 0 && phone.y + 300 <= 800)
        assertTrue(phone.y < 720)
    }
}
