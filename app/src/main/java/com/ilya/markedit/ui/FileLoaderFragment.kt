package com.ilya.markedit.ui


import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.ilya.markedit.MainActivity
import com.ilya.markedit.R
import com.ilya.markedit.utils.FileHistoryAdapter
import com.ilya.markedit.utils.FileHistoryItem
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlin.concurrent.thread

class FileLoaderFragment : Fragment() {

    private lateinit var btnLoadFile: Button
    private lateinit var etUrl: EditText
    private lateinit var btnLoadUrl: Button
    private lateinit var historyContainer: LinearLayout

    private val historyPrefs by lazy {
        requireContext().getSharedPreferences("file_history", Context.MODE_PRIVATE)
    }

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
        historyContainer = view.findViewById(R.id.history_container)

        // Загрузка истории
        loadHistory()

        btnLoadFile.setOnClickListener {
            openFilePicker()
        }

        btnLoadUrl.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                downloadFileFromUrl(url)
            } else {
                Toast.makeText(requireContext(), "Введите URL", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadHistory() {
        historyContainer.removeAllViews()
        val historyCount = historyPrefs.getInt("history_count", 0)

        for (i in 0 until historyCount) {
            val type = historyPrefs.getString("history_type_$i", null) ?: continue
            val name = historyPrefs.getString("history_name_$i", "") ?: ""
            val data = historyPrefs.getString("history_data_$i", "") ?: ""

            addHistoryItemToUI(type, name, data)
        }
    }

    private fun saveHistoryItem(type: String, name: String, data: String) {
        val historyCount = historyPrefs.getInt("history_count", 0)

        // Проверяем, нет ли уже такого элемента
        for (i in 0 until historyCount) {
            val existingType = historyPrefs.getString("history_type_$i", null)
            val existingData = historyPrefs.getString("history_data_$i", null)

            if (existingType == type && existingData == data) {
                // Обновляем имя, если нужно
                if (historyPrefs.getString("history_name_$i", "") != name) {
                    historyPrefs.edit().putString("history_name_$i", name).apply()
                    loadHistory() // Обновляем UI
                }
                return
            }
        }

        // Сохраняем новый элемент
        with(historyPrefs.edit()) {
            putString("history_type_$historyCount", type)
            putString("history_name_$historyCount", name)
            putString("history_data_$historyCount", data)
            putInt("history_count", historyCount + 1)
            apply()
        }

        // Добавляем в UI
        addHistoryItemToUI(type, name, data)
    }

    private fun addHistoryItemToUI(type: String, name: String, data: String) {
        val historyItem = TextView(requireContext()).apply {
            text = name
            setTextAppearance(android.R.style.TextAppearance_Medium)
            setPadding(32, 32, 32, 32)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_history_item)

            // Обработка клика
            setOnClickListener {
                when (type) {
                    "uri" -> openUriFile(Uri.parse(data), false)
                    "url" -> downloadFileFromUrl(data, false)
                }
            }
        }

        historyContainer.addView(historyItem, 0) // Добавляем в начало
    }

    private fun openUriFile(uri: Uri, addToHistory: Boolean = true) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            requireContext().contentResolver.openInputStream(uri)?.use {
                val content = it.bufferedReader().readText()
                val fileName = getFileName(uri)

                if (addToHistory) {
                    saveHistoryItem("uri", fileName, uri.toString())
                }

                (requireActivity() as MainActivity).navigateToViewer(content, uri.toString())
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка открытия файла", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun getFileName(uri: Uri): String {
        return when (uri.scheme) {
            "file" -> uri.lastPathSegment ?: "Файл"
            "content" -> {
                var fileName = "Файл"
                try {
                    requireContext().contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            fileName = cursor.getString(0)
                        }
                    }
                } catch (e: Exception) {
                    // Игнорируем ошибки
                }
                fileName
            }
            else -> "Файл"
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_FILE)
    }

    private fun downloadFileFromUrl(urlString: String, addToHistory: Boolean = true) {
        val progress = ProgressDialog(requireContext()).apply {
            setMessage("Загрузка файла...")
            setCancelable(false)
            show()
        }

        thread {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = connection.inputStream.bufferedReader().readText()
                    val fileName = extractFileName(urlString, connection)

                    requireActivity().runOnUiThread {
                        progress.dismiss()

                        if (addToHistory) {
                            saveHistoryItem("url", fileName, urlString)
                        }

                        (requireActivity() as MainActivity).navigateToViewer(content)
                    }
                } else {
                    requireActivity().runOnUiThread {
                        progress.dismiss()
                        Toast.makeText(requireContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progress.dismiss()
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun extractFileName(urlString: String, connection: HttpURLConnection): String {
        // Пробуем получить имя из Content-Disposition
        val contentDisposition = connection.getHeaderField("Content-Disposition")
        if (contentDisposition != null) {
            val fileNameRegex = "filename=\"?([^\"]+)\"?".toRegex()
            val matchResult = fileNameRegex.find(contentDisposition)
            matchResult?.groupValues?.get(1)?.let { return it }
        }

        // Пробуем получить имя из URL
        val decodedUrl = URLDecoder.decode(urlString, "UTF-8")
        return decodedUrl.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() }
            ?: "Файл с URL"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                openUriFile(uri)
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_OPEN_FILE = 1
    }
}