package com.creditcedi.account

import com.creditcedi.core.Screen

object AccountLayouts {
    val screens = mapOf(
        Screen.ABOUT to R.layout.about,
        Screen.FEEDBACK_DETAIL_GENERIC to R.layout.feedback_detail_generic,
        Screen.ATTACHMENT_DELETE to R.layout.attachment_delete,
        Screen.ATTACHMENT_SOURCE to R.layout.attachment_source,
        Screen.SELECT_ORDER to R.layout.select_order,
        Screen.FEEDBACK_FILLED to R.layout.feedback_filled,
        Screen.FEEDBACK_FORM to R.layout.feedback_form,
        Screen.FEEDBACK_LIST to R.layout.feedback_list,
        Screen.FEEDBACK_EMPTY to R.layout.feedback_empty,
        Screen.LOGOUT to R.layout.logout,
        Screen.PROFILE to R.layout.profile,
        Screen.PROFILE_GUEST to R.layout.profile_guest,
        Screen.WALLET_EDIT to R.layout.wallet_edit,
        Screen.WALLET_SAVED to R.layout.wallet_saved,
        Screen.FEEDBACK_ANSWERED to R.layout.feedback_answered,
        Screen.FEEDBACK_PENDING to R.layout.feedback_pending,
        Screen.FEEDBACK_ATTACHMENTS to R.layout.feedback_attachments,
        Screen.FEEDBACK_SUCCESS to R.layout.feedback_success,
    )
}
