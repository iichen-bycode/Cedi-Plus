package com.creditcedi.access

class SignInViewModel {
    fun validationMessage(phone: String, code: String, accepted: Boolean): String? = when {
        phone.length !in 9..10 || !phone.all(Char::isDigit) -> "Please enter a valid phone number"
        !accepted -> "Please agree to the Terms & Conditions"
        code != "1234" -> "Use the demo verification code 1234"
        else -> null
    }
}
