package com.qompium.fibricheckexample_kotlin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.qompium.fibricheck.camerasdk.FibriChecker
import com.qompium.fibricheckexample_kotlin.databinding.FragmentLabelInfoBinding

/**
 * A simple [androidx.fragment.app.Fragment] subclass.
 * Use the [LabelInfoFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class LabelInfoFragment : Fragment() {
  companion object {
    @JvmStatic
    fun newInstance() =
      LabelInfoFragment().apply {}

    fun toCapitalCase(text: String): String {
      if (text.length <= 1) {
        return text.uppercase()
      }

      val firstCapitalIndex = text.substring(1).indexOfFirst { it.isUpperCase() }
      return if (firstCapitalIndex == -1)
        "${text[0].uppercaseChar()}${text.substring(1)}"
      else
        "${text[0].uppercaseChar()}${text.substring(1 .. firstCapitalIndex)} ${toCapitalCase(text.substring(firstCapitalIndex + 1))}"
    }
  }

    override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    // Inflate the layout for this fragment
    val binding = FragmentLabelInfoBinding.inflate(inflater, container, false)

    val labelInfo = FibriChecker.getLabel()

    labelInfo.forEach { (key, value) ->
      val text = TextView(context)
      text.text = "${toCapitalCase(key)}: $value"
      binding.labelContainer.addView(text)
    }

    return binding.root
  }
}