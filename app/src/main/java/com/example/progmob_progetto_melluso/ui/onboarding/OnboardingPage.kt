package com.example.progmob_progetto_melluso.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.core.AppPermissions

/**
 * The pages of the first-run onboarding, in swipe order.
 *
 * How it works: a page is pure DATA — headline, explanation, cartoon
 * illustration, an optional note and the permission group it asks for — so the
 * screen stays a dumb renderer and adding or reordering a page is a single
 * change here. The system dialog is requested page by page, right after the page
 * that explains the reason for it, so a permission is never asked out of
 * context; the pages that ask for nothing (the welcome and the privacy one) just
 * move on.
 */
enum class OnboardingPage(
    /** Headline shown under the illustration. */
    @param:StringRes val titleRes: Int,
    /** Body text: what the feature gives the user and why it needs a permission. */
    @param:StringRes val descriptionRes: Int,
    /** Centered cartoon illustration. */
    @param:DrawableRes val illustrationRes: Int,
    /** Optional extra line under the body text (the fine print). */
    @param:StringRes val noteRes: Int? = null,
    /**
     * The permissions requested when the user confirms this page. Empty for the
     * pages that request nothing; groups always come from [AppPermissions], so
     * the same permission is never declared twice.
     */
    val permissions: List<String> = emptyList()
) {
    WELCOME(
        titleRes = R.string.onboarding_welcome_title,
        descriptionRes = R.string.onboarding_welcome_body,
        illustrationRes = R.drawable.ic_onboarding_welcome
    ),
    LOCATION(
        titleRes = R.string.onboarding_location_title,
        descriptionRes = R.string.onboarding_location_body,
        illustrationRes = R.drawable.ic_onboarding_location,
        noteRes = R.string.onboarding_location_note,
        permissions = AppPermissions.location
    ),
    STEPS(
        titleRes = R.string.onboarding_steps_title,
        descriptionRes = R.string.onboarding_steps_body,
        illustrationRes = R.drawable.ic_onboarding_steps,
        noteRes = R.string.onboarding_steps_note,
        permissions = AppPermissions.activityRecognition
    ),
    NOTIFICATIONS(
        titleRes = R.string.onboarding_notifications_title,
        descriptionRes = R.string.onboarding_notifications_body,
        illustrationRes = R.drawable.ic_onboarding_notifications,
        noteRes = R.string.onboarding_notifications_note,
        permissions = AppPermissions.notifications
    ),
    PRIVACY(
        titleRes = R.string.onboarding_privacy_title,
        descriptionRes = R.string.onboarding_privacy_body,
        illustrationRes = R.drawable.ic_onboarding_privacy,
        noteRes = R.string.onboarding_privacy_note
    )
}
