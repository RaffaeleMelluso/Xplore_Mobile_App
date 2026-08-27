package com.example.progmob_progetto_melluso.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import com.example.progmob_progetto_melluso.R

/**
 * Up/down vote control for one POI, shared by every screen that votes
 * (journal visits, map POI card, trends ranking).
 *
 * How it works: [Icons.Filled.ThumbUp] is used both ways, rotating it 180°
 * for the down button (the extended icon set with ThumbDown is not a
 * dependency). The selected side is tinted with the primary color; the score
 * is the community sum shown between the two buttons.
 *
 * @param poiId the POI being voted.
 * @param score the community score to display (cached total or window score).
 * @param myVote the signed-in user's vote: 1 (up), -1 (down) or 0 (none).
 * @param onVotePoi invoked with the POI id and +1/-1 when a side is tapped.
 */
@Composable
fun PoiVoteButtons(
    poiId: Long,
    score: Int,
    myVote: Int,
    onVotePoi: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTint = MaterialTheme.colorScheme.primary
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onVotePoi(poiId, 1) }) {
            Icon(
                imageVector = Icons.Filled.ThumbUp,
                contentDescription = stringResource(R.string.journal_vote_up),
                tint = if (myVote == 1) activeTint else inactiveTint
            )
        }
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = { onVotePoi(poiId, -1) }) {
            Icon(
                imageVector = Icons.Filled.ThumbUp,
                contentDescription = stringResource(R.string.journal_vote_down),
                tint = if (myVote == -1) activeTint else inactiveTint,
                modifier = Modifier.rotate(180f)
            )
        }
    }
}
