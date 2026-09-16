package com.example.progmob_progetto_melluso.ui.onboarding

import com.example.progmob_progetto_melluso.core.AppPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the data of the first-run pages: which page asks for what.
 *
 * The API-gated groups (notifications, activity recognition) are empty on the
 * JVM, where the simulated SDK level is 0, so the tests verify the mapping and
 * the invariants that hold on every device rather than the platform gates.
 */
class OnboardingPageTest {

    /**
     * Verifies that the first and the last page ask for nothing: no system
     * dialog may interrupt the welcome or the closing page.
     */
    @Test
    fun welcomeAndPrivacyRequestNothing() {
        assertTrue(OnboardingPage.WELCOME.permissions.isEmpty())
        assertTrue(OnboardingPage.PRIVACY.permissions.isEmpty())
    }

    /**
     * Verifies that the location page asks for both fine and coarse location,
     * which is the group the map, the path recording and the geofence need.
     */
    @Test
    fun locationPageRequestsTheSharedLocationGroup() {
        assertEquals(AppPermissions.location, OnboardingPage.LOCATION.permissions)
        assertEquals(2, OnboardingPage.LOCATION.permissions.size)
    }

    /**
     * Verifies that no page declares a permission set of its own: every group is
     * shared with [AppPermissions], so a permission is never listed twice or
     * forgotten in the manifest.
     */
    @Test
    fun pagesRequestOnlyGroupsOwnedByAppPermissions() {
        val groups = listOf(
            AppPermissions.location,
            AppPermissions.notifications,
            AppPermissions.activityRecognition
        )
        OnboardingPage.entries.forEach { page ->
            assertTrue(
                "${page.name} requests a permission set not owned by AppPermissions",
                page.permissions.isEmpty() || page.permissions in groups
            )
        }
    }

    /**
     * Verifies that every page is fully described, so a page can never be added
     * with a missing resource and render as an empty screen.
     */
    @Test
    fun everyPageIsFullyDescribed() {
        OnboardingPage.entries.forEach { page ->
            assertNotEquals("${page.name} has no title", 0, page.titleRes)
            assertNotEquals("${page.name} has no description", 0, page.descriptionRes)
            assertNotEquals("${page.name} has no illustration", 0, page.illustrationRes)
        }
    }
}
