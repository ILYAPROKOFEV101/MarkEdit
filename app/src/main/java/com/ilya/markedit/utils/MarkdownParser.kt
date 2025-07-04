package com.ilya.markedit.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.ilya.markedit.App
import java.net.URL
import java.util.regex.Pattern



object MarkdownParser {
    private const val HEADER_LEVELS = 6
    private val BOLD_PATTERN = "\\*\\*(.*?)\\*\\*".toRegex()
    private val ITALIC_PATTERN = "(?<!\\*)\\*(?!\\*)(.*?)\\*(?!\\*)".toRegex()
    private val STRIKETHROUGH_PATTERN = "~~(.*?)~~".toRegex()
    private val LINK_PATTERN = "\\[(.*?)\\]\\((.*?)\\)".toRegex()
    private val IMAGE_PATTERN = "!\\[(.*?)\\]\\((.*?)\\)".toRegex()

    fun parse(context: Context, markdown: String): List<View> {
        val views = mutableListOf<View>()
        val lines = markdown.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length.coerceAtMost(HEADER_LEVELS)
                    views.add(createHeaderView(context, line, level))
                    i++
                }

                line.startsWith("|") -> {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        tableLines.add(lines[i])
                        i++
                    }
                    views.addAll(createTableView(context, tableLines))
                }

                line.startsWith("- ") || line.startsWith("* ") || line.matches("\\d+\\..*".toRegex()) -> {
                    val listLines = mutableListOf<String>()
                    val isOrdered = line.matches("\\d+\\..*".toRegex())
                    while (i < lines.size && isListLine(lines[i], isOrdered)) {
                        listLines.add(lines[i])
                        i++
                    }
                    views.add(createListView(context, listLines, isOrdered))
                }

                line.startsWith(">") -> {
                    val quoteLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        quoteLines.add(lines[i].removePrefix(">").trim())
                        i++
                    }
                    views.add(createQuoteView(context, quoteLines))
                }

                line.matches("^[-*]{3,}$".toRegex()) -> {
                    views.add(createDividerView(context))
                    i++
                }

                line.isBlank() -> i++

                else -> views.add(createTextView(context, line)).also { i++ }
            }
        }
        return views
    }

    private fun isListLine(line: String, isOrdered: Boolean): Boolean {
        return when {
            isOrdered -> line.matches("\\d+\\..*".toRegex())
            else -> line.startsWith("- ") || line.startsWith("* ")
        }
    }

    private fun createHeaderView(context: Context, line: String, level: Int): TextView {
        val headerText = line.substring(level).trim()
        return TextView(context).apply {
            text = parseInlineMarkdown(headerText)
            textSize = 26f - (level * 2)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun createTextView(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = parseInlineMarkdown(text)
            textSize = 16f
            setPadding(0, 8, 0, 8)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun parseInlineMarkdown(text: String): SpannableStringBuilder {
        val spannable = SpannableStringBuilder(text)

        // Обработка изображений (заменяем на [Image])
        IMAGE_PATTERN.findAll(text).forEach { match ->
            spannable.replace(match.range.first, match.range.last + 1, "[Image]")
        }

        // Обработка ссылок
        LINK_PATTERN.findAll(text).forEach { match ->
            val textStart = match.range.first
            val textEnd = match.range.last + 1
            val linkText = match.groupValues[1]
            val url = match.groupValues[2]
            spannable.replace(textStart, textEnd, linkText)
            spannable.setSpan(
                URLSpan(url),
                textStart,
                textStart + linkText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Обработка стилей текста
        applyTextStyle(spannable, BOLD_PATTERN, StyleSpan(Typeface.BOLD))
        applyTextStyle(spannable, ITALIC_PATTERN, StyleSpan(Typeface.ITALIC))
        applyTextStyle(spannable, STRIKETHROUGH_PATTERN, StrikethroughSpan())

        return spannable
    }

    private fun applyTextStyle(
        spannable: SpannableStringBuilder,
        pattern: Regex,
        span: CharacterStyle
    ) {
        pattern.findAll(spannable).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            val content = match.groupValues[1]
            spannable.replace(start, end, content)
            spannable.setSpan(
                CharacterStyle.wrap(span),
                start,
                start + content.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun createListView(context: Context, items: List<String>, isOrdered: Boolean): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 0, 8)

            items.forEachIndexed { index, item ->
                val bullet = if (isOrdered) "${index + 1}." else "•"
                val itemText = item.substringAfter(" ").trim()

                addView(TextView(context).apply {
                    text = parseInlineMarkdown("$bullet $itemText")
                    textSize = 16f
                    setPadding(8, 4, 0, 4)
                    movementMethod = LinkMovementMethod.getInstance()
                })
            }
        }
    }

    private fun createTableView(context: Context, tableLines: List<String>): List<View> {
        if (tableLines.size < 2) return emptyList()

        val tableLayout = TableLayout(context).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8, 0, 16)
        }

        // Определение выравнивания
        val alignments = parseTableAlignments(tableLines[1])

        tableLines.forEachIndexed { i, line ->
            if (i == 1) return@forEachIndexed // Пропускаем строку разделителя

            val tableRow = TableRow(context)
            val cells = line.split("|").filterIndexed { index, _ -> index > 0 && index < line.count { it == '|' } }

            cells.forEachIndexed { j, cellContent ->
                val textView = TextView(context).apply {
                    text = parseInlineMarkdown(cellContent.trim())
                    setPadding(16, 8, 16, 8)
                    gravity = alignments.getOrElse(j) { Gravity.START } or Gravity.CENTER_VERTICAL
                    movementMethod = LinkMovementMethod.getInstance()

                    if (i == 0) { // Заголовок
                        setTypeface(null, Typeface.BOLD)
                        setBackgroundColor(Color.parseColor("#F0F0F0"))
                    }
                }

                tableRow.addView(textView)
            }
            tableLayout.addView(tableRow)
        }

        return listOf(tableLayout)
    }



    private fun parseTableAlignments(separatorLine: String): List<Int> {
        return separatorLine.split("|")
            .filterIndexed { index, _ -> index > 0 && index < separatorLine.count { it == '|' } }
            .map { cell ->
                when {
                    cell.startsWith(":") && cell.endsWith(":") -> Gravity.CENTER
                    cell.endsWith(":") -> Gravity.END
                    else -> Gravity.START
                }
            }
    }

    private fun createQuoteView(context: Context, lines: List<String>): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(24, 16, 16, 16)

            lines.forEach { line ->
                addView(TextView(context).apply {
                    text = parseInlineMarkdown(line)
                    textSize = 16f
                    setTypeface(null, Typeface.ITALIC)
                    setPadding(0, 4, 0, 4)
                    movementMethod = LinkMovementMethod.getInstance()
                })
            }
        }
    }

    private fun createDividerView(context: Context): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(0, 24, 0, 24)
            }
            setBackgroundColor(Color.DKGRAY)
        }
    }
}