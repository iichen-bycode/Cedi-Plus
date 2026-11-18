package com.creditcedi.identity

import com.creditcedi.core.Screen

object IdentityLayouts {
    val screens = mapOf(
        Screen.FACE_FAILED to R.layout.face_failed,
        Screen.FACE_GUIDE to R.layout.face_guide,
        Screen.KYC_EXIT to R.layout.kyc_exit,
        Screen.BIRTHDAY to R.layout.birthday,
        Screen.EDUCATION to R.layout.education,
        Screen.VOTER_CARD to R.layout.voter_card,
        Screen.DRIVER_LICENSE to R.layout.driver_license,
        Screen.IDENTITY_FILLED to R.layout.identity_filled,
        Screen.IDENTITY_CARD to R.layout.identity_card,
        Screen.PERSONAL_INFO to R.layout.personal_info,
        Screen.CONTACTS to R.layout.contacts,
        Screen.WALLET_SETUP to R.layout.wallet_setup,
        Screen.DOCUMENT_GUIDE to R.layout.document_guide,
        Screen.FACE_PREVIEW to R.layout.face_preview,
        Screen.DOCUMENT_PREVIEW to R.layout.document_preview,
        Screen.CITY to R.layout.city,
    )
}
