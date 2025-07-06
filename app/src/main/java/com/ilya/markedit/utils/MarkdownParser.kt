package com.ilya.markedit.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.method.ScrollingMovementMethod
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.ilya.markedit.App
import com.ilya.markedit.R
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.regex.Pattern
import kotlin.io.encoding.ExperimentalEncodingApi

import android.util.Base64




object MarkdownParser {
    private const val HEADER_LEVELS = 6
    private val HEADER_PATTERN = "^(#{1,6})\\s+(.*)".toRegex()

    private val CODE_BLOCK_PATTERN = "^```(\\w*)\\s*([\\s\\S]*?)```".toRegex(RegexOption.MULTILINE)
    private val QUOTE_PATTERN = "^>\\s+(.*)".toRegex()

    private val IMAGE_BLOCK_PATTERN = "^!\\[(.*?)\\]\\((.*?)\\)".toRegex()

    private val DIVIDER_PATTERN = "^[-*_]{3,}$".toRegex()
    private val LIST_PATTERN = "^(\\s*)([-*+]|\\d+\\.)\\s+(.*)".toRegex()
    private val TABLE_PATTERN = "^\\|(.+)\\|$".toRegex()
    private val TABLE_SEPARATOR_PATTERN = "^\\|([:-]+[-: ]*)\\|$".toRegex()

    private val CENTER_PATTERN = "\\[center\\](.*?)\\[/center\\]".toRegex(RegexOption.DOT_MATCHES_ALL)
    private val BOLD_PATTERN = "\\*\\*(.*?)\\*\\*".toRegex(RegexOption.DOT_MATCHES_ALL)
    private val ITALIC_PATTERN = "\\*(?!\\*)(.*?)\\*".toRegex(RegexOption.DOT_MATCHES_ALL)
    private val STRIKETHROUGH_PATTERN = "~~(.*?)~~".toRegex(RegexOption.DOT_MATCHES_ALL)
    private val LINK_PATTERN = "\\[(.*?)\\]\\((.*?)\\)".toRegex(RegexOption.DOT_MATCHES_ALL)
    private val IMAGE_PATTERN = "!\\[(.*?)\\]\\((.*?)\\)".toRegex(RegexOption.DOT_MATCHES_ALL)
    private val CODE_PATTERN = "`(.*?)`".toRegex(RegexOption.DOT_MATCHES_ALL)

    fun parse(context: Context, markdown: String): List<View> {
        val views = mutableListOf<View>()
        var currentList: LinearLayout? = null
        var currentListLevel = -1
        var currentTable: TableLayout? = null
        var tableAlignments = mutableListOf<Int>()
        var inCodeBlock = false
        var codeBlockContent = StringBuilder()
        var codeBlockLanguage = ""
        var inQuoteBlock = false
        var quoteContent = StringBuilder()

        val lines = markdown.lines()
        for (line in lines) {
            when {
                inCodeBlock -> {
                    if (line.trim() == "```") {
                        views.add(createCodeBlockView(context, codeBlockContent.toString(), codeBlockLanguage))
                        inCodeBlock = false
                        codeBlockContent.clear()
                    } else {
                        codeBlockContent.appendLine(line)
                    }
                }

                inQuoteBlock -> {
                    val match = QUOTE_PATTERN.find(line)
                    if (match != null) {
                        quoteContent.appendLine(match.groupValues[1])
                    } else {
                        views.add(createQuoteView(context, quoteContent.toString().trim()))
                        inQuoteBlock = false
                        quoteContent.clear()
                        // Re-process the current line
                        parseLine(context, line, views, currentList, currentListLevel, currentTable, tableAlignments)?.let {
                            currentList = it.first
                            currentListLevel = it.second
                            currentTable = it.third
                            tableAlignments = it.fourth
                        }
                    }
                }

                line.startsWith("```") -> {
                    inCodeBlock = true
                    codeBlockLanguage = line.removePrefix("```").trim()
                }

                QUOTE_PATTERN.matches(line) -> {
                    inQuoteBlock = true
                    quoteContent.appendLine(QUOTE_PATTERN.find(line)!!.groupValues[1])
                }

                else -> {
                    parseLine(context, line, views, currentList, currentListLevel, currentTable, tableAlignments)?.let {
                        currentList = it.first
                        currentListLevel = it.second
                        currentTable = it.third
                        tableAlignments = it.fourth
                    }
                }
            }
        }

        // Close any open blocks
        if (currentList != null) {
            views.add(currentList!!)
            currentList = null
        }
        if (currentTable != null) {
            views.add(currentTable!!)
            currentTable = null
        }
        if (inQuoteBlock) {
            views.add(createQuoteView(context, quoteContent.toString().trim()))
        }
        if (inCodeBlock) {
            views.add(createCodeBlockView(context, codeBlockContent.toString(), codeBlockLanguage))
        }

        return views
    }

