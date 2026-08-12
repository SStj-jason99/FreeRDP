package com.pocketremote.freerdp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pocketremote.freerdp.data.HostProfile
import com.pocketremote.freerdp.ssh.HostConnectivityChecker
import kotlinx.coroutines.launch

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
                title = { Text("Iris Remote") },
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
                Text("등록된 호기가 없습니다. + 버튼으로 추가하세요.")
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(hosts, key = { it.id }) { host ->
                HostRow(host = host, onConnect = { onConnect(host) }, onEdit = { onEdit(host) }, onDelete = { onDelete(host) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HostRow(host: HostProfile, onConnect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var reachable by remember(host.id) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(host.id) {
        reachable = HostConnectivityChecker.isReachable(host.host, host.sshPort)
    }

    ListItem(
        headlineContent = { Text(host.displayName) },
        supportingContent = {
            Text(if (host.rdpTargetHost == "127.0.0.1") host.host else "${host.host} → ${host.rdpTargetHost}")
        },
        leadingContent = {
            val color = when (reachable) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.outline
            }
            Box(
                Modifier
                    .size(12.dp)
                    .background(color, shape = CircleShape)
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "수정") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "삭제") }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect)
    )
}
