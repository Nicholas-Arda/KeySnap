package com.example.service.bridge

import com.example.bridge.ActionTestResult
import com.example.bridge.BridgeProtocol

internal fun parseTestActionReply(line: String?): ActionTestResult? = try {
    val prefix = "${BridgeProtocol.MSG_OK}\t"
    if (line?.startsWith(prefix) == true) ActionTestResult.fromWire(line.removePrefix(prefix)) else null
} catch (_: Exception) {
    null
}