    private fun parseLine(
        context: Context,
        line: String,
        views: MutableList<View>,
        currentList: LinearLayout?,
        currentListLevel: Int,
        currentTable: TableLayout?,
        tableAlignments: MutableList<Int>
    ): Quadruple<LinearLayout?, Int, TableLayout?, MutableList<Int>>? {
        return when {
            DIVIDER_PATTERN.matches(line) -> {
                views.add(createDividerView(context))
                Quadruple(currentList, currentListLevel, currentTable, tableAlignments)
            }

            HEADER_PATTERN.matches(line) -> {
                val match = HEADER_PATTERN.find(line)!!
                val level = match.groupValues[1].length.coerceAtMost(HEADER_LEVELS)
                val text = match.groupValues[2].trim()
                views.add(createHeaderView(context, text, level))
                Quadruple(currentList, currentListLevel, currentTable, tableAlignments)
            }

            IMAGE_BLOCK_PATTERN.matches(line) -> {
                val match = IMAGE_BLOCK_PATTERN.find(line)!!
                val altText = match.groupValues[1]
                val imageUrl = match.groupValues[2]
                views.add(createImageView(context, imageUrl, altText))
                Quadruple(currentList, currentListLevel, currentTable, tableAlignments)
            }

            TABLE_PATTERN.matches(line) -> {
                val isSeparator = TABLE_SEPARATOR_PATTERN.matches(line)

                if (isSeparator) {
                    // Parse alignments from separator
                    val alignments = parseTableAlignments(line)
                    Quadruple(currentList, currentListLevel, currentTable, alignments.toMutableList())
                } else {
                    if (currentTable == null) {
                        val newTable = createTableView(context)
                        newTable.addView(createTableRow(context, line, tableAlignments))
                        Quadruple(currentList, currentListLevel, newTable, tableAlignments)
                    } else {
                        currentTable.addView(createTableRow(context, line, tableAlignments))
                        Quadruple(currentList, currentListLevel, currentTable, tableAlignments)
                    }
                }
            }

            LIST_PATTERN.matches(line) -> {
                val match = LIST_PATTERN.find(line)!!
                val indent = match.groupValues[1].length
                val bullet = match.groupValues[2]
                val text = match.groupValues[3]
                val isOrdered = bullet.matches("\\d+\\.".toRegex())
                val level = indent / 4  // Assuming 4 spaces per indent level

                // Close previous list if needed
                if (currentList != null && level < currentListLevel) {
                    views.add(currentList)
                    return Quadruple(null, -1, currentTable, tableAlignments)
                }

                val newList = currentList ?: LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 8, 16, 8)
                }

                val listItem = createListItem(context, text, bullet, isOrdered, level)
                newList.addView(listItem)

                Quadruple(newList, level, currentTable, tableAlignments)
            }

            line.isBlank() -> {
                // Close open blocks
                val result = Quadruple<LinearLayout?, Int, TableLayout?, MutableList<Int>>(
                    first = if (currentList != null) {
                        views.add(currentList!!)
                        null
                    } else {
                        currentList
                    },
                    second = if (currentList != null) -1 else currentListLevel,
                    third = if (currentTable != null) {
                        views.add(currentTable!!)
                        null
                    } else {
                        currentTable
                    },
                    fourth = tableAlignments
                )
                result
            }

