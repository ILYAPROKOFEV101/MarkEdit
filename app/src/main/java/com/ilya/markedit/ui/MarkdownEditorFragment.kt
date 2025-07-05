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


class MarkdownEditorFragment : Fragment() {

    private lateinit var editText: EditText
    private lateinit var saveButton: Button

    private var filePath: String? = null
    private var fileUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_markdown_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editText = view.findViewById(R.id.editText)
        saveButton = view.findViewById(R.id.btn_save)

        val initialMarkdown = arguments?.getString("markdown") ?: ""
        editText.setText(initialMarkdown)

        // Получаем параметры файла
        filePath = arguments?.getString("file_path")
        fileUri = arguments?.getString("file_uri")?.let { Uri.parse(it) }

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
}