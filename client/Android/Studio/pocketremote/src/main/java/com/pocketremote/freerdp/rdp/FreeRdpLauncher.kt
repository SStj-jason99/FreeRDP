package com.pocketremote.freerdp.rdp

import android.content.Context
import android.content.Intent
import com.freerdp.freerdpcore.data.AppDatabase
import com.freerdp.freerdpcore.domain.BookmarkBase
import com.freerdp.freerdpcore.domain.ConnectionReference
import com.freerdp.freerdpcore.presentation.SessionActivity
import com.freerdp.freerdpcore.services.ManualBookmarkGateway
import com.pocketremote.freerdp.data.HostProfile
import com.pocketremote.freerdp.ssh.SshTunnelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SSH 터널을 연 뒤(127.0.0.1:<localPort> -> host의 RDP), FreeRDP 엔진(freeRDPCore)이 실제
 * 화면 렌더링/키보드/터치를 담당하는 SessionActivity를 그 터널을 향해 띄워준다.
 *
 * FreeRDP는 자체 Room(SQLCipher) 북마크 DB로만 접속 대상을 받기 때문에, 우리 쪽 호기
 * 목록(HostProfile)과는 별도로 "이번 접속 1회용" 북마크를 하나 만들어 넘긴다 - 우리 앱의
 * 호기 목록 UX(AMR 경유, 공통계정 등)는 그대로 유지하면서 렌더링 엔진만 FreeRDP로 교체.
 */
object FreeRdpLauncher {

    /** [prepareSessionIntent]의 결과: 띄울 Intent와, 나중에 [teardown]에서 지울 북마크 id */
    data class PreparedSession(val intent: Intent, val bookmarkId: Long)

    /** SSH 연결 + 포트포워딩 + 임시 북마크 생성까지 마치고 SessionActivity로 보낼 Intent를 만든다 */
    suspend fun prepareSessionIntent(
        context: Context,
        profile: HostProfile,
        sshManager: SshTunnelManager,
    ): Result<PreparedSession> = runCatching {
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

        val bookmarkId = withContext(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(context).bookmarkDao()
            ManualBookmarkGateway(dao).insert(bookmark)
        }

        val intent = Intent(context, SessionActivity::class.java).apply {
            putExtra(
                SessionActivity.PARAM_CONNECTION_REFERENCE,
                ConnectionReference.getBookmarkReference(bookmarkId)
            )
        }
        PreparedSession(intent, bookmarkId)
    }

    /** 세션이 끝난 뒤(성공/실패 무관) SSH 터널을 닫고, 1회용 북마크도 지운다 */
    suspend fun teardown(context: Context, profile: HostProfile, sshManager: SshTunnelManager, bookmarkId: Long?) {
        sshManager.disconnect(profile)
        if (bookmarkId != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val dao = AppDatabase.getInstance(context).bookmarkDao()
                    ManualBookmarkGateway(dao).delete(bookmarkId)
                }
            }
        }
    }
}
