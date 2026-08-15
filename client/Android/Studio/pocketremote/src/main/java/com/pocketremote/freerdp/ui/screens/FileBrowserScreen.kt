package com.pocketremote.freerdp.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketremote.freerdp.R
import com.pocketremote.freerdp.data.HostRepository
import com.pocketremote.freerdp.ssh.SftpManager
import com.pocketremote.freerdp.ssh.SshTunnelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
private fun isImageFile(name: String) = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

private val TEXT_EXTENSIONS = setOf("txt", "log", "csv", "ini", "cfg", "conf", "json", "xml", "md", "yaml", "yml")
private fun isTextFile(name: String) = name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS

/** 로그 파일은 수십 MB까지도 흔해서, 미리보기는 앞부분만 잘라 읽는다(전체를 화면에 그리면 버벅임) */
private const val TEXT_PREVIEW_MAX_BYTES = 200_000

/**
 * 업로드용으로 고른 content:// URI의 실제 파일명(확장자 포함)을 알아낸다. 이 이름을 그대로
 * 못 쓰면(원격에 올라갈 파일 이름을 여기서 결정하므로) 원격 PC에서 확장자 없는 파일이 되어
 * 열리지 않는다. DISPLAY_NAME 컬럼이 없거나 조회에 실패하면 null.
 */
private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }

private fun formatDateTime(epochSeconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(epochSeconds * 1000))

// Win32-OpenSSH 서버마다 드라이브 표기 방식이 다르다(네이티브: "C:/Users/x" 또는
// "/C:/Users/x", Cygwin 기반: "/cygdrive/c/Users/x"). 하드코딩하면 서버에 따라
// SSH_FX_NO_SUCH_FILE로 깨지니, 로그인 직후 서버가 실제로 알려준 홈 디렉터리 경로에서
// 드라이브 표기를 그대로 뽑아 쓴다 - 그러면 어떤 방식이든 항상 서버와 일치한다.
private fun driveRootFromHome(home: String): String? {
    Regex("^(/cygdrive/[a-zA-Z])(?:/|$)").find(home)?.let { return it.groupValues[1] + "/" }
    Regex("^(/?[a-zA-Z]:)(?:/|$)").find(home)?.let { return it.groupValues[1] + "/" }
    return null
}

// 목록 행에서는 초 단위까지는 필요 없어서 좀 더 짧게 보여준다.
// SFTP 프로토콜에는 "생성 날짜" 필드가 아예 없어서(SFTP v3 표준상 수정/접근 시간만 존재),
// 대신 수정 날짜를 보여준다 - 라벨도 "수정"이라고 명확히 표시해서 혼동을 막는다.
private fun formatModifiedDate(epochSeconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))

