package com.appcommander.mobile.privilege

/**
 * A component class may be implemented in a library's Java namespace, not
 * necessarily under the APK package name. Only the target APK's manifest
 * component inventory can establish that it belongs to that package.
 *
 * The caller must obtain declared names from PackageManager for the target
 * package (activities, services, receivers and providers), failing closed if
 * that query cannot be completed.
 */
internal object DeclaredComponentSafety {
    fun isDeclared(packageName: String, className: String, declaredNames: Collection<String>): Boolean {
        val expanded = if (className.startsWith(".")) packageName + className else className
        return expanded in declaredNames
    }
}
