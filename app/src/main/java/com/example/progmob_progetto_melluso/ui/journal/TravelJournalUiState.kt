package com.example.progmob_progetto_melluso.ui.journal

import com.example.progmob_progetto_melluso.journal.JournalMedia
import com.example.progmob_progetto_melluso.journal.JournalTravel
import com.example.progmob_progetto_melluso.journal.JournalCity
import com.example.progmob_progetto_melluso.journal.TravelJournal

/**
 * Immutable UI state for the travel journal tab.
 *
 * How it works: the screen only collects this state and emits user intents to
 * the ViewModel; every value here is produced by the ViewModel from the
 * journal repository flows (spec §4).
 *
 * @property travels the whole journal (newest first), used by the list screen.
 * @property activeTravel the ongoing travel (endUtcMillis == null), or null.
 * @property activeSteps the live pedometer steps of the ongoing trip (shown in
 *   the journal card without opening the detail).
 * @property activeDistanceMeters the live pedometer distance of the ongoing trip.
 * @property selectedTravelId which travel is currently opened, or null for the list.
 * @property detail the mutable detail of the opened travel, or null when closed.
 * @property isTravelActionPending true while a start/stop request is being
 *   processed; the buttons are disabled so rapid taps cannot spam the action.
 * @property isLoading true until the first journal emission arrives.
 */
data class TravelJournalUiState(
    val travels: List<JournalTravel> = emptyList(),
    val activeTravel: JournalTravel? = null,
    val activeSteps: Long = 0,
    val activeDistanceMeters: Double = 0.0,
    val selectedTravelId: Long? = null,
    val detail: TravelJournal? = null,
    val isTravelActionPending: Boolean = false,
    val isLoading: Boolean = true
)