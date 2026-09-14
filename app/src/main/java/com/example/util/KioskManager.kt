package com.example.util

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log

object KioskManager {

    private const val TAG = "KioskManager"

    /**
     * Initializes Kiosk Mode (Lock Task Mode) and applies strict device policies.
     * Ensures the guard cannot exit the application or access device settings.
     */
    fun enableKioskMode(activity: Activity) {
        val devicePolicyManager = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(activity, KioskModeReceiver::class.java)

        try {
            if (devicePolicyManager.isDeviceOwnerApp(activity.packageName)) {
                // Set packages allowed to enter lock task mode
                devicePolicyManager.setLockTaskPackages(componentName, arrayOf(activity.packageName))
                
                // Disable keyguard and status bar expansion
                devicePolicyManager.setKeyguardDisabled(componentName, true)
                devicePolicyManager.setStatusBarDisabled(componentName, true)

                // Restrict user from configuring settings or modifying accounts
                devicePolicyManager.addUserRestriction(componentName, UserManager.DISALLOW_FACTORY_RESET)
                devicePolicyManager.addUserRestriction(componentName, UserManager.DISALLOW_SAFE_BOOT)
                devicePolicyManager.addUserRestriction(componentName, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
                devicePolicyManager.addUserRestriction(componentName, UserManager.DISALLOW_ADD_USER)

                // Start Lock Task Mode
                activity.startLockTask()
                Log.d(TAG, "Kiosk Mode enabled successfully.")
            } else {
                Log.w(TAG, "App is not the Device Owner. Cannot enforce strict Kiosk policies. Starting standard LockTask.")
                // Attempt standard screen pinning as fallback
                activity.startLockTask()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling Kiosk Mode: \${e.message}", e)
        }
    }

    /**
     * Disables Kiosk Mode.
     * Used mainly for administrative maintenance.
     */
    fun disableKioskMode(activity: Activity) {
        val devicePolicyManager = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(activity, KioskModeReceiver::class.java)

        try {
            activity.stopLockTask()

            if (devicePolicyManager.isDeviceOwnerApp(activity.packageName)) {
                devicePolicyManager.setLockTaskPackages(componentName, arrayOf())
                devicePolicyManager.setKeyguardDisabled(componentName, false)
                devicePolicyManager.setStatusBarDisabled(componentName, false)
                
                devicePolicyManager.clearUserRestriction(componentName, UserManager.DISALLOW_FACTORY_RESET)
                devicePolicyManager.clearUserRestriction(componentName, UserManager.DISALLOW_SAFE_BOOT)
                Log.d(TAG, "Kiosk Mode disabled.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Kiosk Mode: \${e.message}", e)
        }
    }
}
