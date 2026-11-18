package com.creditcedi.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import com.creditcedi.core.DesignStage
import com.creditcedi.core.JourneyViewModel
import com.creditcedi.core.Screen
import java.util.Locale

internal class FormBinder(private val activity: Activity, private val model: JourneyViewModel) {
    private fun key(index: Int) = model.current.name + "_" + index

    fun input(field: EditText, screen: Screen, index: Int) {
        val key = screen.name + "_" + index
        field.setText(model.field(key))
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                model.edit(key, s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    fun restoreChoice(view: TextView, index: Int) {
        val choice = model.field(key(index))
        if (choice.isNotEmpty()) view.text = choice
    }

    fun select(view: TextView, index: Int, title: String, values: List<String>) {
        AlertDialog.Builder(activity).setTitle(title)
            .setItems(values.toTypedArray()) { _, chosen ->
                view.text = values[chosen]
                model.edit(key(index), values[chosen])
            }.show()
    }

    fun choose(view: TextView, index: Int, stage: DesignStage) {
        val top = (view.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin
        val label = (0 until stage.childCount).map(stage::getChildAt).filterIsInstance<TextView>()
            .filter { it !== view && it !is EditText && it.tag != "Choose" }
            .map { (it.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin to it.text.toString() }
            .filter { it.first < top }.maxByOrNull { it.first }?.second.orEmpty()
        when {
            label.contains("Birth") -> DatePickerDialog(activity, { _, year, month, day ->
                val formatted = String.format(Locale.UK, "%02d-%02d-%04d", day, month + 1, year)
                view.text = formatted
                model.edit(key(index), formatted)
            }, 2000, 0, 1).show()
            label.contains("Gender") -> select(view, index, label, listOf("Male", "Female"))
            label.contains("certificate", true) || label.contains("other certificates", true) ->
                AlertDialog.Builder(activity).setTitle("Select Certificate Type")
                    .setItems(arrayOf("ID Card", "Driving License", "Voter Card")) { _, choice ->
                        model.go(listOf(Screen.IDENTITY_CARD, Screen.DRIVER_LICENSE, Screen.VOTER_CARD)[choice], true)
                    }.show()
            label.contains("Wallet") -> select(view, index, label, listOf("MTN Mobile Money", "Telecel Cash", "AT Money"))
            label.contains("Relationship") -> select(view, index, label, listOf("Parent", "Spouse", "Sibling", "Friend"))
            label.contains("Education") -> select(view, index, label, listOf("Uneducated", "Primary School", "Secondary School", "High School", "University Degree", "Masters Degree", "Doctorate Degree"))
            label.contains("Marriage") -> select(view, index, label, listOf("Single", "Married", "Divorced"))
            label.contains("Feedback") -> select(view, index, label, listOf("Feature Suggestion", "Specific Order Issue", "Other"))
            label.contains("Cycle") -> select(view, index, label, listOf("Weekly", "Monthly"))
            label.contains("Remuneration") -> select(view, index, label, listOf("Below GHS 1,000", "GHS 1,000–5,000", "Above GHS 5,000"))
            else -> select(view, index, label, listOf("Employee", "Self-employed", "Other"))
        }
    }

    fun amount(bar: SeekBar, stage: DesignStage) {
        val value = (0 until stage.childCount).map(stage::getChildAt).filterIsInstance<TextView>()
            .find { it.tag == "GHS 50,000" || it.tag == "GHS 25,000" }
        bar.progress = model.state.getInt("amountStep", 49)
        fun display(step: Int) { value?.text = String.format(Locale.US, "GHS %,d", (step + 1) * 1000) }
        display(bar.progress)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, step: Int, user: Boolean) {
                model.state.putInt("amountStep", step)
                display(step)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
    }
}
