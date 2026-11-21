package com.youfeng.autoadb.presentation

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources

/** Text comes from the module APK, using the system window configuration. */
internal fun Context.moduleResources(modulePackageName: String): Resources =
    createPackageContext(modulePackageName, 0)
        .createConfigurationContext(Configuration(resources.configuration)).resources
