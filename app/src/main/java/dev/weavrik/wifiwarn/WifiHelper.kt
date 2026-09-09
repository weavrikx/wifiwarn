package dev.weavrik.wifiwarn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/** WiFi 连接状态判断 */
object WifiHelper {

    /** 当前连接的 WiFi SSID；非 WiFi 或读取失败返回 null */
    fun currentSsid(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = try {
            cm.getNetworkCapabilities(cm.activeNetwork)
        } catch (e: Exception) {
            Log.d("WifiWarn", "currentSsid: getNetworkCapabilities failed: $e")
            null
        } ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            Log.d("WifiWarn", "currentSsid: not WiFi transport")
            return null
        }
        Log.d("WifiWarn", "currentSsid: active network is WiFi, sdk=${Build.VERSION.SDK_INT}")

        if (Build.VERSION.SDK_INT >= 29) {
            val reflected = try {
                reflectSsid(caps)
            } catch (e: Exception) {
                Log.d("WifiWarn", "currentSsid: reflection exception: $e")
                null
            }
            Log.d("WifiWarn", "currentSsid: reflection result=$reflected")
            if (reflected != null) return reflected
            Log.d("WifiWarn", "currentSsid: reflection failed/denied/empty, falling back to getConnectionInfo")
        }

        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            val ssid = normalizeSsid(info?.ssid)
            Log.d("WifiWarn", "currentSsid: using getConnectionInfo, ssid=$ssid")
            ssid
        } catch (e: Exception) {
            Log.d("WifiWarn", "currentSsid: getConnectionInfo exception: $e")
            null
        }
    }

    private fun reflectSsid(caps: NetworkCapabilities): String? {
        for (methodName in arrayOf("ssid", "getSsid")) {
            try {
                val method = caps.javaClass.getMethod(methodName)
                Log.d("WifiWarn", "currentSsid: reflected method found: $methodName")
                val value = method.invoke(caps)
                val ssid = normalizeSsid(value?.toString())
                Log.d("WifiWarn", "currentSsid: reflected $methodName raw=$value normalized=$ssid")
                if (ssid != null) return ssid
            } catch (e: Exception) {
                Log.d("WifiWarn", "currentSsid: reflected $methodName failed: $e")
                return null
            }
        }
        return null
    }

    private fun normalizeSsid(raw: String?): String? {
        val ssid = raw?.trim().orEmpty().removeSurrounding("\"")
        return ssid.takeIf { it.isNotEmpty() && !it.equals("<unknown ssid>", true) }
    }
}
