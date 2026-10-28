package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test

class InstalledApkBundleExportTest {
    private fun source(name: String, bytes: ByteArray) =
        InstalledApkBundleExport.Source(name, bytes.size.toLong()) { ByteArrayInputStream(bytes) }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertFalse("duplicate ZIP name", files.containsKey(entry.name))
                files[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return files
    }

    @Test fun exportsBaseAndEverySplitWithHashesAndManifest() {
        val base = ByteArray(131_079) { (it * 17).toByte() }
        val split1 = ByteArray(41_003) { (it * 29).toByte() }
        val split2 = byteArrayOf(0, 1, -1, 23, 99)
        val output = ByteArrayOutputStream()
        val result = InstalledApkBundleExport.write(
            "com.example.app",
            listOf(source("base.apk", base), source("split_001.apk", split1), source("split_002.apk", split2)),
            output,
        )
        val files = unzip(output.toByteArray())
        assertEquals(setOf("base.apk", "split_001.apk", "split_002.apk", "manifest.json"), files.keys)
        assertArrayEquals(base, files["base.apk"])
        assertArrayEquals(split1, files["split_001.apk"])
        assertArrayEquals(split2, files["split_002.apk"])
        assertEquals(3, result.entries.size)
        assertEquals((base.size + split1.size + split2.size).toLong(), result.totalApkBytes)
        val manifest = String(files.getValue("manifest.json"), Charsets.UTF_8)
        assertTrue(manifest.contains("\"packageName\":\"com.example.app\""))
        assertTrue(manifest.contains("\"containsAppData\":false"))
        assertTrue(manifest.contains("\"signatureVerified\":false"))
        for ((index, bytes) in listOf(base, split1, split2).withIndex()) {
            assertEquals(sha(bytes), result.entries[index].sha256)
            assertTrue(manifest.contains("\"sha256\":\"${sha(bytes)}\""))
        }
    }

    @Test fun baseOnlyBundleStillCarriesManifest() {
        val output = ByteArrayOutputStream()
        val result = InstalledApkBundleExport.write(
            "com.example.single", listOf(source("base.apk", byteArrayOf(1, 2))), output,
        )
        assertEquals(1, result.entries.size)
        assertEquals(setOf("base.apk", "manifest.json"), unzip(output.toByteArray()).keys)
    }

    @Test fun rejectsInvalidNamesAndLimitsBeforeOpeningAnyInputOrOutput() {
        var opened = 0
        fun unopened(name: String, size: Long) = InstalledApkBundleExport.Source(name, size) {
            opened++
            ByteArrayInputStream(byteArrayOf(1))
        }
        val invalid = listOf(
            listOf(unopened("../base.apk", 1)),
            listOf(unopened("base.apk", 1), unopened("split_003.apk", 1)),
            listOf(unopened("base.apk", 0)),
            listOf(unopened("base.apk", ApkCacheCopy.MAX_APK_BYTES + 1)),
            emptyList(),
        )
        for (sources in invalid) {
            val output = ByteArrayOutputStream()
            assertThrows(IOException::class.java) {
                InstalledApkBundleExport.write("com.example.app", sources, output)
            }
            assertEquals(0, output.size())
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.write(
                "bad/package", listOf(unopened("base.apk", 1)), ByteArrayOutputStream(),
            )
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.write(
                "com.example.app",
                (0 until 65).map { index ->
                    unopened(if (index == 0) "base.apk" else "split_${index.toString().padStart(3, '0')}.apk", 1)
                },
                ByteArrayOutputStream(),
            )
        }
        assertEquals(0, opened)
    }

