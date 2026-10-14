package com.litetv.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object Store {

    private lateinit var prefs: SharedPreferences
    private lateinit var dir: File

    /** Lista que está viendo el reproductor y posición actual (para cambiar de canal). */
    @Volatile
    var current: List<Channel> = emptyList()

    @Volatile
    var index: Int = 0

    val defaults = listOf(
        Source("Colombia (gratis)", "https://iptv-org.github.io/iptv/countries/co.m3u"),
        Source("Español (gratis)", "https://iptv-org.github.io/iptv/languages/spa.m3u")
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("litetv", Context.MODE_PRIVATE)
        dir = File(context.filesDir, "lists").apply { mkdirs() }
    }

    // ---------- Fuentes ----------

    fun sources(): List<Source> {
        val raw = prefs.getString("sources", null) ?: return defaults
        return raw.lines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size == 2 && p[1].isNotBlank()) Source(p[0], p[1]) else null
        }
    }

    private fun saveSources(list: List<Source>) {
        prefs.edit()
            .putString("sources", list.joinToString("\n") { "${it.name}\t${it.url}" })
            .apply()
    }

    fun addSource(s: Source) {
        val list = sources()
        if (list.none { it.url == s.url }) saveSources(list + s)
    }

    fun removeSource(s: Source) {
        saveSources(sources().filter { it.url != s.url })
        cacheFile(s).delete()
    }

    fun resetSources() {
        sources().forEach { cacheFile(it).delete() }
        prefs.edit().remove("sources").apply()
    }

    // ---------- Favoritos ----------

    fun favorites(): Set<String> = HashSet(prefs.getStringSet("favs", emptySet()) ?: emptySet())

    /** Devuelve true si quedó como favorito. */
    fun toggleFavorite(url: String): Boolean {
        val set = HashSet(favorites())
        val added = set.add(url)
        if (!added) set.remove(url)
        prefs.edit().putStringSet("favs", set).apply()
        return added
    }

    // ---------- Listas ----------

    private fun cacheFile(s: Source) = File(dir, "s" + s.url.hashCode().toString(16) + ".m3u")

    /** Lee las listas guardadas en el dispositivo (rápido, sin red). */
    fun loadCached(): List<Channel> {
        val all = ArrayList<Channel>()
        for (s in sources()) {
            val f = cacheFile(s)
            if (f.exists()) {
                try {
                    all.addAll(f.useLines { M3uParser.parse(it) })
                } catch (_: Exception) {
                }
            }
        }
        return all.distinctBy { it.url }
    }

    /** Descarga todas las fuentes en paralelo. Devuelve true si alguna se actualizó. */
    suspend fun refresh(): Boolean = coroutineScope {
        sources().map { s -> async(Dispatchers.IO) { download(s) } }.awaitAll().any { it }
    }

    private fun download(s: Source): Boolean {
        val target = cacheFile(s)
        val tmp = File(target.path + ".tmp")
        return try {
            val req = Request.Builder().url(s.url).header("User-Agent", "LiteTV/1.0").build()
            client.newCall(req).execute().use { r ->
                val body = r.body
                if (!r.isSuccessful || body == null) return false
                body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            }
            val valid = tmp.bufferedReader().useLines { seq -> seq.take(5).any { it.startsWith("#EXT") } }
            if (!valid) {
                tmp.delete()
                return false
            }
            target.delete()
            tmp.renameTo(target)
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }
}
