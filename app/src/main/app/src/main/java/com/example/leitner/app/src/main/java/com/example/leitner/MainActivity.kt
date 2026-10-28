package com.example.leitner

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        Notif.schedule(this, getSharedPreferences("w", 0).getInt("h", 9))
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App() }
            }
        }
    }
}

// تلفظ صوتی با موتور TTS گوشی؛ زبان از تنظیمات (پیش‌فرض en)
@Composable fun rememberSpeaker(): (String) -> Unit {
    val c = LocalContext.current
    val tts = remember { TextToSpeech(c) {} }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    return { s ->
        tts.language = Locale.forLanguageTag(c.getSharedPreferences("w", 0).getString("lang", "en") ?: "en")
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}

@Composable fun App() {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("مرور", "افزودن", "لیست", "آمار").forEachIndexed { i, t ->
                NavigationBarItem(tab == i, { tab = i }, icon = {}, label = { Text(t) })
            }
        }
    }) { p ->
        Column(Modifier.padding(p).padding(16.dp).fillMaxSize()) {
            when (tab) { 0 -> Review(); 1 -> AddScreen(); 2 -> ListScreen(); else -> Stats() }
        }
    }
}

@Composable fun Review() {
    val c = LocalContext.current
    val speak = rememberSpeaker()
    var n by remember { mutableIntStateOf(0) }
    var show by remember { mutableStateOf(false) }
    val w = remember(n) { Store.due(c).firstOrNull() }
    if (w == null) { Text("امروز کلمه‌ای برای مرور نداری 🎉", style = MaterialTheme.typography.titleLarge); return }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("جعبه ${w.box}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.word, style = MaterialTheme.typography.displaySmall)
            TextButton({ speak(w.word) }) { Text("🔊", style = MaterialTheme.typography.headlineMedium) }
        }
        if (show) {
            Text(w.meaning, style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ Store.answer(c, w.id, true); show = false; n++ }) { Text("بلد بودم ✅") }
                OutlinedButton({ Store.answer(c, w.id, false); show = false; n++ }) { Text("بلد نبودم ❌") }
            }
        } else Button({ show = true }) { Text("نمایش معنی") }
    }
}

@Composable fun AddScreen() {
    val c = LocalContext.current
    val sp = c.getSharedPreferences("w", 0)
    var w by remember { mutableStateOf("") }
    var m by remember { mutableStateOf("") }
    var h by remember { mutableIntStateOf(sp.getInt("h", 9)) }
    var lang by remember { mutableStateOf(sp.getString("lang", "en") ?: "en") }
    fun setH(x: Int) { h = (x + 24) % 24; sp.edit().putInt("h", h).apply(); Notif.schedule(c, h) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val txt = c.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val n = Store.addAll(c, parseWords(txt))
            Toast.makeText(c, "$n کلمه‌ی جدید اضافه شد", Toast.LENGTH_LONG).show()
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(w, { w = it }, label = { Text("واژه") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(m, { m = it }, label = { Text("معنی") }, modifier = Modifier.fillMaxWidth())
        Button({ if (w.isNotBlank() && m.isNotBlank()) { Store.add(c, w.trim(), m.trim()); w = ""; m = "" } }, Modifier.fillMaxWidth()) { Text("افزودن به جعبه ۱") }
        HorizontalDivider()
        OutlinedButton({ pick.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("وارد کردن از فایل (CSV / TXT)") }
        Text("هر خط یک کلمه: واژه,معنی  (جداکننده: , ; تب | یا -)", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        OutlinedTextField(lang, { lang = it; sp.edit().putString("lang", it.trim()).apply() },
            label = { Text("زبان تلفظ (en, de, fr, es, tr ...)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ساعت یادآوری: $h:00", Modifier.weight(1f))
            OutlinedButton({ setH(h - 1) }) { Text("−") }
            OutlinedButton({ setH(h + 1) }) { Text("+") }
        }
        TextButton({ Notif.show(c) }) { Text("ارسال نوتیف آزمایشی") }
    }
}

@Composable fun ListScreen() {
    val c = LocalContext.current
    val speak = rememberSpeaker()
    var l by remember { mutableStateOf(Store.all(c).toList()) }
    LazyColumn {
        items(l, key = { it.id }) { w ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.word, style = MaterialTheme.typography.titleMedium)
                    Text("${w.meaning} · جعبه ${w.box}")
                }
                TextButton({ speak(w.word) }) { Text("🔊") }
                TextButton({ Store.delete(c, w.id); l = Store.all(c) }) { Text("حذف") }
            }
        }
    }
}

@Composable fun Stats() {
    val c = LocalContext.current
    val l = remember { Store.all(c) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("کل کلمات: ${l.size}", style = MaterialTheme.typography.titleLarge)
        Text("موعد مرور امروز: ${l.count { it.next <= today() }}")
        Text("یادگرفته‌شده (جعبه ۵): ${l.count { it.box == 5 }}")
        HorizontalDivider()
        for (b in 1..5) {
            val k = l.count { it.box == b }
            Text("جعبه $b (مرور هر ${INTERVALS[b - 1]} روز): $k کلمه")
            LinearProgressIndicator(progress = { if (l.isEmpty()) 0f else k / l.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        }
    }
}
