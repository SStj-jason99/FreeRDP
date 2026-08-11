package com.pocketremote.freerdp.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 저장된 원격 PC(호기) 접속 프로필 하나.
 *
 * SSH 계정과 RDP 계정을 분리한 이유: sshd 로그인 계정과 RDP 로그인 계정이 실제로는 같은
 * 윈도우 계정인 경우가 대부분이지만, 강제로 합치지 않고 각각 입력받아 도메인 계정 등
 * 다른 환경에도 대응 가능하게 했다.
 */
@Serializable
data class HostProfile(
    val id: String = UUID.randomUUID().toString(),

    /** 목록에 표시될 별명. 예: "3호기", "사무실PC" */
    val displayName: String,

    /** SSH 접속 대상 주소. AMR을 거쳐야 하는 경우 이 값은 AMR의 IP가 된다. */
    val host: String,

    val sshPort: Int = 22,
    val sshUsername: String,
    val sshPassword: String,

    /**
     * SSH 터널이 실제로 포워딩할 RDP 대상 IP. 기본값 "127.0.0.1"이면 host 자신이 RDP 대상.
     * AMR을 경유해서 그 뒤의 별도 비전 PC로 접속해야 하면 여기에 비전 PC의 실제 IP를 넣는다.
     */
    val rdpTargetHost: String = "127.0.0.1",

    val rdpPort: Int = 3389,
    val rdpUsername: String,
    val rdpPassword: String,

    /** 접속 직전 SSH로 대상 PC 배경화면을 단색 검정으로 바꿔서 전송량을 줄인다 */
    val blackenWallpaperOnConnect: Boolean = true,
)
