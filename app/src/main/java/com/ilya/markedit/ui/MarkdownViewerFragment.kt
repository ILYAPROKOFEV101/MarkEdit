package com.ilya.markedit.ui
import com.ilya.markedit.R

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.ilya.markedit.MainActivity

import com.ilya.markedit.utils.MarkdownParser

class MarkdownViewerFragment : Fragment() {

    private var currentMarkdown: String = ""
    private var filePath: String? = null
    private var fileUri: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_markdown_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentMarkdown = arguments?.getString("markdown") ?: ""
        filePath = arguments?.getString("file_path")
        fileUri = arguments?.getString("file_uri")

        renderMarkdown(currentMarkdown)

        parentFragmentManager.setFragmentResultListener("markdown_update", viewLifecycleOwner) { _, bundle ->
            val newMarkdown = bundle.getString("markdown") ?: ""
            currentMarkdown = newMarkdown
            renderMarkdown(currentMarkdown)
        }

        val editButton = view.findViewById<Button>(R.id.btn_edit)
        editButton.setOnClickListener {
            (activity as? MainActivity)?.navigateToEditor(
                markdown = currentMarkdown,
                filePath = filePath,
                fileUri = fileUri
            )
        }
    }

    private fun renderMarkdown(markdown: String) {
        val container = view?.findViewById<LinearLayout>(R.id.container)
        container?.removeAllViews()
        MarkdownParser.parse(requireContext(), markdown).forEach { view ->
            container?.addView(view)
        }
    }
}