package com.pocketremote.freerdp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pocketremote.freerdp.R
import com.pocketremote.freerdp.data.DefaultCredentials
import com.pocketremote.freerdp.data.HostProfile

/** 호기를 새로 등록하거나(existing == null) 기존 호기를 수정하는 화면 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditHostScreen(
    existing: HostProfile?,
    existingHosts: List<HostProfile>,
    defaults: DefaultCredentials,
    onSave: (HostProfile) -> Unit,
    onCancel: () -> Unit,
) {
    var displayName by remember { mutableStateOf(existing?.displayName ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var sshUsername by remember { mutableStateOf(existing?.sshUsername ?: defaults.sshUsername) }
    var sshPassword by remember { mutableStateOf(existing?.sshPassword ?: defaults.sshPassword) }
    var useJumpHost by remember { mutableStateOf(existing?.let { it.rdpTargetHost != "127.0.0.1" } ?: defaults.useJumpHost) }
    var rdpTargetHost by remember { mutableStateOf(existing?.rdpTargetHost ?: defaults.rdpTargetHost) }
    var rdpUsername by remember { mutableStateOf(existing?.rdpUsername ?: defaults.rdpUsername) }
    var rdpPassword by remember { mutableStateOf(existing?.rdpPassword ?: defaults.rdpPassword) }
    var blacken by remember { mutableStateOf(existing?.blackenWallpaperOnConnect ?: true) }

    // host(SSH 접속 주소)만 기준으로 중복을 본다. AMR 경유 접속의 rdpTargetHost는 기본값이
    // 자동으로 채워지고 실사용 환경에서 여러 호기가 같은 값을 공유하는 경우가 많아, 이걸
    // 기준으로 삼으면 host를 입력하기도 전에 오탐이 뜬다 - host가 진짜 유일 식별자다.
    val effectiveTarget = host.trim().lowercase()
    val isDuplicate = effectiveTarget.isNotBlank() && existingHosts.any {
        it.id != existing?.id && it.host.trim().lowercase() == effectiveTarget
    }

    val canSave = displayName.isNotBlank() && host.isNotBlank() && sshUsername.isNotBlank() &&
        rdpUsername.isNotBlank() && (!useJumpHost || rdpTargetHost.isNotBlank()) && !isDuplicate

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) stringResource(R.string.addedit_title_add) else stringResource(R.string.addedit_title_edit)) },
                navigationIcon = { TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) } },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                HostProfile(
                                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                                    displayName = displayName,
                                    host = host,
                                    sshUsername = sshUsername,
                                    sshPassword = sshPassword,
                                    rdpTargetHost = if (useJumpHost) rdpTargetHost else "127.0.0.1",
                                    rdpUsername = rdpUsername,
                                    rdpPassword = rdpPassword,
                                    blackenWallpaperOnConnect = blacken,
                                )
                            )
                        },
                    ) { Text(stringResource(R.string.common_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(displayName, { displayName = it }, label = { Text(stringResource(R.string.addedit_label_display_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                host, { host = it }, label = { Text(stringResource(R.string.addedit_label_ssh_host)) },
                isError = isDuplicate,
                supportingText = {
                    if (isDuplicate) Text(stringResource(R.string.addedit_error_duplicate), color = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(sshUsername, { sshUsername = it }, label = { Text(stringResource(R.string.addedit_label_ssh_username)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                sshPassword, { sshPassword = it }, label = { Text(stringResource(R.string.addedit_label_ssh_password)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.addedit_label_use_jump_host), modifier = Modifier.weight(1f))
                Switch(checked = useJumpHost, onCheckedChange = { useJumpHost = it })
            }
            if (useJumpHost) {
                OutlinedTextField(
                    rdpTargetHost, { rdpTargetHost = it },
                    label = { Text(stringResource(R.string.addedit_label_rdp_target_host)) },
                    isError = isDuplicate,
                    supportingText = {
                        if (isDuplicate) Text(stringResource(R.string.addedit_error_duplicate), color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(rdpUsername, { rdpUsername = it }, label = { Text(stringResource(R.string.addedit_label_rdp_username)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                rdpPassword, { rdpPassword = it }, label = { Text(stringResource(R.string.addedit_label_rdp_password)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.addedit_label_blacken), modifier = Modifier.weight(1f))
                Switch(checked = blacken, onCheckedChange = { blacken = it })
            }
        }
    }
}
