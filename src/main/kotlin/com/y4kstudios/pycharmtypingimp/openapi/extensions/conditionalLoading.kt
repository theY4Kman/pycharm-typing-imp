package com.y4kstudios.pycharmtypingimp.openapi.extensions

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.BuildNumber

/**
 * Raise ExtensionNotApplicableException to cancel loading of an Extension (i.e. Extension Point
 * implementation) if the test returns true.
 */
internal fun notApplicableIf(test: () -> Boolean) {
    if (test()) {
        throw ExtensionNotApplicableException.create()
    }
}

/**
 * Cancel loading an Extension if the Application version passes some condition
 */
internal fun notApplicableIfAppVersion(test: (appVersion: BuildNumber) -> Boolean) {
    val appVersion = ApplicationInfo.getInstance().build
    notApplicableIf { test(appVersion) }
}

/**
 * Cancel loading an Extension if the Application's baseline version (e.g. 213 or 222) and
 * optional build number (e.g. 5397) pass some condition.
 */
internal fun notApplicableIfBuild(test: (baselineVersion: Int, buildNumber: Int?) -> Boolean) {
    notApplicableIfAppVersion { appVersion ->
        val buildNumber = appVersion.components.getOrNull(1)
        test(appVersion.baselineVersion, buildNumber)
    }
}

/**
 * Cancel loading an Extension if the Application's baseline version (e.g. 213 or 222) and
 * build number (e.g. 5397) pass some condition. If no build number can be determined, assume the
 * Extension *should* be loaded.
 */
internal fun notApplicableOnlyIfBuild(test: (baselineVersion: Int, buildNumber: Int) -> Boolean) {
    notApplicableIfBuild { baselineVersion, buildNumber ->
        buildNumber != null && test(baselineVersion, buildNumber)
    }
}
