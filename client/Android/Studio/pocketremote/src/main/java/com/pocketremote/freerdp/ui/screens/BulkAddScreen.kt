package com.pocketremote.freerdp.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pocketremote.freerdp.R
import com.pocketremote.freerdp.data.DefaultCredentials
import com.pocketremote.freerdp.data.HostProfile
import com.pocketremote.freerdp.data.HostRepository

/** 호기 번호 -> AMR IP (1호기=172.17.132.11, 2호기=.12, ...) */
private fun amrAddressForHo(hoNumber: Int): String = "172.17.132.${10 + hoNumber}"

// 중복 판정은 host(SSH 접속 주소)만 기준으로 한다. AMR 경유 접속의 rdpTargetHost는 실사용
// 환경에서 다 같은 값을 쓰는 경우가 많아 구분 기준으로 의미가 없다 - host가 실제로 매
// 호기마다 달라지는 값이라 이게 진짜 유일 식별자다.
private fun effectiveTarget(p: HostProfile): String = p.host.trim().lowercase()

private fun registerAndFinish(
    repository: HostRepository,
    toAdd: List<HostProfile>,
    skipped: List<HostProfile>,
    sshUsername: String,
    sshPassword: String,
    useJumpHost: Boolean,
    rdpTargetHost: String,
    rdpUsername: String,
    rdpPassword: String,
    context: Context,
    onDone: () -> Unit,
) {
    toAdd.forEach { repository.upsert(it) }
    repository.saveDefaultCredentials(
        DefaultCredentials(
            sshUsername = sshUsername,
            sshPassword = sshPassword,
            useJumpHost = useJumpHost,
            rdpTargetHost = rdpTargetHost,
            rdpUsername = rdpUsername,
            rdpPassword = rdpPassword,
        )
    )
    // resultMessage를 화면에 Text로 띄워봤자 onDone()이 바로 뒤로가기를 해버려서 보일 새가
    // 없었다(기존 버그) - Toast로 바꿔서 화면이 전환돼도 확실히 보이게 한다.
    val message = if (skipped.isEmpty()) context.getString(R.string.bulkadd_toast_done, toAdd.size)
                  else context.getString(R.string.bulkadd_toast_done_with_skip, toAdd.size, skipped.size)
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    onDone()
}

