package com.ilya.markedit

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ilya.markedit.ui.FileLoaderFragment
import com.ilya.markedit.ui.MarkdownEditorFragment
import com.ilya.markedit.ui.MarkdownViewerFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Включение Edge-to-Edge режима
        enableEdgeToEdge()

        // Установка макета
        setContentView(R.layout.activity_main)

        // Настройка отступов для системных панелей
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Загрузка начального фрагмента (FileLoaderFragment)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FileLoaderFragment())
                .commit()
        }
    }

    /**
     * Метод для навигации к экрану просмотра Markdown.
     */
    fun navigateToViewer(content: String, filePath: String? = null, fileUri: String? = null) {
        val fragment = MarkdownViewerFragment().apply {
            arguments = Bundle().apply {
                putString("markdown", content)
                putString("file_path", filePath)
                putString("file_uri", fileUri)
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun navigateToEditor(markdown: String, filePath: String? = null, fileUri: String? = null) {
        val fragment = MarkdownEditorFragment().apply {
            arguments = Bundle().apply {
                putString("markdown", markdown)
                putString("file_path", filePath)
                putString("file_uri", fileUri)
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}