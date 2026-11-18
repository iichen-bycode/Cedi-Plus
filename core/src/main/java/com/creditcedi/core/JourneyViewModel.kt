package com.creditcedi.core

import android.os.Bundle
import com.creditcedi.core.Screen.*

class JourneyViewModel(val state: Bundle = Bundle()) {
    val current get() = Screen.valueOf(state.getString("screen") ?: WELCOME.name)
    var render: (Screen) -> Unit = {}
    private val history = state.getStringArrayList("history") ?: arrayListOf()
    val signedIn get() = state.getBoolean("signedIn")
    val hasOrder get() = state.getBoolean("hasOrder")
    val hasFeedback get() = state.getBoolean("hasFeedback")
    val home get() = if (hasOrder) HOME_ACTIVE else HOME
    val profile get() = if (signedIn) PROFILE else PROFILE_GUEST
    val orders get() = if (hasOrder) ORDERS else ORDERS_EMPTY

    fun go(screen: Screen, replace: Boolean = false) {
        if (screen == current) return
        if (!replace) history.add(current.name)
        state.putStringArrayList("history", ArrayList(history))
        state.putString("screen", screen.name)
        render(screen)
    }

    fun root(screen: Screen) {
        history.clear()
        state.putStringArrayList("history", arrayListOf())
        state.putString("screen", screen.name)
        render(screen)
    }

    fun back(): Boolean {
        if (history.isEmpty()) return false
        val previous = Screen.valueOf(history.removeAt(history.lastIndex))
        state.putStringArrayList("history", ArrayList(history))
        state.putString("screen", previous.name)
        render(previous)
        return true
    }

    fun login() { state.putBoolean("signedIn", true); root(home) }
    fun signOut() {
        for (key in state.keySet().filter { it.startsWith("field.") }) state.remove(key)
        state.putBoolean("signedIn", false)
        state.putBoolean("hasOrder", false)
        state.putBoolean("hasFeedback", false)
        root(HOME)
    }

    fun placeOrder() { state.putBoolean("hasOrder", true); go(ORDER_SUCCESS) }
    fun submitFeedback() { state.putBoolean("hasFeedback", true); go(FEEDBACK_SUCCESS) }
    fun field(key: String) = state.getString("field.$key").orEmpty()
    fun edit(key: String, value: String) { state.putString("field.$key", value) }
}
