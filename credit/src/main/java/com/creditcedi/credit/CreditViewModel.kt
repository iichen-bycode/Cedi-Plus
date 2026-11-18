package com.creditcedi.credit

import com.creditcedi.core.Screen
import com.creditcedi.core.Screen.*

enum class OrderStatusFilter(val requestStatus: Int?) {
    ALL(null),
    PENDING(1),
    DISBURSING(2),
    TO_BE_REPAID(3),
    REPAID(4),
    DENIED(5),
    OVERDUE(6),
}

class CreditViewModel {
    fun orderStatusFilter(requestStatus: Int?) =
        OrderStatusFilter.values().firstOrNull { it.requestStatus == requestStatus } ?: OrderStatusFilter.ALL

    fun applicationDestination(signedIn: Boolean, hasOrder: Boolean) = when {
        !signedIn -> SIGN_IN
        hasOrder -> ORDER_DETAIL
        else -> WITHDRAW
    }

    fun orderDestination(label: String): Screen? = when (label) {
        "Waiting For Review" -> REVIEW_PENDING
        "Disbursing" -> DISBURSING
        "Pending Repayment" -> REPAYMENT
        "Rejected" -> DECLINED
        "Payout Failed" -> PAYOUT_FAILED
        "Overdue" -> OVERDUE
        "Repayment Completed" -> REPAID
        else -> null
    }
}
