package com.v2ray.ang.enums

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Permission types used in the app, handling API level differences.
 */
enum class PermissionType {
    /** Camera permission (used for scanning QR codes) */
    CAMERA {
        override fun getPermission(): String = Manifest.permission.CAMERA
    },

    /** Notification permission (Android 13+) */
    POST_NOTIFICATIONS {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun getPermission(): String = Manifest.permission.POST_NOTIFICATIONS
    },

    /** Local network access permission (Android 17+) */
    ACCESS_LOCAL_NETWORK {
        @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
        override fun getPermission(): String = Manifest.permission.ACCESS_LOCAL_NETWORK
    },

    /**
     * Location permission (used for the weather chip on the search bar).
     * Requests both coarse and fine together: requesting coarse alone is
     * unreliable on many OEMs (MIUI/HyperOS included) and FusedLocationProviderClient
     * tends to fail/return null far more often when only coarse is granted.
     */
    LOCATION {
        override fun getPermission(): String = Manifest.permission.ACCESS_FINE_LOCATION
        override fun getPermissions(): Array<String> = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    };

    /** Return the actual Android permission string */
    abstract fun getPermission(): String

    /** Return all permission strings to request for this type (defaults to a single permission) */
    open fun getPermissions(): Array<String> = arrayOf(getPermission())

    /** Return a human-readable label for the permission */
    fun getLabel(): String {
        return when (this) {
            CAMERA -> "Camera"
            POST_NOTIFICATIONS -> "Notification"
            ACCESS_LOCAL_NETWORK -> "Local Network"
            LOCATION -> "Location"
        }
    }
}