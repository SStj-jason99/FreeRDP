package com.pocketremote.freerdp.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** 목록 화면에서 각 호기의 "접속 가능한지"를 빠르게 훑어보기 위한 가벼운 TCP 도달성 체크. */
object HostConnectivityChecker {
    suspend fun isReachable(host: String, port: Int, timeoutMs: Int = 1500): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                true
            } catch (e: Exception) {
                false
            }
        }
}
