package com.eliadca.talks

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.LibVosk
import org.vosk.LogLevel

/** The offline engine ships native code; make sure it loads on a real Android runtime. */
@RunWith(AndroidJUnit4::class)
class NativeLibrariesTest {
    @Test
    fun voskNativeLibraryLoads() {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
    }
}
