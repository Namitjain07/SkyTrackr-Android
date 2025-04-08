package com.flight.flightq1

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.json.JSONObject

class JsonBottomSheetFragment : BottomSheetDialogFragment() {
    
    companion object {
        private const val ARG_JSON_STRING = "json_string"
        private const val ARG_TITLE = "title"
        
        fun newInstance(jsonString: String, title: String? = null): JsonBottomSheetFragment {
            val fragment = JsonBottomSheetFragment()
            val args = Bundle()
            args.putString(ARG_JSON_STRING, jsonString)
            args.putString(ARG_TITLE, title)
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_json_bottom_sheet, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val jsonContentTextView = view.findViewById<TextView>(R.id.tv_json_content)
        val titleTextView = view.findViewById<TextView>(R.id.tv_bottom_sheet_title)
        val closeButton = view.findViewById<Button>(R.id.btn_close_bottom_sheet)
        
        // Get arguments
        val jsonString = arguments?.getString(ARG_JSON_STRING) ?: ""
        val title = arguments?.getString(ARG_TITLE)
        
        // Set title if provided
        if (!title.isNullOrEmpty()) {
            titleTextView.text = title
        }
        
        // Format and display JSON
        try {
            val jsonObject = JSONObject(jsonString)
            val formattedJson = jsonObject.toString(4)
            jsonContentTextView.text = formattedJson
        } catch (e: Exception) {
            // If it's not valid JSON, just display the raw string
            jsonContentTextView.text = jsonString
            
            // Log the error but don't crash
            Log.e("JsonBottomSheet", "Error parsing JSON: ${e.message}", e)
        }
        
        // Set close button click listener
        closeButton.setOnClickListener {
            dismiss()
        }
    }
}
