package com.appcommander.mobile.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Streaming integrity check for App Commander APK bundle ZIPs opened via SAF.
 * Never extracts or executes APK entries. SHA-256 is an integrity comparison,
 * NOT APK signing verification or a guarantee of package authenticity.
 *
 * ZIP local entries, CRCs and the central directory are checked independently.
 * Only canonical App Commander ZIP32 exports are accepted; this is not a
 * general-purpose ZIP security scanner or an APK signing verifier.
 */
internal object InstalledApkBundleVerifier {
    private const val MAX_MANIFEST_BYTES = 16 * 1024
    private val header = Regex(
        """\{"format":"app-commander-apk-bundle-v1","packageName":"([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+)","containsAppData":false,"signatureVerified":false,"entries":\[(.*)]}""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val record = Regex(
        """\{"name":"(base\.apk|split_[0-9]{3}\.apk)","size":([1-9][0-9]*),"sha256":"([a-f0-9]{64})"}""",
    )
    private val discard = object : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    /**
     * App Commander writes APK entries with DEFLATE level 0, so legitimate
     * exports do not expand substantially. Bound inflation WHILE reading,
     * before ZIP central metadata is available. A forged, highly compressed
     * APK entry must not consume hundreds of MiB of CPU work just to be
     * rejected by the canonical-layout check at the end.
     *
     * Track raw input per entry rather than since the start of the ZIP: a
     * compressed later split must not borrow the raw-byte budget of base.apk.
     * The 1 MiB slack covers inflater read-ahead and tiny metadata entries.
     * This is intentionally NOT a general-purpose ZIP reader.
     */
    private class BoundedEntryStream(
        private val zip: ZipInputStream,
        private val tracking: ZipBundleStructure.TrackingStream,
    ) : InputStream() {
        private val rawStart = tracking.bytesRead
        private var expanded = 0L

        override fun read(): Int {
            val value = zip.read()
            if (value >= 0) account(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = zip.read(buffer, offset, length)
            if (count > 0) account(count)
            return count
        }

        private fun account(count: Int) {
            expanded += count
            val raw = tracking.bytesRead - rawStart
            if (raw < 0 || expanded > 1024L * 1024 + 16L * raw) {
                throw IOException("APK bundle entry expands beyond canonical ZIP budget")
            }
        }
    }

    data class Result(
        val packageName: String,
        val entries: List<InstalledApkBundleExport.EntryDigest>,
        val totalApkBytes: Long,
    )

    @Throws(IOException::class)
    fun verify(source: InputStream): Result {
        val actual = ArrayList<InstalledApkBundleExport.EntryDigest>()
        var total = 0L
        var manifest: String? = null
        val localEntries = ArrayList<ZipBundleStructure.LocalEntry>()
        val tracking = ZipBundleStructure.TrackingStream(source)
        var nextLocalOffset = 0L
        ZipInputStream(tracking).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val localHeader = ZipBundleStructure.captureLocalEntry(tracking, nextLocalOffset, entry)
                if (entry.isDirectory || manifest != null) throw IOException("Unexpected bundle ZIP entry")
                if (entry.name == "manifest.json") {
                    if (actual.isEmpty()) throw IOException("Bundle has no APK entries")
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var zeroReads = 0
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        if (count == 0) {
                            if (++zeroReads >= 8) throw IOException("Bundle manifest stream stalled")
                            continue
                        }
                        zeroReads = 0
                        if (count > MAX_MANIFEST_BYTES - output.size()) {
                            throw IOException("Bundle manifest exceeds safe limit")
                        }
                        output.write(buffer, 0, count)
                    }
                    manifest = decodeStrictUtf8(output.toByteArray(), "APK bundle manifest")
                } else {
                    if (actual.size >= InstalledApkBundleExport.MAX_APK_COUNT) {
                        throw IOException("Too many APK bundle entries")
                    }
                    val expectedName = if (actual.isEmpty()) "base.apk" else
                        "split_" + actual.size.toString().padStart(3, '0') + ".apk"
                    if (entry.name != expectedName) throw IOException("Unexpected APK bundle entry name")
                    val copied = ApkCacheCopy.copyWithSha256(
                        BoundedEntryStream(zip, tracking), discard, ApkCacheCopy.MAX_APK_BYTES,
                    )
                    if (copied.sizeBytes > InstalledApkBundleExport.MAX_TOTAL_BYTES - total) {
                        throw IOException("Bundle exceeds total size limit")
                    }
                    total += copied.sizeBytes
                    actual.add(InstalledApkBundleExport.EntryDigest(entry.name, copied.sizeBytes, copied.sha256))
                }
                zip.closeEntry() // Checks CRC when present; never extracts files.
                if (entry.size < 0 || entry.compressedSize < 0 || entry.crc < 0) {
                    throw IOException("Incomplete ZIP local entry metadata")
                }
                val completed = localHeader.copy(
                    size = entry.size,
                    compressedSize = entry.compressedSize,
                    crc = entry.crc,
                )
                localEntries.add(completed)
                // App Commander writes DEFLATED entries with a signed 16-byte
                // data descriptor because general-purpose bit 3 is enabled.
                nextLocalOffset = completed.offset + completed.headerLength +
                    completed.compressedSize + 16L
            }
            // ZipInputStream stops at the first central-directory record. Read
            // the remaining raw bytes, then compare the bounded central index
            // against the already checked local entries and reject truncation.
            tracking.drain()
            ZipBundleStructure.validate(tracking.finalBytes(), tracking.bytesRead, localEntries)
        }
        val manifestText = manifest ?: throw IOException("Missing APK bundle manifest")
        val match = header.matchEntire(manifestText) ?: throw IOException("Invalid APK bundle manifest")
        val entryText = match.groupValues[2]
        val matches = record.findAll(entryText).toList()
        if (matches.size != actual.size || matches.joinToString(",") { it.value } != entryText) {
            throw IOException("APK bundle manifest entries do not match")
        }
        matches.forEachIndexed { index, item ->
            val expected = actual[index]
            val bytes = item.groupValues[2].toLongOrNull()
            if (item.groupValues[1] != expected.name || bytes != expected.bytes ||
                item.groupValues[3] != expected.sha256
            ) throw IOException("APK bundle checksum mismatch")
        }
        return Result(match.groupValues[1], actual, total)
    }
}
