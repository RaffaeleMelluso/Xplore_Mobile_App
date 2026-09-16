package com.example.progmob_progetto_melluso.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.core.AppPermissions
import kotlinx.coroutines.launch

/** Size of the illustrated scene on a page. */
private val ILLUSTRATION_SIZE = 200.dp

/** Page dots: the inactive ones are small squares, the active one a pill. */
private val DOT_WIDTH = 8.dp
private val DOT_ACTIVE_WIDTH = 22.dp
private val DOT_HEIGHT = 8.dp
private val DOT_SPACING = 8.dp

/**
 * First-run onboarding: the swipeable pages that explain, one by one, why
 * Xplore needs the location, the step counter and the notifications.
 *
 * How it works: the pages come from [OnboardingPage] (pure data) and live in a
 * [HorizontalPager], so the user swipes through them; the dots show the position
 * and the same swipe can be done with the button. Each page that needs a
 * permission asks for it when the user confirms THAT page, so the Android dialog
 * appears one at a time, right after the reason for it, and the flow moves on
 * once the user has answered it. A refusal never blocks the pages and is not
 * recorded as such: the app only remembers that the pages were accepted, and
 * degrades gracefully (the map has its own in-context request when the user
 * wants to enable location later). [onAccepted] is invoked when the last page is
 * confirmed, so the host activity never navigates while a system dialog is open.
 *
 * @param onAccepted invoked when the user accepted the pages.
 * @param modifier applied to the whole screen.
 */
@Composable
fun OnboardingScreen(
    onAccepted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = OnboardingPage.entries
    val lastIndex = pages.lastIndex
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Moves on: to the next page, or to the acceptance when there is no next one.
    val moveOn: () -> Unit = {
        if (pagerState.currentPage == lastIndex) {
            onAccepted()
        } else {
            scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    // The permission dialog belongs to the page that motivated it: it is
    // requested on that page's "continue" and the pages move on once the user
    // has answered. The launcher always invokes the latest lambda, so reading the
    // current page inside it stays correct even if the result arrives late.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> moveOn() }

    // What the page on screen still has to ask for. Nothing to ask means no
    // dialog is opened at all (so no pointless system round-trip on a device
    // where the grant is already there) and the hint below stays hidden.
    val currentPage = pages[pagerState.currentPage]
    val pendingPermissions = remember(pagerState.currentPage) {
        AppPermissions.missing(context, currentPage.permissions)
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { index ->
                OnboardingPageContent(page = pages[index])
            }

            PageIndicator(current = pagerState.currentPage, count = pages.size)

            // Only when a dialog is really coming: it warns that the system
            // prompt is the expected next step, instead of surprising the user.
            if (pendingPermissions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(id = R.string.onboarding_permission_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (pendingPermissions.isEmpty()) {
                        moveOn()
                    } else {
                        permissionLauncher.launch(pendingPermissions.toTypedArray())
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        id = if (pagerState.currentPage == lastIndex) {
                            R.string.onboarding_start
                        } else {
                            R.string.onboarding_continue
                        }
                    )
                )
            }

            // Explicit way back: swiping is otherwise the only one.
            TextButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                enabled = pagerState.currentPage > 0
            ) {
                Text(stringResource(id = R.string.onboarding_back))
            }
        }
    }
}

/**
 * A single page: the centered illustration, its headline, the explanation and
 * the optional fine print.
 *
 * How it works: the column scrolls vertically so small screens and landscape do
 * not clip the text, and centers its content when it fits.
 *
 * @param page the data of the page to render.
 * @param modifier applied to the page.
 */
@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = page.illustrationRes),
            // Decorative: the headline right below carries the same meaning.
            contentDescription = null,
            modifier = Modifier.size(ILLUSTRATION_SIZE)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(id = page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(id = page.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        page.noteRes?.let { noteRes ->
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(id = noteRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Dots showing how many pages there are and which one is on screen: the active
 * dot stretches into a pill, so the position reads at a glance.
 */
@Composable
private fun PageIndicator(
    current: Int,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DOT_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .width(if (index == current) DOT_ACTIVE_WIDTH else DOT_WIDTH)
                    .height(DOT_HEIGHT)
                    .clip(CircleShape)
                    .background(
                        color = if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            )
        }
    }
}
