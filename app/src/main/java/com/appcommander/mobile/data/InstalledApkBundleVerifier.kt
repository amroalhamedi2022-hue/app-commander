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
        ZipInputStream(tracking).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
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
                    manifest = output.toString(Charsets.UTF_8.name())
                } else {
                    if (actual.size >= InstalledApkBundleExport.MAX_APK_COUNT) {
                        throw IOException("Too many APK bundle entries")
                    }
                    val expectedName = if (actual.isEmpty()) "base.apk" else
                        "split_" + actual.size.toString().padStart(3, '0') + ".apk"
                    if (entry.name != expectedName) throw IOException("Unexpected APK bundle entry name")
                    val copied = ApkCacheCopy.copyWithSha256(zip, discard, ApkCacheCopy.MAX_APK_BYTES)
                    if (copied.sizeBytes > InstalledApkBundleExport.MAX_TOTAL_BYTES - total) {
                        throw IOException("Bundle exceeds total size limit")
                    }
                    total += copied.sizeBytes
                    actual.add(InstalledApkBundleExport.EntryDigest(entry.name, copied.sizeBytes, copied.sha256))
                }
                zip.closeEntry() // Checks CRC when present; never extracts files.
                localEntries.add(ZipBundleStructure.LocalEntry(
                    entry.name, entry.size, entry.compressedSize, entry.crc,
                ))
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
