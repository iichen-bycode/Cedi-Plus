package com.creditcedi.account

import android.os.Bundle

class FeedbackViewModel(private val state: Bundle) {
    fun attachments(images: Boolean): List<String> =
        state.getStringArrayList(if (images) "images" else "documents").orEmpty()

    fun attach(uri: String, images: Boolean): Boolean {
        val items = attachments(images).toMutableList()
        if (uri in items) return true
        if (items.size >= 9) return false
        items += uri
        save(images, items)
        return true
    }

    fun remove(uri: String, images: Boolean) = save(images, attachments(images) - uri)

    private fun save(images: Boolean, items: List<String>) {
        state.putStringArrayList(if (images) "images" else "documents", ArrayList(items))
    }

    fun clear() {
        state.remove("images")
        state.remove("documents")
    }
}
