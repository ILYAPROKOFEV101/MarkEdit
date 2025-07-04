package com.ilya.markedit.ui


import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ilya.markedit.MainActivity
import com.ilya.markedit.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class FileLoaderFragment : Fragment() {

    private lateinit var btnLoadFile: Button
    private lateinit var etUrl: EditText
    private lateinit var btnLoadUrl: Button

    private var currentFileUri: Uri? = null  // чтобы знать, откуда читать и куда сохранять

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_file_loader, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnLoadFile = view.findViewById(R.id.btn_load_file)
        etUrl = view.findViewById(R.id.et_url)
        btnLoadUrl = view.findViewById(R.id.btn_load_url)

        btnLoadFile.setOnClickListener {
            openFilePicker()
        }

        btnLoadUrl.setOnClickListener {
            val url = etUrl.text.toString()
            if (url.isNotEmpty()) {
                downloadFileFromUrl(url)
            } else {
                Toast.makeText(requireContext(), "Введите URL", Toast.LENGTH_SHORT).show()
            }
        }

        // Слушаем результат редактирования markdown, чтобы сохранить изменения в файл
        parentFragmentManager.setFragmentResultListener("markdown_result", viewLifecycleOwner) { _, bundle ->
            val updatedMarkdown = bundle.getString("markdown") ?: return@setFragmentResultListener
            currentFileUri?.let { uri ->
                saveToFile(uri, updatedMarkdown)
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*" // Разрешаем только текстовые файлы
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_FILE)
    }

    private fun downloadFileFromUrl(urlString: String) {
        Thread {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    requireActivity().runOnUiThread {
                        (requireActivity() as MainActivity).navigateToViewer(content)
                        currentFileUri = null // это загруженный из сети файл, пока не сохраняем
                    }
                } else {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Ошибка загрузки файла", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Ошибка подключения", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                // Сохраняем URI для будущих сохранений
                currentFileUri = uri

                requireContext().contentResolver.openInputStream(uri)?.use {
                    val content = it.bufferedReader().readText()
                    (requireActivity() as MainActivity).navigateToViewer(
                        content = content,
                        fileUri = uri.toString() // Передаем URI в просмотрщик
                    )
                }
            }
        }
    }

    private fun saveToFile(uri: Uri, content: String) {
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
                Toast.makeText(requireContext(), "Файл сохранён", Toast.LENGTH_SHORT).show()
            } ?: throw Exception("Не удалось открыть файл для записи")
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка сохранения файла", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    companion object {
        private const val REQUEST_CODE_OPEN_FILE = 1
    }
}