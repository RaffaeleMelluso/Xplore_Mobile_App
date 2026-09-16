package com.example.progmob_progetto_melluso.ui.onboarding

import com.example.progmob_progetto_melluso.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [OnboardingViewModel]: the tri-state first-run flag and the
 * persistence of the acceptance. The repository is mocked, so only the
 * ViewModel logic is exercised.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Preferences repository stub reporting [completed] as the persisted flag. */
    private fun preferencesRepository(completed: Boolean): UserPreferencesRepository {
        val repository = mock<UserPreferencesRepository>()
        whenever(repository.onboardingCompleted).thenReturn(MutableStateFlow(completed))
        return repository
    }

    /**
     * Verifies that, before the stored flag is read, the state is undecided: the
     * host must not send the user anywhere yet.
     */
    @Test
    fun uiState_startsUndecided() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(preferencesRepository(completed = false))

        assertNull(viewModel.uiState.value.completed)
    }

    /**
     * Verifies that a first launch (nothing stored) is reported as not
     * completed, so the pages are shown.
     */
    @Test
    fun uiState_reportsNotCompletedOnFirstRun() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(preferencesRepository(completed = false))
        // Subscribe so the WhileSubscribed state flow starts collecting upstream.
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.completed)
    }

    /**
     * Verifies that a following launch, where the acceptance is already stored,
     * is reported as completed so the pages are skipped.
     */
    @Test
    fun uiState_reportsCompletedAfterAcceptance() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(preferencesRepository(completed = true))
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.completed == true)
        assertFalse(viewModel.uiState.value.completed == false)
    }

    /**
     * Verifies that accepting the pages persists the flag through the
     * preferences repository (the onboarding never shows again).
     */
    @Test
    fun completeOnboarding_persistsAcceptance() = runTest(dispatcher) {
        val repository = preferencesRepository(completed = false)
        val viewModel = OnboardingViewModel(repository)

        viewModel.completeOnboarding()
        advanceUntilIdle()

        verify(repository).setOnboardingCompleted(true)
    }
}
