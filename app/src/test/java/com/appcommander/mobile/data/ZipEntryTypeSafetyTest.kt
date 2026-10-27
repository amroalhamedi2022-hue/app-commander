package com.appcommander.mobile.data

import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Test

class ZipEntryTypeSafetyTest {
    private fun attributes(mode: Int): Long = (mode.toLong() and 0xffffL) shl 16

    @Test fun acceptsUnixRegularFilesAndDirectoryPathsWhenTypesAgree() {
        ZipEntryTypeSafety.validate(3, attributes(0x8000 or 0x01a4), "base.apk")
        ZipEntryTypeSafety.validate(3, attributes(0x4000 or 0x01ed), "assets/")
    }

    @Test fun rejectsUnixSymlinksAndSpecialFilesystemObjects() {
        assertThrows(IOException::class.java) {
            ZipEntryTypeSafety.validate(3, attributes(0xa000 or 0x01ff), "base.apk")
        }
        for (mode in listOf(0x1000, 0x2000, 0x6000, 0xc000)) {
            assertThrows(IOException::class.java) {
                ZipEntryTypeSafety.validate(3, attributes(mode or 0x01a4), "payload.bin")
            }
        }
    }

    @Test fun rejectsFileTypeAndTrailingSlashMismatches() {
        assertThrows(IOException::class.java) {
            ZipEntryTypeSafety.validate(3, attributes(0x8000 or 0x01a4), "assets/")
        }
        assertThrows(IOException::class.java) {
            ZipEntryTypeSafety.validate(3, attributes(0x4000 or 0x01ed), "base.apk")
        }
    }

    @Test fun ignoresUnixModeBitsWhenHostSystemIsNotUnixOrTypeIsUnspecified() {
        ZipEntryTypeSafety.validate(0, attributes(0xa000 or 0x01ff), "base.apk")
        ZipEntryTypeSafety.validate(3, attributes(0x01a4), "base.apk")
    }
}