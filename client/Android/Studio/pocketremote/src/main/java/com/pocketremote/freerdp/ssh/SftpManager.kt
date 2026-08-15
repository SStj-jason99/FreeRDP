package com.pocketremote.freerdp.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 원격 PC의 파일을 SFTP로 내려받거나 올리는 기능. RDP 세션과 무관하게, SSH 연결만으로 동작한다
 * (급할 때 원격 접속 없이 파일만 빠르게 확인/다운로드하는 용도).
 *
 * SSH 세션은 이미 SshTunnelManager가 열어둔 걸 그대로 재사용한다(SFTP는 SSH 프로토콜
 * 안에 포함된 서브시스템이라 OpenSSH 서버에 별도 설정 없이 바로 된다).
 */
class SftpManager(private val tunnelManager: SshTunnelManager) {

    private var channel: ChannelSftp? = null

    data class RemoteFile(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val modifiedEpochSeconds: Long,
    )

    suspend fun open(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val session = tunnelManager.currentSession() ?: error("SSH가 먼저 연결되어 있어야 합니다")
            val ch = session.openChannel("sftp") as ChannelSftp
            ch.connect(10_000)
            channel = ch
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 로그인 계정의 홈 디렉터리 경로 (예: C:\Users\admin). SFTP 접속 직후의 pwd()가 곧 홈 디렉터리라는 프로토콜 관례 */
    suspend fun homeDirectory(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: error("SFTP가 열려있지 않습니다")
            Result.success(ch.pwd())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 지정 경로의 파일/폴더 목록. path가 비어있으면 로그인 계정 홈 디렉터리부터 시작 */
    suspend fun list(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: error("SFTP가 열려있지 않습니다")
            // JSch ChannelSftp에는 home() 메서드가 없다. SFTP 접속 직후의 pwd()가
            // 곧 로그인 계정의 홈 디렉터리라는 프로토콜 관례를 이용한다.
            val target = path.ifBlank { ch.pwd() }
            @Suppress("UNCHECKED_CAST")
            val entries = ch.ls(target) as List<ChannelSftp.LsEntry>
            val result = entries
                .filterNot { it.filename == "." || it.filename == ".." }
                .map {
                    RemoteFile(
                        name = it.filename,
                        path = if (target.endsWith("/")) "$target${it.filename}" else "$target/${it.filename}",
                        isDirectory = it.attrs.isDir,
                        sizeBytes = it.attrs.size,
                        modifiedEpochSeconds = it.attrs.mTime.toLong(),
                    )
                }
                .sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })
            Result.success(result)
        } catch (e: Exception) {
            // Was `catch (e: SftpException)` only -- the `channel ?: error(...)` guard above
            // throws IllegalStateException, which isn't an SftpException, so a call made
            // before/after the SFTP channel is open used to crash the whole app instead of
            // surfacing as a Result.failure like every other method here already does.
            Result.failure(e)
        }
    }

    /** 원격 파일을 폰의 앱 전용 다운로드 폴더로 받는다 (런타임 권한이 필요 없는 영역) */
    suspend fun download(remotePath: String, destFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: error("SFTP가 열려있지 않습니다")
            destFile.parentFile?.mkdirs()
            destFile.outputStream().use { out -> ch.get(remotePath, out) }
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 폰의 파일을 원격 PC의 지정 폴더로 올린다 */
    suspend fun upload(localFile: File, remoteDirPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: error("SFTP가 열려있지 않습니다")
            val remotePath = if (remoteDirPath.endsWith("/")) "$remoteDirPath${localFile.name}" else "$remoteDirPath/${localFile.name}"
            localFile.inputStream().use { input -> ch.put(input, remotePath) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        channel?.disconnect()
        channel = null
    }
}