/**
 * 원격 PC의 파일을 SFTP로 내려받거나 올리는 화면. RDP 세션(원격 접속)과 별개로 동작해서,
 * 화면을 안 켜고 급하게 파일만 확인/다운로드할 때 쓴다.
 *
 * 편의 기능:
 *  - 파일 하나 길게 누르면 선택모드로 들어가서 여러 개 체크 후 한번에 다운로드
 *  - 이미지 파일은 목록에서부터 작은 썸네일로 미리 보이고, 탭하면 전체화면 + 좌우로 넘기며 다음 이미지도 확인
 *  - 텍스트/로그 파일은 탭하면 바로 보고 편집 + 저장, 찾기(검색) 가능
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(hostId: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { HostRepository.getInstance(context) }
    val profile = remember { repository.hosts.value.find { it.id == hostId } }
    val scope = rememberCoroutineScope()

    if (profile == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.filebrowser_error_profile_not_found))
        }
        return
    }

    // Toast는 반드시 메인 스레드에서 호출해야 한다 - IO 스레드에서 바로 부르면 크래시 난다.
    fun showToast(message: String, long: Boolean = false) {
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    val sshManager = remember { SshTunnelManager() }
    val sftpManager = remember { SftpManager(sshManager) }
    // JSch의 SFTP 채널은 여러 코루틴이 동시에 건드리면 불안정해질 수 있어서,
    // 썸네일 로딩/이미지 미리보기 등 백그라운드로 자동으로 도는 요청들은 이 락으로 한 번에 하나씩만 나가게 한다.
    val sftpMutex = remember { Mutex() }

    var currentPath by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<SftpManager.RemoteFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }

    // 이미지 미리보기: 인덱스로 관리해서 좌우로 넘기며 같은 폴더의 다른 이미지도 바로 볼 수 있게 함
    var previewImageIndex by remember { mutableStateOf(-1) }
    val thumbnailCache = remember { mutableStateMapOf<String, android.graphics.Bitmap>() }
    val fullImageCache = remember { mutableStateMapOf<String, File>() }

    // Desktop / 드라이브 루트 바로가기 버튼용으로 기억해둔다 (최초 진입 시 계산한 경로).
    var desktopPath by remember { mutableStateOf<String?>(null) }
    var driveRootPath by remember { mutableStateOf<String?>(null) }

    var previewLoading by remember { mutableStateOf(false) }
    // 텍스트 미리보기 + 편집 상태: 원격 경로를 같이 들고 있어야 "저장" 시 어디로 다시 올릴지 안다
    var previewTextPath by remember { mutableStateOf<String?>(null) }
    var previewTextName by remember { mutableStateOf("") }
    var previewTextContent by remember { mutableStateOf("") }
    var previewTextTruncated by remember { mutableStateOf(false) }
    var previewTextModified by remember { mutableStateOf(0L) }
    var savingText by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        selectionMode = false
        selectedPaths = emptySet()
    }

    suspend fun refresh(path: String) {
        loading = true
        val result = sftpManager.list(path)
        result.onSuccess {
            files = it
            errorMessage = null
        }.onFailure {
            errorMessage = it.message
        }
        loading = false
    }

    LaunchedEffect(hostId) {
        // JSch's own connect-timeout doesn't reliably fire for every kind of network failure
        // (e.g. a host that silently drops packets instead of actively refusing the
        // connection), which used to leave this screen stuck on the loading spinner forever
        // when tapped on an unreachable host. Wrap the whole handshake in our own timeout so
        // it always resolves one way or the other, regardless of what JSch does internally.
        val completed = withTimeoutOrNull(10_000) {
            val sshResult = sshManager.connect(profile.copy(blackenWallpaperOnConnect = false))
            if (sshResult.isFailure) {
                errorMessage = context.getString(R.string.filebrowser_error_ssh_failed, sshResult.exceptionOrNull()?.message)
                return@withTimeoutOrNull
            }
            val openResult = sftpManager.open()
            if (openResult.isFailure) {
                errorMessage = context.getString(R.string.filebrowser_error_sftp_open_failed, openResult.exceptionOrNull()?.message)
                return@withTimeoutOrNull
            }

            // 처음 열 때는 홈 폴더 대신 바로 바탕화면(Desktop)부터 보여준다.
            // 혹시 바탕화면 조회에 실패하면(권한 문제 등) 안전하게 홈 디렉터리로 대체한다.
            val home = sftpManager.homeDirectory().getOrNull()
            driveRootPath = home?.let { driveRootFromHome(it) }
            val resolvedDesktopPath = home?.let { if (it.endsWith("/")) "${it}Desktop" else "$it/Desktop" }
            if (resolvedDesktopPath != null && sftpManager.list(resolvedDesktopPath).isSuccess) {
                desktopPath = resolvedDesktopPath
                currentPath = resolvedDesktopPath
                refresh(resolvedDesktopPath)
            } else {
                refresh("")
            }
        }
        if (completed == null && errorMessage == null) {
            errorMessage = context.getString(R.string.filebrowser_error_timeout)
        }
        loading = false
    }

    // 목록이 바뀔 때마다 이미지 파일들의 작은 썸네일을 순서대로(동시에 X) 받아온다.
    LaunchedEffect(files) {
        val images = files.filter { !it.isDirectory && isImageFile(it.name) }
        for (img in images) {
            if (thumbnailCache.containsKey(img.path)) continue
            sftpMutex.withLock {
                try {
                    val cacheFile = File(context.cacheDir, "thumb_${kotlin.math.abs(img.path.hashCode())}.dat")
                    val ready = cacheFile.exists() || sftpManager.download(img.path, cacheFile).isSuccess
                    if (ready) {
                        val options = BitmapFactory.Options().apply { inSampleSize = 6 }
                        val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeFile(cacheFile.absolutePath, options) }
                        if (bmp != null) thumbnailCache[img.path] = bmp
                    }
                } catch (_: Exception) {
                    // 썸네일 하나 실패해도 나머지는 계속 시도
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sftpManager.close()
            scope.launch(Dispatchers.IO) { sshManager.disconnect(null) }
        }
    }

    // GetContent()가 반환하는 URI는(특히 사진 선택기를 거치면) 파이프로 실시간 스트리밍되는
    // 경우가 있어서, 용량이 큰 파일(영상 등)은 복사가 끝나기 전에 반대쪽에서 파이프를 먼저
    // 닫아버려 "Pipe closed" 오류로 실패할 수 있다. OpenDocument()는 SAF(문서 접근 프레임워크)
    // 경유라 큰 파일에도 더 안정적이다.
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                // SftpManager.upload() names the remote file after this local file's name, so
                // it must keep the picked file's real name (extension included) - staging it
                // under an extension-less "upload_tmp_<millis>" name (as before) meant the file
                // landed on the remote PC with no extension at all, so Windows had no idea what
                // to open it with. Put it in its own throwaway subfolder so same-named uploads
                // picked back-to-back can't collide with each other in the cache dir.
                val displayName = queryDisplayName(context, uri) ?: "upload_${System.currentTimeMillis()}"
                val stagingDir = File(context.cacheDir, "upload_${System.currentTimeMillis()}").apply { mkdirs() }
                val tempFile = File(stagingDir, displayName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                sftpManager.upload(tempFile, currentPath.ifBlank { "." })
                tempFile.delete()
                stagingDir.delete()
                refresh(currentPath)
                showToast(context.getString(R.string.filebrowser_toast_upload_done))
            } catch (e: Exception) {
                showToast(context.getString(R.string.filebrowser_toast_upload_failed, e.message), long = true)
            }
        }
    }

    val imageFilesInFolder = remember(files) { files.filter { !it.isDirectory && isImageFile(it.name) } }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.filebrowser_title_selected_count, selectedPaths.size)) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.filebrowser_action_cancel_selection))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val targets = files.filter { it.path in selectedPaths && !it.isDirectory }
                                scope.launch(Dispatchers.IO) {
                                    var success = 0
                                    targets.forEach { f ->
                                        val downloadsDir = context.getExternalFilesDir("downloads") ?: context.filesDir
                                        val dest = File(downloadsDir, f.name)
                                        if (sftpManager.download(f.path, dest).isSuccess) success++
                                    }
                                    showToast(context.getString(R.string.filebrowser_toast_download_summary, success, targets.size), long = true)
                                    exitSelectionMode()
                                }
                            },
                            enabled = selectedPaths.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.filebrowser_action_download_selected))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            currentPath.ifBlank { stringResource(R.string.filebrowser_path_home) },
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onExit) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.filebrowser_action_exit)) }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val target = desktopPath
                                if (target != null) {
                                    currentPath = target
                                    scope.launch { refresh(target) }
                                }
                            },
                            enabled = desktopPath != null,
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.filebrowser_action_go_desktop))
                        }
                        IconButton(
                            onClick = {
                                val target = driveRootPath
                                if (target != null) {
                                    currentPath = target
                                    scope.launch { refresh(target) }
                                }
                            },
                            enabled = driveRootPath != null,
                        ) {
                            Icon(Icons.Filled.Storage, contentDescription = stringResource(R.string.filebrowser_action_go_drive_root))
                        }
                        IconButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.UploadFile, contentDescription = stringResource(R.string.filebrowser_action_upload))
                        }
                    },
                    // 경로 표시줄과 아래 파일 목록이 구분되게 살짝 다른 톤의 배경을 준다
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!selectionMode) androidx.compose.material3.HorizontalDivider(thickness = 2.dp)
            when {
                loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
                errorMessage != null -> Text(errorMessage ?: "", modifier = Modifier.padding(16.dp))
                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    // 상위 폴더로 이동하는 항목 (홈이 아닐 때만 표시)
                    if (currentPath.isNotBlank() && !selectionMode) {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.filebrowser_item_parent_folder)) },
                                leadingContent = {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.fillMaxWidth().combinedClickable(
                                    onClick = {
                                        val parent = currentPath.substringBeforeLast("/", "")
                                        currentPath = parent
                                        scope.launch { refresh(parent) }
                                    },
                                ),
                            )
                        }
                    }
                    items(files, key = { it.path }) { file ->
                        val isSelected = file.path in selectedPaths
                        ListItem(
                            headlineContent = { Text(file.name) },
                            supportingContent = {
                                // 폴더도 수정 날짜는 있으니 같이 보여준다(용량은 파일에만 표시).
                                val dateText = formatModifiedDate(file.modifiedEpochSeconds)
                                Text(
                                    if (!file.isDirectory) stringResource(R.string.filebrowser_row_info_with_size, (file.sizeBytes / 1024).toString(), dateText)
                                    else stringResource(R.string.filebrowser_row_info_no_size, dateText)
                                )
                            },
                            leadingContent = {
                                val thumb = if (!selectionMode || file.isDirectory) thumbnailCache[file.path] else null
                                when {
                                    selectionMode && !file.isDirectory -> {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedPaths = if (checked) selectedPaths + file.path else selectedPaths - file.path
                                            },
                                        )
                                    }
                                    thumb != null -> {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            when {
                                                file.isDirectory -> Icons.Filled.Folder
                                                isImageFile(file.name) -> Icons.Filled.ImageIcon
                                                else -> Icons.Filled.Description
                                            },
                                            contentDescription = null,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onLongClick = {
                                    if (!file.isDirectory) {
                                        selectionMode = true
                                        selectedPaths = selectedPaths + file.path
                                    }
                                },
                                onClick = {
                                    when {
                                        selectionMode -> {
                                            if (!file.isDirectory) {
                                                selectedPaths = if (isSelected) selectedPaths - file.path else selectedPaths + file.path
                                            }
                                        }
                                        file.isDirectory -> {
                                            currentPath = file.path
                                            scope.launch { refresh(file.path) }
                                        }
                                        isImageFile(file.name) -> {
                                            // 이미지 미리보기 다이얼로그를 이 파일의 인덱스로 열기 (좌우로 다른 이미지도 넘겨볼 수 있음)
                                            val idx = imageFilesInFolder.indexOfFirst { it.path == file.path }
                                            if (idx >= 0) previewImageIndex = idx
                                        }
                                        isTextFile(file.name) -> {
                                            // 로그/텍스트 파일: 통째로 받아서 화면에서 바로 보고 편집할 수 있게
                                            scope.launch(Dispatchers.IO) {
                                                previewLoading = true
                                                val cacheFile = File(context.cacheDir, "preview_${file.name}")
                                                val result = sftpManager.download(file.path, cacheFile)
                                                previewLoading = false
                                                result.onSuccess { downloaded ->
                                                    val bytes = downloaded.readBytes()
                                                    previewTextTruncated = bytes.size > TEXT_PREVIEW_MAX_BYTES
                                                    val content = String(bytes, 0, minOf(bytes.size, TEXT_PREVIEW_MAX_BYTES), Charsets.UTF_8)
                                                    previewTextPath = file.path
                                                    previewTextName = file.name
                                                    previewTextContent = content
                                                    previewTextModified = file.modifiedEpochSeconds
                                                }.onFailure {
                                                    showToast(context.getString(R.string.filebrowser_toast_preview_failed, it.message), long = true)
                                                }
                                            }
                                        }
                                        else -> {
                                            scope.launch(Dispatchers.IO) {
                                                val downloadsDir = context.getExternalFilesDir("downloads") ?: context.filesDir
                                                val dest = File(downloadsDir, file.name)
                                                val result = sftpManager.download(file.path, dest)
                                                result.onSuccess {
                                                    showToast(context.getString(R.string.filebrowser_toast_download_saved, it.absolutePath), long = true)
                                                }.onFailure {
                                                    showToast(context.getString(R.string.filebrowser_toast_download_failed, it.message), long = true)
                                                }
                                            }
                                        }
                                    }
                                },
                            ),
                        )
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
        }

        if (previewLoading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    // 이미지 미리보기 전체화면 다이얼로그 (좌우로 넘기며 폴더 내 다른 이미지도 확인)
    if (previewImageIndex in imageFilesInFolder.indices) {
        ImagePreviewDialog(
            images = imageFilesInFolder,
            startIndex = previewImageIndex,
            loadImage = { remoteFile ->
                fullImageCache[remoteFile.path]?.let { return@ImagePreviewDialog it }
                sftpMutex.withLock {
                    val cacheFile = File(context.cacheDir, "full_${kotlin.math.abs(remoteFile.path.hashCode())}.dat")
                    val ok = cacheFile.exists() || sftpManager.download(remoteFile.path, cacheFile).isSuccess
                    if (ok) {
                        fullImageCache[remoteFile.path] = cacheFile
                        cacheFile
                    } else {
                        null
                    }
                }
            },
            onDismiss = { previewImageIndex = -1 },
        )
    }

    // 텍스트/로그 파일 미리보기 + 편집 다이얼로그
    if (previewTextPath != null) {
        TextPreviewDialog(
            fileName = previewTextName,
            modifiedEpochSeconds = previewTextModified,
            initialContent = previewTextContent,
            truncated = previewTextTruncated,
            saving = savingText,
            onSave = onSave@{ newContent ->
                val path = previewTextPath ?: return@onSave
                scope.launch(Dispatchers.IO) {
                    savingText = true
                    try {
                        val tempFile = File(context.cacheDir, "upload_edit_${System.currentTimeMillis()}")
                        tempFile.writeText(newContent, Charsets.UTF_8)
                        val remoteDir = path.substringBeforeLast("/", "")
                        val result = sftpManager.upload(tempFile, remoteDir)
                        tempFile.delete()
                        result.onSuccess {
                            showToast(context.getString(R.string.filebrowser_toast_save_done))
                        }.onFailure {
                            showToast(context.getString(R.string.filebrowser_toast_save_failed, it.message), long = true)
                        }
                    } finally {
                        savingText = false
                    }
                }
            },
            onDismiss = { previewTextPath = null },
        )
    }
}

/**
 * 이미지 전체화면 미리보기. 좌우로 스와이프하면 같은 폴더의 다음/이전 이미지로 넘어간다.
 * 하단에 파일명과 수정 날짜/시간, 몇 번째 이미지인지 표시한다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImagePreviewDialog(
    images: List<SftpManager.RemoteFile>,
    startIndex: Int,
    loadImage: suspend (SftpManager.RemoteFile) -> File?,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val file = images[page]
                var localFile by remember(file.path) { mutableStateOf<File?>(null) }
                var failed by remember(file.path) { mutableStateOf(false) }

                LaunchedEffect(file.path) {
                    val result = loadImage(file)
                    localFile = result
                    failed = result == null
                }

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val lf = localFile
                    when {
                        lf != null -> {
                            val bitmap = remember(lf) { BitmapFactory.decodeFile(lf.absolutePath)?.asImageBitmap() }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = file.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                Text(stringResource(R.string.filebrowser_image_cannot_display), color = Color.White)
                            }
                        }
                        failed -> Text(stringResource(R.string.filebrowser_image_load_failed), color = Color.White)
                        else -> CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White)
            }

            // 하단: 파일 이름 + 날짜/시간 + 몇 번째 / 전체 몇 장
            val currentFile = images.getOrNull(pagerState.currentPage)
            if (currentFile != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(currentFile.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(
                            R.string.filebrowser_image_page_indicator,
                            formatDateTime(currentFile.modifiedEpochSeconds),
                            pagerState.currentPage + 1,
                            images.size,
                        ),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * 텍스트/로그 파일 미리보기 + 편집 + 찾기(검색) 다이얼로그.
 * 로그 파일이 커서 찾기 버튼으로 다음/이전 일치 위치까지 커서를 옮겨서 스크롤해준다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TextPreviewDialog(
    fileName: String,
    modifiedEpochSeconds: Long,
    initialContent: String,
    truncated: Boolean,
    saving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var fieldValue by remember(initialContent) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initialContent))
    }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatch by remember { mutableStateOf(-1) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    // 대소문자 구분 없이 검색어가 나오는 모든 위치를 찾는다
    val matches = remember(fieldValue.text, searchQuery) {
        if (searchQuery.isBlank()) emptyList() else {
            val result = mutableListOf<Int>()
            var idx = fieldValue.text.indexOf(searchQuery, 0, ignoreCase = true)
            while (idx >= 0) {
                result.add(idx)
                idx = fieldValue.text.indexOf(searchQuery, idx + 1, ignoreCase = true)
            }
            result
        }
    }

    fun jumpTo(matchIdx: Int) {
        if (matches.isEmpty()) return
        val safeIdx = ((matchIdx % matches.size) + matches.size) % matches.size
        currentMatch = safeIdx
        val start = matches[safeIdx]
        fieldValue = fieldValue.copy(selection = androidx.compose.ui.text.TextRange(start, start + searchQuery.length))
        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Surface로 감싸야 LocalContentColor가 배경색에 맞게 자동으로 설정된다.
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(fileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (modifiedEpochSeconds > 0) {
                            Text(
                                formatDateTime(modifiedEpochSeconds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close)) }
                },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.filebrowser_action_search))
                    }
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 12.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { onSave(fieldValue.text) }) {
                            Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.common_save))
                        }
                    }
                },
            )

            if (searchVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; currentMatch = -1 },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.filebrowser_search_placeholder)) },
                        singleLine = true,
                    )
                    Text(
                        if (matches.isEmpty()) stringResource(R.string.filebrowser_search_no_matches)
                        else stringResource(R.string.filebrowser_search_match_count, (currentMatch.coerceAtLeast(0)) + 1, matches.size),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = { jumpTo(currentMatch - 1) }, enabled = matches.isNotEmpty()) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.filebrowser_action_previous))
                    }
                    IconButton(onClick = { jumpTo(currentMatch + 1) }, enabled = matches.isNotEmpty()) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.filebrowser_action_next))
                    }
                }
            }

            if (truncated) {
                Text(
                    stringResource(R.string.filebrowser_truncated_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            OutlinedTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .bringIntoViewRequester(bringIntoViewRequester),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp),
            )
        }
        }
    }
}