    @Test fun rejectsAggregateTwoGibLimitBeforeReadingSources() {
        var reads = 0
        val sources = (0 until 5).map { index ->
            InstalledApkBundleExport.Source(
                if (index == 0) "base.apk" else "split_${index.toString().padStart(3, '0')}.apk",
                ApkCacheCopy.MAX_APK_BYTES,
            ) {
                reads++
                ByteArrayInputStream(byteArrayOf(1))
            }
        }
        val destination = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.write("com.example.app", sources, destination)
        }
        assertEquals(0, reads)
        assertEquals(0, destination.size())
    }

    @Test fun detectsTruncatedAndGrowingInstalledApks() {
        val truncated = InstalledApkBundleExport.Source("base.apk", 4) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }
        val grown = InstalledApkBundleExport.Source("base.apk", 2) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }
        for (source in listOf(truncated, grown)) {
            assertThrows(IOException::class.java) {
                InstalledApkBundleExport.write("com.example.app", listOf(source), ByteArrayOutputStream())
            }
        }
    }

    @Test fun sourceOrDestinationFailurePropagatesForSafCleanup() {
        val failingSource = InstalledApkBundleExport.Source("base.apk", 2) {
            throw IOException("source denied")
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.write("com.example.app", listOf(failingSource), ByteArrayOutputStream())
        }
        val badOutput = object : OutputStream() {
            override fun write(b: Int) { throw IOException("SAF provider refused write") }
            override fun write(b: ByteArray, off: Int, len: Int) {
                throw IOException("SAF provider refused write")
            }
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.write(
                "com.example.app", listOf(source("base.apk", byteArrayOf(1, 2, 3))), badOutput,
            )
        }
    }

    @Test fun stalledInputFailsWithoutInfiniteLoop() {
        val stalled = InstalledApkBundleExport.Source("base.apk", 1) {
            object : InputStream() {
                override fun read(): Int = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int = 0
            }
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.write("com.example.app", listOf(stalled), ByteArrayOutputStream())
        }
    }

    @Test fun exportedSafDocumentIsReopenedAfterCloseAndVerified() {
        var stored = ByteArrayOutputStream()
        var closed = false
        var reads = 0
        val base = byteArrayOf(1, 2, 3, 4, 5)
        val split = byteArrayOf(6, 7, 8)
        val result = InstalledApkBundleExport.writeAndVerify(
            "com.example.app",
            listOf(source("base.apk", base), source("split_001.apk", split)),
            openForWrite = {
                object : OutputStream() {
                    override fun write(b: Int) { stored.write(b) }
                    override fun write(b: ByteArray, off: Int, len: Int) { stored.write(b, off, len) }
                    override fun close() { closed = true }
                }
            },
            openForRead = {
                assertTrue("Must close the SAF output before reopening", closed)
                reads++
                ByteArrayInputStream(stored.toByteArray())
            },
        )
        assertEquals(1, reads)
        assertEquals(2, result.entries.size)
        assertEquals(8L, result.totalApkBytes)
    }

    @Test fun silentlyTruncatedSafDocumentCannotBeReportedAsExported() {
        var saved = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.writeAndVerify(
                "com.example.app",
                listOf(source("base.apk", byteArrayOf(1, 2, 3))),
                openForWrite = { saved.also { it.reset() } },
                openForRead = {
                    val bytes = saved.toByteArray()
                    ByteArrayInputStream(bytes.copyOf(bytes.size - 22))
                },
            )
        }
    }

    @Test fun silentlyModifiedSafDocumentCannotBeReportedAsExported() {
        val saved = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.writeAndVerify(
                "com.example.app",
                listOf(source("base.apk", byteArrayOf(1, 2, 3))),
                openForWrite = { saved },
                openForRead = {
                    val bytes = saved.toByteArray()
                    // Corrupt local ZIP payload/header without updating the index.
                    bytes[0] = (bytes[0].toInt() xor 1).toByte()
                    ByteArrayInputStream(bytes)
                },
            )
        }
    }

    @Test fun providerRefusingReadbackFailsClosed() {
        val saved = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.writeAndVerify(
                "com.example.app",
                listOf(source("base.apk", byteArrayOf(1))),
                openForWrite = { saved },
                openForRead = { throw IOException("SAF provider is write-only") },
            )
        }
    }

    @Test fun writeFailureNeverAttemptsReadback() {
        var readback = false
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.writeAndVerify(
                "com.example.app",
                listOf(source("base.apk", byteArrayOf(1, 2))),
                openForWrite = {
                    object : OutputStream() {
                        override fun write(b: Int) { throw IOException("SAF disk full") }
                    }
                },
                openForRead = {
                    readback = true
                    ByteArrayInputStream(byteArrayOf())
                },
            )
        }
        assertFalse(readback)
    }


    @Test fun rejectsInvalidBundleMetadataBeforeOpeningSafDestination() {
        val invalidInputs = listOf(
            "bad/package" to listOf(source("base.apk", byteArrayOf(1))),
            "com.example.app" to listOf(source("../base.apk", byteArrayOf(1))),
            "com.example.app" to listOf(
                InstalledApkBundleExport.Source("base.apk", 0) { ByteArrayInputStream(byteArrayOf()) },
            ),
        )
        for ((packageName, sources) in invalidInputs) {
            var writes = 0
            var reads = 0
            assertThrows(IOException::class.java) {
                InstalledApkBundleExport.writeAndVerify(
                    packageName,
                    sources,
                    openForWrite = {
                        writes++
                        ByteArrayOutputStream()
                    },
                    openForRead = {
                        reads++
                        ByteArrayInputStream(byteArrayOf())
                    },
                )
            }
            assertEquals("invalid metadata must not create/truncate a SAF document", 0, writes)
            assertEquals("invalid metadata must not trigger readback", 0, reads)
        }
    }


    @Test fun bundleReadbackFailureTriggersCleanupAndPreservesReadbackError() {
        val saved = ByteArrayOutputStream()
        var deletes = 0
        val thrown = assertThrows(IOException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = { deletes++; true },
                write = {
                    InstalledApkBundleExport.writeAndVerify(
                        "com.example.app",
                        listOf(source("base.apk", byteArrayOf(1, 2, 3))),
                        openForWrite = { saved },
                        openForRead = { throw IOException("SAF provider denied readback") },
                    )
                },
            )
        }
        assertEquals("SAF provider denied readback", thrown.message)
        assertEquals("The newly created SAF document must be cleaned exactly once", 1, deletes)
    }

    @Test fun bundleReadbackMismatchStillReportsIntegrityFailureWhenCleanupIsDenied() {
        val saved = ByteArrayOutputStream()
        var deletes = 0
        val thrown = assertThrows(IOException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = {
                    deletes++
                    throw SecurityException("provider denied deletion")
                },
                write = {
                    InstalledApkBundleExport.writeAndVerify(
                        "com.example.app",
                        listOf(source("base.apk", byteArrayOf(1, 2, 3))),
                        openForWrite = { saved },
                        openForRead = {
                            val bytes = saved.toByteArray()
                            ByteArrayInputStream(bytes.copyOf(bytes.size - 22))
                        },
                    )
                },
            )
        }
        assertEquals("Cleanup is best-effort and must run exactly once", 1, deletes)
        assertFalse("Cleanup error must not replace the archive verification failure",
            thrown.message.orEmpty().contains("provider denied deletion"))
    }

    @Test fun bundleVerifierFailsClosedWhenProviderRepeatedlyReturnsZeroBytes() {
        val archive = ByteArrayOutputStream().also { output ->
            InstalledApkBundleExport.write(
                "com.example.app",
                listOf(source("base.apk", byteArrayOf(1, 2, 3, 4))),
                output,
            )
        }.toByteArray()
        val delegate = ByteArrayInputStream(archive)
        var zeroReads = 0
        val brokenProvider = object : java.io.InputStream() {
            override fun read(): Int = delegate.read()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (length > 0 && zeroReads < 8) {
                    zeroReads++
                    return 0
                }
                return delegate.read(buffer, offset, length)
            }
        }

        val error = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(brokenProvider)
        }
        assertTrue(error.message.orEmpty().contains("stalled"))
        assertEquals("Repeated zero-progress reads must be bounded", 8, zeroReads)
    }

    @Test fun writeAndVerifyUsesTheValidatedSnapshotIfProviderOpenerMutatesCallerList() {
        val base = ByteArray(4_097) { (it * 13).toByte() }
        val sources = mutableListOf(source("base.apk", base))
        val saved = ByteArrayOutputStream()

        val result = InstalledApkBundleExport.writeAndVerify(
            packageName = "com.example.snapshot",
            sources = sources,
            openForWrite = {
                // Simulate a callback that mutates the caller-owned collection
                // after validation but before the ZIP writer consumes it.
                sources.clear()
                saved
            },
            openForRead = { ByteArrayInputStream(saved.toByteArray()) },
        )

        assertTrue("the caller list was deliberately mutated", sources.isEmpty())
        assertEquals(listOf("base.apk"), result.entries.map { it.name })
        assertArrayEquals(base, unzip(saved.toByteArray()).getValue("base.apk"))
        assertEquals(sha(base), result.entries.single().sha256)
    }

    @Test fun writeUsesSnapshotWhenDestinationCallbackMutatesCallerList() {
        val base = ByteArray(2_049) { (it * 7).toByte() }
        val sources = mutableListOf(source("base.apk", base))
        val saved = ByteArrayOutputStream()
        var mutated = false
        val destination = object : OutputStream() {
            private fun mutateOnce() {
                if (!mutated) {
                    mutated = true
                    sources.clear()
                }
            }
            override fun write(b: Int) {
                mutateOnce()
                saved.write(b)
            }
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                mutateOnce()
                saved.write(buffer, offset, length)
            }
        }

        val result = InstalledApkBundleExport.write(
            "com.example.snapshot", sources, destination,
        )
        assertTrue("destination callback ran", mutated)
        assertTrue("the caller list was deliberately mutated", sources.isEmpty())
        assertEquals(listOf("base.apk"), result.entries.map { it.name })
        assertArrayEquals(base, unzip(saved.toByteArray()).getValue("base.apk"))
    }


    @Test fun cancellationDuringBundleWriteSkipsReadbackAndLeavesCleanupToCaller() {
        val cancelled = CancellationException("cancel bundle export")
        val destination = ByteArrayOutputStream()
        var checks = 0
        var readback = false
        var deletes = 0
        val thrown = assertThrows(CancellationException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = { deletes++; true },
            ) {
                InstalledApkBundleExport.writeAndVerify(
                    "com.example.app",
                    listOf(source("base.apk", ByteArray(128 * 1024) { it.toByte() })),
                    openForWrite = { destination },
                    openForRead = {
                        readback = true
                        ByteArrayInputStream(destination.toByteArray())
                    },
                    checkCancelled = {
                        checks++
                        if (checks == 4) throw cancelled
                    },
                )
            }
        }
        assertSame(cancelled, thrown)
        assertFalse("cancelled output must never be reported as verified", readback)
        assertEquals(1, deletes)
        assertFalse(InstalledApkExport.cleanupWasUnconfirmed(thrown))
    }


    @Test fun cancellationAfterBundleReadbackCloseTriggersScopedCleanup() {
        val base = byteArrayOf(3, 1, 4, 1, 5)
        val saved = ByteArrayOutputStream()
        var outputClosed = false
        var readbackClosed = false
        var deletes = 0
        val cancelled = CancellationException("cancel after bundle readback")
        val thrown = assertThrows(CancellationException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = { deletes++; true },
            ) {
                InstalledApkBundleExport.writeAndVerify(
                    "com.example.cancel",
                    listOf(source("base.apk", base)),
                    openForWrite = {
                        object : OutputStream() {
                            override fun write(b: Int) { saved.write(b) }
                            override fun write(b: ByteArray, off: Int, len: Int) { saved.write(b, off, len) }
                            override fun close() { outputClosed = true }
                        }
                    },
                    openForRead = {
                        assertTrue(outputClosed)
                        object : ByteArrayInputStream(saved.toByteArray()) {
                            override fun close() {
                                readbackClosed = true
                                super.close()
                            }
                        }
                    },
                    checkCancelled = {
                        if (readbackClosed) throw cancelled
                    },
                )
            }
        }
        assertSame(cancelled, thrown)
        assertTrue(readbackClosed)
        assertEquals("only the fresh partial SAF document is cleaned", 1, deletes)
        assertFalse(InstalledApkExport.cleanupWasUnconfirmed(thrown))
    }
}
