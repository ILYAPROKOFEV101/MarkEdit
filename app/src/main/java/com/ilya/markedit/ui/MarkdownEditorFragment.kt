package com.ilya.markedit.ui
import android.content.Context
import android.net.Uri
import com.ilya.markedit.R



import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.ilya.markedit.MainActivity
import java.io.File



import android.widget.*


class MarkdownEditorFragment : Fragment() {

    private lateinit var editText: EditText
    private lateinit var saveButton: Button
    private lateinit var toolbar: LinearLayout

    private var filePath: String? = null
    private var fileUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_markdown_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editText = view.findViewById(R.id.editText)
        saveButton = view.findViewById(R.id.btn_save)
        toolbar = view.findViewById(R.id.toolbar)

        val initialMarkdown = arguments?.getString("markdown") ?: ""
        editText.setText(initialMarkdown)

        filePath = arguments?.getString("file_path")
        fileUri = arguments?.getString("file_uri")?.let { Uri.parse(it) }

        setupToolbar()

        saveButton.setOnClickListener {
            val updatedMarkdown = editText.text.toString()

            when {
                filePath != null -> saveToLocalFile(filePath!!, updatedMarkdown)
                fileUri != null -> saveToUriFile(fileUri!!, updatedMarkdown)
                else -> Toast.makeText(requireContext(), "Файл не выбран", Toast.LENGTH_SHORT).show()
            }

            setFragmentResult("markdown_update", Bundle().apply {
                putString("markdown", updatedMarkdown)
                putString("file_path", filePath)
                putString("file_uri", fileUri?.toString())
            })

            parentFragmentManager.popBackStack()
        }
    }

    private fun setupToolbar() {
        val actions = listOf(
            ToolbarAction("B") { wrapSelection("**", "**") } to "Жирный",
            ToolbarAction("I") { wrapSelection("*", "*") } to "Курсив",
            ToolbarAction("H") { insertAtLineStart("# ") } to "Заголовок",
            ToolbarAction("1.") { insertAtLineStart("1. ") } to "Нумерованный список",
            ToolbarAction("-") { insertAtLineStart("- ") } to "Маркированный список",
            ToolbarAction("[]()") { insertLink() } to "Ссылка",
            ToolbarAction("![]()") { wrapSelection("![", "](url)") } to "Изображение"
        )

        actions.forEach { (action, description) ->
            addToolbarButton(action.symbol, action.handler, description)
        }
    }

    private fun addToolbarButton(
        symbol: String,
        handler: () -> Unit,
        contentDesc: String
    ) {
        val button = Button(requireContext()).apply {
            text = symbol
            contentDescription = contentDesc
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener { handler() }
        }
        toolbar.addView(button)
    }

    private fun wrapSelection(prefix: String, postfix: String) {
        val start = editText.selectionStart
        val end = editText.selectionEnd

        if (start == end) {
            editText.text.insert(start, "$prefix$postfix")
            editText.setSelection(start + prefix.length)
        } else {
            val selected = editText.text.substring(start, end)
            editText.text.replace(start, end, "$prefix$selected$postfix")
            editText.setSelection(end + prefix.length + postfix.length)
        }
    }

    private fun insertAtLineStart(text: String) {
        val cursorPos = editText.selectionStart
        val textContent = editText.text.toString()

        var lineStart = cursorPos
        while (lineStart > 0 && textContent[lineStart - 1] != '\n') {
            lineStart--
        }

        editText.text.insert(lineStart, text)
        editText.setSelection(cursorPos + text.length)
    }

    private fun insertLink() {
        val start = editText.selectionStart
        val end = editText.selectionEnd

        if (start == end) {
            editText.text.insert(start, "[текст](url)")
            editText.setSelection(start + 1, start + 6) // Выделяет "текст"
        } else {
            val selected = editText.text.substring(start, end)
            editText.text.replace(start, end, "[$selected](url)")
            editText.setSelection(end + 3, end + 6) // Выделяет "url"
        }
    }

    private fun saveToLocalFile(filePath: String, content: String) {
        try {
            File(filePath).writeText(content)
            Toast.makeText(requireContext(), "Файл сохранён", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToUriFile(uri: Uri, content: String) {
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray())
                Toast.makeText(requireContext(), "Файл сохранён", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(requireContext(), "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Нет разрешения на запись", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private data class ToolbarAction(
        val symbol: String,
        val handler: () -> Unit
    )
}