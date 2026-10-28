package com.appcommander.mobile.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class VerifiedInstallProviderContractTest {
    @Test fun installerProviderIsPrivateAndOnlySharesVerifiedSnapshotDirectory() {
        val manifest = File("src/main/AndroidManifest.xml")
        val paths = File("src/main/res/xml/verified_apk_paths.xml")
        assertTrue(manifest.isFile)
        assertTrue(paths.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val doc = factory.newDocumentBuilder().parse(manifest)
        val providers = doc.getElementsByTagName("provider")
        assertEquals(1, providers.length)
        val provider = providers.item(0) as org.w3c.dom.Element
        val ns = "http://schemas.android.com/apk/res/android"
        assertEquals("androidx.core.content.FileProvider", provider.getAttributeNS(ns, "name"))
        assertEquals("false", provider.getAttributeNS(ns, "exported"))
        assertEquals("true", provider.getAttributeNS(ns, "grantUriPermissions"))
        assertEquals("$" + "{applicationId}.verifiedapks", provider.getAttributeNS(ns, "authorities"))

        val pathDoc = factory.newDocumentBuilder().parse(paths)
        val root = pathDoc.documentElement
        assertEquals("paths", root.tagName)
        val entries = root.childNodes
        val elementEntries = (0 until entries.length).mapNotNull { entries.item(it) as? org.w3c.dom.Element }
        assertEquals(1, elementEntries.size)
        assertEquals("cache-path", elementEntries.single().tagName)
        assertEquals("${ApkInstallSnapshot.DIRECTORY}/", elementEntries.single().getAttribute("path"))
    }
    @Test fun inspectorOwnsSnapshotUntilInstallerLaunchReturns() {
        val ui = File("src/main/java/com/appcommander/mobile/ui/AppCommanderApp.kt").readText()
        val repository = File("src/main/java/com/appcommander/mobile/data/AppRepository.kt").readText()
        assertTrue(repository.contains("internal data class VerifiedInstallSnapshot"))
        assertTrue(repository.contains("VerifiedInstallSnapshot(file, verifiedUri)"))
        assertTrue(repository.contains("discardVerifiedInstall(snapshot: VerifiedInstallSnapshot, failure: Throwable)"))
        assertTrue(ui.contains("VerifiedInstallHandoff<VerifiedInstallSnapshot>"))
        assertTrue(ui.contains(".also(handoff::record)"))
        assertTrue(ui.contains("handoff.discardIfNotTransferred("))
        val launch = ui.indexOf("context.startActivity(install)")
        val transfer = ui.indexOf("handoff.markTransferred()")
        assertTrue("ownership may transfer only after Android accepts the activity launch", launch >= 0 && transfer > launch)
    }

}