/**
 * 호기 번호 범위만 입력하면 등록되는 일괄등록 화면. AMR 주소는 호기 번호로 자동 계산되고,
 * 계정/포트 등은 DefaultCredentials 기본값이 미리 채워져 있어 손댈 필요가 없다.
 * 필요할 때만 "상세 설정"을 펼쳐서 값을 바꿀 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkAddScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { HostRepository.getInstance(context) }
    val savedDefaults = remember { repository.defaultCredentials.value }

    var startNumber by remember { mutableStateOf("1") }
    var count by remember { mutableStateOf("17") }
    var namePattern by remember { mutableStateOf("{n}호기") }

    var sshPort by remember { mutableStateOf("22") }
    var sshUsername by remember { mutableStateOf(savedDefaults.sshUsername) }
    var sshPassword by remember { mutableStateOf(savedDefaults.sshPassword) }
    var useJumpHost by remember { mutableStateOf(savedDefaults.useJumpHost) }
    var rdpTargetHost by remember { mutableStateOf(savedDefaults.rdpTargetHost) }
    var rdpUsername by remember { mutableStateOf(savedDefaults.rdpUsername) }
    var rdpPassword by remember { mutableStateOf(savedDefaults.rdpPassword) }
    var rdpPort by remember { mutableStateOf("3389") }
    var blackenWallpaper by remember { mutableStateOf(true) }

    var showAdvanced by remember { mutableStateOf(false) }
    var previewText by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    // 중복(이미 등록된 접속 대상)이 섞여 있을 때, 등록 전에 확인 다이얼로그로 안내하기 위한
    // 대기 상태. toAdd/skipped 둘 다 들고 있는다.
    var pendingRegistration by remember { mutableStateOf<Pair<List<HostProfile>, List<HostProfile>>?>(null) }

    // 아래 두 곳(비-Composable 컨텍스트: 일반 함수·onClick 람다)에서 재사용해야 해서
    // 이 시점(Composable 컨텍스트)에서 미리 값을 읽어둔다.
    val checkNumbersError = stringResource(R.string.bulkadd_error_check_numbers)

    fun buildProfiles(): List<HostProfile>? {
        val start = startNumber.toIntOrNull() ?: return null
        val n = count.toIntOrNull() ?: return null
        return (0 until n).map { offset ->
            val hoNumber = start + offset
            val name = namePattern.replace("{n}", hoNumber.toString())
            HostProfile(
                displayName = name,
                host = amrAddressForHo(hoNumber),
                sshPort = sshPort.toIntOrNull() ?: 22,
                sshUsername = sshUsername,
                sshPassword = sshPassword,
                rdpTargetHost = if (useJumpHost) rdpTargetHost.ifBlank { "127.0.0.1" } else "127.0.0.1",
                rdpPort = rdpPort.toIntOrNull() ?: 3389,
                rdpUsername = if (useJumpHost) rdpUsername else sshUsername,
                rdpPassword = if (useJumpHost) rdpPassword else sshPassword,
                blackenWallpaperOnConnect = blackenWallpaper && !useJumpHost,
            )
        }
    }

    fun updatePreview() {
        val profiles = buildProfiles()
        previewText = if (profiles == null) {
            checkNumbersError
        } else {
            profiles.joinToString("\n") { "${it.displayName}  ->  ${it.host}" }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.bulkadd_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.bulkadd_desc))

            Spacer(Modifier.padding(top = 16.dp))
            Row {
                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(stringResource(R.string.bulkadd_label_start_number))
                    OutlinedTextField(
                        value = startNumber,
                        onValueChange = { startNumber = it; updatePreview() },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(stringResource(R.string.bulkadd_label_count))
                    OutlinedTextField(
                        value = count,
                        onValueChange = { count = it; updatePreview() },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            }

            Spacer(Modifier.padding(top = 12.dp))
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) stringResource(R.string.bulkadd_toggle_hide_advanced) else stringResource(R.string.bulkadd_toggle_show_advanced))
            }

            if (showAdvanced) {
                Text(stringResource(R.string.bulkadd_label_name_pattern))
                OutlinedTextField(
                    value = namePattern,
                    onValueChange = { namePattern = it; updatePreview() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(Modifier.padding(top = 16.dp))
                Text(stringResource(R.string.bulkadd_section_ssh), style = MaterialTheme.typography.titleSmall)
                Row {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(stringResource(R.string.bulkadd_label_ssh_port))
                        OutlinedTextField(value = sshPort, onValueChange = { sshPort = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(stringResource(R.string.bulkadd_label_rdp_port))
                        OutlinedTextField(value = rdpPort, onValueChange = { rdpPort = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                }
                OutlinedTextField(
                    value = sshUsername,
                    onValueChange = { sshUsername = it },
                    label = { Text(stringResource(R.string.common_label_username)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sshPassword,
                    onValueChange = { sshPassword = it },
                    label = { Text(stringResource(R.string.common_label_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )

                Spacer(Modifier.padding(top = 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useJumpHost, onCheckedChange = { checked -> useJumpHost = checked; updatePreview() })
                    Text(stringResource(R.string.addedit_label_use_jump_host))
                }
                if (useJumpHost) {
                    Text(stringResource(R.string.bulkadd_label_rdp_target_host))
                    OutlinedTextField(
                        value = rdpTargetHost,
                        onValueChange = { rdpTargetHost = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(stringResource(R.string.bulkadd_section_rdp_account), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = rdpUsername,
                        onValueChange = { rdpUsername = it },
                        label = { Text(stringResource(R.string.common_label_username)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = rdpPassword,
                        onValueChange = { rdpPassword = it },
                        label = { Text(stringResource(R.string.common_label_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                    )
                }

                Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = blackenWallpaper, onCheckedChange = { blackenWallpaper = it }, enabled = !useJumpHost)
                    Text(stringResource(R.string.bulkadd_label_blacken))
                }
            }

            Spacer(Modifier.padding(top = 16.dp))
            Button(onClick = { updatePreview() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bulkadd_button_preview))
            }

            if (previewText.isNotBlank()) {
                Text(previewText, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.padding(top = 16.dp))
            Button(
                onClick = {
                    val profiles = buildProfiles()
                    if (profiles == null) {
                        resultMessage = checkNumbersError
                    } else {
                        // upsert()는 id로만 중복을 판단하는데, 여기서 만드는 프로필은 매번
                        // 새 랜덤 id를 받으므로 같은 범위로 두 번 누르면 무조건 다 새로
                        // 추가돼버렸다(진짜 중복 등록 버그). 이미 등록된 host는 걸러낸다.
                        val existingTargets = repository.hosts.value.map(::effectiveTarget).toSet()
                        val (toAdd, skipped) = profiles.partition { effectiveTarget(it) !in existingTargets }
                        if (skipped.isEmpty()) {
                            registerAndFinish(repository, toAdd, skipped, sshUsername, sshPassword,
                                useJumpHost, rdpTargetHost, rdpUsername, rdpPassword, context, onDone)
                        } else {
                            // 바로 등록하지 않고, 몇 개가 왜 제외되는지 먼저 확인시킨다.
                            pendingRegistration = toAdd to skipped
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = sshUsername.isNotBlank() && (!useJumpHost || (rdpTargetHost.isNotBlank() && rdpUsername.isNotBlank())),
            ) {
                Text(stringResource(R.string.bulkadd_button_register_all))
            }

            resultMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
        }
    }

    pendingRegistration?.let { (toAdd, skipped) ->
        AlertDialog(
            onDismissRequest = { pendingRegistration = null },
            title = { Text(stringResource(R.string.bulkadd_dialog_title_duplicates)) },
            text = {
                Text(stringResource(R.string.bulkadd_dialog_msg_duplicates, toAdd.size + skipped.size, skipped.size, toAdd.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRegistration = null
                    registerAndFinish(repository, toAdd, skipped, sshUsername, sshPassword,
                        useJumpHost, rdpTargetHost, rdpUsername, rdpPassword, context, onDone)
                }) { Text(stringResource(R.string.bulkadd_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRegistration = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
