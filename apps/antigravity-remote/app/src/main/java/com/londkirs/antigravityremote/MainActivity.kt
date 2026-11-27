package com.londkirs.antigravityremote

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var listBox: LinearLayout
    private lateinit var localBox: LinearLayout
    private val textColor = Color.parseColor("#E8EAF6")
    private val mutedColor = Color.parseColor("#9AA3C7")
    private val cardColor = Color.parseColor("#1A1F36")
    private val accent = Color.parseColor("#7C8CFF")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Launcher opens the chats directly once agy is installed; this screen stays reachable from the chat menu.
        if (savedInstanceState == null && intent?.action == Intent.ACTION_MAIN && AgyInstaller.isInstalled(this)) {
            startActivity(Intent(this, ChatListActivity::class.java))
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }
        root.addView(TextView(this).apply {
            setText(R.string.main_title)
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textColor)
        })
        root.addView(TextView(this).apply {
            setText(R.string.main_subtitle)
            textSize = 14f
            setTextColor(mutedColor)
            setPadding(0, dp(6), 0, dp(18))
        })

        root.addView(sectionLabel(R.string.local_section))
        localBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(localBox)

        root.addView(sectionLabel(R.string.remote_section))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(pillButton(getString(R.string.paste_link)) { pasteLink() }, weighted())
        actions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        actions.addView(pillButton(getString(R.string.setup_guide)) { showGuide() }, weighted())
        root.addView(actions)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, 0) }
        root.addView(listBox)
        root.addView(pillButton(getString(R.string.add_connection)) { editConnection(null) }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8)
        })

        setContentView(ScrollView(this).apply { addView(root); isFillViewport = true })
    }

    override fun onResume() {
        super.onResume()
        render()
        renderLocal()
    }

    private fun sectionLabel(res: Int) = TextView(this).apply {
        setText(res)
        textSize = 13f
        setTextColor(mutedColor)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(4), dp(18), 0, dp(8))
    }

    // ---- Run Antigravity on this phone ------------------------------------------------------------

    private fun renderLocal() {
        localBox.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        card.addView(TextView(this).apply {
            setText(R.string.local_title)
            textSize = 17f
            setTextColor(textColor)
            setTypeface(typeface, Typeface.BOLD)
        })
        val supported = AgyInstaller.isSupportedDevice()
        val installed = AgyInstaller.isInstalled(this)
        val status = when {
            !supported -> getString(R.string.local_unsupported)
            installed -> getString(R.string.local_installed, AgyInstaller.installedVersion(this) ?: "?")
            else -> getString(R.string.local_not_installed)
        }
        card.addView(TextView(this).apply {
            text = status
            textSize = 13f
            setTextColor(mutedColor)
            setPadding(0, dp(4), 0, 0)
        })
        // Shell is always available; agy can be started whenever it is installed.
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        val gap = { LinearLayout.LayoutParams(dp(8), 1) }
        if (installed) {
            row.addView(smallButton(getString(R.string.chat), true) { startActivity(Intent(this, ChatListActivity::class.java)) })
            row.addView(View(this), gap())
            row.addView(smallButton(getString(R.string.start_agy), false) { openTerminal(TerminalService.KIND_AGY) })
            row.addView(View(this), gap())
            row.addView(smallButton(getString(R.string.visual_ui), false) { startHub() })
            row.addView(View(this), gap())
        } else if (supported) {
            row.addView(smallButton(getString(R.string.install_agy), true) { install() })
            row.addView(View(this), gap())
        }
        row.addView(smallButton(getString(R.string.shell), false) { openTerminal(TerminalService.KIND_SHELL) })
        if (installed && supported) {
            row.addView(View(this), gap())
            row.addView(smallButton(getString(R.string.update), false) { install() })
        }
        card.addView(row)
        localBox.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun openTerminal(kind: String?) {
        startActivity(Intent(this, TerminalActivity::class.java).apply { kind?.let { putExtra(TerminalActivity.EXTRA_NEW, it) } })
    }

    /** Starts `agy --hub` in the background service and shows its web UI in the app's WebView. */
    private fun startHub() {
        val label = TextView(this).apply { setText(R.string.hub_starting); setPadding(dp(24), dp(16), dp(24), dp(8)) }
        val dialog = AlertDialog.Builder(this).setView(label).setCancelable(false).create()
        dialog.show()
        HubClient.ensureRunning(this) { ok ->
            dialog.dismiss()
            if (ok) {
                startActivity(Intent(this, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, TerminalService.hubUiUrl ?: TerminalService.HUB_URL))
            } else {
                Toast.makeText(this, R.string.hub_failed, Toast.LENGTH_LONG).show()
                openTerminal(null)
            }
        }
    }

    private fun install() {
        val bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        val label = TextView(this).apply { setText(R.string.stage_manifest); setPadding(0, 0, 0, dp(8)) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(label); addView(bar)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.installing)
            .setView(box)
            .setCancelable(false)
            .create()
        dialog.show()
        val main = android.os.Handler(mainLooper)
        Thread {
            try {
                val release = AgyInstaller.install(this) { stage, done, total ->
                    main.post {
                        when (stage) {
                            AgyInstaller.STAGE_MANIFEST -> { label.setText(R.string.stage_manifest); bar.isIndeterminate = true }
                            AgyInstaller.STAGE_DOWNLOAD -> {
                                label.text = getString(R.string.stage_download, done / 1048576, if (total > 0) total / 1048576 else 0)
                                bar.isIndeterminate = total <= 0
                                if (total > 0) bar.progress = (done * 1000 / total).toInt()
                            }
                            AgyInstaller.STAGE_VERIFY -> { label.setText(R.string.stage_verify); bar.isIndeterminate = true }
                            AgyInstaller.STAGE_EXTRACT -> { label.setText(R.string.stage_extract); bar.isIndeterminate = true }
                        }
                    }
                }
                main.post {
                    dialog.dismiss()
                    renderLocal()
                    Toast.makeText(this, getString(R.string.install_done, release.version), Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, ChatListActivity::class.java))
                }
            } catch (e: Exception) {
                main.post {
                    dialog.dismiss()
                    renderLocal()
                    AlertDialog.Builder(this)
                        .setTitle(R.string.install_failed)
                        .setMessage(e.message ?: e.toString())
                        .setPositiveButton(R.string.close, null)
                        .show()
                }
            }
        }.start()
    }

    private fun weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun rounded(color: Int, radius: Int = 14) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun pillButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(textColor)
        background = rounded(Color.parseColor("#2A3157"))
        stateListAnimator = null
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun smallButton(label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setTextColor(if (filled) Color.WHITE else textColor)
        background = rounded(if (filled) accent else Color.parseColor("#2A3157"), 10)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun render() {
        listBox.removeAllViews()
        val list = Connections.load(this)
        list.forEach { c ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(cardColor)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setOnClickListener { open(c) }
                setOnLongClickListener { editConnection(c); true }
            }
            card.addView(TextView(this).apply {
                text = c.name
                textSize = 17f
                setTextColor(textColor)
                setTypeface(typeface, Typeface.BOLD)
            })
            card.addView(TextView(this).apply {
                text = c.url
                textSize = 12f
                setTextColor(mutedColor)
                textDirection = View.TEXT_DIRECTION_LTR
                setSingleLine()
            })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
            }
            val gap = { LinearLayout.LayoutParams(dp(8), 1) }
            row.addView(smallButton(getString(R.string.open_in_app), true) { openInApp(c.url) })
            row.addView(View(this), gap())
            row.addView(smallButton(getString(R.string.open_in_chrome), false) { Connections.openInChrome(this, c.url) })
            row.addView(View(this), gap())
            row.addView(smallButton(getString(R.string.edit), false) { editConnection(c) })
            card.addView(row)
            listBox.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            })
        }
    }

    private fun open(c: Connection) {
        if (c.chrome) Connections.openInChrome(this, c.url) else openInApp(c.url)
    }

    private fun openInApp(url: String) {
        startActivity(Intent(this, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, url))
    }

    private fun pasteLink() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)
        val url = Connections.findUrl(text)
        if (url == null) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
        } else {
            openInApp(url)
        }
    }

    private fun showGuide() {
        AlertDialog.Builder(this)
            .setTitle(R.string.guide_title)
            .setMessage(R.string.guide_body)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun editConnection(existing: Connection?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(this).apply {
            setHint(R.string.name_hint)
            setText(existing?.name ?: "")
            setSingleLine()
        }
        val url = EditText(this).apply {
            setHint(R.string.url_hint)
            setText(existing?.url ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            textDirection = View.TEXT_DIRECTION_LTR
            setSingleLine()
        }
        val modeLabel = TextView(this).apply { setText(R.string.mode_label); setPadding(0, dp(12), 0, 0) }
        val web = RadioButton(this).apply { id = View.generateViewId(); setText(R.string.mode_webview) }
        val chrome = RadioButton(this).apply { id = View.generateViewId(); setText(R.string.mode_chrome) }
        val group = RadioGroup(this).apply { addView(web); addView(chrome) }
        group.check(if (existing?.chrome == true) chrome.id else web.id)
        box.addView(name); box.addView(url); box.addView(modeLabel); box.addView(group)

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_connection else R.string.edit)
            .setView(box)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.delete) { _, _ -> confirmDelete(existing) }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val normalized = Connections.normalize(url.text.toString())
                if (normalized == null) {
                    url.error = getString(R.string.invalid_url)
                    return@setOnClickListener
                }
                val label = name.text.toString().trim().ifEmpty { android.net.Uri.parse(normalized).host ?: normalized }
                val list = Connections.load(this)
                val item = Connection(existing?.id ?: UUID.randomUUID().toString(), label, normalized, group.checkedRadioButtonId == chrome.id)
                val idx = list.indexOfFirst { it.id == item.id }
                if (idx >= 0) list[idx] = item else list += item
                Connections.save(this, list)
                render()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDelete(c: Connection) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                Connections.save(this, Connections.load(this).filterNot { it.id == c.id })
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
