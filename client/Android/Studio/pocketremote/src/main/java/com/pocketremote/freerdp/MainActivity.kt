package com.pocketremote.freerdp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketremote.freerdp.data.HostProfile
import com.pocketremote.freerdp.data.HostRepository
import com.pocketremote.freerdp.rdp.FreeRdpLauncher
import com.pocketremote.freerdp.ssh.SshTunnelManager
import com.pocketremote.freerdp.ui.screens.AddEditHostScreen
import com.pocketremote.freerdp.ui.screens.HostListScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PocketRemoteApp()
                }
            }
        }
    }
}

@Composable
fun PocketRemoteApp() {
    val context = LocalContext.current
    val repository = remember { HostRepository.getInstance(context) }
    val navController: NavHostController = rememberNavController()
    val scope = rememberCoroutineScope()

    // 세션이 끝나고 돌아왔을 때 SSH 터널/임시 북마크를 정리하기 위해 기억해둔다
    var pending by remember {
        mutableStateOf<Triple<HostProfile, SshTunnelManager, Long>?>(null)
    }
    val sessionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val p = pending ?: return@rememberLauncherForActivityResult
        pending = null
        scope.launch {
            FreeRdpLauncher.teardown(context, p.first, p.second, p.third)
        }
    }

    fun connect(profile: HostProfile) {
        val sshManager = SshTunnelManager()
        scope.launch {
            val result = FreeRdpLauncher.prepareSessionIntent(context, profile, sshManager)
            result.onSuccess { prepared ->
                pending = Triple(profile, sshManager, prepared.bookmarkId)
                sessionLauncher.launch(prepared.intent)
            }.onFailure {
                // 연결 실패 시 열려 있을 수 있는 SSH 세션 정리
                scope.launch { sshManager.disconnect(profile) }
            }
        }
    }

    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            val hosts by repository.hosts.collectAsStateWithLifecycle()
            HostListScreen(
                hosts = hosts,
                onConnect = ::connect,
                onAdd = { navController.navigate("edit/new") },
                onEdit = { navController.navigate("edit/${it.id}") },
                onDelete = { repository.delete(it.id) },
            )
        }
        composable("edit/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            val hosts by repository.hosts.collectAsStateWithLifecycle()
            val existing = hosts.firstOrNull { it.id == id }
            val defaults by repository.defaultCredentials.collectAsStateWithLifecycle()
            AddEditHostScreen(
                existing = existing,
                defaults = defaults,
                onSave = { profile ->
                    repository.upsert(profile)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
