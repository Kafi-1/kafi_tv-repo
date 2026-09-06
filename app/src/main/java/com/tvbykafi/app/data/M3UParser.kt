package com.tvbykafi.app.data

import com.tvbykafi.app.data.model.Channel

object M3UParser {

    fun parse(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:")) {
                val chno = extractAttribute(line, "tvg-chno")?.toIntOrNull() ?: 0
                val tvgName = extractAttribute(line, "tvg-name") ?: ""
                val tvgLogo = extractAttribute(line, "tvg-logo") ?: ""
                val groupTitle = extractAttribute(line, "group-title") ?: "General"
                val displayName = line.substringAfterLast(",").trim()

                val name = tvgName.ifEmpty { displayName }

                var url = ""
                var j = i + 1
                while (j < lines.size) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                        url = nextLine
                        break
                    }
                    j++
                }

                if (url.isNotEmpty()) {
                    channels.add(
                        Channel(
                            id = "ch_$chno",
                            chno = chno,
                            name = name,
                            logo = tvgLogo,
                            url = url,
                            category = groupTitle,
                            group = groupTitle
                        )
                    )
                }

                i = j + 1
            } else {
                i++
            }
        }

        return channels.sortedBy { it.chno }
    }

    private fun extractAttribute(line: String, attr: String): String? {
        val pattern = """$attr="([^"]*)"""".toRegex()
        return pattern.find(line)?.groupValues?.get(1)
    }
}
