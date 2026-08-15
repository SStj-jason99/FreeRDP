package com.pocketremote.freerdp.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.freerdp.freerdpcore.presentation.ApplicationSettingsActivity
import com.pocketremote.freerdp.R
import com.pocketremote.freerdp.data.HostProfile
import com.pocketremote.freerdp.ssh.HostConnectivityChecker
import com.pocketremote.freerdp.ui.theme.IrisError
import com.pocketremote.freerdp.ui.theme.IrisPrimary
import com.pocketremote.freerdp.ui.theme.IrisSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    hosts: List<HostProfile>,
    onConnect: (HostProfile) -> Unit,
    onAdd: () -> Unit,
    onBulkAdd: () -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
    onFiles: (HostProfile) -> Unit,
) {
    var topMenuExpanded by remember { mutableStateOf(false) }
    var languageDialogOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_toolbar_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Iris Remote", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { topMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.hostlist_menu_more))
                    }
                    DropdownMenu(expanded = topMenuExpanded, onDismissRequest = { topMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hostlist_menu_bulk_add)) },
                            onClick = { topMenuExpanded = false; onBulkAdd() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hostlist_menu_settings)) },
                            onClick = {
                                topMenuExpanded = false
                                context.startActivity(Intent(context, ApplicationSettingsActivity::class.java))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hostlist_menu_language)) },
                            onClick = {
                                topMenuExpanded = false
                                languageDialogOpen = true
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.hostlist_action_add)) }
        },
    ) { padding ->
        if (languageDialogOpen) {
            LanguagePickerDialog(onDismiss = { languageDialogOpen = false })
        }
        if (hosts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.hostlist_empty_state),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Scaffold's FAB floats over the content instead of reserving space for it, so
            // without extra bottom padding here the last card ends up hidden underneath the
            // "add host" FAB when scrolled all the way down. 88dp ~= FAB height(56dp) +
            // its own margin(16dp) + a little breathing room.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(hosts, key = { it.id }) { host ->
                HostCard(
                    host = host,
                    onConnect = { onConnect(host) },
                    onEdit = { onEdit(host) },
                    onDelete = { onDelete(host) },
                    onFiles = { onFiles(host) },
                )
            }
        }
    }
}

@Composable
private fun HostCard(
    host: HostProfile,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFiles: () -> Unit,
) {
    var reachable by remember(host.id) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(host.id) {
        reachable = HostConnectivityChecker.isReachable(host.host, host.sshPort)
    }

    val statusColor = when (reachable) {
        true -> IrisSuccess
        false -> IrisError
        null -> MaterialTheme.colorScheme.outline
    }

    Card(
        onClick = onConnect,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(IrisPrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Laptop, contentDescription = null, tint = IrisPrimary)
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(host.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (host.rdpTargetHost == "127.0.0.1") host.host
                    else stringResource(R.string.hostlist_target_via_format, host.host, host.rdpTargetHost),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onFiles,
                // 접속 가능으로 확실히 확인된 호기에서만 누를 수 있게 한다. 아직 확인 중이거나
                // 접속 불가로 확인된 경우 모두 막는다(체크 끝나기 전에 눌러서 타임아웃까지
                // 기다리게 되는 상황 자체를 없앤다).
                enabled = reachable == true,
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = stringResource(R.string.hostlist_action_files),
                    tint = if (reachable == true) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class AppLanguage(val tag: String, val label: String)

private val supportedLanguages = listOf(
    AppLanguage("ko", "한국어"),
    AppLanguage("en", "English"),
    AppLanguage("vi", "Tiếng Việt"),
)

@Composable
private fun LanguagePickerDialog(onDismiss: () -> Unit) {
    // 현재 앱에 적용된 언어(시스템 기본값을 따르는 중이면 아무 것도 선택되지 않은 상태).
    val currentTag = AppCompatDelegate.getApplicationLocales().get(0)?.language
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hostlist_language_dialog_title)) },
        text = {
            Column {
                supportedLanguages.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(lang.tag)
                                )
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = lang.tag == currentTag, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(lang.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}
