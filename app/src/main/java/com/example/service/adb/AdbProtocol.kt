package com.example.service.adb

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AdbProtocol {
    const val A_SYNC = 0x434e5953
    const val A_CNXN = 0x4e584e43
    const val A_AUTH = 0x48545541
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414b4f
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257
    const val A_STLS = 0x534c5453

    const val STLS_VERSION = 0x01000000

    const val AUTH_TYPE_TOKEN = 1
    const val AUTH_TYPE_SIGNATURE = 2
    const val AUTH_TYPE_RSA_PUBLIC = 3

    const val ADB_VERSION = 0x01000000
    const val MAX_PAYLOAD = 1024 * 1024

    data class AdbMessage(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payloadLength: Int,
        val payloadChecksum: Int,
        val magic: Int,
        val payload: ByteArray
    ) {
        fun writeTo(out: OutputStream) {
            val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(command)
            buf.putInt(arg0)
            buf.putInt(arg1)
            buf.putInt(payloadLength)
            buf.putInt(payloadChecksum)
            buf.putInt(magic)
            out.write(buf.array())
            if (payload.isNotEmpty()) {
                out.write(payload)
            }
            out.flush()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AdbMessage
            return command == other.command && arg0 == other.arg0 && arg1 == other.arg1 &&
                    payloadLength == other.payloadLength && payloadChecksum == other.payloadChecksum &&
                    magic == other.magic && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = command
            result = 31 * result + arg0
            result = 31 * result + arg1
            result = 31 * result + payloadLength
            result = 31 * result + payloadChecksum
            result = 31 * result + magic
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    fun calculateChecksum(payload: ByteArray): Int {
        var sum = 0
        for (b in payload) {
            sum += b.toInt() and 0xFF
        }
        return sum
    }

    fun createMessage(command: Int, arg0: Int, arg1: Int, payload: ByteArray): AdbMessage {
        val checksum = calculateChecksum(payload)
        val magic = command xor -0x1
        return AdbMessage(
            command = command,
            arg0 = arg0,
            arg1 = arg1,
            payloadLength = payload.size,
            payloadChecksum = checksum,
            magic = magic,
            payload = payload
        )
    }

    fun readMessage(input: InputStream): AdbMessage {
        val header = ByteArray(24)
        var readTotal = 0
        while (readTotal < 24) {
            val r = input.read(header, readTotal, 24 - readTotal)
            if (r == -1) throw java.io.EOFException("Unexpected EOF while reading ADB header")
            readTotal += r
        }

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.getInt()
        val arg0 = buf.getInt()
        val arg1 = buf.getInt()
        val payloadLength = buf.getInt()
        val payloadChecksum = buf.getInt()
        val magic = buf.getInt()

        val payload = if (payloadLength > 0) {
            val p = ByteArray(payloadLength)
            var pRead = 0
            while (pRead < payloadLength) {
                val r = input.read(p, pRead, payloadLength - pRead)
                if (r == -1) throw java.io.EOFException("Unexpected EOF while reading ADB payload")
                pRead += r
            }
            p
        } else {
            ByteArray(0)
        }

        return AdbMessage(command, arg0, arg1, payloadLength, payloadChecksum, magic, payload)
    }
}
