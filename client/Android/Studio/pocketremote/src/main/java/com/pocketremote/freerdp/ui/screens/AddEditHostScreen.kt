package com.pocketremote.freerdp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pocketremote.freerdp.data.DefaultCredentials
import com.pocketremote.freerdp.data.HostProfile

/** 호기를 새로 등록하거나(existing == null) 기존 호기를 수정하는 화면 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditHostScreen(
    existing: HostProfile?,
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

    val canSave = displayName.isNotBlank() && host.isNotBlank() && sshUsername.isNotBlank() &&
        rdpUsername.isNotBlank() && (!useJumpHost || rdpTargetHost.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "호기 추가" else "호기 수정") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("취소") } },
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
                    ) { Text("저장") }
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
            OutlinedTextField(displayName, { displayName = it }, label = { Text("별명 (예: 3호기)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(host, { host = it }, label = { Text("SSH 접속 주소 (IP)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sshUsername, { sshUsername = it }, label = { Text("SSH 계정") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                sshPassword, { sshPassword = it }, label = { Text("SSH 비밀번호") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("AMR 경유 접속", modifier = Modifier.weight(1f))
                Switch(checked = useJumpHost, onCheckedChange = { useJumpHost = it })
            }
            if (useJumpHost) {
                OutlinedTextField(
                    rdpTargetHost, { rdpTargetHost = it },
                    label = { Text("실제 RDP 대상 PC IP (비전 PC)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(rdpUsername, { rdpUsername = it }, label = { Text("RDP 계정") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                rdpPassword, { rdpPassword = it }, label = { Text("RDP 비밀번호") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("접속 시 배경화면 검게(속도 최적화)", modifier = Modifier.weight(1f))
                Switch(checked = blacken, onCheckedChange = { blacken = it })
            }
        }
    }
}
