package com.appcommander.mobile.data

import java.io.IOException

/**
 * Applies the same host-aware ZIP entry-type policy to APKs and exported bundles.
 *
 * External attributes are host-system dependent. Interpret POSIX mode bits only
 * for Unix-created records; unknown/unspecified types remain compatible, while
 * links and special filesystem objects fail closed before a verifier reports
 * success. This is structural validation, not signature or malware verification.
 */
internal object ZipEntryTypeSafety {
    private const val UNIX_HOST_SYSTEM = 3
    private const val TYPE_MASK = 0xf000
    private const val REGULAR_FILE = 0x8000
    private const val DIRECTORY = 0x4000
    private const val SYMBOLIC_LINK = 0xa000
    private const val DOS_DIRECTORY = 0x10

    @Throws(IOException::class)
    fun validate(hostSystem: Int, externalAttributes: Long, name: String) {
        val directoryPath = name.endsWith("/")
        if (hostSystem != UNIX_HOST_SYSTEM) {
            // PKWARE ZIP APPNOTE maps the low byte to DOS attributes for
            // DOS/FAT host records. A set directory bit on a file path is a
            // contradiction. Do not require the bit for a directory path:
            // common ZIP writers mark directories by the trailing slash alone.
            val dosAttributes = (externalAttributes and 0xffL).toInt()
            val hasDosDirectoryBit = (dosAttributes and DOS_DIRECTORY) != 0
            if (hasDosDirectoryBit && !directoryPath) {
                throw IOException("ZIP DOS directory attribute disagrees with path")
            }
            return
        }

        val unixMode = ((externalAttributes ushr 16) and 0xffffL).toInt()
        val fileType = unixMode and TYPE_MASK
        if (fileType == 0) return

        when (fileType) {
            REGULAR_FILE -> if (directoryPath) {
                throw IOException("ZIP regular-file type disagrees with directory path")
            }
            DIRECTORY -> if (!directoryPath) {
                throw IOException("ZIP directory type lacks directory path")
            }
            SYMBOLIC_LINK -> throw IOException("ZIP symbolic-link entries are unsupported")
            else -> throw IOException("ZIP special filesystem entry types are unsupported")
        }
    }
}