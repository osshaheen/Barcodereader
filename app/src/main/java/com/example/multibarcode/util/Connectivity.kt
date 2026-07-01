package com.example.multibarcode.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Simple online/offline check used to route writes to Firestore or the local outbox. */
object Connectivity {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        // Only require that the network claims internet access. We deliberately do NOT require
        // NET_CAPABILITY_VALIDATED: on many real networks (captive checks, blocked Google
        // connectivity probes, slow validation) VALIDATED is false even though the internet
        // works fine — which used to make uploads wrongly report "no internet".
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
