package com.example.progmob_progetto_melluso.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.core.AppContainer
import com.example.progmob_progetto_melluso.journal.JournalTravel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Travel journal tab: the "libretto" of trips.
 *
 * How it works: it collects [TravelJournalUiState] from the ViewModel and shows
 * either the journal list (with a start/stop button for the ongoing trip) or,
 * when a travel is opened, the [TravelDetailScreen]. The screen only reads
 * state and emits intents; no business logic here (spec §4).
 *
 * @param onTravelsMapSelected optional callback when the map filter wants a trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelJournalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: TravelJournalViewModel = viewModel(
        factory = remember(context) {
            TravelJournalViewModelFactory(AppContainer.travelJournalRepository(context))
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    // Travel awaiting the delete confirmation dialog, if any.
    var pendingDelete by remember { mutableStateOf<JournalTravel?>(null) }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val selectedId = uiState.selectedTravelId
    if (selectedId != null) {
        TravelDetailScreen(
            uiState = uiState,
            onBack = viewModel::closeTravel,
            onSave = { title, description -> viewModel.saveTravelDetails(title, description) },
            onAddMedia = { uri, type -> viewModel.addMedia(uri, type) },
            onRemoveMedia = viewModel::removeMedia,
            onAddTransport = viewModel::addTransport,
            onRemoveTransport = viewModel::removeTransport,
            onRenameCity = viewModel::renameCity,
            onRemoveVisit = viewModel::removeVisit,
            onVotePoi = viewModel::votePoi,
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journal_my_travels)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CallToActionBar(
                activeTravel = uiState.activeTravel,
                pending = uiState.isTravelActionPending,
                onStart = viewModel::startTravel,
                onStop = viewModel::stopTravel
            )
            if (uiState.travels.isEmpty()) {
                EmptyJournal()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(uiState.travels, key = { it.travelId }) { travel ->
                        JournalTravelCard(
                            travel = travel,
                            onClick = { viewModel.openTravel(travel.travelId) },
                            onDelete = { pendingDelete = travel }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { travel ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.journal_delete_title)) },
            text = { Text(stringResource(R.string.journal_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTravel(travel.travelId)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.journal_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.journal_delete_cancel))
                }
            }
        )
    }
}

/**
 * Header card with the Start/Stop control for the ongoing trip.
 *
 * How it works: the active state uses the primary (teal) container to match
 * the app palette (previously the coral secondary container clashed); the
 * buttons are disabled while a start/stop request is being processed so rapid
 * taps cannot spam the action.
 */
@Composable
private fun CallToActionBar(
    activeTravel: JournalTravel?,
    pending: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = activeTravel != null
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) {
                        stringResource(R.string.journal_ongoing)
                    } else {
                        stringResource(R.string.journal_my_travels)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (isActive) {
                    Text(
                        text = activeTravel?.title ?: stringResource(R.string.journal_untitled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (isActive) {
                Button(onClick = onStop, enabled = !pending) {
                    Text(stringResource(R.string.journal_stop_travel))
                }
            } else {
                Button(onClick = onStart, enabled = !pending) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.journal_start_travel))
                }
            }
        }
    }
}

/** Empty-state message for a journal with no trips. */
@Composable
private fun EmptyJournal() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.journal_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.journal_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A single journal entry in the list, with a delete action. */
@Composable
private fun JournalTravelCard(
    travel: JournalTravel,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = travel.title ?: stringResource(R.string.journal_untitled),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTravelRange(travel.startUtcMillis, travel.endUtcMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.journal_delete_trip),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Formats a travel date range, e.g. "Aug 24 2026 – Aug 31 2026". */
private fun formatTravelRange(startUtc: Long, endUtc: Long?): String {
    val day = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val start = day.format(Date(startUtc))
    val end = endUtc?.let { day.format(Date(it)) }
    return if (end == null) "From $start" else "$start \u2013 $end"
}