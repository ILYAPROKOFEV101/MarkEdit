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


class MarkdownEditorFragment : Fragment() {

    private lateinit var editText: EditText
    private lateinit var saveButton: Button

    private var filePath: String? = null
    private var currentFileUri: Uri? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_markdown_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uriString = arguments?.getString("file_uri")
        currentFileUri = if (!uriString.isNullOrEmpty()) Uri.parse(uriString) else null

        editText = view.findViewById(R.id.editText)
        saveButton = view.findViewById(R.id.btn_save)

        val initialMarkdown = arguments?.getString("markdown") ?: ""
        editText.setText(initialMarkdown)

        filePath = arguments?.getString("file_path")  // Например, путь к файлу



        fun setFileUri(uri: Uri) {
            currentFileUri = uri
        }
        saveButton.setOnClickListener {
            val updatedMarkdown = editText.text.toString()

            currentFileUri?.let { uri ->
                try {
                    requireContext().contentResolver.openOutputStream(uri)?.use {
                        it.write(updatedMarkdown.toByteArray())
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                }
            }

            // Отправляем результат обратно, если надо
            setFragmentResult("markdown_update", Bundle().apply {
                putString("markdown", updatedMarkdown)
                putString("file_uri", currentFileUri?.toString())
            })

            parentFragmentManager.popBackStack()
        }
    }
}
