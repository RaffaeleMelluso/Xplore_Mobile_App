package com.example.progmob_progetto_melluso.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.progmob_progetto_melluso.journal.TravelJournalRepository

/**
 * Factory that builds [TravelJournalViewModel] with its repository dependency.
 *
 * How it works: the manual DI container ([AppContainer]) is not a
 * ViewModelProvider, so this factory bridges the two worlds.
 */
class TravelJournalViewModelFactory(
    private val journalRepository: TravelJournalRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TravelJournalViewModel::class.java)) {
            return TravelJournalViewModel(journalRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}