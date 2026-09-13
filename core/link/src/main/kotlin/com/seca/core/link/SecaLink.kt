package com.seca.core.link

import android.content.Context
import com.seca.core.link.identity.IdentityVault
import com.seca.core.link.identity.LinkIdentity
import com.seca.core.link.relay.PublishResult
import com.seca.core.link.relay.RelayClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Seca Link on this phone: its identity, its relays, and publishing what other
 * Seca phones need to reach it.
 */
class SecaLink(context: Context) {

    private val vault = IdentityVault(context.applicationContext)
    private val relayClient = RelayClient()
    val settings = LinkSettings(context.applicationContext)

    @Volatile
    private var identity: LinkIdentity? = null

    /** The identity, created the first time: generating the keys and sealing them takes a moment. */
    suspend fun identity(): LinkIdentity = identity ?: withContext(Dispatchers.IO) {
        vault.loadOrCreate().also { identity = it }
    }

    /** Publishes the pre-key bundle to every relay at once, reporting each answer as it arrives. */
    fun publishPrekeys(): Flow<Pair<String, PublishResult>> = channelFlow {
        val event = PrekeyBundle.eventOf(identity())
        settings.relays().forEach { url ->
            launch(Dispatchers.IO) {
                val result = relayClient.publish(url, event)
                if (result == PublishResult.Accepted) settings.publishedAt = System.currentTimeMillis()
                send(url to result)
            }
        }
    }
}
