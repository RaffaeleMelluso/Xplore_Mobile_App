package com.example.progmob_progetto_melluso.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Single source of truth for the Android runtime permissions the app needs,
 * grouped by the feature that motivates them.
 *
 * How it works: every group is gated by API level, so a device that does not
 * have a permission never sees a request for it (POST_NOTIFICATIONS exists from
 * Android 13, ACTIVITY_RECOGNITION from Android 10). The first-run onboarding
 * asks for ONE group per page, right after the page that explains it, and the
 * rest of the app only queries the state through [hasLocation], [missing] and
 * [statuses], so the groups can never drift between callers.
 */
object AppPermissions {

    /** Location (fine and coarse): route recording, geofencing, nearby travellers. */
    val location: List<String> = PermissionGroup.LOCATION.permissions

    /**
     * Notifications: the foreground service notice and the nearby-place alert.
     * Empty before Android 13, where the permission does not exist.
     */
    val notifications: List<String> = PermissionGroup.NOTIFICATIONS.permissions

    /**
     * Body activity recognition, behind the step counter. Empty before Android
     * 10, where the step sensor needs no runtime permission.
     */
    val activityRecognition: List<String> = PermissionGroup.ACTIVITY_RECOGNITION.permissions

    /**
     * True when fine OR coarse location has been granted.
     *
     * Coarse is enough on purpose: the map, the path recording and the geofence
     * all work on an approximate fix, only with a wider radius.
     */
    fun hasLocation(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * The subset of [permissions] the user has not granted yet.
     *
     * Used to skip a request that would show no dialog at all (already granted)
     * and to keep the UI from promising one that will not appear.
     */
    fun missing(context: Context, permissions: List<String>): List<String> =
        permissions.filterNot { permission -> isGranted(context, permission) }

    /**
     * The state of every group, in the order the UI should offer them.
     *
     * Used by the settings section, which lets the user grant later what they
     * refused during the first run. Groups the platform does not have are
     * returned too: the caller filters them out with
     * [PermissionGroup.isSupported], so the list stays a faithful picture of the
     * vocabulary instead of depending on the device.
     */
    fun statuses(context: Context): List<PermissionStatus> =
        PermissionGroup.entries.map { group ->
            PermissionStatus(
                group = group,
                missing = missing(context, group.permissions)
            )
        }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * A group of runtime permissions together with the feature that needs them.
 *
 * Why it exists: the first-run pages, the map and the settings section must all
 * talk about the same three things. Keeping the permission lists here — and
 * exposing them through [AppPermissions] — means a new permission is declared
 * once and every caller picks it up.
 */
enum class PermissionGroup(
    /** The Android permissions this group asks for; empty when the platform has none. */
    val permissions: List<String>
) {

    /** Location: route recording, geofences, nearby travellers. */
    LOCATION(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    ),

    /** Body activity recognition, behind the step counter (Android 10 and later). */
    ACTIVITY_RECOGNITION(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            emptyList()
        }
    ),

    /** Notifications: the tracking notice and the nearby-place alert (Android 13 and later). */
    NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
    );

    /**
     * True when this platform actually has the permission, so the UI must offer
     * it. False for the groups that do not exist on an older Android version.
     */
    val isSupported: Boolean get() = permissions.isNotEmpty()
}

/**
 * Snapshot of one permission group, as read from the platform.
 *
 * @param group the group this snapshot describes.
 * @param missing the permissions of the group still not granted.
 */
data class PermissionStatus(
    val group: PermissionGroup,
    val missing: List<String>
) {

    /**
     * True when the group needs nothing from the user: every permission is
     * granted. A group the platform does not have also has nothing missing, so
     * callers must filter it out with [PermissionGroup.isSupported] before
     * reading this as "granted".
     */
    val isSatisfied: Boolean get() = missing.isEmpty()
}
