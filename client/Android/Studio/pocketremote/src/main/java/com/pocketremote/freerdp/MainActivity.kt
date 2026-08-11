package com.pocketremote.freerdp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.freerdp.freerdpcore.application.GlobalApp
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

    // SessionActivity는 documentLaunchMode="always"라 startActivityForResult로 결과를
    // 돌려받을 수 없다 - 대신 GlobalApp의 세션 이벤트 리스너로 종료 시점을 감지한다.
    fun connect(profile: HostProfile) {
        val sshManager = SshTunnelManager()
        scope.launch {
            val result = FreeRdpLauncher.prepareSessionIntent(context, profile, sshManager)
            result.onSuccess { (intent, instance) ->
                fun cleanup() {
                    GlobalApp.unregisterSessionListener(instance)
                    scope.launch { FreeRdpLauncher.teardown(profile, sshManager) }
                }
                GlobalApp.registerSessionListener(instance, object : GlobalApp.SessionEventListener {
                    override fun onConnectionSuccess() {}
                    override fun onConnectionFailure() = cleanup()
                    override fun onDisconnected() = cleanup()
                })
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    cleanup()
                }
            }.onFailure {
                // SSH 연결/터널 자체가 실패한 경우 (아직 세션은 만들어지지 않았음)
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
