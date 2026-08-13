package com.example.progmob_progetto_melluso.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.progmob_progetto_melluso.R

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onGoogleSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            text = stringResource(id = R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
        }

        if (uiState.isAuthenticated) {
            val label = uiState.displayName ?: uiState.email
            Text(
                text = label?.let { stringResource(id = R.string.login_signed_in_as, it) }
                    ?: stringResource(id = R.string.login_signed_in),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            OutlinedButton(
                onClick = onSignOutClick,
                enabled = !uiState.isLoading
            ) {
                Text(text = stringResource(id = R.string.logout))
            }
        } else {
            Button(
                onClick = onGoogleSignInClick,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.login_with_google))
            }
        }

        uiState.errorMessage?.let { message ->
            Text(
                modifier = Modifier.padding(top = 16.dp),
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
