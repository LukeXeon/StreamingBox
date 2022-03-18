package open.source.streamingbox

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.Proxy
import android.os.Build
import android.os.Process
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy.NO_PROXY

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
internal object NetworkCompat {
    private val application by lazy {
        Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentApplication")
            .apply {
                isAccessible = true
            }
            .invoke(null) as Application
    }

    private val isNoProxy by lazy<(String) -> Boolean> {
        val method = Proxy::class.java
            .getDeclaredMethod(
                "getProxy",
                Context::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
        return@lazy {
            method.invoke(null, application, it) == NO_PROXY
        }
    }

    private val isDebug by lazy {
        application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    private val spacing = Regex("\\s+")

    fun isLocalHost(host: String): Boolean {
        return if (host.equals("localhost", ignoreCase = true)) {
            true
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                InetAddresses.parseNumericAddress(host).isLoopbackAddress
            } else {
                isNoProxy(host)
            }
        }
    }

    private fun toInetSocketAddress(src: String): InetSocketAddress {
        val parts = src.split(":")
        var bytes = HexDecoding.decode(parts[0])
        val port = parts[1].toInt(16)
        bytes = HexDecoding.toNetworkOrder(bytes)
        val address = InetAddress.getByAddress(bytes)
        return InetSocketAddress(address, port)
    }

    private fun addressDeepEquals(
        left: InetSocketAddress,
        right: InetSocketAddress
    ): Boolean {
        if (left.address.isLoopbackAddress && right.address.isLoopbackAddress) {
            if (left.port == right.port) {
                return true
            }
        }
        return left == right
    }

    @SuppressLint("InlinedApi")
    fun getConnectionOwnerUid(
        protocol: Int,
        local: InetSocketAddress,
        remote: InetSocketAddress
    ): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val m = application.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager
            m.getConnectionOwnerUid(OsConstants.IPPROTO_TCP, local, remote)
        } else {
            Log.d(TAG, "getConnectionOwnerUid: protocol: $protocol local: $local remote: $remote")
            val name = when (protocol) {
                OsConstants.IPPROTO_TCP -> "tcp"
                OsConstants.IPPROTO_UDP -> "udp"
                OsConstants.IPPROTO_RAW -> "raw"
                else -> throw IllegalArgumentException()
            }
            File("/proc/net/${name}").useLines { protocol4 ->
                File("/proc/net/${name}6").useLines { protocol6 ->
                    val connection = arrayOf(protocol4, protocol6)
                        .asSequence()
                        .onEach { it.drop(1) }
                        .flatMap { it }
                        .map { it.trim().split(spacing) }
                        .map {
                            TcpConnection(
                                it[7].toInt(),
                                toInetSocketAddress(it[1]),
                                toInetSocketAddress(it[2])
                            )
                        }
                        .findConnection {
                            addressDeepEquals(
                                it.localAddress,
                                local
                            ) && addressDeepEquals(
                                it.remoteAddress,
                                remote
                            )
                        }
                    return connection?.uid ?: Process.INVALID_UID
                }
            }
        }
    }

    private inline fun Sequence<TcpConnection>.findConnection(predicate: (TcpConnection) -> Boolean): TcpConnection? {
        return if (isDebug) {
            val connections = this.toList()
            Log.d(TAG, "connections: \n${connections.joinToString("\n")}")
            connections.find(predicate)
        } else {
            this.find(predicate)
        }
    }

    private const val TAG = "NetworkCompat"

    private data class TcpConnection(
        val uid: Int,
        val localAddress: InetSocketAddress,
        val remoteAddress: InetSocketAddress
    )

}