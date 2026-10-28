package com.sabri.intrudercatcher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class LockableApp(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable? = null,
    var locked: Boolean
)

class AppSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var adapter: AppSelectionAdapter

    /** التطبيق اللي طلب المستخدم قفله حاليًا، ونستنى إعداد PIN/نمط له (V12) */
    private var pendingLockApp: LockableApp? = null
    /** لو المستخدم اختار "بصمة الوجه" وما عنده PIN/نمط أصلًا، نطلبه أولًا كخط رجوع آمن */
    private var pendingBiometricAfterPin: Boolean = false

    private val setPinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            if (pendingBiometricAfterPin) {
                pendingBiometricAfterPin = false
                AppLockPrefs.setUseBiometric(this, true)
            }
            finalizeLockForPendingApp()
        } else {
            pendingLockApp = null
            pendingBiometricAfterPin = false
        }
    }

    private val setPatternLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            finalizeLockForPendingApp()
        } else {
            pendingLockApp = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)

        recyclerView = findViewById(R.id.recyclerApps)
        recyclerView.layoutManager = LinearLayoutManager(this)

        setupBiometricToggle()
        setupSelfLockToggle()
        setupTabs()
        setupDefaultRelockRow()
        setupNavigationRows()
        setupChangeLockCodeRow()
        setupAdminProtectionRow()

        adapter = AppSelectionAdapter(
            fullApps = emptyList(),
            onLockIconClick = { app -> onLockIconClicked(app) },
            onSettingsClick = { app -> showRelockSettingsDialog(app.packageName) }
        )
        recyclerView.adapter = adapter
        setupSearchBar()

        // V48: تحميل التطبيقات في خيط خلفي حتى تظهر الشاشة فورًا
        Thread {
            val apps = try {
                loadLaunchableApps()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "خطأ أثناء تحميل التطبيقات: ${e.message}", Toast.LENGTH_LONG).show()
                }
                emptyList()
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    adapter.setApps(apps)
                    Toast.makeText(this, getString(R.string.applock_apps_found_debug, apps.size), Toast.LENGTH_LONG).show()
                }
            }
        }.start()

        // يُستدعى أخيرًا حتى ما ينلغيش بألوان التبويبات الثابتة اللي بيحطها setupTabs()
        applyTheme()
    }

    /** V16: نعيد تطبيق الألوان عند الرجوع من شاشة "المظهر" - onCreate ما بيتنفذش تاني عند الرجوع بزر الرجوع */
    override fun onResume() {
        super.onResume()
        applyTheme()
        if (::devicePolicyManager.isInitialized) {
            refreshAdminProtectionStatus()
        }
        checkAndRequestLockPermissions()
    }

    private fun checkAndRequestLockPermissions() {
        if (!AppLockPermissions.hasUsageAccess(this)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.permission_usage_access_title))
                .setMessage(getString(R.string.permission_usage_access_desc))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.permission_open_settings)) { _, _ ->
                    AppLockPermissions.requestUsageAccess(this)
                }
                .setNegativeButton(getString(R.string.permission_later), null)
                .show()
            return
        }
        if (!AppLockPermissions.hasOverlayPermission(this)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.permission_overlay_title))
                .setMessage(getString(R.string.permission_overlay_desc))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.permission_open_settings)) { _, _ ->
                    AppLockPermissions.requestOverlayPermission(this)
                }
                .setNegativeButton(getString(R.string.permission_later), null)
                .show()
            return
        }
        if (!AppLockPermissions.hasIgnoreBatteryOptimizations(this)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.permission_battery_title))
                .setMessage(getString(R.string.permission_battery_desc))
                .setPositiveButton(getString(R.string.permission_open_settings)) { _, _ ->
                    AppLockPermissions.requestIgnoreBatteryOptimizations(this)
                }
                .setNegativeButton(getString(R.string.permission_later), null)
                .show()
        }
    }

    // ===================== V14: شريط البحث القابل للطي =====================

    private fun setupSearchBar() {
        val ivSearchIcon = findViewById<View>(R.id.ivSearchIcon)
        val ivSearchBack = findViewById<View>(R.id.ivSearchBack)
        val etSearchQuery = findViewById<android.widget.EditText>(R.id.etSearchQuery)

        fun expandSearch() {
            ivSearchIcon.visibility = View.GONE
            ivSearchBack.visibility = View.VISIBLE
            etSearchQuery.visibility = View.VISIBLE
            etSearchQuery.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etSearchQuery, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        fun collapseSearch() {
            etSearchQuery.text.clear()
            adapter.filter("")
            etSearchQuery.visibility = View.GONE
            ivSearchBack.visibility = View.GONE
            ivSearchIcon.visibility = View.VISIBLE
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etSearchQuery.windowToken, 0)
        }

        ivSearchIcon.setOnClickListener { expandSearch() }
        ivSearchBack.setOnClickListener { collapseSearch() }

        etSearchQuery.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    // ===================== V13: التبويبين (الإعدادات / قائمة التطبيقات) =====================

    private fun setupTabs() {
        val tabSettings = findViewById<TextView>(R.id.tabSettings)
        val tabAppsList = findViewById<TextView>(R.id.tabAppsList)
        val containerSettings = findViewById<View>(R.id.containerSettings)
        val containerAppsList = findViewById<View>(R.id.containerAppsList)

        fun selectSettingsTab() {
            containerSettings.visibility = View.VISIBLE
            containerAppsList.visibility = View.GONE
            tabSettings.setTextColor(ThemeManager.currentPalette(this@AppSelectionActivity).accent)
            tabAppsList.setTextColor(0xFF8A7FA8.toInt())
        }

        fun selectAppsListTab() {
            containerSettings.visibility = View.GONE
            containerAppsList.visibility = View.VISIBLE
            tabAppsList.setTextColor(ThemeManager.currentPalette(this@AppSelectionActivity).accent)
            tabSettings.setTextColor(0xFF8A7FA8.toInt())
        }

        tabSettings.setOnClickListener { selectSettingsTab() }
        tabAppsList.setOnClickListener { selectAppsListTab() }

        selectSettingsTab() // نبدأ دائمًا بتبويب الإعدادات، زي التصميم المرجعي
    }

    /** V13: صف "إعادة القفل الافتراضية" داخل تبويب الإعدادات */
    private fun setupDefaultRelockRow() {
        updateDefaultRelockSummary()
        findViewById<View>(R.id.btnEditDefaultRelock).setOnClickListener {
            showRelockSettingsDialog(targetPackage = null)
        }
    }

    private fun updateDefaultRelockSummary() {
        val summary = findViewById<TextView>(R.id.tvDefaultRelockSummary)
        val mode = AppLockPrefs.getDefaultRelockMode(this)
        summary.text = when (mode) {
            AppLockPrefs.RELOCK_SCREEN_OFF -> getString(R.string.applock_relock_screen_off)
            AppLockPrefs.RELOCK_AFTER_MINUTES -> {
                val minutes = AppLockPrefs.getDefaultRelockMinutes(this)
                "${getString(R.string.applock_relock_after)} $minutes ${getString(R.string.applock_relock_minutes_unit)}"
            }
            else -> getString(R.string.applock_relock_immediate)
        }
    }

    // ===================== V12: أيقونة القفل لكل تطبيق =====================

    /**
     * V19: أول مرة بس (لما مفيش أي رمز محفوظ خالص) بنسأل عن الطريقة
     * (نمط/PIN/بصمة). بعد كده أي تطبيق تاني تضغط قفله، بيتقفل فورًا
     * بنفس الطريقة المحفوظة من غير ما نسأل تاني - بالظبط زي ما طلبت.
     */
    private fun onLockIconClicked(app: LockableApp) {
        if (app.locked) {
            unlockApp(app)
        } else if (AppLockPrefs.hasLockConfigured(this)) {
            pendingLockApp = app
            finalizeLockForPendingApp()
        } else {
            showLockMethodChooser(app)
        }
    }

    private fun showLockMethodChooser(app: LockableApp) {
        val options = arrayOf(
            getString(R.string.applock_type_face),
            getString(R.string.applock_type_fingerprint),
            getString(R.string.applock_method_pattern),
            getString(R.string.applock_method_pin)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.applock_choose_lock_method_title))
            .setItems(options) { _, which ->
                pendingLockApp = app
                when (which) {
                    0, 1 -> chooseFaceLock(app)
                    2 -> choosePatternLock(app)
                    3 -> choosePinLock(app)
                }
            }
            .setNegativeButton(getString(R.string.applock_cancel)) { _, _ -> pendingLockApp = null }
            .show()
    }

    private fun chooseFaceLock(app: LockableApp) {
        val canUseBiometric = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canUseBiometric) {
            Toast.makeText(this, getString(R.string.applock_face_not_supported), Toast.LENGTH_LONG).show()
            pendingLockApp = null
            return
        }

        if (!AppLockPrefs.hasLockConfigured(this)) {
            // البصمة/الوجه دائمًا خيار إضافي فوق PIN أو نمط - لازم خط رجوع
            // آمن أول مرة حتى ما ينحبس المستخدم لو فشلت البصمة يومًا
            pendingBiometricAfterPin = true
            setPinLauncher.launch(Intent(this, SetPinActivity::class.java))
        } else {
            AppLockPrefs.setUseBiometric(this, true)
            finalizeLockForPendingApp()
        }
    }

    private fun choosePatternLock(app: LockableApp) {
        if (AppLockPrefs.hasPattern(this)) {
            AppLockPrefs.setLockType(this, AppLockPrefs.LOCK_TYPE_PATTERN)
            finalizeLockForPendingApp()
        } else {
            setPatternLauncher.launch(Intent(this, SetPatternActivity::class.java))
        }
    }

    private fun choosePinLock(app: LockableApp) {
        if (AppLockPrefs.hasPin(this)) {
            AppLockPrefs.setLockType(this, AppLockPrefs.LOCK_TYPE_PIN)
            finalizeLockForPendingApp()
        } else {
            setPinLauncher.launch(Intent(this, SetPinActivity::class.java))
        }
    }

    private fun finalizeLockForPendingApp() {
        val app = pendingLockApp ?: return
        pendingLockApp = null

        app.locked = true
        val locked = AppLockPrefs.getLockedApps(this).toMutableSet()
        locked.add(app.packageName)
        AppLockPrefs.setLockedApps(this, locked)
        AppLockMonitorService.start(this)

        adapter.notifyAppChanged(app.packageName)
        Toast.makeText(this, getString(R.string.applock_app_locked_toast, app.label), Toast.LENGTH_SHORT).show()
    }

    private fun unlockApp(app: LockableApp) {
        app.locked = false
        val locked = AppLockPrefs.getLockedApps(this).toMutableSet()
        locked.remove(app.packageName)
        AppLockPrefs.setLockedApps(this, locked)

        if (locked.isEmpty()) {
            AppLockMonitorService.stop(this)
        }

        adapter.notifyAppChanged(app.packageName)
        Toast.makeText(this, getString(R.string.applock_app_unlocked_toast, app.label), Toast.LENGTH_SHORT).show()
    }

    // ===================== V12: نافذة إعدادات إعادة القفل لكل تطبيق =====================

    /**
     * V13: نفس نافذة إعادة القفل تُستخدم في حالتين:
     * targetPackage != null -> إعداد خاص بتطبيق معيّن (من تبويب قائمة التطبيقات)
     * targetPackage == null -> الإعداد العام الافتراضي (من تبويب الإعدادات)
     */
    private fun showRelockSettingsDialog(targetPackage: String?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_relock_settings, null)
        val radioImmediate = view.findViewById<RadioButton>(R.id.radioImmediate)
        val radioScreenOff = view.findViewById<RadioButton>(R.id.radioScreenOff)
        val radioAfterMinutes = view.findViewById<RadioButton>(R.id.radioAfterMinutes)
        val seekMinutes = view.findViewById<SeekBar>(R.id.seekMinutes)
        val tvMinutesValue = view.findViewById<TextView>(R.id.tvMinutesValue)
        val btnOk = view.findViewById<android.widget.Button>(R.id.btnRelockOk)
        val btnCancel = view.findViewById<android.widget.Button>(R.id.btnRelockCancel)

        val currentMode = if (targetPackage != null) {
            AppLockPrefs.getRelockMode(this, targetPackage)
        } else {
            AppLockPrefs.getDefaultRelockMode(this)
        }
        val currentMinutes = if (targetPackage != null) {
            AppLockPrefs.getRelockMinutes(this, targetPackage)
        } else {
            AppLockPrefs.getDefaultRelockMinutes(this)
        }

        // V47: إدارة الاختيار يدويًا (زر بعد N دقيقة داخل حاوية فرعية فلا يديره RadioGroup)
        var selectedMode = when (currentMode) {
            AppLockPrefs.RELOCK_SCREEN_OFF -> AppLockPrefs.RELOCK_SCREEN_OFF
            AppLockPrefs.RELOCK_AFTER_MINUTES -> AppLockPrefs.RELOCK_AFTER_MINUTES
            else -> AppLockPrefs.RELOCK_IMMEDIATE
        }
        val radios = listOf(
            radioImmediate to AppLockPrefs.RELOCK_IMMEDIATE,
            radioScreenOff to AppLockPrefs.RELOCK_SCREEN_OFF,
            radioAfterMinutes to AppLockPrefs.RELOCK_AFTER_MINUTES
        )
        val refreshRadios = {
            radios.forEach { (rb, m) -> rb.isChecked = (m == selectedMode) }
        }
        radios.forEach { (rb, m) ->
            rb.setOnClickListener {
                selectedMode = m
                refreshRadios()
            }
        }
        refreshRadios()
        // السلايدر يبدأ من دقيقة واحدة (progress 0 = دقيقة 1)
        seekMinutes.progress = (currentMinutes - 1).coerceIn(0, 59)
        tvMinutesValue.text = currentMinutes.toString()

        seekMinutes.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvMinutesValue.text = (progress + 1).toString()
                if (fromUser) {
                    selectedMode = AppLockPrefs.RELOCK_AFTER_MINUTES
                    refreshRadios()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        btnOk.setOnClickListener {
            val mode = selectedMode
            val minutes = seekMinutes.progress + 1
            if (targetPackage != null) {
                AppLockPrefs.setRelockMode(this, targetPackage, mode)
                AppLockPrefs.setRelockMinutes(this, targetPackage, minutes)
            } else {
                AppLockPrefs.setDefaultRelockMode(this, mode)
                AppLockPrefs.setDefaultRelockMinutes(this, minutes)
                AppLockPrefs.clearAllAppRelockOverrides(this)
                updateDefaultRelockSummary()
            }
            dialog.dismiss()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /**
     * البصمة/الوجه هنا خيار سريع إضافي فوق PIN أو النمط، مو بديل عنهما -
     * لذا يُخفى الزر تمامًا لو الجهاز ما فيه أي بصمة/وجه مسجّل أصلًا.
     */
    private fun setupBiometricToggle() {
        val row = findViewById<android.view.View>(R.id.rowBiometricToggle)
        val switchBiometric = findViewById<Switch>(R.id.switchBiometric)

        val canUseBiometric = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canUseBiometric) {
            row.visibility = View.GONE
            return
        }

        switchBiometric.isChecked = AppLockPrefs.useBiometric(this)
        switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            AppLockPrefs.setUseBiometric(this, isChecked)
        }
    }

    /**
     * V18: نطبّق اللون على شاشة "قائمة التطبيقات" بس (بتبويبيها) بناءً على
     * طلبك - باقي شاشات التطبيق رجعت بألوانها الثابتة الأصلية. أي نص هنا
     * فاضل بلون أبيض/رمادي فاتح ثابت دايمًا (مش مشتق من اللون المختار) عشان
     * يفضل واضح ومقروء مهما كان اللون - اللون بس بيدخل في الخلفيات والروابط
     * القصيرة اللي دايمًا فوق خلفية غامقة ثابتة.
     */
    private fun applyTheme() {
        val palette = ThemeManager.currentPalette(this)

        findViewById<View>(R.id.appSelectionRoot)?.setBackgroundColor(palette.background)
        findViewById<View>(R.id.appSelectionHeader)?.setBackgroundColor(palette.surface)
        findViewById<View>(R.id.appSelectionTabBar)?.setBackgroundColor(palette.surface)

        findViewById<TextView>(R.id.btnEditDefaultRelock)?.setTextColor(palette.accent)
        findViewById<TextView>(R.id.tvAttemptLogArrow)?.setTextColor(palette.accent)
        findViewById<TextView>(R.id.tvThemeArrow)?.setTextColor(palette.accent)

        findViewById<android.widget.ImageView>(R.id.ivSearchIcon)?.setColorFilter(palette.accent)
        findViewById<android.widget.ImageView>(R.id.ivSearchBack)?.setColorFilter(palette.accent)

        // نلوّن التبويب المفعّل حاليًا فقط - غير المفعّل يفضل بلونه الرمادي الثابت
        val tabSettings = findViewById<TextView>(R.id.tabSettings)
        val tabAppsList = findViewById<TextView>(R.id.tabAppsList)
        val containerSettings = findViewById<View>(R.id.containerSettings)
        if (containerSettings?.visibility == View.VISIBLE) {
            tabSettings?.setTextColor(palette.accent)
            tabAppsList?.setTextColor(0xFF8A7FA8.toInt())
        } else {
            tabAppsList?.setTextColor(palette.accent)
            tabSettings?.setTextColor(0xFF8A7FA8.toInt())
        }

        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    /** V15: قفل تطبيق IntruderCatcher نفسه - يستخدم نفس PIN/نمط قفل التطبيقات الأخرى */
    private fun setupSelfLockToggle() {
        val switchSelfLock = findViewById<Switch>(R.id.switchSelfLock)
        switchSelfLock.isChecked = AppLockPrefs.isSelfLockEnabled(this)
        switchSelfLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !AppLockPrefs.hasLockConfigured(this)) {
                Toast.makeText(this, getString(R.string.applock_self_lock_needs_pin), Toast.LENGTH_LONG).show()
                switchSelfLock.isChecked = false
                return@setOnCheckedChangeListener
            }
            AppLockPrefs.setSelfLockEnabled(this, isChecked)
        }
    }

    /** V15: صفوف الانتقال لشاشتي السجل والمظهر */
    private fun setupNavigationRows() {
        findViewById<View>(R.id.rowAttemptLog).setOnClickListener {
            startActivity(Intent(this, AttemptLogActivity::class.java))
        }
        findViewById<View>(R.id.rowTheme).setOnClickListener {
            startActivity(Intent(this, ThemeActivity::class.java))
        }
    }

    /**
     * V23: "تأكيد الهوية قبل التغيير" - قبل ما نسمح بتغيير الرمز، لازم
     * المستخدم يثبت الأول إنه فعلاً صاحب الرمز الحالي (يدخّله صح أو يفتح
     * بالبصمة)، وإلا أي حد ماسك الموبايل مفتوح كان يقدر يغيّر الرمز من
     * غير ما يعرفه أصلًا. نعيد استخدام نفس شاشة تحقق قفل IntruderCatcher
     * نفسه، فبيتسجّل السجل والحماية بنفس المستوى بالظبط.
     */
    private val confirmIdentityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            showLockTypeChooserForChange()
        }
    }

    /** V21: يفتح شاشة إعداد رمز/نمط جديد ليستبدل الرمز الحالي */
    private fun setupChangeLockCodeRow() {
        findViewById<View>(R.id.rowChangeLockCode).setOnClickListener {
            confirmIdentityLauncher.launch(Intent(this, SelfLockActivity::class.java))
        }
    }

    private fun showLockTypeChooserForChange() {
        val options = arrayOf(
            getString(R.string.applock_type_pin),
            getString(R.string.applock_type_pattern),
            getString(R.string.applock_type_face),
            getString(R.string.applock_type_fingerprint)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.applock_choose_type_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, SetPinActivity::class.java))
                    1 -> startActivity(Intent(this, SetPatternActivity::class.java))
                    else -> enableBiometricAsMethod()
                }
            }
            .setNegativeButton(getString(R.string.applock_biometric_cancel), null)
            .show()
    }

    /**
     * V24: تفعيل البصمة (وجه أو إصبع - أندرويد بيشغّل نفس نافذة النظام
     * للاتنين تقنيًا، مفيش طريقة نفرّق بينهم برمجيًا). البصمة دايمًا خيار
     * إضافي سريع فوق رمز PIN/النمط الحالي (لازم يكون عندك رمز أساسي
     * أصلًا - وهو موجود قطعًا هنا لأن الوصول لشاشة "تغيير الرمز" أصلًا
     * بيتطلب رمز حالي شغّال).
     */
    private fun enableBiometricAsMethod() {
        val canUseBiometric = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canUseBiometric) {
            Toast.makeText(this, getString(R.string.applock_face_not_supported), Toast.LENGTH_LONG).show()
            return
        }
        AppLockPrefs.setUseBiometric(this, true)
        findViewById<Switch>(R.id.switchBiometric)?.isChecked = true
        Toast.makeText(this, getString(R.string.applock_biometric_enabled_toast), Toast.LENGTH_SHORT).show()
    }

    /** V21: حالة صلاحية مدير الجهاز (حماية إزالة التثبيت) + زر تفعيلها لو مش مفعّلة */
    private fun setupAdminProtectionRow() {
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, LockAdminReceiver::class.java)

        refreshAdminProtectionStatus()

        findViewById<View>(R.id.btnEnableAdminProtection).setOnClickListener {
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.applock_admin_enable_explanation)
                    )
                }
                startActivity(intent)
            }
        }
    }

    private fun refreshAdminProtectionStatus() {
        val active = devicePolicyManager.isAdminActive(adminComponent)
        val statusText = findViewById<TextView>(R.id.tvAdminProtectionStatus)
        val enableButton = findViewById<View>(R.id.btnEnableAdminProtection)
        if (active) {
            statusText.text = getString(R.string.applock_uninstall_protection_active)
            statusText.setTextColor(0xFF4CAF50.toInt())
            enableButton.visibility = View.GONE
        } else {
            statusText.text = getString(R.string.applock_uninstall_protection_inactive)
            statusText.setTextColor(0xFFF44336.toInt())
            enableButton.visibility = View.VISIBLE
        }
    }

    private fun loadLaunchableApps(): List<LockableApp> {
        val pm = packageManager
        // نستخدم queryIntentActivities بدل getInstalledApplications - يرجع فقط
        // التطبيقات اللي فعليًا لها أيقونة قابلة للفتح، وما يحتاج صلاحية
        // QUERY_ALL_PACKAGES الموسّعة
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(mainIntent, android.content.pm.PackageManager.MATCH_ALL)
        val lockedSet = AppLockPrefs.getLockedApps(this)

        return resolved
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != packageName } // ما نقفل نفسنا
            .distinctBy { it.packageName }
            .mapNotNull { appInfo: ApplicationInfo ->
                try {
                    LockableApp(
                        packageName = appInfo.packageName,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        locked = lockedSet.contains(appInfo.packageName)
                    )
                } catch (e: Exception) {
                    // تطبيق واحد فيه مشكلة (أيقونة تالفة مثلًا) - نتجاهله بدل ما نوقف القائمة كلها
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }
}
