package com.creditcedi.identity

import com.creditcedi.core.Screen
import com.creditcedi.core.Screen.*

class IdentityViewModel {
    fun next(screen: Screen) = when (screen) {
        WALLET_SETUP -> CONTACTS
        CONTACTS -> PERSONAL_INFO
        PERSONAL_INFO -> IDENTITY_CARD
        FACE_PREVIEW -> IDENTITY_FILLED
        else -> ORDER_DETAIL
    }
}
