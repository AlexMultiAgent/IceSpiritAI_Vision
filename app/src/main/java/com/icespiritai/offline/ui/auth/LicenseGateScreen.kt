package com.icespiritai.offline.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.auth.LicenseState
import com.icespiritai.offline.auth.LicenseViewModel
import com.icespiritai.offline.auth.VerifyResult

@Composable
fun LicenseGateScreen(vm: LicenseViewModel) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()

    var username by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.license_gate_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.license_gate_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.license_gate_username_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.license_gate_code_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            val errorText = (state as? LicenseState.Error)?.let { errorMessage(ctx, it.result) }
            if (errorText != null) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = { vm.submit(username.trim(), code.trim()) },
                enabled = state !is LicenseState.Verifying && username.isNotBlank() && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state is LicenseState.Verifying) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text(stringResource(R.string.license_gate_login))
                }
            }
        }
    }
}

private fun errorMessage(ctx: android.content.Context, r: VerifyResult): String = when (r) {
    is VerifyResult.ParseFailed -> ctx.getString(R.string.license_error_parse_failed)
    is VerifyResult.SignatureMismatch -> ctx.getString(R.string.license_error_signature_mismatch)
    is VerifyResult.UsernameMismatch -> ctx.getString(R.string.license_error_username_mismatch)
    is VerifyResult.Expired -> ctx.getString(R.string.license_error_expired, "—")
    is VerifyResult.Valid -> "" // not expected when state is Error
}
