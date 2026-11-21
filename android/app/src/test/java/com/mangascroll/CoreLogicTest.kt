package com.mangascroll

import com.mangascroll.data.NaturalOrder
import com.mangascroll.reader.buildSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreLogicTest {

    @Test
    fun pagesSortByNumberNotText() {
        // the order the pages were actually sent in
        val sent = listOf("08.jpg", "09.jpg", "10.jpg", "11.jpg", "12.jpg", "13.jpg",
            "01.jpg", "02.jpg", "03.jpg", "04.jpg", "05.jpg", "06.jpg", "07.jpg")
        assertEquals((1..13).map { "%02d.jpg".format(it) }, sent.sortedWith(NaturalOrder))

        assertEquals(listOf("1.png", "2.png", "10.png"), listOf("10.png", "2.png", "1.png").sortedWith(NaturalOrder))
        assertEquals(
            listOf("Chapter 2", "Chapter 9", "chapter 10", "Chapter 100"),
            listOf("Chapter 100", "chapter 10", "Chapter 9", "Chapter 2").sortedWith(NaturalOrder),
        )
        assertEquals(listOf("page_001", "page_02", "page_3"), listOf("page_3", "page_02", "page_001").sortedWith(NaturalOrder))
    }

    @Test
    fun slicesCoverEveryPixelWithNoGapsOrOverlap() {
        // real sizes of the 13 uploaded pages (728 px wide, 3001–5489 px tall)
        val heights = listOf(5489, 3352, 3001, 3357, 3001, 3713, 3606, 4103, 3005, 3855, 3764, 4257, 4427)
        val pages = heights.mapIndexed { i, h -> Triple(File("%02d.jpg".format(i + 1)), 728, h) }
        val (segs, starts) = buildSegments(pages)

        heights.forEachIndexed { i, h ->
            val mine = segs.filter { it.pageIndex == i }
            assertEquals(starts[i], segs.indexOf(mine.first()))
            assertEquals(0, mine.first().top)
            assertEquals(h, mine.last().bottom)
            mine.zipWithNext().forEach { (a, b) -> assertEquals(a.bottom, b.top) }
            mine.forEach { assertTrue("slice too tall", it.bottom - it.top <= 728 * 2 + 728 / 4) }
        }
        println("13 pages, ${heights.sum()} px tall in total -> ${segs.size} slices")
    }
}
