import android.content.Context
import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.conscrypt.Conscrypt
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/** Single libadb owner so pairing and transport always use the same persistent ADB identity. */
internal object LibAdbConnectionManager {
    private const val TAG = "LibAdbConnectionManager"
    private val lock = Any()

    @Volatile
    private var instance: AbsAdbConnectionManager? = null

    fun get(context: Context): AbsAdbConnectionManager = instance ?: synchronized(lock) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(context: Context): AbsAdbConnectionManager {
        ensureConscryptProvider()
        val identity = com.example.service.adb.AdbCrypto.getOrCreateKeyPair(context)
        return object : AbsAdbConnectionManager() {
            override fun getPrivateKey(): PrivateKey = identity.privateKey
            override fun getCertificate(): Certificate = identity.certificate
            override fun getDeviceName(): String = "keysnap"
        }.apply {
            setApi(Build.VERSION.SDK_INT)
            setTimeout(15, TimeUnit.SECONDS)
            setThrowOnUnauthorised(true)
        }
    }

    private fun ensureConscryptProvider() {
        val provider = Security.getProvider("Conscrypt")
        if (provider == null || provider.javaClass.name != Conscrypt.newProvider().javaClass.name) {
            if (provider != null) Security.removeProvider(provider.name)
            check(Security.insertProviderAt(Conscrypt.newProvider(), 1) > 0) {
                "Unable to install the Conscrypt TLS provider"
            }
        }
        Log.d(TAG, "Using TLS provider ${Security.getProvider("Conscrypt")?.javaClass?.name}")
    }
}
