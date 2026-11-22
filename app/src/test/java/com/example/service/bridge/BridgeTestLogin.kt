package com.example.service.bridge

import com.example.bridge.BridgeAuth
import com.example.bridge.BridgeProtocol
import java.io.BufferedReader
import java.io.Writer
import java.net.Socket

/** Logs a raw test socket in to a bridge the way [BridgeClient] does; returns the line answering AUTH. */
internal fun loginForTest(socket: Socket, reader: BufferedReader, writer: Writer, token: String): String? {
    val endpoint = BridgeAuth.endpoint(socket.localAddress, socket.localPort)
    val clientNonce = BridgeAuth.newNonce()
    writer.write("${BridgeProtocol.CMD_HELLO2}\t$clientNonce\n")
    writer.flush()
    val serverNonce = reader.readLine()?.split('\t')?.getOrNull(1)
    writer.write("${BridgeProtocol.CMD_AUTH}\t${BridgeAuth.proof(token, BridgeAuth.ROLE_CLIENT, endpoint, clientNonce, serverNonce)}\n")
    writer.flush()
    return reader.readLine()
}

/** Answers [BridgeClient]'s login on an accepted [socket] as a protocol-63 bridge holding [token] would; false when the client's proof is wrong. */
internal fun acceptLoginForTest(socket: Socket, reader: BufferedReader, writer: Writer, token: String): Boolean {
    val endpoint = BridgeAuth.endpoint(socket.inetAddress, socket.port)
    val clientNonce = reader.readLine()?.split('\t')?.getOrNull(1)
    val serverNonce = BridgeAuth.newNonce()
    writer.write("${BridgeProtocol.MSG_CHALLENGE}\t$serverNonce\t${BridgeAuth.proof(token, BridgeAuth.ROLE_SERVER, endpoint, clientNonce, serverNonce)}\n")
    writer.flush()
    val ok = reader.readLine() == "${BridgeProtocol.CMD_AUTH}\t${BridgeAuth.proof(token, BridgeAuth.ROLE_CLIENT, endpoint, clientNonce, serverNonce)}"
    writer.write(if (ok) "${BridgeProtocol.MSG_OK}\t${BridgeProtocol.VERSION}\t1\t0\t\n" else "${BridgeProtocol.MSG_ERROR}\tunauthorized\n")
    writer.flush()
    return ok
}
