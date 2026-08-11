package com.pocketremote.freerdp.rdp

import android.content.Context
import android.content.Intent
import com.freerdp.freerdpcore.application.GlobalApp
import com.freerdp.freerdpcore.domain.BookmarkBase
import com.freerdp.freerdpcore.presentation.SessionActivity
import com.pocketremote.freerdp.data.HostProfile
import com.pocketremote.freerdp.ssh.SshTunnelManager

/**
 * SSH 터널을 연 뒤(127.0.0.1:<localPort> -> host의 RDP), FreeRDP 엔진(freeRDPCore)이 실제
 * 화면 렌더링/키보드/터치를 담당하는 SessionActivity를 그 터널을 향해 띄워준다.
 *
 * 세션은 [GlobalApp.createSession]으로 우리가 직접(메모리 상에서만) 만들어서 그 instance
 * 핸들을 SessionActivity에 넘긴다 - freeRDPCore의 북마크 DB(SQLCipher)에 굳이 쓰고 지울
 * 필요가 없고, 세션 종료 시점도 GlobalApp의 [GlobalApp.SessionEventListener]로 정확히 알 수
 * 있다(SessionActivity는 documentLaunchMode="always"라 startActivityForResult로는 결과를
 * 돌려받을 수 없다).
 */
object FreeRdpLauncher {

    /** SSH 연결 + 포트포워딩 + FreeRDP 세션 생성까지 마치고 SessionActivity로 보낼 Intent를 만든다 */
    suspend fun prepareSessionIntent(
        context: Context,
        profile: HostProfile,
        sshManager: SshTunnelManager,
    ): Result<Pair<Intent, Long>> = runCatching {
        sshManager.connect(profile).getOrThrow()
        val localPort = sshManager.openLocalPortForward(profile.rdpTargetHost, profile.rdpPort)

        val bookmark = BookmarkBase().apply {
            label = profile.displayName
            hostname = "127.0.0.1"
            port = localPort
            username = profile.rdpUsername
            password = profile.rdpPassword
            domain = ""
        }

        val session = GlobalApp.createSession(bookmark, context.applicationContext)
        val intent = Intent(context, SessionActivity::class.java).apply {
            putExtra(SessionActivity.PARAM_INSTANCE, session.instance)
        }
        intent to session.instance
    }

    /** 세션이 끝난 뒤(성공/실패 무관) SSH 터널을 닫는다 */
    suspend fun teardown(profile: HostProfile, sshManager: SshTunnelManager) {
        sshManager.disconnect(profile)
    }
}
