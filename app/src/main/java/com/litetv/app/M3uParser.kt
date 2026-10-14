package com.litetv.app

object M3uParser {

    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(lines: Sequence<String>): List<Channel> {
        val out = ArrayList<Channel>()
        var pendingName: String? = null
        var group = ""
        var logo = ""

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF")) {
                val attrs = HashMap<String, String>()
                for (m in attrRegex.findAll(line)) {
                    attrs[m.groupValues[1].lowercase()] = m.groupValues[2]
                }
                group = attrs["group-title"].orEmpty().trim().ifEmpty { "Sin categoría" }
                logo = attrs["tvg-logo"].orEmpty()

                // El nombre va después de la última comilla (si hay atributos) y de la primera coma.
                val tail = if (line.contains('"')) line.substringAfterLast('"') else line
                val comma = tail.indexOf(',')
                val title = if (comma >= 0) tail.substring(comma + 1).trim() else ""
                pendingName = title.ifEmpty { attrs["tvg-name"].orEmpty().trim() }
            } else if (line.startsWith("#")) {
                continue
            } else {
                val n = pendingName
                if (n != null && line.startsWith("http")) {
                    val name = n.ifEmpty { "Canal ${out.size + 1}" }
                    out.add(Channel(name, line, group, logo))
                }
                pendingName = null
            }
        }
        return out
    }
}
