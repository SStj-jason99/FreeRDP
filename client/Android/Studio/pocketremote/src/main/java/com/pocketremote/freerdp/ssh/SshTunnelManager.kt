package com.pocketremote.freerdp.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.pocketremote.freerdp.data.HostProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * SSH 연결 + RDP용 로컬 포트포워딩을 담당한다.
 *
 * 사용자가 접속 버튼을 누르면: 1) target PC로 SSH 접속, 2) (옵션) 배경화면을 검정으로
 * 바꿔서 원격 화면 전송량을 줄임, 3) 로컬(폰 안) 임의 포트 -> target PC의 RDP 포트로
 * 터널을 뚫음, 4) FreeRDP 엔진이 그 로컬 포트로 접속.
 */
class SshTunnelManager {

    private var session: Session? = null
    private var originalWallpaperPath: String? = null

    val isConnected: Boolean
        get() = session?.isConnected == true

    suspend fun connect(profile: HostProfile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsch = JSch()
            val newSession = jsch.getSession(profile.sshUsername, profile.host, profile.sshPort)
            newSession.setPassword(profile.sshPassword)
            newSession.setConfig("StrictHostKeyChecking", "no")
            newSession.connect(15_000)
            newSession.serverAliveInterval = 15_000
            newSession.serverAliveCountMax = 4
            session = newSession

            // AMR 경유 접속일 때는 SSH로 접속한 서버가 리눅스(AMR)라서 이 Windows 전용
            // 명령이 의미가 없다 - 스킵한다.
            if (profile.blackenWallpaperOnConnect && profile.rdpTargetHost == "127.0.0.1") {
                runCatching { blackenWallpaper() }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun openLocalPortForward(remoteHost: String, remotePort: Int): Int = withContext(Dispatchers.IO) {
        val s = session ?: error("SSH 세션이 아직 연결되지 않았습니다")
        s.setPortForwardingL(0, remoteHost, remotePort)
    }

    private fun execCommand(command: String): String {
        val s = session ?: error("SSH 세션이 아직 연결되지 않았습니다")
        val channel = s.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        val out = ByteArrayOutputStream()
        channel.outputStream = out
        channel.connect(10_000)
        var waited = 0
        while (!channel.isClosed && waited < 5000) {
            Thread.sleep(50)
            waited += 50
        }
        channel.disconnect()
        return out.toString(Charsets.UTF_8.name()).trim()
    }

    private fun blackenWallpaper() {
        originalWallpaperPath = runCatching {
            execCommand(
                """powershell -NoProfile -Command "(Get-ItemProperty -Path 'HKCU:\Control Panel\Desktop' -Name Wallpaper -ErrorAction SilentlyContinue).Wallpaper""""
            )
        }.getOrNull()?.trim()

        execCommand(
            """reg add "HKCU\Control Panel\Desktop" /v Wallpaper /t REG_SZ /d "" /f""" +
                " & " +
                """reg add "HKCU\Control Panel\Desktop" /v WallpaperStyle /t REG_SZ /d "0" /f""" +
                " & " +
                "RUNDLL32.EXE user32.dll,UpdatePerUserSystemParameters"
        )
    }

    private fun restoreWallpaper() {
        val path = originalWallpaperPath
        if (path.isNullOrBlank()) return
        runCatching {
            execCommand(
                """reg add "HKCU\Control Panel\Desktop" /v Wallpaper /t REG_SZ /d "$path" /f""" +
                    " & RUNDLL32.EXE user32.dll,UpdatePerUserSystemParameters"
            )
        }
    }

    suspend fun disconnect(profile: HostProfile? = null) = withContext(Dispatchers.IO) {
        if (profile?.blackenWallpaperOnConnect == true) {
            runCatching { restoreWallpaper() }
        }
        session?.disconnect()
        session = null
        originalWallpaperPath = null
    }

    /** SFTP 등 다른 기능에서 같은 SSH 세션을 재사용할 수 있게 노출 */
    fun currentSession(): Session? = session
}
