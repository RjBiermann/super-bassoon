package com.rjbiermann.giffyviewer.core.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Issue #33: OS-level connectivity behind the offline banner. Deliberately a
 * DIFFERENT mechanism from the honest request-failure copy (FeedScreen's
 * IOException branch): this speaks about the DEVICE network, not a response.
 *
 * ConnectivityManager truth, no request probes: initial value read from the
 * active network, later changes ride registerDefaultNetworkCallback events.
 * The registration lives the app-singleton lifetime (same shape as
 * TokenStore — constructed once per shell), so StateFlow holders always get
 * live truth and the banner hides instantly on reconnect.
 */
class NetworkMonitor(
    context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val isOnlineFlow = MutableStateFlow(activeNetworkOnline())

    /** True while the system has a validated network available. */
    val isOnline: StateFlow<Boolean> = isOnlineFlow

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnlineFlow.value = true
            }

            override fun onLost(network: Network) {
                // Only the DEFAULT network loss means offline; re-read in case
                // another network is already (or still) active.
                isOnlineFlow.value = activeNetworkOnline()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                // A captive portal reports a network without VALIDATED.
                isOnlineFlow.value =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }

    init {
        // API 24+ — the floor of both shells (mobile 24 / TV 26).
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    private fun activeNetworkOnline(): Boolean =
        connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}
