package com.londkirs.apkbridge

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import com.londkirs.apkbridge.Bridge.app
import com.londkirs.apkbridge.Bridge.log
import com.londkirs.apkbridge.Bridge.isLocalTarget
import com.londkirs.apkbridge.Bridge.localAddresses
import com.londkirs.apkbridge.Bridge.addressesOf
import com.londkirs.apkbridge.Bridge.LOCAL_HOST

/** Finding the Wireless Debugging pairing / connect services on the network (mDNS). */
object Mdns {

    /**
     * Finds a Wireless Debugging service via mDNS. For the local device the
     * returned host is 127.0.0.1, which adbd accepts and which survives Wi‑Fi IP changes.
     */
    @Suppress("DEPRECATION")
    suspend fun discover(serviceType: String, host: String, timeoutMs: Long): Endpoint? {
        val nsd = app.getSystemService(NsdManager::class.java) ?: return null
        val wantLocal = isLocalTarget(host)
        val locals = localAddresses()
        val result = CompletableDeferred<Endpoint?>()
        val lock = Any()
        val queue = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        var fallback: Endpoint? = null

        fun accept(info: NsdServiceInfo) {
            val addrs = addressesOf(info).mapNotNull { it.hostAddress?.substringBefore('%') }
            val port = info.port
            if (port <= 0) return
            val matches = if (wantLocal) addrs.any { it in locals } else addrs.any { it == host }
            val endpointHost = if (wantLocal) LOCAL_HOST
                else addrs.firstOrNull { it == host } ?: addrs.firstOrNull() ?: host
            val ep = Endpoint(endpointHost, port)
            if (matches) result.complete(ep) else synchronized(lock) { if (fallback == null) fallback = ep }
        }

        fun resolveNext() {
            val next = synchronized(lock) {
                if (resolving || result.isCompleted) null else queue.removeFirstOrNull()?.also { resolving = true }
            } ?: return
            // A fresh listener per call: NsdManager rejects re-using one that is still registered,
            // and older Android versions only resolve one service at a time.
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    synchronized(lock) { resolving = false }
                    resolveNext()
                }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    synchronized(lock) { resolving = false }
                    accept(info)
                    resolveNext()
                }
            }
            runCatching { nsd.resolveService(next, listener) }.onFailure {
                synchronized(lock) { resolving = false }
            }
        }

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                log(tr("גילוי mDNS נכשל (קוד $errorCode).", "mDNS discovery failed (code $errorCode)."))
                result.complete(null)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(lock) { queue.addLast(serviceInfo) }
                resolveNext()
            }
        }

        val multicast = runCatching {
            (app.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager)
                .createMulticastLock("apkbridge-mdns").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        var started = false
        try {
            started = runCatching {
                nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discovery)
            }.isSuccess
            if (!started) return null
            return withTimeoutOrNull(timeoutMs) { result.await() } ?: synchronized(lock) { fallback }
        } finally {
            if (started) runCatching { nsd.stopServiceDiscovery(discovery) }
            runCatching { multicast?.release() }
        }
    }

}
