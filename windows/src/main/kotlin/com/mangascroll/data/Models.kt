package com.mangascroll.data

data class Page(val file: String, val w: Int, val h: Int)

data class Chapter(
    val id: String,
    val name: String,
    val added: Long,
    val pages: List<Page>,
    val posIndex: Int = 0,
    val posOffset: Int = 0,
    val progress: Float = 0f,
    val read: Boolean = false,
)

data class Manga(
    val id: String,
    val title: String,
    val created: Long,
    val lastRead: Long = 0L,
    val lastChapterId: String? = null,
    val chapters: List<Chapter> = emptyList(),
    /** AI enhance mode: "off", "reading" (current + next chapters) or "all". */
    val enhance: String = "off",
) {
    val pageCount: Int get() = chapters.sumOf { it.pages.size }
    val readCount: Int get() = chapters.count { it.read }

    /** The chapter "Continue" should open. */
    fun continueChapter(): Chapter? {
        if (chapters.isEmpty()) return null
        val idx = chapters.indexOfFirst { it.id == lastChapterId }
        if (idx >= 0) {
            val last = chapters[idx]
            if (!last.read) return last
            return chapters.drop(idx + 1).firstOrNull { !it.read } ?: last
        }
        return chapters.firstOrNull { !it.read } ?: chapters.first()
    }
}

/** Sorts "2.jpg" before "10.jpg", "ch 9" before "ch 10", etc. */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("[0-9]+|[^0-9]+")
    private fun isNum(s: String) = s[0] in '0'..'9'

    override fun compare(a: String, b: String): Int {
        val ca = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val cb = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val c = if (isNum(x) && isNum(y)) {
                val xs = x.trimStart('0')
                val ys = y.trimStart('0')
                if (xs.length != ys.length) xs.length - ys.length else xs.compareTo(ys)
            } else x.compareTo(y)
            if (c != 0) return c
        }
        return ca.size - cb.size
    }
}
