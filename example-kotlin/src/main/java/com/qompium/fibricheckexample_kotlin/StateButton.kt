package com.qompium.fibricheckexample_kotlin

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ToggleButton
import androidx.core.content.withStyledAttributes

val DefaultItems = listOf("Please", "Add", "Items")


/**
 * TODO: document your custom view class.
 */
class StateButton : LinearLayout {
  private var _items: List<String> = DefaultItems
  private var _buttons: List<ToggleButton> = listOf()
  private var _value: String = DefaultItems[0]
  var onValueChange: ((index: Int, value: String) -> Unit)? = null

  /**
   * The items to add
   */
  var items: List<String>
    get() = _items
    set(value) {
      _items = value
      invalidateButtons()
    }

  var value: String
    get() = _value
    set(value) {
      _value = value
      updateButtons()
    }

  constructor(context: Context) : super(context) {
    init(null, 0)
  }

  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    init(attrs, 0)
  }

  constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
    context,
    attrs,
    defStyle
  ) {
    init(attrs, defStyle)
  }

  private fun init(attrs: AttributeSet?, defStyle: Int) {
    // Load attributes
    orientation = HORIZONTAL

    context.withStyledAttributes(
      attrs, R.styleable.StateButton, defStyle, 0
    ) {
      val stringItems = getString(
        R.styleable.StateButton_items
      )

      _items = stringItems?.split(',')?.map { it.trim() } ?: DefaultItems
      _value = getString(
        R.styleable.StateButton_initialValue
      ) ?: _items[0]
    }

    invalidateButtons()
  }

  private fun invalidateButtons() {
    this.removeAllViews()
    val buttons = _items.mapIndexed { index, text ->
      val button = ToggleButton(context)
      button.text = text
      button.textOn = text
      button.textOff = text
      button.isChecked = text == _value
      button.setOnClickListener {
        _buttons.forEachIndexed { toggleButtonIndex, toggleButton -> toggleButton.isChecked = toggleButtonIndex == index }
        _value = text
        onValueChange?.invoke(index, text)
      }
      addView(button)
      button
    }

    _buttons = buttons
  }

  private fun updateButtons() {
    _buttons.forEach {
      it.isChecked = it.text == _value
    }
  }
}