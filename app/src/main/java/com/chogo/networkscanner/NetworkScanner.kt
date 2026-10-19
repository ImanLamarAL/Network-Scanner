package com.chogo.networkscanner

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

data class Device(
    val ip: String,
    val hostname: String,
    val openPorts: List<Int>,
    val responseMs: Long
)

class NetworkScanner(private val context: Context) {

    private val commonPorts = listOf(80, 443, 22, 53, 445, 8080)

    /** Returns the phone's own IPv4 address, or null if not connected. */
    fun getLocalIp(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val props = cm.getLinkProperties(network) ?: return null
        return props.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    /** Scans x.x.x.1 to x.x.x.254 on the same /24 as [localIp]. */
    suspend fun scan(
        localIp: String,
        onProgress: (scanned: Int, total: Int) -> Unit,
        onDeviceFound: (Device) -> Unit
    ) {
        val prefix = localIp.substringBeforeLast(".")
        val total = 254
        val done = AtomicInteger(0)
        val limiter = Semaphore(64) // max 64 hosts probed at once

        coroutineScope {
            (1..254).map { host ->
                async(Dispatchers.IO) {
                    limiter.withPermit {
                        val device = probeHost("$prefix.$host")
                        if (device != null) onDeviceFound(device)
                        onProgress(done.incrementAndGet(), total)
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * A host counts as alive if a port accepts the connection, if it actively
     * refuses it (a refusal means something answered), or if ping succeeds.
     */
    private fun probeHost(ip: String): Device? {
        var alive = false
        var rtt = -1L
        val openPorts = mutableListOf<Int>()

        for (port in commonPorts) {
            val t = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress(ip, port), 200) }
                alive = true
                openPorts.add(port)
                if (rtt < 0) rtt = (System.nanoTime() - t) / 1_000_000
            } catch (e: ConnectException) {
                if (e.message?.contains("refused", ignoreCase = true) == true) {
                    alive = true
                    if (rtt < 0) rtt = (System.nanoTime() - t) / 1_000_000
                }
            } catch (e: IOException) {
                // timeout or unreachable: nothing answered on this port
            }
        }

        if (!alive) {
            val t = System.nanoTime()
            alive = try {
                InetAddress.getByName(ip).isReachable(300)
            } catch (e: IOException) {
                false
            }
            if (alive) rtt = (System.nanoTime() - t) / 1_000_000
        }

        if (!alive) return null

        val name = try {
            InetAddress.getByName(ip).canonicalHostName
        } catch (e: Exception) {
            ip
        }
        return Device(
            ip = ip,
            hostname = if (name == ip) "Unknown" else name,
            openPorts = openPorts,
            responseMs = rtt.coerceAtLeast(0)
        )
    }
}