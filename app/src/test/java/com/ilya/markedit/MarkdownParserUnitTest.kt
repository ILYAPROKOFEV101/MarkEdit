import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.ilya.markedit.utils.MarkdownParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownParserTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testEmptyMarkdown() {
        val views = MarkdownParser.parse(context, "")
        assertTrue(views.isEmpty())
    }



    @Test
    fun testHeaderParsing() {
        val markdown = "# Header 1"
        val views = MarkdownParser.parse(context, markdown)

        assertEquals(1, views.size)
        assertTrue(views[0] is TextView)
        val textView = views[0] as TextView
        assertEquals("Header 1", textView.text.toString())
        assertEquals(24f, textView.textSize / context.resources.displayMetrics.scaledDensity)
        assertEquals(Typeface.BOLD, textView.typeface.style)
    }

    @Test
    fun testMultipleHeaderLevels() {
        val headers = (1..6).map { "#".repeat(it) + " Header $it" }.joinToString("\n")
        val views = MarkdownParser.parse(context, headers)

        assertEquals(6, views.size)
        views.forEachIndexed { index, view ->
            assertTrue(view is TextView)
            assertEquals(24 - (index * 2), (view as TextView).textSize.toInt() / context.resources.displayMetrics.scaledDensity.toInt())
        }
    }

    @Test
    fun testHeaderLevelExceedingMax() {
        val markdown = "####### Header 7"
        val views = MarkdownParser.parse(context, markdown)

        assertEquals(1, views.size)
        assertTrue(views[0] is TextView)
        val textView = views[0] as TextView
        assertEquals(14f, textView.textSize / context.resources.displayMetrics.scaledDensity)
    }

    @Test
    fun testHeaderWithoutSpace() {
        val markdown = "#Header"
        val views = MarkdownParser.parse(context, markdown)

        assertEquals(1, views.size)
        assertTrue(views[0] is TextView)
        val textView = views[0] as TextView
        assertEquals("Header", textView.text.toString())
        assertEquals(16f, textView.textSize / context.resources.displayMetrics.scaledDensity)
    }

    @Test
    fun testHeaderWithExtraSpaces() {
        val markdown = "  ##  Spaced Header  "
        val views = MarkdownParser.parse(context, markdown)

        assertEquals(1, views.size)
        val textView = views[0] as TextView
        assertEquals("Spaced Header", textView.text.toString())
        assertEquals(22f, textView.textSize / context.resources.displayMetrics.scaledDensity)
    }

    @Test
    fun testCodeBlockParsing() {
        val markdown = """
            ```kotlin
            fun test() {}
            ```
        """.trimIndent()

        val views = MarkdownParser.parse(context, markdown)
        assertEquals(1, views.size)
        assertTrue(views[0] is LinearLayout)

        val codeBlock = views[0] as LinearLayout
        assertEquals(2, codeBlock.childCount)

        val languageView = codeBlock.getChildAt(0) as TextView
        assertEquals("kotlin", languageView.text.toString())

        val codeView = codeBlock.getChildAt(1) as TextView
        assertTrue(codeView.text.contains("fun test() {}"))
    }

    @Test
    fun testQuoteBlockParsing() {
        val markdown = """
            > This is a quote
            > with multiple lines
        """.trimIndent()

        val views = MarkdownParser.parse(context, markdown)
        assertEquals(1, views.size)
        assertTrue(views[0] is LinearLayout)

        val quoteView = views[0] as LinearLayout
        assertEquals(1, quoteView.childCount)

        val textView = quoteView.getChildAt(0) as TextView
        assertEquals("This is a quote\nwith multiple lines", textView.text.toString())
        assertEquals(Typeface.ITALIC, textView.typeface.style)
    }

    @Test
    fun testImageParsing() {
        val markdown = "![Alt text](https://example.com/image.jpg )"
        val views = MarkdownParser.parse(context, markdown)

        assertEquals(1, views.size)
        assertTrue(views[0] is LinearLayout)

        val container = views[0] as LinearLayout
        assertEquals(2, container.childCount)

        assertTrue(container.getChildAt(0) is View) // ProgressBar
        assertTrue(container.getChildAt(1) is ImageView)
    }

    @Test
    fun testListParsing() {
        val markdown = """
            - Item 1
              - Subitem 1
            1. Ordered item
        """.trimIndent()

        val views = MarkdownParser.parse(context, markdown)
        assertEquals(1, views.size)
        assertTrue(views[0] is LinearLayout)

        val list = views[0] as LinearLayout
        assertEquals(3, list.childCount) // Item 1, Subitem 1, Ordered item
    }

    @Test
    fun testTableParsing() {
        val markdown = """
            | Header 1 | Header 2 |
            |----------|----------|
            | Cell 1   | Cell 2   |
        """.trimIndent()

        val views = MarkdownParser.parse(context, markdown)
        assertEquals(1, views.size)
        assertTrue(views[0] is TableLayout)

        val table = views[0] as TableLayout
        assertEquals(2, table.childCount)
    }

    @Test
    fun testInlineFormatting() {
        val markdown = "**Bold** *Italic* ~~Strikethrough~~ `Code`"
        val spannable = MarkdownParser.parseInlineMarkdown(markdown)

        assertNotNull(spannable.getSpans(0, 4, StyleSpan::class.java).firstOrNull { it.style == Typeface.BOLD })
        assertNotNull(spannable.getSpans(6, 11, StyleSpan::class.java).firstOrNull { it.style == Typeface.ITALIC })
        assertNotNull(spannable.getSpans(13, 27, StrikethroughSpan::class.java).firstOrNull())
        assertNotNull(spannable.getSpans(29, 33, TypefaceSpan::class.java).firstOrNull { it.family == "monospace" })
    }
}