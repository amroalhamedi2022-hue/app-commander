package com.appcommander.mobile.ui

import com.appcommander.mobile.data.InstalledApkExport
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class SafExportFailureMessageTest {
    @Test fun baseApkCancellationShowsManualCleanupOnlyWhenUnconfirmed() {
        val cancelled = CancellationException("export cancelled")
        val thrown = assertThrows(CancellationException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = { false },
                write = { throw cancelled },
            )
        }

        assertSame(cancelled, thrown)
        assertNotNull(SafExportFailureMessage.baseApkCancellation(thrown))
        assertTrue(requireNotNull(SafExportFailureMessage.baseApkCancellation(thrown))
            .contains("لم يتأكد حذف الملف الجزئي"))

        val cleanCancellation = CancellationException("cancelled before a partial file existed")
        assertTrue(requireNotNull(SafExportFailureMessage.baseApkCancellation(cleanCancellation))
            .contains("لم يُعتمد الملف كنسخة مكتملة"))
    }

    @Test fun bundleCancellationKeepsCancellationAndReportsUnconfirmedZipCleanup() {
        val cancelled = CancellationException("bundle export cancelled")
        val thrown = assertThrows(CancellationException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = { throw SecurityException("provider denied delete") },
                write = { throw cancelled },
            )
        }

        assertSame(cancelled, thrown)
        val message = SafExportFailureMessage.bundleCancellation(thrown)
        assertNotNull(message)
        assertTrue(requireNotNull(message).contains("لم يتأكد حذف ملف ZIP الجزئي"))
        assertTrue(InstalledApkExport.cleanupWasUnconfirmed(thrown))
    }

    @Test fun ordinaryFailuresKeepDistinctBaseAndBundleCleanupMessages() {
        val unconfirmed = IOException("readback failed")
        val thrown = assertThrows(IOException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = { false },
                write = { throw unconfirmed },
            )
        }
        assertTrue(SafExportFailureMessage.baseApkFailure(thrown).contains("احذفه يدويًا"))
        assertTrue(SafExportFailureMessage.bundleFailure(thrown).contains("ملف ZIP الجزئي"))

        val confirmed = IOException("copy failed")
        val confirmedThrown = assertThrows(IOException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = { true },
                write = { throw confirmed },
            )
        }
        assertTrue(SafExportFailureMessage.baseApkFailure(confirmedThrown).contains("تم تأكيد حذف"))
        assertTrue(SafExportFailureMessage.bundleFailure(confirmedThrown).contains("تم تأكيد حذف"))
    }

    @Test fun confirmedBundleCancellationStillExplainsThatNoCompleteArchiveWasReported() {
        val cancelled = CancellationException("cancelled")
        val thrown = assertThrows(CancellationException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = { true },
                write = { throw cancelled },
            )
        }
        assertTrue(SafExportFailureMessage.bundleCancellation(thrown)
            .contains("لم يُعتمد ملف ZIP كنسخة مكتملة"))
    }

}
