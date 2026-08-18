package com.elysium.nexus.tvnode.transport

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * TvLinkListener — the TV-side control surface (Master Order v0.10 Phase 20,
 * audit P0-12: never publish a made-up port; bind first, advertise the real
 * bound port).
 *
 * Binds a real [ServerSocket] on the loopback-visible wildcard interface and
 * serves every accepted socket with [TvLinkServer] (handshake → mandatory
 * pairing gate → ACTION loop). The caller reads [boundPort] AFTER [start]
 * succeeds and hands it to discovery — `port = 0` is never advertised.
 *
 * Pure JVM: unit tests bind real ports on this host (like the transport
 * tests already do) and run a full phone↔TV round trip.
 */
class TvLinkListener(
    private val dispatcher: TvActionDispatcher,
    private val pairingGateProvider: () -> PairingGate,
    private val maxPending: Int = 8
) {

    sealed class State {
        object Stopped : State()
        data class Bound(val port: Int) : State()
        data class Failed(val reason: String) : State()
    }

    private val running = AtomicBoolean(false)
    private val stateRef = AtomicReference<State>(State.Stopped)
    private val serverSocketRef = AtomicReference<ServerSocket>()

    /** The real bound port once [start] succeeded; 0 otherwise. */
    val boundPort: Int get() = (stateRef.get() as? State.Bound)?.port ?: 0

    fun start(): State {
        if (!running.compareAndSet(false, true)) return stateRef.get()
        return try {
            val serverSocket = ServerSocket(0, maxPending)
            serverSocketRef.set(serverSocket)
            stateRef.set(State.Bound(serverSocket.localPort))
            Thread {
                try {
                    while (running.get()) {
                        val socket = try {
                            serverSocket.accept()
                        } catch (e: Exception) {
                            break // server socket closed (stop) or fatal accept error
                        }
                        Thread {
                            TvLinkServer(dispatcher, pairingGateProvider()).handle(socket)
                        }.apply { isDaemon = true }.start()
                    }
                } finally {
                    runCatching { serverSocket.close() }
                }
            }.apply { isDaemon = true }.start()
            stateRef.get()
        } catch (e: Exception) {
            running.set(false)
            stateRef.set(State.Failed(e.message ?: e.javaClass.simpleName))
            stateRef.get()
        }
    }

    /** Stop accepting and release the port. In-flight links are not severed. */
    fun stop() {
        running.set(false)
        // Closing the server socket unblocks a pending accept() (fail-closed:
        // a stopped listener must never keep a half-open accept).
        serverSocketRef.getAndSet(null)?.let { runCatching { it.close() } }
        stateRef.set(State.Stopped)
    }

    fun state(): State = stateRef.get()
}