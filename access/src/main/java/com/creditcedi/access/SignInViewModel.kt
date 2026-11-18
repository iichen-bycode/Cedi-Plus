package com.creditcedi.access

class SignInViewModel {
    fun validationMessage(phone: String, code: String, accepted: Boolean): String? = when {
        phone.length !in 9..10 || !phone.all(Char::isDigit) -> "Please enter a valid phone number"
        !accepted -> "Please agree to the Terms & Conditions"
        code.isBlank() || !code.all(Char::isDigit) -> "Please enter the verification code"
        else -> null
    }
}
