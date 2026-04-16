package com.steve1316.uma_android_automation

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.common.ReleaseLevel
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactNativeHost
import com.google.mlkit.common.MlKit
import expo.modules.ApplicationLifecycleDispatcher
import expo.modules.ReactNativeHostWrapper

class MainApplication : Application(), ReactApplication {
    override val reactNativeHost: ReactNativeHost =
        ReactNativeHostWrapper(
            this,
            object : DefaultReactNativeHost(this) {
                override fun getPackages(): List<ReactPackage> =
                    PackageList(this).packages.apply {
                        // Packages that cannot be autolinked yet can be added manually here, for example:
                        // add(MyReactNativePackage())
                        add(StartPackage())
                    }

                override fun getJSMainModuleName(): String = "index"

                override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

                override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
            },
        )

    override val reactHost: ReactHost get() = ReactNativeHostWrapper.createReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        super.onCreate()

        // Initialize ML Kit explicitly. We removed the auto-init ContentProvider
        // in AndroidManifest.xml (tools:node="remove") because under certain
        // Android process-restart conditions on emulators, the static
        // MlKitContext from the dying process incarnation lingers into the
        // new one — and the ContentProvider's attachInfo() then crashes with
        // an uncatchable "IllegalStateException: MlKitContext is already
        // initialized" before this onCreate even runs. Initializing here
        // puts the call inside user code where we CAN catch that case, so a
        // stale static just means "reuse what's there" instead of killing
        // the new process within ~2 seconds.
        //
        // Must run before anything that uses ML Kit APIs (CustomImageUtils
        // uses InputImage for OCR), which is why this is the very first
        // statement after super.onCreate().
        try {
            MlKit.initialize(this)
        } catch (e: IllegalStateException) {
            // Static context from prior process incarnation is still populated.
            // ML Kit is already usable — just log and move on.
            Log.i("MainApplication", "[MLKIT] Context already initialized (reusing prior state): ${e.message}")
        }

        try {
            DefaultNewArchitectureEntryPoint.releaseLevel = ReleaseLevel.valueOf(BuildConfig.REACT_NATIVE_RELEASE_LEVEL.uppercase())
        } catch (e: IllegalArgumentException) {
            DefaultNewArchitectureEntryPoint.releaseLevel = ReleaseLevel.STABLE
        }
        loadReactNative(this)
        ApplicationLifecycleDispatcher.onApplicationCreate(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ApplicationLifecycleDispatcher.onConfigurationChanged(this, newConfig)
    }
}
