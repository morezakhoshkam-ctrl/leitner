package com.example.leitner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

fun today() = System.currentTimeMillis() / 86400000L

// فاصله مرور هر جعبه (روز): جعبه ۱ تا ۵
val INTERVALS = intArrayOf(1, 2, 4, 8, 16)

data class Word(val id: Long, val word: String, val meaning: String, var box: Int = 1, var next: Long = today())

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("w", 0)

    fun all(c: Context): MutableList<Word> {
        val a = JSONArray(sp(c).getString("d", "[]"))
        return MutableList(a.length()) {
            val o = a.getJSONObject(it)
            Word(o.getLong("id"), o.getString("w"), o.getString("m"), o.getInt("b"), o.getLong("n"))
        }
    }

    fun save(c: Context, l: List<Word>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("w", it.word).put("m", it.meaning).put("b", it.box).put("n", it.next)) }
        sp(c).edit().putString("d", a.toString()).apply()
    }

    fun add(c: Context, w: String, m: String) = save(c, all(c).also { it.add(Word(System.currentTimeMillis(), w, m)) })
    fun delete(c: Context, id: Long) = save(c, all(c).filter { it.id != id })
    fun due(c: Context) = all(c).filter { it.next <= today() }.sortedBy { it.box }

    // بلد بودم → یک جعبه جلو؛ بلد نبودم → برگشت به جعبه ۱
    fun answer(c: Context, id: Long, knew: Boolean) {
        val l = all(c)
        l.find { it.id == id }?.let {
            it.box = if (knew) minOf(it.box + 1, 5) else 1
            it.next = today() + INTERVALS[it.box - 1]
        }
        save(c, l)
    }
}

// ---- وارد کردن گروهی ----
// هر خط: واژه,معنی  (جداکننده: , ; تب | ، یا " - ")
fun parseWords(text: String): List<Pair<String, String>> =
    text.removePrefix("\uFEFF").lines().mapNotNull { line ->
        val p = line.trim().split(Regex("\\s*[,;\\t|،]\\s*|\\s+-\\s+"), 2)
        if (p.size == 2 && p[0].isNotBlank() && p[1].isNotBlank()) p[0].trim('"', ' ') to p[1].trim('"', ' ') else null
    }

fun Store.addAll(c: Context, pairs: List<Pair<String, String>>): Int {
    val l = all(c)
    val have = l.map { it.word.lowercase() }.toMutableSet()
    val t = System.currentTimeMillis()
    var n = 0
    pairs.forEach { (w, m) -> if (have.add(w.lowercase())) { l.add(Word(t + n, w, m)); n++ } }
    save(c, l)
    return n
}
