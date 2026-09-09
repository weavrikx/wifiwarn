package dev.weavrik.wifiwarn

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import dev.weavrik.wifiwarn.databinding.ActivityMainBinding
import dev.weavrik.wifiwarn.databinding.DialogAppPickerBinding
import dev.weavrik.wifiwarn.databinding.ItemAppBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var refreshingSwitches = false
    private var permissionCheckedThisEntry = false
    private var permissionDialogShowing = false
    private val expandedSsids = mutableSetOf<String>()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.POST_NOTIFICATIONS] == true) {
            Config.setNotifEnabled(this, true)
        }
        refreshSwitches()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.migrateLegacy(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSwitches()
        setupBottomNavigation()

        binding.homeView.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.settingsView.btnAddSsid.setOnClickListener { showAddSsidDialog() }
        renderSettings()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        handleOverlayPermissionChange()
        refreshSwitches()
        if (!permissionCheckedThisEntry) {
            permissionCheckedThisEntry = true
            requestMissingPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        permissionCheckedThisEntry = false
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { showTab(settings = false); true }
                R.id.nav_settings -> {
                    renderSettings()
                    showTab(settings = true)
                    true
                }
                else -> false
            }
        }
    }

    private fun showTab(settings: Boolean) {
        binding.homeView.root.visibility = if (settings) View.GONE else View.VISIBLE
        binding.settingsView.root.visibility = if (settings) View.VISIBLE else View.GONE
    }

    private fun setupSwitches() {
        binding.homeView.swMaster.setOnCheckedChangeListener { _, checked ->
            if (refreshingSwitches) return@setOnCheckedChangeListener
            Config.setMasterEnabled(this, checked)
        }

        binding.homeView.swNotif.setOnCheckedChangeListener { _, checked ->
            if (refreshingSwitches) return@setOnCheckedChangeListener
            if (checked && !hasNotificationPermission()) {
                setSwitch(binding.homeView.swNotif, false)
                toast("未授予通知权限，请先授权")
                openNotificationSettings()
                return@setOnCheckedChangeListener
            }
            Config.setNotifEnabled(this, checked)
        }

        binding.homeView.swOverlay.setOnCheckedChangeListener { _, checked ->
            if (refreshingSwitches) return@setOnCheckedChangeListener
            if (checked && !Settings.canDrawOverlays(this)) {
                setSwitch(binding.homeView.swOverlay, false)
                toast("未授予悬浮窗权限，请先授权")
                openOverlaySettings()
                return@setOnCheckedChangeListener
            }
            Config.setOverlayEnabled(this, checked)
        }
    }

    private fun setSwitch(switch: SwitchMaterial, checked: Boolean) {
        refreshingSwitches = true
        switch.isChecked = checked
        refreshingSwitches = false
    }

    private fun refreshSwitches() {
        val notifGranted = hasNotificationPermission()
        val overlayGranted = Settings.canDrawOverlays(this)

        if (!notifGranted) Config.setNotifEnabled(this, false)
        if (!overlayGranted) Config.setOverlayEnabled(this, false)

        setSwitch(binding.homeView.swMaster, Config.isMasterEnabled(this))
        setSwitch(binding.homeView.swNotif, notifGranted && Config.isNotifEnabled(this))
        setSwitch(binding.homeView.swOverlay, overlayGranted && Config.isOverlayEnabled(this))

        binding.homeView.swNotif.alpha = if (notifGranted) 1f else 0.4f
        binding.homeView.swOverlay.alpha = if (overlayGranted) 1f else 0.4f
    }

    private fun missingPermissions(): List<String> {
        val needed = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        return needed
    }

    private fun requestMissingPermissions() {
        val needed = missingPermissions()
        if (needed.isEmpty()) return

        if (!Config.hasRequestedPermissions(this)) {
            Config.markPermissionsRequested(this)
            permLauncher.launch(needed.toTypedArray())
            return
        }

        val permanentlyDenied = needed.any { !shouldShowRequestPermissionRationale(it) }
        showPermissionGuidance(needed, permanentlyDenied)
    }

    private fun showPermissionGuidance(needed: List<String>, permanentlyDenied: Boolean) {
        if (isFinishing || isDestroyed || permissionDialogShowing) return
        val names = needed.joinToString("、") { permissionDisplayName(it) }
        val message = if (permanentlyDenied) {
            "以下权限已被系统限制或永久拒绝，无法再次弹窗申请：$names。请前往设置页手动开启。"
        } else {
            "以下权限尚未授予：$names。为了正常读取当前 WiFi，请前往设置页手动开启。"
        }
        permissionDialogShowing = true
        MaterialAlertDialogBuilder(this)
            .setTitle("需要授权")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ -> openAppSettings() }
            .setNegativeButton("稍后", null)
            .setOnDismissListener { permissionDialogShowing = false }
            .show()
    }

    private fun permissionDisplayName(permission: String): String = when (permission) {
        Manifest.permission.ACCESS_FINE_LOCATION -> "定位"
        Manifest.permission.NEARBY_WIFI_DEVICES -> "附近设备"
        Manifest.permission.POST_NOTIFICATIONS -> "通知"
        else -> permission
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            toast("无法打开设置页")
        }
    }

    private fun handleOverlayPermissionChange() {
        val granted = Settings.canDrawOverlays(this)
        if (granted && !Config.getOverlayGranted(this)) {
            Config.setOverlayEnabled(this, true)
        }
        Config.setOverlayGranted(this, granted)
    }

    private fun updateAccessibilityStatus() {
        val enabled = isAccessibilityEnabled()
        binding.homeView.tvStatus.text = if (enabled) {
            "✓ 无障碍服务已开启"
        } else {
            "✗ 无障碍服务未开启（点击下方按钮开启）"
        }
        binding.homeView.btnOpenAccessibility.isEnabled = !enabled
        binding.homeView.btnOpenAccessibility.text = if (enabled) "无障碍服务已开启" else "开启无障碍服务"
        binding.homeView.btnOpenAccessibility.alpha = if (enabled) 0.5f else 1f
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            toast("无法打开悬浮窗设置页")
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        } catch (_: Exception) {
            toast("无法打开通知权限设置页")
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 || hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    // ---------- 设置页 ----------

    private fun renderSettings() {
        val container = binding.settingsView.ssidContainer
        container.removeAllViews()
        val rules = Config.loadRules(this)

        if (rules.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "还没有添加任何 SSID，点击右上角按钮添加。"
                setPadding(dp(16), dp(48), dp(16), dp(8))
                gravity = Gravity.CENTER_HORIZONTAL
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                textSize = 14f
            })
            return
        }

        for (rule in rules) {
            container.addView(buildRuleView(rule))
        }
    }

    private fun buildRuleView(rule: WifiRule): View {
        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.card_radius)
            cardElevation = resources.getDimension(R.dimen.card_elevation)
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card_bg))
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.divider)
            strokeWidth = dp(1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(7)
                bottomMargin = dp(7)
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(TextView(this).apply {
            text = rule.ssid
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val isExpanded = expandedSsids.contains(rule.ssid)
        header.addView(textButton(if (isExpanded) "收起" else "展开") {
            if (expandedSsids.contains(rule.ssid)) expandedSsids.remove(rule.ssid)
            else expandedSsids.add(rule.ssid)
            renderSettings()
        })
        header.addView(textButton("删除") {
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("删除 SSID")
                .setMessage("确认删除 WiFi「${rule.ssid}」及其应用列表？")
                .setPositiveButton("删除") { _, _ ->
                    Config.removeSsid(this@MainActivity, rule.ssid)
                    expandedSsids.remove(rule.ssid)
                    renderSettings()
                }
                .setNegativeButton("取消", null)
                .show()
        })
        content.addView(header)

        content.addView(TextView(this).apply {
            text = "${rule.packages.size} 个应用"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(6))
        })

        if (isExpanded) {
            content.addView(buildAppList(rule))
        }

        card.addView(content)
        return card
    }

    private fun buildAppList(rule: WifiRule): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        if (rule.packages.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "还没有添加应用。"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                textSize = 13f
                setPadding(0, dp(4), 0, dp(4))
            })
        } else {
            for (pkg in rule.packages.sorted()) {
                list.addView(buildAppRow(rule, pkg))
            }
        }

        list.addView(textButton("+ 添加应用") { showAppPicker(rule) })
        return list
    }

    private fun buildAppRow(rule: WifiRule, pkg: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }

        row.addView(ImageView(this).apply {
            setImageDrawable(appIcon(pkg))
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            contentDescription = appIconLabel(pkg)
        })

        row.addView(TextView(this).apply {
            text = "${appLabel(pkg)}\n$pkg"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
            }
        })

        row.addView(textButton("删除") {
            Config.removePackage(this@MainActivity, rule.ssid, pkg)
            renderSettings()
        })
        return row
    }

    private fun textButton(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primary))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }

    private fun showAddSsidDialog() {
        val input = EditText(this).apply {
            hint = "请输入 WiFi SSID"
            setSingleLine()
            setBackgroundResource(R.drawable.bg_search)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), 0)
            addView(input)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("添加 SSID")
            .setView(wrapper)
            .setPositiveButton("保存", null)
            .setNeutralButton("使用当前 WiFi", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val ssid = WifiHelper.currentSsid(this)
                if (ssid != null) {
                    input.setText(ssid)
                    input.setSelection(input.text.length)
                } else {
                    toast("读取当前 WiFi 失败：请检查定位/附近设备权限")
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val ssid = input.text.toString().trim()
                if (ssid.isEmpty()) {
                    input.error = "请输入 SSID"
                    return@setOnClickListener
                }
                if (Config.addSsid(this, ssid)) {
                    renderSettings()
                    dialog.dismiss()
                } else {
                    input.error = "该 SSID 已存在"
                }
            }
        }
        dialog.show()
    }

    private fun showAppPicker(rule: WifiRule) {
        val pickerBinding = DialogAppPickerBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("选择应用")
            .setView(pickerBinding.root)
            .setNegativeButton("取消", null)
            .create()

        pickerBinding.progressLoading.visibility = View.VISIBLE
        pickerBinding.lvApps.visibility = View.GONE
        dialog.show()

        Thread {
            val apps = loadInstalledApps()
            runOnUiThread {
                if (isFinishing || isDestroyed || !dialog.isShowing) return@runOnUiThread
                val adapter = AppListAdapter(this, apps)
                pickerBinding.lvApps.adapter = adapter
                pickerBinding.lvApps.setOnItemClickListener { _, _, position, _ ->
                    val app = adapter.getItem(position)
                    Config.addPackage(this, rule.ssid, app.packageName)
                    renderSettings()
                    dialog.dismiss()
                }
                pickerBinding.etSearch.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        adapter.filter(s?.toString().orEmpty())
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
                pickerBinding.progressLoading.visibility = View.GONE
                pickerBinding.lvApps.visibility = View.VISIBLE
            }
        }.start()
    }

    private data class AppInfo(val appName: String, val packageName: String, val icon: Drawable?)

    private class AppListAdapter(
        private val context: Context,
        initial: List<AppInfo>
    ) : BaseAdapter() {

        private val inflater = LayoutInflater.from(context)
        private var all: List<AppInfo> = initial
        private var filtered: List<AppInfo> = initial

        fun filter(query: String) {
            val q = query.trim().lowercase()
            filtered = if (q.isEmpty()) {
                all
            } else {
                all.filter { it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            }
            notifyDataSetChanged()
        }

        override fun getCount(): Int = filtered.size

        override fun getItem(position: Int): AppInfo = filtered[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: ViewHolder
            if (convertView == null) {
                val b = ItemAppBinding.inflate(inflater, parent, false)
                view = b.root
                holder = ViewHolder(b.ivIcon, b.tvAppName, b.tvPkg)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }
            val app = filtered[position]
            holder.icon.setImageDrawable(app.icon)
            holder.name.text = app.appName
            holder.pkg.text = app.packageName
            return view
        }

        private class ViewHolder(
            val icon: ImageView,
            val name: TextView,
            val pkg: TextView
        )
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val installed = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            emptyList()
        }
        return installed
            .asSequence()
            .filter { it.enabled }
            .mapNotNull { ai ->
                val name = pm.getApplicationLabel(ai).toString().trim()
                if (name.isEmpty()) null
                else AppInfo(name, ai.packageName, pm.getApplicationIcon(ai))
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    private fun appLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    private fun appIcon(pkg: String): Drawable? = try {
        packageManager.getApplicationIcon(pkg)
    } catch (_: Exception) {
        null
    }

    private fun appIconLabel(pkg: String): String = appLabel(pkg)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
