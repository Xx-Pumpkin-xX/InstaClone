package vn.edu.usth.instaclone.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

// Trả về dòng giá trị true/false: true = đang có mạng
fun observeOnline(context: Context): Flow<Boolean> = callbackFlow {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Kiểm tra trạng thái ngay lúc bắt đầu
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    trySend(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)

    // Lắng nghe khi mạng có hoặc mất
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(true)
        }

        override fun onLost(network: Network) {
            trySend(false)
        }
    }
    cm.registerDefaultNetworkCallback(callback)

    // Khi màn hình đóng thì ngừng lắng nghe
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()