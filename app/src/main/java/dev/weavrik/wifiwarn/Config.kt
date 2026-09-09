package dev.weavrik.wifiwarn

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** 一条 WiFi 规则：某个 SSID 下需要警告的应用包名集合 */
data class WifiRule(val ssid: String, val packages: MutableSet<String>)

/** 配置存储：多 SSID → 多应用包名 + 三个开关 */
object Config {
    private const val PREFS = "wifi_warn_config"
    private const val KEY_SSID = "ssid"
    private const val KEY_PKG = "target_pkg"
    private const val KEY_RULES = "rules"
    private const val KEY_MASTER = "master_enabled"
    private const val KEY_NOTIF = "notif_enabled"
    private const val KEY_OVERLAY = "overlay_enabled"
    private const val KEY_OVERLAY_GRANTED = "overlay_granted"
    private const val KEY_PERM_REQUESTED = "perm_requested_once"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // 兼容旧版单 SSID/单包名配置，读一次后不会再写回
    fun migrateLegacy(context: Context) {
        val prefs = sp(context)
        val legacySsid = prefs.getString(KEY_SSID, null)?.trim().orEmpty()
        val legacyPkg = prefs.getString(KEY_PKG, null)?.trim().orEmpty()
        if (legacySsid.isEmpty() || legacyPkg.isEmpty() || prefs.contains(KEY_RULES)) return
        val rules = loadRules(context).toMutableList()
        val existing = rules.firstOrNull { it.ssid.equals(legacySsid, true) }
        if (existing != null) {
            existing.packages.add(legacyPkg)
        } else {
            rules.add(WifiRule(legacySsid, mutableSetOf(legacyPkg)))
        }
        saveRules(context, rules)
    }

    fun loadRules(context: Context): MutableList<WifiRule> {
        val json = sp(context).getString(KEY_RULES, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<WifiRule>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ssid = obj.optString("ssid").trim()
                val pkgs = mutableSetOf<String>()
                val pkgArr = obj.optJSONArray("packages")
                if (pkgArr != null) {
                    for (j in 0 until pkgArr.length()) {
                        val pkg = pkgArr.optString(j).trim()
                        if (pkg.isNotEmpty()) pkgs.add(pkg)
                    }
                }
                if (ssid.isNotEmpty()) list.add(WifiRule(ssid, pkgs))
            }
            list
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveRules(context: Context, rules: List<WifiRule>) {
        val arr = JSONArray()
        for (rule in rules) {
            val obj = JSONObject()
            obj.put("ssid", rule.ssid)
            val pkgArr = JSONArray()
            rule.packages.sorted().forEach { pkgArr.put(it) }
            obj.put("packages", pkgArr)
            arr.put(obj)
        }
        sp(context).edit().putString(KEY_RULES, arr.toString()).apply()
    }

    fun findRulesForSsid(context: Context, ssid: String): WifiRule? =
        loadRules(context).firstOrNull { it.ssid.equals(ssid, ignoreCase = true) }

    /** @return true 表示新增成功；SSID 已存在返回 false */
    fun addSsid(context: Context, ssid: String): Boolean {
        val trimmed = ssid.trim()
        if (trimmed.isEmpty()) return false
        val rules = loadRules(context)
        if (rules.any { it.ssid.equals(trimmed, ignoreCase = true) }) return false
        rules.add(WifiRule(trimmed, mutableSetOf()))
        saveRules(context, rules)
        return true
    }

    fun removeSsid(context: Context, ssid: String) {
        val rules = loadRules(context)
        rules.removeAll { it.ssid.equals(ssid, ignoreCase = true) }
        saveRules(context, rules)
    }

    fun addPackage(context: Context, ssid: String, pkg: String) {
        if (pkg.isBlank()) return
        val rules = loadRules(context)
        val rule = rules.firstOrNull { it.ssid.equals(ssid, ignoreCase = true) } ?: return
        if (rule.packages.add(pkg.trim())) saveRules(context, rules)
    }

    fun removePackage(context: Context, ssid: String, pkg: String) {
        val rules = loadRules(context)
        val rule = rules.firstOrNull { it.ssid.equals(ssid, ignoreCase = true) } ?: return
        if (rule.packages.remove(pkg)) saveRules(context, rules)
    }

    fun isMasterEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_MASTER, true)
    fun setMasterEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_MASTER, enabled).apply()

    fun isNotifEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_NOTIF, false)
    fun setNotifEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_NOTIF, enabled).apply()

    fun isOverlayEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_OVERLAY, false)
    fun setOverlayEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_OVERLAY, enabled).apply()

    fun getOverlayGranted(context: Context): Boolean = sp(context).getBoolean(KEY_OVERLAY_GRANTED, false)
    fun setOverlayGranted(context: Context, granted: Boolean) =
        sp(context).edit().putBoolean(KEY_OVERLAY_GRANTED, granted).apply()

    fun hasRequestedPermissions(context: Context): Boolean =
        sp(context).getBoolean(KEY_PERM_REQUESTED, false)

    fun markPermissionsRequested(context: Context) =
        sp(context).edit().putBoolean(KEY_PERM_REQUESTED, true).apply()
}
