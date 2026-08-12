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
 * PARAM_INSTANCE로 우리가 직접 만든 세션을 넘기는 방식은 시도해봤지만 실제 기기 테스트에서
 * 두 가지 업스트림 버그를 드러냈다: session.getSurface()가 세션 생성 직후엔 null이라 NPE가
 * 나고(SessionActivity.java, bindSession()은 이미 null 체크를 하는데 PARAM_INSTANCE 분기는
 * 안 함 - 고쳐서 커밋함), 무엇보다 그 경로는 "이미 연결 중인 세션을 다시 화면에 붙이는" 용도라
 * 실제 연결(connectWithTitle -> ConnectThread -> LibFreeRDP.connect)을 시작해주지 않는다.
 * 그래서 실제로 검증된 경로인 "북마크 참조로 실행"(PARAM_CONNECTION_REFERENCE)을 쓴다 -
 * freeRDPCore 자체 SQLCipher 북마크 DB에 이번 접속 1회용 북마크를 넣고 그 id로 실행한 뒤,
 * 세션이 끝나면 지운다.
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
            // FITSCREEN (not the default AUTOMATIC) is what gates SessionActivity's
            // onConfigurationChanged -> sendMonitorLayout dynamic resize - without it,
            // rotating the phone mid-session doesn't ask the RDP server to match the new
            // orientation/aspect ratio, it just stays at the size from initial connect.
            screenSettings.setResolution(BookmarkBase.ScreenSettings.FITSCREEN)
        }

        val bookmarkId = withContext(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(context.applicationContext).bookmarkDao()
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

    /** 세션 화면에서 돌아온 뒤 SSH 터널을 닫고, 1회용 북마크도 지운다 */
    suspend fun teardown(context: Context, profile: HostProfile, sshManager: SshTunnelManager, bookmarkId: Long?) {
        sshManager.disconnect(profile)
        if (bookmarkId != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val dao = AppDatabase.getInstance(context.applicationContext).bookmarkDao()
                    ManualBookmarkGateway(dao).delete(bookmarkId)
                }
            }
        }
    }
}
