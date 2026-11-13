package com.elysium.nexus.core.transport.mac

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal class NexusScreenSignaling(private val port: Int = DEFAULT_PORT) {
    private val running = AtomicBoolean(false)
    private val writerLock = Any()
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var writer: PrintWriter? = null
    var onOfferRequested: ((sendOffer: (String) -> Unit) -> Unit)? = null
    var onAnswer: ((String) -> Unit)? = null
    var onIceCandidate: ((String, String?, Int) -> Unit)? = null

    fun start(): Int {
        check(running.compareAndSet(false, true)) { "Screen signaling is already running" }
        val server = try {
            ServerSocket(port).apply { reuseAddress = true }
        } catch (error: Exception) {
            running.set(false)
            throw error
        }
        serverSocket = server
        thread(name = "NexusScreenSignalingAccept", isDaemon = true) {
            while (running.get()) {
                try {
                    val socket = server.accept().apply { tcpNoDelay = true }
                    synchronized(writerLock) {
                        activeSocket?.close()
                        activeSocket = socket
                    }
                    handle(socket)
                } catch (error: Exception) {
                    if (running.get()) Log.e(TAG, "Signaling accept failed", error)
                }
            }
        }
        return server.localPort
    }

    private fun handle(socket: Socket) {
        thread(name = "NexusScreenSignalingClient", isDaemon = true) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val output = PrintWriter(socket.getOutputStream(), true)
                synchronized(writerLock) { writer = output }
                onOfferRequested?.invoke { sdp ->
                    send(JSONObject().put("type", "offer").put("sdp", Base64.encodeToString(sdp.toByteArray(), Base64.NO_WRAP)))
                }
                while (running.get() && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    runCatching {
                        val json = JSONObject(line)
                        when (json.optString("type")) {
                            "answer" -> {
                                val sdp = String(Base64.decode(json.getString("sdp"), Base64.DEFAULT), Charsets.UTF_8)
                                onAnswer?.invoke(sdp)
                            }
                            "ice" -> onIceCandidate?.invoke(
                                json.getString("candidate"),
                                json.optString("sdpMid").ifBlank { null },
                                json.optInt("sdpMLineIndex", 0)
                            )
                            else -> Log.w(TAG, "Ignoring unknown signaling message type")
                        }
                    }.onFailure { Log.w(TAG, "Ignoring malformed signaling message", it) }
                }
            } catch (error: Exception) {
                if (running.get()) Log.w(TAG, "Signaling client ended", error)
            } finally {
                synchronized(writerLock) {
                    if (activeSocket === socket) {
                        activeSocket = null
                        writer = null
                    }
                }
                runCatching { socket.close() }
            }
        }
    }

    fun sendAnswer(sdp: String) {
        send(JSONObject().put("type", "answer").put("sdp", Base64.encodeToString(sdp.toByteArray(), Base64.NO_WRAP)))
    }

    fun sendIce(candidate: String, mid: String?, line: Int) {
        send(JSONObject().put("type", "ice").put("candidate", candidate).put("sdpMid", mid ?: "").put("sdpMLineIndex", line))
    }

    private fun send(json: JSONObject) {
        synchronized(writerLock) {
            writer?.println(json.toString()) ?: Log.w(TAG, "No Mac signaling client is connected yet")
        }
    }

    fun stop() {
        running.set(false)
        synchronized(writerLock) {
            writer = null
            runCatching { activeSocket?.close() }
            activeSocket = null
        }
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    internal class Advertiser(context: Context) {
        private val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        @Volatile private var listener: NsdManager.RegistrationListener? = null

        fun start(port: Int) {
            if (listener != null) return
            val registration = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    Log.i(TAG, "Advertising Android screen share as ${info.serviceName}:${info.port}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { Log.e(TAG, "Bonjour registration failed: $code") }
                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) { Log.w(TAG, "Bonjour unregistration failed: $code") }
            }
            listener = registration
            val service = NsdServiceInfo().apply {
                serviceName = "Elysium Nexus Android"
                serviceType = SERVICE_TYPE
                this.port = port
            }
            manager.registerService(service, NsdManager.PROTOCOL_DNS_SD, registration)
        }

        fun stop() {
            listener?.let { runCatching { manager.unregisterService(it) } }
            listener = null
        }
    }

    private companion object {
        const val TAG = "NexusScreenSignaling"
        const val DEFAULT_PORT = 9999
        const val SERVICE_TYPE = "_jsm_video._tcp."
    }
}
