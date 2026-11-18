package com.creditcedi.credit

import com.creditcedi.core.Screen

object CreditLayouts {
    val screens = mapOf(
        Screen.ORDERS to R.layout.orders,
        Screen.ORDERS_EMPTY to R.layout.orders_empty,
        Screen.EXTENSION to R.layout.extension,
        Screen.ELIGIBLE to R.layout.eligible,
        Screen.EXTENSION_CONFIRM to R.layout.extension_confirm,
        Screen.OVERDUE to R.layout.overdue,
        Screen.DISBURSING to R.layout.disbursing,
        Screen.REVIEW_PENDING to R.layout.review_pending,
        Screen.ORDER_SUCCESS to R.layout.order_success,
        Screen.ORDER_DETAIL to R.layout.order_detail,
        Screen.RATING_FEEDBACK to R.layout.rating_feedback,
        Screen.RATING_CHANGED to R.layout.rating_changed,
        Screen.RATING to R.layout.rating,
        Screen.PERMISSION_PROMPT to R.layout.permission_prompt,
        Screen.WITHDRAW_EXIT to R.layout.withdraw_exit,
        Screen.WITHDRAW_SUCCESS to R.layout.withdraw_success,
        Screen.WITHDRAW_ADJUSTED to R.layout.withdraw_adjusted,
        Screen.HOME_ACTIVE to R.layout.home_active,
        Screen.HOME_PRODUCTS to R.layout.home_products,
        Screen.HOME_APPLIED to R.layout.home_applied,
        Screen.HOME to R.layout.home,
        Screen.REVIEW_EXIT to R.layout.review_exit,
        Screen.REVIEW_COUNTDOWN to R.layout.review_countdown,
        Screen.PAYOUT_FAILED to R.layout.payout_failed,
        Screen.REPAID_LATE to R.layout.repaid_late,
        Screen.DECLINED to R.layout.declined,
        Screen.REPAID to R.layout.repaid,
        Screen.REPAYMENT to R.layout.repayment,
        Screen.WITHDRAW to R.layout.withdraw,
        Screen.LUCKY to R.layout.lucky,
    )
}
