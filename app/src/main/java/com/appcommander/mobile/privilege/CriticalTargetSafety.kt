package com.appcommander.mobile.privilege

/**
 * A privileged destructive command must never run when the device's launcher
 * or active keyboard cannot be identified. All HOME candidates are protected,
 * not only the current preferred launcher, to avoid leaving the user stranded.
 */
internal object CriticalTargetSafety {
    fun reason(packageName: String, homePackages: Set<String>?, keyboardPackage: String?): String? {
        if (homePackages.isNullOrEmpty()) return "Cannot verify home launcher packages"
        if (keyboardPackage.isNullOrBlank()) return "Cannot verify active keyboard"
        if (packageName in homePackages) return "Home launcher protected"
        if (packageName == keyboardPackage) return "Active keyboard protected"
        return null
    }
}
