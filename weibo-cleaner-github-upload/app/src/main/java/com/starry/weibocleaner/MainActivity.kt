package com.starry.weibocleaner

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(CleanerPrefs.FILE, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private lateinit var permissionText: TextView

    private fun buildUi() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            setBackgroundColor(0xFFF7F7F8.toInt())
        }
        scroll.addView(page)

        val title = TextView(this).apply {
            text = "微博批量清理助手"
            textSize = 24f
            setTextColor(0xFF202124.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        page.addView(title)
        page.addView(text("安卓端 · 本机辅助操作", 13f, 0xFF777777.toInt(), 5))
        page.addView(text("选择清理项目", 17f, 0xFF222222.toInt(), 25, true))

        page.addView(actionButton("❤️  清除微博点赞（含图片微博）") {
            setMode(CleanerPrefs.MODE_LIKES)
            launchWeibo()
        })
        page.addView(actionButton("🖼️  清除图片赞") {
            setMode(CleanerPrefs.MODE_IMAGE_LIKES)
            launchWeibo()
        })
        page.addView(actionButton("🗑️  清除不可见微博残留记录") {
            setMode(CleanerPrefs.MODE_HIDDEN_LIKES)
            launchWeibo()
        })
        page.addView(actionButton("👥  清除关注") {
            setMode(CleanerPrefs.MODE_FOLLOWS)
            launchWeibo()
        })

        page.addView(text("权限设置", 17f, 0xFF222222.toInt(), 25, true))
        permissionText = text("正在检查无障碍服务状态…", 13f, 0xFF777777.toInt(), 6)
        page.addView(permissionText)
        page.addView(actionButton("开启无障碍权限") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        page.addView(text("使用步骤", 17f, 0xFF222222.toInt(), 24, true))
        page.addView(text(
            "1. 先开启本助手的无障碍权限。\n" +
            "2. 选择清理模式，打开微博 App。\n" +
            "3. 点赞进入「我 → 我的赞 → 赞」；图片赞进入「我 → 我的赞 → 图片」；不可见残留进入「我 → 我的赞 → 赞」；取关进入「我 → 关注 → 关注的人」。\n" +
            "4. 页面出现本助手悬浮控制条后，先点「扫描本页」，核对识别数量，再点「开始」。图片列表中的缩略图需要先进入具体内容页，识别到已赞控件后才能取消。\n" +
            "5. 处理过程中可以暂停或停止。请保持微博页面在前台。",
            13f, 0xFF555555.toInt(), 6
        ))

        page.addView(text("重要说明", 17f, 0xFF222222.toInt(), 24, true))
        page.addView(text(
            "本工具通过 Android 无障碍服务读取微博当前屏幕上可访问的按钮文字，并模拟点击。普通点赞和图片微博详情页中的已赞按钮按相同规则识别；「图片赞」模式用于你手动打开图片赞列表后扫描。不可见残留模式只会识别同一条内容容器中同时出现‘此微博已不可见/微博已不可见’和‘删除’的按钮，不会把普通微博的删除入口当作目标。不同微博版本的按钮名称和页面结构可能不同，扫描数量为 0 时不会执行。\n\n" +
            "无障碍权限很敏感：服务只响应微博 App（com.sina.weibo），不读取密码、不联网、不上传屏幕内容。请只从你信任的来源安装；不用时可在系统设置里关闭权限。取消点赞和取消关注无法由本工具自动恢复。",
            12f, 0xFF777777.toInt(), 8
        ))

        setContentView(scroll)
        updatePermissionStatus()
    }

    private fun setMode(mode: String) {
        prefs.edit().putString(CleanerPrefs.KEY_MODE, mode).apply()
        Toast.makeText(this, "已选择${CleanerPrefs.modeLabel(mode)}。打开微博后，在目标列表扫描。", Toast.LENGTH_LONG).show()
    }

    private fun launchWeibo() {
        val launch = packageManager.getLaunchIntentForPackage(CleanerPrefs.WEIBO_PACKAGE)
        if (launch == null) {
            Toast.makeText(this, "未检测到微博 App，请先安装并登录微博。", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(launch)
    }

    private fun updatePermissionStatus() {
        if (!::permissionText.isInitialized) return
        val enabled = isServiceEnabled()
        permissionText.text = if (enabled) "已开启。打开微博列表后会出现清理控制条。" else "尚未开启。必须先授予无障碍权限，工具才能操作微博页面。"
        permissionText.setTextColor(if (enabled) 0xFF16833C.toInt() else 0xFFB05A00.toInt())
    }

    private fun isServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expected = ComponentName(this, CleanerAccessibilityService::class.java).flattenToString()
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.let { info -> ComponentName(info.packageName, info.name).flattenToString() == expected } }
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE6162D.toInt()))
        setPadding(dp(12), dp(8), dp(12), dp(8))
        gravity = Gravity.CENTER
        setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = dp(9)
        layoutParams = params
    }

    private fun text(value: String, size: Float, color: Int, top: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(top)
        layoutParams = p
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
