package com.elysium.nexus.tvnode.transport

import com.elysium.nexus.tvnode.protocol.TvLinkProtocol
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * TvFrameStream — read/write one [TvLinkProtocol] frame at a time over a
 * real socket (PR2 slice 4, §11 framing + §10 authenticated local channel).
 *
 * This is the transport that carries the phone↔TV link on the wire. It stays
 * pure JVM (InputStream/OutputStream) so the SAME code runs on Android and in
 * the JVM unit tests over real loopback `ServerSocket`/`Socket` — no mock
 * sockets, no fake network. Byte-parity is guaranteed by [TvLinkProtocol]
 * itself (length-prefixed `u32 BE | type | payload`).
 *
 * The caller owns the streams/socket lifecycle. Reads are blocking and
 * single-threaded per end of the link; the state machine above (handshake →
 * ACTION loop) is the only reader per side, as required for ordered Frames.
 */
internal class TvFrameStream(
    private val input: InputStream,
    private val output: OutputStream
) : AutoCloseable {

    /** Send one length-prefixed frame atomically. */
    fun send(type: TvLinkProtocol.FrameType, payload: ByteArray = ByteArray(0)) {
        val frame = TvLinkProtocol.encodeFrame(type, payload)
        output.write(frame)
        output.flush()
    }

    /**
     * Read the next frame. Returns null on a clean end-of-stream (peer
     * closed the socket). Throws [TvFrameStream.ProtocolException] on any
     * malformed input — the caller MUST tear down the link (fail-closed,
     * §10).
     */
    fun read(): TvLinkProtocol.Frame? {
        val lengthBytes = readExactly(4) ?: return null
        if (lengthBytes.isEmpty()) return null
        val length = readUInt32BE(lengthBytes)
        if (length < 1 || length > TvLinkProtocol.MAX_FRAME_SIZE) {
            throw ProtocolException("refusing frame of length $length (max ${TvLinkProtocol.MAX_FRAME_SIZE})")
        }
        val body = readExactly(length.toInt()) ?: throw ProtocolException("peer closed mid-frame")
        val typeByte = body[0]
        val type = TvLinkProtocol.FrameType.fromByte(typeByte)
            ?: throw ProtocolException("unknown frame type $typeByte")
        val payload = ByteArray(body.size - 1)
        System.arraycopy(body, 1, payload, 0, payload.size)
        return TvLinkProtocol.Frame(type, payload)
    }

    override fun close() {
        input.close()
        output.close()
    }

    /** Reads exactly n bytes, or null from a clean EOF. Fails on short read. */
    private fun readExactly(n: Int): ByteArray? {
        val buf = ByteArray(n)
        var pos = 0
        while (pos < n) {
            val r = input.read(buf, pos, n - pos)
            if (r < 0) {
                // 0 bytes consumed is a clean EOF; nothing read mid-frame is an error.
                return if (pos == 0) null else throw EOFException("peer closed mid-frame")
            }
            pos += r
        }
        return buf
    }

    private fun readUInt32BE(b: ByteArray): Long =
        ((b[0].toLong() and 0xFF) shl 24) or
            ((b[1].toLong() and 0xFF) shl 16) or
            ((b[2].toLong() and 0xFF) shl 8) or
            (b[3].toLong() and 0xFF)

    /** Thrown on any malformed or unauthorized wire input — fail-closed. */
    class ProtocolException(message: String) : RuntimeException(message)
}
