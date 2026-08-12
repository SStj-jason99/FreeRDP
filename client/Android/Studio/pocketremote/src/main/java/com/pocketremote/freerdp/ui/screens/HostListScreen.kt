package com.pocketremote.freerdp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
) {
    var topMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Iris Remote", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { topMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "더보기")
                    }
                    DropdownMenu(expanded = topMenuExpanded, onDismissRequest = { topMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("여러 대 한번에 등록") },
                            onClick = { topMenuExpanded = false; onBulkAdd() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "추가") }
        },
    ) { padding ->
        if (hosts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "등록된 호기가 없습니다. + 버튼으로 추가하세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(hosts, key = { it.id }) { host ->
                HostCard(host = host, onConnect = { onConnect(host) }, onEdit = { onEdit(host) }, onDelete = { onDelete(host) })
            }
        }
    }
}

@Composable
private fun HostCard(host: HostProfile, onConnect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
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
                    if (host.rdpTargetHost == "127.0.0.1") host.host else "${host.host} → ${host.rdpTargetHost}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "수정", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
