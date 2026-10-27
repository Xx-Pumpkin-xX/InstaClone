package vn.edu.usth.instaclone.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

// Trả về dòng giá trị true/false: true = đang có mạng THẬT SỰ ra được internet.
// Chỉ có INTERNET là chưa đủ (vd: Wi-Fi đã kết nối nhưng không ra internet),
// nên phải có thêm VALIDATED (Android đã kiểm tra là ra được internet).
fun observeOnline(context: Context): Flow<Boolean> = callbackFlow {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun hasInternet(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // Kiểm tra trạng thái ngay lúc bắt đầu
    trySend(hasInternet(cm.getNetworkCapabilities(cm.activeNetwork)))

    // Lắng nghe khi trạng thái mạng đổi hoặc mất mạng
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            trySend(hasInternet(caps))
        }

        override fun onLost(network: Network) {
            trySend(false)
        }
    }
    cm.registerDefaultNetworkCallback(callback)

    // Khi màn hình đóng thì ngừng lắng nghe
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()