package com.example.progmob_progetto_melluso.core

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the permission vocabulary shared by the first-run pages, the
 * map and the settings section.
 *
 * Why they matter: the settings section offers the very groups the onboarding
 * pages request, and the two must never disagree — a permission added in one
 * place and forgotten in the other would leave a feature impossible to enable
 * after a refusal during the first run.
 *
 * The API-gated groups (notifications, activity recognition) are empty on the
 * JVM, where the simulated SDK level is 0, so the tests verify the invariants
 * that hold on every device rather than the platform gates.
 */
class AppPermissionsTest {

    /**
     * Verifies that the named lists are the enum's lists, not copies of them:
     * this delegation is what keeps the first-run pages and the settings in
     * agreement.
     */
    @Test
    fun namedGroupsAreTheGroupVocabulary() {
        assertEquals(PermissionGroup.LOCATION.permissions, AppPermissions.location)
        assertEquals(PermissionGroup.ACTIVITY_RECOGNITION.permissions, AppPermissions.activityRecognition)
        assertEquals(PermissionGroup.NOTIFICATIONS.permissions, AppPermissions.notifications)
    }

    /**
     * Verifies that location is exactly fine plus coarse, the pair the map, the
     * path recording and the geofence are built on.
     */
    @Test
    fun locationGroupIsFineAndCoarse() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            PermissionGroup.LOCATION.permissions
        )
    }

    /**
     * Verifies that no permission belongs to two groups, so the settings section
     * can never ask for the same grant twice.
     */
    @Test
    fun groupsNeverShareAPermission() {
        val declared = PermissionGroup.entries.flatMap { group -> group.permissions }
        assertEquals(declared.size, declared.toSet().size)
    }

    /**
     * Verifies that "supported" means "has permissions on this platform", which
     * is what decides whether the settings section shows the row at all.
     */
    @Test
    fun supportedGroupsAreTheNonEmptyOnes() {
        PermissionGroup.entries.forEach { group ->
            assertEquals(group.permissions.isNotEmpty(), group.isSupported)
        }
        assertTrue(PermissionGroup.LOCATION.isSupported)
    }

    /**
     * Verifies the semantics the row relies on: satisfied means nothing is
     * missing, and a single missing permission is enough to offer the button.
     */
    @Test
    fun statusIsSatisfiedOnlyWhenNothingIsMissing() {
        assertTrue(PermissionStatus(PermissionGroup.LOCATION, emptyList()).isSatisfied)
        assertFalse(
            PermissionStatus(
                PermissionGroup.LOCATION,
                listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            ).isSatisfied
        )
    }
}
