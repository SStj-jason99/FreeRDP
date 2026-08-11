package com.pocketremote.freerdp.data

import kotlinx.serialization.Serializable

/**
 * 여러 호기를 등록할 때 매번 똑같이 입력하게 되는 공통 계정 정보.
 * 현장 특성상 AMR 계정과 그 뒤 비전 PC 계정이 모든 호기에서 동일해서, 기본값 자체를
 * 현장에서 실제로 쓰는 값으로 미리 채워뒀다.
 */
@Serializable
data class DefaultCredentials(
    val sshUsername: String = "drobot",
    val sshPassword: String = "drobot",
    val useJumpHost: Boolean = true,
    val rdpTargetHost: String = "192.168.0.213",
    val rdpUsername: String = "admin",
    val rdpPassword: String = "q",
)
