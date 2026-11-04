package com.starry.weibocleaner

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class CleanerAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var statusView: TextView? = null
    private var countView: TextView? = null
    private var scanButton: Button? = null
    private var startButton: Button? = null
    private var pauseButton: Button? = null
    private var stopButton: Button? = null

    private var running = false
    private var paused = false
    private var mode = CleanerPrefs.MODE_LIKES
    private var succeeded = 0
    private var failed = 0
    private var noScrollRounds = 0
    private val failedSignatures = mutableSetOf<String>()

    private data class Candidate(
        val clickNode: AccessibilityNodeInfo,
        val signature: String,
        val label: String
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createPanel()
        refreshMode()
        showPanel(false)
        setStatus("已连接。打开微博列表后，先扫描当前页面。")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == CleanerPrefs.WEIBO_PACKAGE) {
            showPanel(true)
            refreshMode()
            if (running && !paused) {
                handler.removeCallbacks(stepRunnable)
                handler.postDelayed(stepRunnable, 500)
            }
        } else if (!running) {
            showPanel(false)
        }
    }

    override fun onInterrupt() {
        stopRun("无障碍服务被系统中断。")
    }

    override fun onDestroy() {
        stopRun("服务已关闭。")
        removePanel()
        super.onDestroy()
    }

    private fun refreshMode() {
        val saved = getSharedPreferences(CleanerPrefs.FILE, MODE_PRIVATE)
            .getString(CleanerPrefs.KEY_MODE, CleanerPrefs.MODE_LIKES)
        mode = when (saved) {
            CleanerPrefs.MODE_FOLLOWS -> CleanerPrefs.MODE_FOLLOWS
            CleanerPrefs.MODE_IMAGE_LIKES -> CleanerPrefs.MODE_IMAGE_LIKES
            CleanerPrefs.MODE_HIDDEN_LIKES -> CleanerPrefs.MODE_HIDDEN_LIKES
            else -> CleanerPrefs.MODE_LIKES
        }
        panel?.let {
            it.findViewWithTag<TextView>("mode_label")?.text = "当前模式：${CleanerPrefs.modeLabel(mode)}"
        }
    }

    private fun createPanel() {
        if (panel != null) return
        val context = this
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat()
        }
        val title = TextView(context).apply {
            text = "微博批量清理助手"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.rgb(230, 22, 45))
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val hide = Button(context).apply {
                text = "收起"
                textSize = 11f
                isAllCaps = false
                setOnClickListener { showPanel(false) }
            }
            addView(hide, LinearLayout.LayoutParams(dp(56), dp(38)))
        }
        outer.addView(header)

        val modeText = TextView(context).apply {
            tag = "mode_label"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(dp(2), dp(8), dp(2), dp(5))
        }
        outer.addView(modeText)

        statusView = TextView(context).apply {
            text = "等待扫描…"
            textSize = 12f
            setTextColor(Color.rgb(55, 55, 55))
            setPadding(dp(2), dp(3), dp(2), dp(5))
        }
        outer.addView(statusView)

        countView = TextView(context).apply {
            text = "识别 0 项 · 成功 0 项 · 未确认 0 项"
            textSize = 11f
            setTextColor(Color.GRAY)
            setPadding(dp(2), dp(2), dp(2), dp(7))
        }
        outer.addView(countView)

        val row1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        scanButton = makeButton("扫描本页") { scanPage() }
        startButton = makeButton("开始") { startRun() }
        row1.addView(scanButton, weightedButtonParams())
        row1.addView(startButton, weightedButtonParams())
        outer.addView(row1)

        val row2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        pauseButton = makeButton("暂停") { togglePause() }
        stopButton = makeButton("停止") { stopRun("已停止。") }
        row2.addView(pauseButton, weightedButtonParams())
        row2.addView(stopButton, weightedButtonParams())
        outer.addView(row2)

        panel = outer
        val params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(90)
        }
        try {
            windowManager?.addView(outer, params)
        } catch (_: Exception) {
            panel = null
        }
        refreshMode()
    }

    private fun makeButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.rgb(40, 40, 40))
        setOnClickListener { action() }
    }

    private fun weightedButtonParams() = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
        marginStart = dp(3)
        marginEnd = dp(3)
        topMargin = dp(5)
    }

    private fun showPanel(show: Boolean) {
        panel?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun removePanel() {
        try {
            panel?.let { windowManager?.removeView(it) }
        } catch (_: Exception) { }
        panel = null
    }

    private fun setStatus(message: String) {
        statusView?.text = message
    }

    private fun updateCounts(found: Int = 0) {
        countView?.text = "识别 $found 项 · 成功 $succeeded 项 · 未确认 $failed 项"
    }

    private fun scanPage() {
        refreshMode()
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != CleanerPrefs.WEIBO_PACKAGE) {
            setStatus("请先切换到微博 App 的目标列表页面。")
            return
        }
        val candidates = findCandidates(root, excludeFailed = false)
        val taskName = if (mode == CleanerPrefs.MODE_HIDDEN_LIKES) "不可见残留记录" else "可处理项目"
        setStatus("当前页面识别到 ${candidates.size} 个${taskName}。核对后再点开始。")
        updateCounts(candidates.size)
    }

    private fun startRun() {
        if (running) return
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != CleanerPrefs.WEIBO_PACKAGE) {
            setStatus("请先切换到微博 App 的目标列表页面。")
            return
        }
        val found = findCandidates(root, excludeFailed = false)
        if (found.isEmpty()) {
            setStatus("当前页面没有识别到「已赞」或「已关注」按钮。没有执行任何操作。")
            updateCounts(0)
            return
        }
        succeeded = 0
        failed = 0
        noScrollRounds = 0
        failedSignatures.clear()
        running = true
        paused = false
        setControls()
        setStatus("开始处理。请保持微博在前台，不要离开目标列表。")
        handler.postDelayed(stepRunnable, 300)
    }

    private fun togglePause() {
        if (!running) return
        paused = !paused
        pauseButton?.text = if (paused) "继续" else "暂停"
        setStatus(if (paused) "已暂停。" else "继续处理中…")
        if (!paused) handler.postDelayed(stepRunnable, 250)
    }

    private fun stopRun(message: String) {
        running = false
        paused = false
        handler.removeCallbacks(stepRunnable)
        setControls()
        if (message.isNotBlank()) setStatus(message)
    }

    private fun setControls() {
        startButton?.isEnabled = !running
        scanButton?.isEnabled = !running
        pauseButton?.isEnabled = running
        pauseButton?.text = if (paused) "继续" else "暂停"
        stopButton?.isEnabled = running
    }

    private val stepRunnable = object : Runnable {
        override fun run() {
            if (!running || paused) return
            val root = rootInActiveWindow
            if (root == null || root.packageName?.toString() != CleanerPrefs.WEIBO_PACKAGE) {
                setStatus("微博页面不在前台。请切回微博，任务会暂停等待。")
                handler.postDelayed(this, 900)
                return
            }
            val targets = findCandidates(root, excludeFailed = true)
            if (targets.isNotEmpty()) {
                noScrollRounds = 0
                processCandidate(targets.first())
                return
            }
            val beforeFingerprint = screenFingerprint(root)
            val scrolled = scrollForward(root)
            if (scrolled) {
                setStatus("当前屏没有待处理按钮，正在检查下一屏…")
                handler.postDelayed({
                    if (!running || paused) return@postDelayed
                    val afterRoot = rootInActiveWindow
                    val afterFingerprint = if (afterRoot != null && afterRoot.packageName?.toString() == CleanerPrefs.WEIBO_PACKAGE) screenFingerprint(afterRoot) else beforeFingerprint
                    if (afterFingerprint == beforeFingerprint) noScrollRounds++ else noScrollRounds = 0
                    if (noScrollRounds >= 3) {
                        running = false
                        setControls()
                        setStatus("列表已连续三次没有滚动变化。成功 $succeeded 项，未确认 $failed 项。请重新扫描核对。")
                    } else {
                        handler.postDelayed(this, 150)
                    }
                }, 1200)
            } else {
                noScrollRounds++
                if (noScrollRounds >= 3) {
                    running = false
                    setControls()
                    setStatus("已到当前列表可滚动范围末尾。成功 $succeeded 项，未确认 $failed 项。请重新扫描核对。")
                } else {
                    setStatus("正在确认列表是否到底…")
                    handler.postDelayed(this, 900)
                }
            }
        }
    }

    private fun processCandidate(candidate: Candidate) {
        val rootBefore = rootInActiveWindow
        val countBefore = if (rootBefore != null) findCandidates(rootBefore, true).size else 0
        val clicked = try { candidate.clickNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
        if (!clicked) {
            failed++
            failedSignatures.add(candidate.signature)
            updateCounts(countBefore)
            setStatus("有一个按钮无法点击，已跳过；继续处理其他项目。")
            handler.postDelayed(stepRunnable, 700)
            return
        }
        setStatus("已触发一次操作，正在检查页面状态…")
        handler.postDelayed({
            if (!running) return@postDelayed
            if (mode == CleanerPrefs.MODE_FOLLOWS) confirmUnfollowIfShown()
            if (mode == CleanerPrefs.MODE_HIDDEN_LIKES) confirmDeleteIfShown()
            handler.postDelayed({
                if (!running) return@postDelayed
                val rootAfter = rootInActiveWindow
                val stillThere = if (rootAfter != null && rootAfter.packageName?.toString() == CleanerPrefs.WEIBO_PACKAGE) {
                    findCandidates(rootAfter, excludeFailed = false).any { it.signature == candidate.signature }
                } else true
                if (stillThere) {
                    failed++
                    failedSignatures.add(candidate.signature)
                    setStatus("微博页面没有显示状态变化：该项记为未确认，不会反复点击。")
                } else {
                    succeeded++
                    val successLabel = if (mode == CleanerPrefs.MODE_HIDDEN_LIKES) "已确认删除一条不可见残留记录。" else "已确认页面状态发生变化，继续处理…"
                    setStatus(successLabel)
                }
                val countNow = rootAfter?.let { findCandidates(it, true).size } ?: 0
                updateCounts(countNow)
                handler.postDelayed(stepRunnable, 1100)
            }, 900)
        }, 500)
    }

    private fun confirmUnfollowIfShown() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != CleanerPrefs.WEIBO_PACKAGE) return
        val allText = collectText(root).lowercase(Locale.ROOT)
        if (!allText.contains("取消关注") && !allText.contains("确定取关") && !allText.contains("确定取消关注")) return
        val confirm = findNode(root) { node ->
            val label = nodeLabel(node).replace(" ", "")
            label == "确定" || label == "确认" || label == "确定取消关注"
        }
        confirm?.let { target ->
            val click = clickableAncestor(target) ?: target
            click.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun confirmDeleteIfShown() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != CleanerPrefs.WEIBO_PACKAGE) return
        val allText = collectText(root).replace(" ", "")
        val looksLikeDeleteDialog = listOf("确定删除", "确认删除", "删除这条", "删除该微博").any { allText.contains(it) }
        if (!looksLikeDeleteDialog) return
        val confirm = findNode(root) { node ->
            val label = nodeLabel(node).replace(" ", "").trim()
            label == "确定" || label == "确认" || label == "删除"
        }
        confirm?.let { target ->
            val click = clickableAncestor(target) ?: target
            click.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun findCandidates(root: AccessibilityNodeInfo, excludeFailed: Boolean): List<Candidate> {
        val results = LinkedHashMap<String, Candidate>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isVisibleToUser && node.isEnabled) {
                val label = nodeLabel(node)
                val target = targetForNode(node, label)
                if (target != null) {
                    val clickable = clickableAncestor(node) ?: node
                    val signature = signatureFor(node, target)
                    if ((!excludeFailed || !failedSignatures.contains(signature)) && !results.containsKey(signature)) {
                        results[signature] = Candidate(clickable, signature, target)
                    }
                }
            }
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(root)
        return results.values.toList()
    }

    private fun targetForNode(node: AccessibilityNodeInfo, raw: String): String? {
        val label = raw.replace(" ", "").replace("\n", "").trim()
        if (mode == CleanerPrefs.MODE_HIDDEN_LIKES) {
            if (label != "删除") return null
            return if (belongsToInvisibleLikedPost(node)) "不可见记录删除" else null
        }
        if (mode == CleanerPrefs.MODE_FOLLOWS) {
            return if (label == "已关注" || label == "取消关注" || label.startsWith("已关注(") || label.startsWith("已关注（")) "已关注" else null
        }
        val explicitLiked = listOf("已赞", "已点赞", "取消赞", "取消点赞", "取消喜欢").any { label.contains(it) }
        val hasLikeControl = label.contains("赞") || label.contains("点赞") || label.contains("喜欢")
        val selectedLike = hasLikeControl && (node.isSelected || node.isChecked)
        return if (explicitLiked || selectedLike) "已赞" else null
    }

    private fun belongsToInvisibleLikedPost(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(7) {
            if (current == null) return false
            val text = collectText(current!!).replace(" ", "")
            val invisible = text.contains("此微博已不可见") ||
                text.contains("微博已不可见") ||
                text.contains("内容已不可见") ||
                text.contains("微博不存在") ||
                text.contains("该微博已被删除")
            if (invisible && text.contains("删除")) return true
            current = current!!.parent
        }
        return false
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        return listOf(text, desc).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            if (current == null) return null
            if (current!!.isClickable && current!!.isEnabled) return current
            current = current!!.parent
        }
        return null
    }

    private fun signatureFor(node: AccessibilityNodeInfo, target: String): String {
        var current: AccessibilityNodeInfo? = node
        repeat(4) {
            if (current == null) return@repeat
            val text = collectText(current!!)
                .replace("已关注", "")
                .replace("取消关注", "")
                .replace("已点赞", "")
                .replace("已赞", "")
                .replace("取消点赞", "")
                .replace("取消赞", "")
                .replace(" ", "")
                .take(180)
            if (text.length >= 5) return "$target:$text"
            current = current!!.parent
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return "$target:${node.viewIdResourceName.orEmpty()}:${rect.left}:${rect.top}:${rect.width()}"
    }

    private fun collectText(node: AccessibilityNodeInfo): String {
        val out = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || out.length > 1200) return
            n.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append(' ') }
            n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append(' ') }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(node)
        return out.toString()
    }

    private fun findNode(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNode(child, predicate)
            if (found != null) return found
        }
        return null
    }


    private fun screenFingerprint(root: AccessibilityNodeInfo): String {
        return collectText(root).replace(" ", "").take(1600)
    }

    private fun scrollForward(root: AccessibilityNodeInfo): Boolean {
        val scrollables = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isVisibleToUser && node.isScrollable) scrollables.add(node)
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        val sorted = scrollables.sortedByDescending { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            rect.width() * rect.height()
        }
        return sorted.any { node ->
            try { node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) } catch (_: Exception) { false }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