            else -> {
                // If we're in a list, add to the last item
                if (currentList != null && currentList.childCount > 0) {
                    val lastChild = currentList.getChildAt(currentList.childCount - 1) as? TextView
                    lastChild?.apply {
                        text = SpannableStringBuilder(text).append("\n").append(parseInlineMarkdown(line))
                    }
                    return Quadruple(currentList, currentListLevel, currentTable, tableAlignments)
                }

                views.add(createParagraphView(context, line))
                Quadruple(currentList, currentListLevel, currentTable, tableAlignments)
            }
        }
    }

    private fun createHeaderView(context: Context, text: String, level: Int): TextView {
        return TextView(context).apply {
            this.text = parseInlineMarkdown(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, when (level) {
                1 -> 24f
                2 -> 22f
                3 -> 20f
                4 -> 18f
                5 -> 16f
                else -> 14f
            })
            setTypeface(null, Typeface.BOLD)
            setPadding(16, 24, 16, 8)
            gravity = Gravity.CENTER
            movementMethod = LinkMovementMethod.getInstance()
        }
    }


    private fun createImageView(context: Context, imageUrl: String, altText: String): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 16)
            }
            gravity = Gravity.CENTER

            // ProgressBar для индикации загрузки
            val progressBar = ProgressBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            addView(progressBar)

            // ImageView для отображения картинки
            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = altText
                visibility = View.GONE
            }
            addView(imageView)

            // Загрузка изображения в фоне
            loadImageAsync(imageUrl, imageView, progressBar)
        }
    }


    @OptIn(ExperimentalEncodingApi::class)
    private fun loadImageAsync(url: String, imageView: ImageView, progressBar: ProgressBar) {
        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        // 1. Проверка кэша
        val cachedBitmap = imageCache.get(url)
        if (cachedBitmap != null) {
            Log.d("MarkdownParser", "Loaded from cache: $url")
            progressBar.visibility = View.GONE
            imageView.setImageBitmap(cachedBitmap)
            imageView.visibility = View.VISIBLE

            val maxWidth = Resources.getSystem().displayMetrics.widthPixels - 32
            val scaleFactor = maxWidth.toFloat() / cachedBitmap.width
            val height = (cachedBitmap.height * scaleFactor).toInt()
            imageView.layoutParams = LinearLayout.LayoutParams(maxWidth, height)
            return
        }

        // 2. Если нет в кэше — загружаем
        executor.execute {
            try {
                val bitmap = if (url.startsWith("data:image")) {
                    Log.d("MarkdownParser", "Loading image from base64")
                    val base64Data = url.substringAfter("base64,")
                    val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                } else {
                    Log.d("MarkdownParser", "Loading image from URL: $url")
                    val imageUrl = URL(url)
                    val connection = imageUrl.openConnection() as HttpURLConnection
                    connection.doInput = true
                    connection.connect()

                    val responseCode = connection.responseCode
                    Log.d("MarkdownParser", "Response code: $responseCode")
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw IOException("HTTP error code: $responseCode")
                    }

                    val input = connection.inputStream
                    BitmapFactory.decodeStream(input)
                }

                if (bitmap == null) throw IOException("Failed to decode image")

                // 3. Сохраняем в кэш
                imageCache.put(url, bitmap)

                handler.post {
                    progressBar.visibility = View.GONE
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE

                    val maxWidth = Resources.getSystem().displayMetrics.widthPixels - 32
                    val scaleFactor = maxWidth.toFloat() / bitmap.width
                    val height = (bitmap.height * scaleFactor).toInt()

                    imageView.layoutParams = LinearLayout.LayoutParams(maxWidth, height)
                }
            } catch (e: Exception) {
                Log.e("MarkdownParser", "Error loading image", e)
                handler.post {
                    progressBar.visibility = View.GONE
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                    imageView.visibility = View.VISIBLE
                }
            }
        }
    }


    private fun createParagraphView(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = parseInlineMarkdown(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(16, 8, 16, 8)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    fun parseInlineMarkdown(text: String): SpannableStringBuilder {
        val spannable = SpannableStringBuilder(text)
        applySpanMatches(spannable, CENTER_PATTERN) { _, start, end ->
            spannable.setSpan(
                AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        applySpanMatches(spannable, BOLD_PATTERN) { _, start, end ->
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        applySpanMatches(spannable, ITALIC_PATTERN) { _, start, end ->
            spannable.setSpan(
                StyleSpan(Typeface.ITALIC),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        applySpanMatches(spannable, STRIKETHROUGH_PATTERN) { _, start, end ->
            spannable.setSpan(
                StrikethroughSpan(),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        applySpanMatches(spannable, CODE_PATTERN) { content, start, end ->
            spannable.setSpan(
                TypefaceSpan("monospace"),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                BackgroundColorSpan(Color.parseColor("#F0F0F0")),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        applySpanMatches(spannable, LINK_PATTERN) { groups, start, end ->
            val url = groups[2]
            spannable.setSpan(
                URLSpan(url),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.BLUE),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                UnderlineSpan(),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        applySpanMatches(spannable, IMAGE_PATTERN) { groups, start, end ->
            spannable.replace(start, end, "🖼️ ${groups[1]}")
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#388E3C")),
                start,
                start + 2 + groups[1].length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    private inline fun applySpanMatches(
        spannable: SpannableStringBuilder,
        pattern: Regex,
        applySpan: (groups: List<String>, start: Int, end: Int) -> Unit
    ) {
        val matches = pattern.findAll(spannable).toList()
        var offset = 0

        for (match in matches) {
            val groups = match.groupValues
            val start = match.range.first - offset
            val end = match.range.last + 1 - offset
            val fullMatchLength = match.value.length

            applySpan(groups, start, end)

            // Удаляем исходные символы разметки
            spannable.replace(start, end, groups[1])
            offset += fullMatchLength - groups[1].length
        }
    }

    private fun createListItem(context: Context, text: String, bullet: String, isOrdered: Boolean, level: Int): TextView {
        val prefix = if (isOrdered) "$bullet " else "• "
        val indent = level * 30

        return TextView(context).apply {
            this.text = parseInlineMarkdown("$prefix$text")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(indent + 16, 8, 16, 8)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun createTableView(context: Context): TableLayout {
        return TableLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            isShrinkAllColumns = true
            isStretchAllColumns = true
        }
    }


    private fun createTableRow(context: Context, line: String, alignments: List<Int>): TableRow {
        val columns = line.trim().trim('|').split("|").map { it.trim() }
        val row = TableRow(context)

        columns.forEachIndexed { index, cell ->
            val textView = TextView(context).apply {
                text = parseInlineMarkdown(cell)
                setPadding(8, 8, 8, 8)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = when (alignments.getOrNull(index) ?: Gravity.START) {
                    Gravity.CENTER -> Gravity.CENTER
                    Gravity.END -> Gravity.END
                    else -> Gravity.START
                }
            }
            row.addView(textView)
        }

        return row
    }

    private fun parseTableAlignments(separatorLine: String): List<Int> {
        return separatorLine.trim().trim('|').split("|").map { cell ->
            when {
                cell.startsWith(":") && cell.endsWith(":") -> Gravity.CENTER
                cell.startsWith(":") -> Gravity.START
                cell.endsWith(":") -> Gravity.END
                else -> Gravity.START
            }
        }
    }

    private fun createQuoteView(context: Context, text: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 16, 16)
            background = context.getDrawable(R.drawable.quote_background)

            addView(TextView(context).apply {
                this.text = parseInlineMarkdown(text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(null, Typeface.ITALIC)
                setPadding(0, 4, 0, 4)
                movementMethod = LinkMovementMethod.getInstance()
            })
        }
    }

    private fun createCodeBlockView(context: Context, code: String, language: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2B2B2B"))
            setPadding(16, 16, 16, 16)

            if (language.isNotBlank()) {
                addView(TextView(context).apply {
                    text = language
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 8)
                })
            }

            addView(TextView(context).apply {
                text = code
                setTextColor(Color.parseColor("#A9B7C6"))
                setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                movementMethod = ScrollingMovementMethod()
            })
        }
    }

    private fun createDividerView(context: Context): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(16, 24, 16, 24)
            }
            setBackgroundColor(Color.DKGRAY)
        }
    }



    data class Quadruple<out A, out B, out C, out D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}