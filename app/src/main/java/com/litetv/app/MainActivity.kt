package com.litetv.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Holder(val tv: TextView) : RecyclerView.ViewHolder(tv)

class MainActivity : Activity() {

    companion object {
        const val FAV = "★ Favoritos"
        const val ALL = "Todos"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null

    private var all: List<Channel> = emptyList()
    private var groups: List<String> = listOf(FAV, ALL)
    private var selected = -1
    private var shown: List<Channel> = emptyList()
    private var favs: Set<String> = emptySet()
    private var loading = true
    private var focusedOnce = false

    private lateinit var search: EditText
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView
    private lateinit var channelsView: RecyclerView
    private lateinit var groupAdapter: GroupAdapter
    private lateinit var channelAdapter: ChannelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        search = findViewById(R.id.search)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)
        channelsView = findViewById(R.id.channels)
        val groupsView = findViewById<RecyclerView>(R.id.groups)

        groupAdapter = GroupAdapter()
        channelAdapter = ChannelAdapter()
        groupsView.layoutManager = LinearLayoutManager(this)
        groupsView.adapter = groupAdapter
        groupsView.setHasFixedSize(true)
        channelsView.layoutManager = LinearLayoutManager(this)
        channelsView.adapter = channelAdapter
        channelsView.setHasFixedSize(true)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter()
            }
        })

        findViewById<View>(R.id.btn_sources).setOnClickListener { showSources() }

        favs = Store.favorites()
        load(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ---------- Carga ----------

    private fun load(refresh: Boolean) {
        loadJob?.cancel()
        loadJob = scope.launch {
            loading = true
            val cached = withContext(Dispatchers.IO) { Store.loadCached() }
            setChannels(cached)
            if (cached.isEmpty()) progress.visibility = View.VISIBLE

            if (refresh) {
                val changed = withContext(Dispatchers.IO) { Store.refresh() }
                if (changed) {
                    setChannels(withContext(Dispatchers.IO) { Store.loadCached() })
                }
            }
            loading = false
            progress.visibility = View.GONE
            applyFilter()
        }
    }

    private fun setChannels(list: List<Channel>) {
        val previous = groups.getOrNull(selected)
        all = list
        groups = listOf(FAV, ALL) + list.map { it.group }.distinct().sorted()
        selected = groups.indexOf(previous).takeIf { it >= 0 }
            ?: if (favs.isNotEmpty()) 0 else 1
        groupAdapter.notifyDataSetChanged()
        applyFilter()

        if (!focusedOnce && list.isNotEmpty()) {
            focusedOnce = true
            channelsView.post {
                channelsView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }
    }

    private fun applyFilter() {
        val q = search.text.toString().trim()
        shown = when {
            q.isNotEmpty() -> all.filter { it.name.contains(q, ignoreCase = true) }
            selected == 0 -> all.filter { favs.contains(it.url) }
            selected <= 1 -> all
            else -> {
                val g = groups[selected]
                all.filter { it.group == g }
            }
        }
        channelAdapter.notifyDataSetChanged()

        val msg = when {
            loading || shown.isNotEmpty() -> ""
            q.isNotEmpty() -> "Sin resultados"
            selected == 0 && all.isNotEmpty() ->
                "Aún no tienes favoritos.\nMantén pulsado OK sobre un canal para añadirlo."
            all.isEmpty() -> "No hay canales.\nRevisa tu conexión o añade una lista en «Fuentes»."
            else -> ""
        }
        empty.text = msg
        empty.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---------- Acciones ----------

    private fun open(position: Int) {
        if (position < 0 || position >= shown.size) return
        Store.current = shown
        Store.index = position
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun toggleFav(position: Int) {
        if (position < 0 || position >= shown.size) return
        val added = Store.toggleFavorite(shown[position].url)
        favs = Store.favorites()
        Toast.makeText(
            this,
            if (added) "Añadido a favoritos" else "Quitado de favoritos",
            Toast.LENGTH_SHORT
        ).show()
        if (selected == 0) applyFilter() else channelAdapter.notifyItemChanged(position)
    }

    private fun showSources() {
        val src = Store.sources()
        val labels = src.map { it.name } + "＋ Añadir lista M3U" + "Restaurar listas gratuitas"
        AlertDialog.Builder(this)
            .setTitle("Fuentes")
            .setItems(labels.toTypedArray()) { _, i ->
                when {
                    i < src.size -> confirmDelete(src[i])
                    i == src.size -> addSource()
                    else -> {
                        Store.resetSources()
                        load(true)
                    }
                }
            }
            .show()
    }

    private fun confirmDelete(s: Source) {
        AlertDialog.Builder(this)
            .setTitle(s.name)
            .setMessage(s.url)
            .setPositiveButton("Eliminar") { _, _ ->
                Store.removeSource(s)
                load(false)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addSource() {
        val input = EditText(this).apply {
            hint = "https://…/lista.m3u"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        AlertDialog.Builder(this)
            .setTitle("URL de la lista M3U")
            .setView(input)
            .setPositiveButton("Añadir") { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("http")) {
                    val name = Uri.parse(url).host ?: "Lista"
                    Store.addSource(Source(name, url))
                    load(true)
                } else {
                    Toast.makeText(this, "La URL debe empezar con http", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Adaptadores ----------

    private inner class GroupAdapter : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_group, parent, false) as TextView
            )

        override fun getItemCount() = groups.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            h.tv.text = groups[position]
            h.tv.isSelected = position == selected
            h.tv.setOnClickListener {
                val pos = h.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val old = selected
                selected = pos
                if (old >= 0) notifyItemChanged(old)
                notifyItemChanged(pos)
                if (search.text.isNotEmpty()) search.setText("") else applyFilter()
            }
        }
    }

    private inner class ChannelAdapter : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_channel, parent, false) as TextView
            )

        override fun getItemCount() = shown.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val ch = shown[position]
            h.tv.text = (if (favs.contains(ch.url)) "★  " else "") + ch.name
            h.tv.setOnClickListener { open(h.bindingAdapterPosition) }
            h.tv.setOnLongClickListener {
                toggleFav(h.bindingAdapterPosition)
                true
            }
        }
    }
}
