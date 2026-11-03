package dk.sdu.expenseshare.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Emits whether the device has a network connection that reaches the internet, starting with
 * the current state. Used to retry queued writes as soon as connectivity returns.
 */
fun networkAvailability(context: Context): Flow<Boolean> = callbackFlow {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
    val active = connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
    trySend(active?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
    connectivityManager.registerDefaultNetworkCallback(callback)
    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
