package com.seca.core.link

import android.content.Context
import android.content.Intent
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

/**
 * Tor through Orbot, once the owner turns it on: relays then see a Tor exit
 * rather than the phone's address. Orbot takes SOCKS connections on the phone
 * itself, and relay names are resolved through Tor as well, never locally.
 */
class TorAccess(private val context: Context) {

    /** What Seca Link can expect of Tor right now. */
    enum class State { NotInstalled, NotRunning, Ready }

    fun installed(): Boolean = runCatching { context.packageManager.getPackageInfo(ORBOT, 0) }.isSuccess

    /** Whether Orbot takes connections on its SOCKS port; a short check that touches the network: off the main thread. */
    fun state(): State = when {
        !installed() -> State.NotInstalled
        runCatching { Socket().use { it.connect(InetSocketAddress(HOST, PORT), CHECK_MILLIS) } }.isSuccess -> State.Ready
        else -> State.NotRunning
    }

    /** Asks Orbot to start Tor, which it does in the background when its settings allow. */
    fun requestStart() {
        runCatching {
            context.sendBroadcast(Intent(ACTION_START).setPackage(ORBOT).putExtra(EXTRA_PACKAGE_NAME, context.packageName))
        }
    }

    /** Opens Orbot, or its page in the phone's app store when it is not installed. */
    fun open() {
        val launch = context.packageManager.getLaunchIntentForPackage(ORBOT)
            ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$ORBOT"))
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    companion object {
        const val ORBOT = "org.torproject.android"
        private const val ACTION_START = "org.torproject.android.intent.action.START"
        private const val EXTRA_PACKAGE_NAME = "org.torproject.android.intent.extra.PACKAGE_NAME"
        private const val HOST = "127.0.0.1"
        private const val PORT = 9050
        private const val CHECK_MILLIS = 500

        /** Orbot's SOCKS proxy, on the phone itself. */
        val proxy: Proxy by lazy { Proxy(Proxy.Type.SOCKS, InetSocketAddress(HOST, PORT)) }
    }
}
