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

    @Throws(IOException::class)
    fun validate(hostSystem: Int, externalAttributes: Long, name: String) {
        if (hostSystem != UNIX_HOST_SYSTEM) return

        val unixMode = ((externalAttributes ushr 16) and 0xffffL).toInt()
        val fileType = unixMode and TYPE_MASK
        if (fileType == 0) return

        val directoryPath = name.endsWith("/")
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