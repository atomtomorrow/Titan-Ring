package com.titan.ringlight

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private data class AppItem(val pkg: String, val label: String)

    private val palette = listOf(
        "Красный" to 0xFFFF3B30.toInt(),
        "Оранжевый" to 0xFFFF9500.toInt(),
        "Жёлтый" to 0xFFFFCC00.toInt(),
        "Зелёный" to 0xFF34C759.toInt(),
        "Зелёный WhatsApp" to 0xFF25D366.toInt(),
        "Бирюзовый" to 0xFF00C7BE.toInt(),
        "Голубой Telegram" to 0xFF2AABEE.toInt(),
        "Синий" to 0xFF007AFF.toInt(),
        "Фиолетовый" to 0xFFAF52DE.toInt(),
        "Розовый" to 0xFFFF2D55.toInt(),
        "Белый" to 0xFFFFFFFF.toInt(),
    )

    private lateinit var list: ListView
    private lateinit var statusNotif: Button
    private lateinit var statusAcc: Button
    private lateinit var defaultBtn: Button
    private val adapter = AppAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = ListView(this).apply {
            divider = null
            clipToPadding = false
            setOnApplyWindowInsetsListener { v, ins ->
                val s = ins.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, s.top, 0, s.bottom)
                ins
            }
        }
        list.addHeaderView(buildHeader(), null, false)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            (list.getItemAtPosition(pos) as? AppItem)?.let { pickAppColor(it) }
        }
        setContentView(list)
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ---------- UI helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun text(s: CharSequence, size: Float = 14f, bold: Boolean = false, topDp: Int = 4) =
        TextView(this).apply {
            text = s
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(topDp), 0, dp(4))
        }

    private fun button(s: String, onClick: () -> Unit) =
        Button(this).apply { text = s; isAllCaps = false; setOnClickListener { onClick() } }

    private fun switch(label: String, key: String, def: Boolean) = Switch(this).apply {
        text = label
        textSize = 15f
        setPadding(0, dp(8), 0, dp(8))
        isChecked = Prefs.bool(this@MainActivity, key, def)
        setOnCheckedChangeListener { _, on ->
            Prefs.setBool(this@MainActivity, key, on)
            RingBus.settingsChanged()
            if (on && key != Prefs.K_SCREEN_OFF) RingBus.test(testColors())
        }
    }

    private fun seek(label: String, key: String, min: Int, max: Int, def: Int, fmt: (Int) -> String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tv = text("", 14f, topDp = 8)
        val sb = SeekBar(this)
        sb.min = min
        sb.max = max
        sb.progress = Prefs.int(this, key, def)
        tv.text = "$label: ${fmt(sb.progress)}"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                tv.text = "$label: ${fmt(p)}"
                if (!fromUser) return
                Prefs.setInt(this@MainActivity, key, p)
                RingBus.settingsChanged()
                if (key != Prefs.K_SCREEN_MIN) RingBus.test(testColors())
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        box.addView(tv)
        box.addView(sb)
        return box
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(text(title, 18f, bold = true, topDp = 20))
    }

    private fun testColors(): List<Int> =
        Prefs.configuredColors(this).ifEmpty { palette.take(4).map { it.second } }

    // ---------- header with settings ----------

    private fun buildHeader(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        root.addView(text("Titan Ring", 24f, bold = true))
        root.addView(text("Кольцо вокруг фронтальной камеры светится цветом приложения, от которого есть непрочитанные уведомления."))

        section(root, "Разрешения")
        statusNotif = button("") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        statusAcc = button("") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        root.addView(statusNotif)
        root.addView(statusAcc)
        root.addView(text("Если Android пишет «Настройка заблокирована» (ограниченные настройки): откройте «О приложении» → ⋮ → «Разрешить ограниченные настройки» и включите снова.", 12f))
        root.addView(button("Открыть «О приложении»") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })

        section(root, "Основное")
        root.addView(switch("Подсветка включена", Prefs.K_ENABLED, true))
        root.addView(button("▶ Тест (6 секунд)") { RingBus.test(testColors()) })

        section(root, "Вид")
        root.addView(seek("Толщина кольца", Prefs.K_THICK, 1, 12, 3) { "$it dp" })
        root.addView(seek("Отступ от камеры", Prefs.K_GAP, 0, 15, 2) { "$it dp" })
        root.addView(seek("Яркость кольца", Prefs.K_ALPHA, 10, 100, 100) { "$it %" })
        root.addView(switch("Пульсация", Prefs.K_PULSE, true))
        root.addView(switch("Несколько приложений: делить кольцо на сектора (иначе цвета по очереди)", Prefs.K_SEGMENTS, false))

        section(root, "Калибровка положения")
        root.addView(text("Камера ищется автоматически. Если кольцо смещено — двигайте ползунки, тест включится сам.", 12f))
        root.addView(seek("Сдвиг по X", Prefs.K_DX, -150, 150, 0) { "$it px" })
        root.addView(seek("Сдвиг по Y", Prefs.K_DY, -150, 150, 0) { "$it px" })
        root.addView(seek("Радиус", Prefs.K_DR, -40, 60, 0) { "${if (it > 0) "+" else ""}$it px" })

        section(root, "При выключенном экране")
        root.addView(switch("Будить экран при новом уведомлении (чёрный фон + кольцо)", Prefs.K_SCREEN_OFF, false))
        root.addView(seek("Держать включённым", Prefs.K_SCREEN_MIN, 1, 30, 2) { "$it мин" })
        root.addView(text("Нажатие на чёрный экран или кнопка питания закрывает его. Чёрные пиксели AMOLED не светятся, но включённый экран всё равно расходует батарею.", 12f))

        section(root, "Цвета приложений")
        defaultBtn = button("") {
            pickColor("Цвет для остальных приложений", includeDefault = false) { c ->
                Prefs.setInt(this, Prefs.K_DEFAULT, c ?: Prefs.OFF)
                updateDefaultBtn()
                adapter.notifyDataSetChanged()
                NotifListener.refresh()
            }
        }
        root.addView(defaultBtn)
        root.addView(text("Нажмите на приложение, чтобы задать ему цвет. Сверху — настроенные и те, что уже присылали уведомления.", 12f))
        return root
    }

    private fun refreshStatus() {
        val nm = getSystemService(NotificationManager::class.java)
        val notifOk = nm?.isNotificationListenerAccessGranted(ComponentName(this, NotifListener::class.java)) == true
        val me = ComponentName(this, RingService::class.java)
        val accOk = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .split(':').any { ComponentName.unflattenFromString(it) == me }
        statusNotif.text = (if (notifOk) "✅" else "❌") + "  Доступ к уведомлениям"
        statusAcc.text = (if (accOk) "✅" else "❌") + "  Служба «Titan Ring» в Спец. возможностях"
        updateDefaultBtn()
        adapter.notifyDataSetChanged()
    }

    private fun updateDefaultBtn() {
        val c = Prefs.defaultColor(this)
        val prefix = "Остальные приложения: "
        defaultBtn.text = if (c == Prefs.OFF) prefix + "не светят" else SpannableString("$prefix●").apply {
            setSpan(ForegroundColorSpan(c), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(1.5f), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    // ---------- color picking ----------

    private fun dot(color: Int, name: String): CharSequence = SpannableString("●   $name").apply {
        setSpan(ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(RelativeSizeSpan(1.6f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun pickColor(title: String, includeDefault: Boolean, onPick: (Int?) -> Unit) {
        val entries = mutableListOf<Pair<CharSequence, Int?>>()
        if (includeDefault) entries += "Как у остальных" to null
        entries += "Не светить" to Prefs.OFF
        palette.forEach { (name, col) -> entries += dot(col, name) to col }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(entries.map { it.first }.toTypedArray()) { _, i -> onPick(entries[i].second) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun pickAppColor(item: AppItem) {
        pickColor(item.label, includeDefault = true) { c ->
            Prefs.setAppColor(this, item.pkg, c)
            adapter.notifyDataSetChanged()
            NotifListener.refresh()
            if (c != null && c != Prefs.OFF) RingBus.test(listOf(c), 3000)
        }
    }

    // ---------- app list ----------

    private fun loadApps() {
        thread {
            val pm = packageManager
            val map = LinkedHashMap<String, String>()
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, 0).forEach {
                map.putIfAbsent(it.activityInfo.packageName, it.loadLabel(pm).toString())
            }
            val seen = Prefs.seen(this)
            seen.forEach { p ->
                if (p !in map) map[p] = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString()
                } catch (e: Exception) { p }
            }
            map.remove(packageName)
            val items = map.map { AppItem(it.key, it.value) }.sortedWith(
                compareBy<AppItem>(
                    { if (Prefs.appColor(this, it.pkg) != null) 0 else if (it.pkg in seen) 1 else 2 },
                    { it.label.lowercase() }
                )
            )
            runOnUiThread {
                adapter.items = items
                adapter.notifyDataSetChanged()
            }
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        var items: List<AppItem> = emptyList()

        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = position.toLong()

        private fun makeRow(): LinearLayout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(ImageView(context), LinearLayout.LayoutParams(dp(40), dp(40)))
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                addView(TextView(context).apply { textSize = 16f })
                addView(TextView(context).apply { textSize = 12f; alpha = 0.7f })
            }
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            }, LinearLayout.LayoutParams(dp(26), dp(26)))
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: makeRow()
            val item = items[position]
            val icon = row.getChildAt(0) as ImageView
            val texts = row.getChildAt(1) as LinearLayout
            val dotView = row.getChildAt(2)

            icon.setImageDrawable(try { packageManager.getApplicationIcon(item.pkg) } catch (e: Exception) { null })
            (texts.getChildAt(0) as TextView).text = item.label

            val own = Prefs.appColor(this@MainActivity, item.pkg)
            val def = Prefs.defaultColor(this@MainActivity)
            val effective = own ?: def
            (texts.getChildAt(1) as TextView).text = when {
                own == Prefs.OFF -> "не светит"
                own != null -> "свой цвет"
                def == Prefs.OFF -> "как у остальных (не светит)"
                else -> "как у остальных"
            }
            val bg = dotView.background as GradientDrawable
            if (effective == Prefs.OFF) {
                bg.setColor(Color.TRANSPARENT)
                bg.setStroke(dp(2), Color.GRAY)
            } else {
                bg.setColor(effective)
                bg.setStroke(dp(1), Color.GRAY)
            }
            dotView.alpha = if (own == null) 0.45f else 1f
            return row
        }
    }
}
