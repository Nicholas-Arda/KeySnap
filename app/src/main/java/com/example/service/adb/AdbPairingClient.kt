package com.example.service.adb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdbPairingClient(private val context: Context) {

    companion object {
        private const val TAG = "AdbPairingClient"
    }

    sealed class PairingResult {
        data class Success(val message: String) : PairingResult()
        data class Error(val error: String, val throwable: Throwable? = null) : PairingResult()
    }

    suspend fun pair(
        host: String = "127.0.0.1",
        pairingPort: Int,
        pairingCode: String,
    ): PairingResult = withContext(Dispatchers.IO) {
        val code = pairingCode.trim()
        if (pairingPort !in 1..65535) {
            return@withContext PairingResult.Error("Invalid ADB pairing port: $pairingPort")
        }
        if (code.isEmpty()) {
            return@withContext PairingResult.Error("ADB pairing code cannot be empty.")
        }

        // Deliberately without $host: that is the device's real address on the user's network,
        // and this project keeps IP addresses out of logs in every build type. The port alone is
        // what makes this line useful for diagnosing a failed pairing.
        Log.d(TAG, "Starting standards-compliant ADB pairing on port $pairingPort")
        try {
            val paired = LibAdbConnectionManager.get(context).pair(host, pairingPort, code)
            if (paired) {
                PairingResult.Success("Pairing completed successfully! (Port: $pairingPort)")
            } else {
                PairingResult.Error("ADB pairing was rejected by the device. Check the code and port.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "ADB pairing failed", error)
            PairingResult.Error(
                "Pairing error: ${error.localizedMessage ?: error.message ?: error.javaClass.simpleName}",
                error,
            )
        }
    }
}
