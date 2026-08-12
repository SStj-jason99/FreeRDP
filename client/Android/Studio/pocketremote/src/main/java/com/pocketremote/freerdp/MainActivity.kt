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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.pocketremote.freerdp.ui.screens.BulkAddScreen
import com.pocketremote.freerdp.ui.screens.HostListScreen
import com.pocketremote.freerdp.ui.theme.PocketRemoteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketRemoteTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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

    // 연결 중이거나 접속 화면이 떠 있는 동안 정리해야 할 SSH 터널/1회용 북마크.
    // SessionActivity는 documentLaunchMode="always"라 startActivityForResult로는 결과를
    // 못 돌려받으므로, "우리 화면이 다시 보인다(=RDP 화면에서 돌아왔다)"는 라이프사이클
    // 신호로 정리 시점을 감지한다.
    var pending by remember {
        mutableStateOf<Triple<HostProfile, SshTunnelManager, Long?>?>(null)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                pending?.let { (profile, sshManager, bookmarkId) ->
                    pending = null
                    scope.launch { FreeRdpLauncher.teardown(context, profile, sshManager, bookmarkId) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun connect(profile: HostProfile) {
        val sshManager = SshTunnelManager()
        scope.launch {
            val result = FreeRdpLauncher.prepareSessionIntent(context, profile, sshManager)
            result.onSuccess { prepared ->
                pending = Triple(profile, sshManager, prepared.bookmarkId)
                try {
                    context.startActivity(prepared.intent)
                } catch (e: Exception) {
                    pending = null
                    scope.launch { FreeRdpLauncher.teardown(context, profile, sshManager, prepared.bookmarkId) }
                }
            }.onFailure {
                // SSH 연결/터널 자체가 실패한 경우 (세션·북마크는 아직 안 만들어졌음)
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
                onBulkAdd = { navController.navigate("bulk") },
                onEdit = { navController.navigate("edit/${it.id}") },
                onDelete = { repository.delete(it.id) },
            )
        }
        composable("bulk") {
            BulkAddScreen(onDone = { navController.popBackStack() })
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
