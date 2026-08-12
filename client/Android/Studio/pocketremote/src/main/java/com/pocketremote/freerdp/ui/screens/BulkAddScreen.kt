package com.pocketremote.freerdp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pocketremote.freerdp.data.DefaultCredentials
import com.pocketremote.freerdp.data.HostProfile
import com.pocketremote.freerdp.data.HostRepository

/** 호기 번호 -> AMR IP (1호기=172.17.132.11, 2호기=.12, ...) */
private fun amrAddressForHo(hoNumber: Int): String = "172.17.132.${10 + hoNumber}"

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
            "호기 번호와 대수를 확인해주세요"
        } else {
            profiles.joinToString("\n") { "${it.displayName}  ->  ${it.host}" }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("여러 대 한번에 등록") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("호기 번호만 입력하면 AMR 주소·계정은 자동으로 채워집니다.")

            Spacer(Modifier.padding(top = 16.dp))
            Row {
                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    Text("시작 호기 번호")
                    OutlinedTextField(
                        value = startNumber,
                        onValueChange = { startNumber = it; updatePreview() },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                    Text("몇 대")
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
                Text(if (showAdvanced) "상세 설정 숨기기" else "상세 설정")
            }

            if (showAdvanced) {
                Text("표시 이름 패턴 ({n} 자리에 호기 번호가 들어감)")
                OutlinedTextField(
                    value = namePattern,
                    onValueChange = { namePattern = it; updatePreview() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(Modifier.padding(top = 16.dp))
                Text("공통 SSH 계정 (AMR 경유 시에는 AMR 로그인 정보)", style = MaterialTheme.typography.titleSmall)
                Row {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text("SSH 포트")
                        OutlinedTextField(value = sshPort, onValueChange = { sshPort = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                        Text("RDP 포트")
                        OutlinedTextField(value = rdpPort, onValueChange = { rdpPort = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                }
                OutlinedTextField(
                    value = sshUsername,
                    onValueChange = { sshUsername = it },
                    label = { Text("사용자 이름") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sshPassword,
                    onValueChange = { sshPassword = it },
                    label = { Text("비밀번호") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )

                Spacer(Modifier.padding(top = 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useJumpHost, onCheckedChange = { checked -> useJumpHost = checked; updatePreview() })
                    Text("AMR 경유 접속")
                }
                if (useJumpHost) {
                    Text("비전 PC(RDP 대상) IP")
                    OutlinedTextField(
                        value = rdpTargetHost,
                        onValueChange = { rdpTargetHost = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text("비전 PC 계정", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = rdpUsername,
                        onValueChange = { rdpUsername = it },
                        label = { Text("사용자 이름") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = rdpPassword,
                        onValueChange = { rdpPassword = it },
                        label = { Text("비밀번호") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                    )
                }

                Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = blackenWallpaper, onCheckedChange = { blackenWallpaper = it }, enabled = !useJumpHost)
                    Text("접속 시 배경화면 검정으로 전환 (AMR 경유 시에는 적용 안 됨)")
                }
            }

            Spacer(Modifier.padding(top = 16.dp))
            Button(onClick = { updatePreview() }, modifier = Modifier.fillMaxWidth()) {
                Text("미리보기")
            }

            if (previewText.isNotBlank()) {
                Text(previewText, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.padding(top = 16.dp))
            Button(
                onClick = {
                    val profiles = buildProfiles()
                    if (profiles == null) {
                        resultMessage = "호기 번호와 대수를 확인해주세요"
                    } else {
                        profiles.forEach { repository.upsert(it) }
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
                        resultMessage = "${profiles.size}개 등록 완료"
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = sshUsername.isNotBlank() && (!useJumpHost || (rdpTargetHost.isNotBlank() && rdpUsername.isNotBlank())),
            ) {
                Text("전부 등록하기")
            }

            resultMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}
