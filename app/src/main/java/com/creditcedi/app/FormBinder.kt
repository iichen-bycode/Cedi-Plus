package com.creditcedi.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import com.creditcedi.core.JourneyViewModel
import com.creditcedi.core.Screen
import com.creditcedi.core.R as CoreR
import java.util.Locale
import kotlin.math.ceil

internal class FormBinder(
    private val activity: Activity,
    private val model: JourneyViewModel,
    private val onFieldChanged: (Screen, Int, String, String) -> Unit = { _, _, _, _ -> }
) {
    private data class InputChange(
        val screen: Screen,
        val index: Int,
        val key: String,
        var initialValue: String,
        var changed: Boolean = false
    )

    private val inputChanges = mutableListOf<InputChange>()
    private var optionProvider: (Screen, Int, String) -> List<String>? = { _, _, _ -> null }
    private var choiceHandler: (Screen, Int, String, ViewGroup) -> Unit = { _, _, _, _ -> }
    private var missingOptionsHandler: () -> Unit = {}

    fun setChoiceHandlers(
        options: (Screen, Int, String) -> List<String>?,
        onChosen: (Screen, Int, String, ViewGroup) -> Unit,
        onMissing: () -> Unit
    ) {
        optionProvider = options
        choiceHandler = onChosen
        missingOptionsHandler = onMissing
    }

    fun beginScreen() {
        flushPendingChanges()
        inputChanges.clear()
    }

    fun flushPendingChanges() {
        inputChanges.forEach { change ->
            if (!change.changed) return@forEach
            val value = model.field(change.key)
            if (value != change.initialValue) {
                onFieldChanged(change.screen, change.index, change.initialValue, value)
            }
            change.initialValue = value
            change.changed = false
        }
    }

    private fun key(index: Int) = model.current.name + "_" + index

    fun input(field: EditText, screen: Screen, index: Int, fixedPrefix: String? = null) {
        val key = screen.name + "_" + index
        val storedValue = model.field(key)
        val initialValue = fixedPrefix?.let { prefix ->
            val suffix = if (storedValue.startsWith(prefix, ignoreCase = true)) {
                storedValue.substring(prefix.length)
            } else {
                storedValue
            }
            prefix + suffix
        } ?: storedValue
        if (initialValue != storedValue) model.edit(key, initialValue)
        field.setText(if (fixedPrefix == null) initialValue else initialValue.removePrefix(fixedPrefix))
        val change = InputChange(screen, index, key, initialValue)
        inputChanges += change
        var normalizingFixedPrefix = false
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val oldValue = model.field(key)
                val enteredValue = s.toString()
                val normalizedInput = fixedPrefix?.let { prefix ->
                    if (!normalizingFixedPrefix && enteredValue.startsWith(prefix, ignoreCase = true)) {
                        enteredValue.substring(prefix.length).also { suffix ->
                            normalizingFixedPrefix = true
                            field.setText(suffix)
                            field.setSelection(suffix.length)
                            normalizingFixedPrefix = false
                        }
                    } else {
                        enteredValue
                    }
                } ?: enteredValue
                val newValue = fixedPrefix?.let { it + normalizedInput } ?: normalizedInput
                if (oldValue != newValue) {
                    model.edit(key, newValue)
                    change.changed = true
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                change.initialValue = model.field(key)
                change.changed = false
            } else if (change.changed) {
                flushPendingChanges()
            }
        }
    }

    fun restoreChoice(view: TextView, index: Int) {
        val choice = model.field(key(index))
        if (choice.isNotEmpty()) view.text = choice
    }

    fun select(
        view: TextView,
        index: Int,
        title: String,
        values: List<String>,
        stage: ViewGroup? = null
    ) {
        if (values.isEmpty()) {
            missingOptionsHandler()
            return
        }
        showChoiceDialog(title, values) { chosen ->
            val newValue = values[chosen]
            val page = model.current
            val oldValue = model.field(page.name + "_" + index)
            model.edit(page.name + "_" + index, newValue)
            view.text = newValue
            if (page in setOf(Screen.IDENTITY_CARD, Screen.DRIVER_LICENSE, Screen.VOTER_CARD, Screen.IDENTITY_FILLED)) {
                view.setTextColor(Color.rgb(18, 13, 1))
            }
            if (oldValue != newValue) onFieldChanged(page, index, oldValue, newValue)
            (stage ?: (view.parent as? ViewGroup))?.let { owner ->
                choiceHandler(page, index, newValue, owner)
            }
        }
    }

    private fun showChoiceDialog(title: String, values: List<String>, onChosen: (Int) -> Unit) {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val adapter = object : ArrayAdapter<String>(
            activity,
            android.R.layout.simple_list_item_1,
            values
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent)
                val text = row as? TextView ?: row.findViewById<TextView>(android.R.id.text1)
                text.apply {
                    setSingleLine(false)
                    maxLines = Int.MAX_VALUE
                    ellipsize = null
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = dp(48)
                    setPadding(dp(24), dp(10), dp(24), dp(10))
                    layoutParams = (layoutParams ?: ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )).apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
                }
                return row
            }
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setAdapter(adapter) { _, chosen -> onChosen(chosen) }
            .show()
    }

    fun choose(view: TextView, index: Int, stage: ViewGroup) {
        val top = view.top
        val label = (0 until stage.childCount).map(stage::getChildAt).filterIsInstance<TextView>()
            .filter { it !== view && it !is EditText && it.tag != "Choose" }
            .map { it.top to it.text.toString() }
            .filter { it.first < top }.maxByOrNull { it.first }?.second.orEmpty()
        val dynamicValues = optionProvider(model.current, index, label)
        if (dynamicValues != null) {
            val title = if (model.current == Screen.CONTACTS) {
                activity.getString(CoreR.string.text_type_of_relationship)
            } else label
            select(view, index, title, dynamicValues, stage)
            return
        }
        when {
            label.contains("Birth") -> {
                val page = model.current
                val fieldKey = "${page.name}_$index"
                BirthdayDateDialog(activity, model.field(fieldKey)) { formatted ->
                    val oldValue = model.field(fieldKey)
                    view.text = formatted
                    if (page in setOf(Screen.IDENTITY_CARD, Screen.DRIVER_LICENSE, Screen.VOTER_CARD, Screen.IDENTITY_FILLED)) {
                        view.setTextColor(Color.rgb(18, 13, 1))
                    }
                    model.edit(fieldKey, formatted)
                    if (oldValue != formatted) onFieldChanged(page, index, oldValue, formatted)
                }.show()
            }
            label.contains("Gender") -> select(view, index, label, listOf("Male", "Female"), stage)
            label.contains("certificate", true) || label.contains("other certificates", true) -> {
                val screens = listOf(Screen.IDENTITY_CARD, Screen.DRIVER_LICENSE, Screen.VOTER_CARD)
                val values = listOf("ID Card", "Driving License", "Voter Card")
                showChoiceDialog("Select Certificate Type", values) { choice ->
                    val selectedScreen = screens[choice]
                    model.state.putString("authenticationCardScreen", selectedScreen.name)
                    model.go(selectedScreen, true)
                }
            }
            label.contains("Wallet") -> select(view, index, label, listOf("MTN Mobile Money", "Telecel Cash", "AT Money"), stage)
            label.contains("Relationship") -> select(view, index, label, listOf("Parent", "Spouse", "Sibling", "Friend"), stage)
            label.contains("Education") -> select(view, index, label, listOf("Uneducated", "Primary School", "Secondary School", "High School", "University Degree", "Masters Degree", "Doctorate Degree"), stage)
            label.contains("Marriage") -> select(view, index, label, listOf("Single", "Married", "Divorced"), stage)
            label.contains("Feedback") -> select(view, index, label, listOf("Feature Suggestion", "Specific Order Issue", "Other"), stage)
            label.contains("Cycle") -> select(view, index, label, listOf("Weekly", "Monthly"), stage)
            label.contains("Remuneration") -> select(view, index, label, listOf("Below GHS 1,000", "GHS 1,000–5,000", "Above GHS 5,000"), stage)
            else -> select(view, index, label, listOf("Employee", "Self-employed", "Other"), stage)
        }
    }

    fun amount(bar: SeekBar, stage: ViewGroup) {
        val value = (0 until stage.childCount).map(stage::getChildAt).filterIsInstance<TextView>()
            .find { it.tag == "GHS 50,000" || it.tag == "GHS 25,000" }
        val minimum = model.state.getLong("amountMin", 0L)
        val maximum = model.state.getLong("amountMax", -1L)
        val rangeValid = model.state.getBoolean("amountRangeValid", false) && minimum >= 0L && maximum >= minimum
        if (!rangeValid) {
            bar.isEnabled = false
            value?.text = "GHS 0"
            return
        }
        val steps = ceil((maximum - minimum).coerceAtLeast(0L) / 1000.0).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        bar.max = steps
        val minLabel = (0 until stage.childCount).map(stage::getChildAt).filterIsInstance<TextView>()
            .find { it.text.toString().replace(",", "").toLongOrNull() == 0L }
        val maxLabel = (0 until stage.childCount).map(stage::getChildAt).filterIsInstance<TextView>()
            .find { it.text.toString().replace(",", "").toLongOrNull() == 50_000L }
        minLabel?.text = String.format(Locale.US, "%,d", minimum)
        maxLabel?.text = String.format(Locale.US, "%,d", maximum)

        var amount = if (model.state.getBoolean("amountTouched", false)) {
            model.state.getLong("applyAmountValue", 0L).coerceIn(minimum, maximum)
        } else 0L
        val initialProgress = if (model.state.getBoolean("amountTouched", false)) {
            when {
                amount >= maximum -> steps
                amount <= minimum -> 0
                else -> ((amount - minimum) / 1000L).toInt().coerceIn(0, steps)
            }
        } else 0
        bar.progress = initialProgress
        fun display(amountValue: Long) {
            amount = amountValue.coerceIn(0L, maximum)
            value?.text = String.format(Locale.US, "GHS %,d", amount)
        }
        display(amount)
        fun amountAt(progress: Int): Long = if (progress >= steps) maximum else {
            (minimum + progress.toLong() * 1000L).coerceAtMost(maximum)
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, step: Int, fromUser: Boolean) {
                if (!fromUser) return
                val selected = amountAt(step)
                model.state.putBoolean("amountTouched", true)
                model.state.putLong("applyAmountValue", selected)
                display(selected)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {
                if (!model.state.getBoolean("amountTouched", false)) {
                    model.state.putBoolean("amountTouched", true)
                    model.state.putLong("applyAmountValue", minimum)
                    display(minimum)
                }
            }
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
    }
}
