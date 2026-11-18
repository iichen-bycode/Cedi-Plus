package com.creditcedi.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.Manifest
import com.creditcedi.account.FeedbackViewModel
import com.creditcedi.identity.IdentityViewModel
import com.creditcedi.credit.CreditViewModel
import com.creditcedi.credit.OrderStatusFilter
import com.creditcedi.credit.R as CreditR
import com.creditcedi.access.SignInViewModel
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.content.pm.ActivityInfo
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.os.SystemClock
import android.graphics.Typeface
import com.creditcedi.access.R as AccessR
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.creditcedi.access.AccessLayouts
import com.creditcedi.account.AccountLayouts
import com.creditcedi.credit.CreditLayouts
import com.creditcedi.identity.IdentityLayouts
import com.creditcedi.core.DesignStage
import com.creditcedi.core.R as CoreR
import com.creditcedi.core.JourneyViewModel
import com.creditcedi.core.Screen
import com.creditcedi.core.Screen.*
import java.util.Locale
import java.util.IdentityHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private lateinit var model: JourneyViewModel
    private lateinit var viewport: ScrollView
    private lateinit var stage: DesignStage
    private lateinit var form: FormBinder
    private val pages = AccessLayouts.screens + AccountLayouts.screens + CreditLayouts.screens + IdentityLayouts.screens
    private val consent by lazy { getSharedPreferences("preferences", MODE_PRIVATE) }
    private val credit = CreditViewModel()
    private val signInForm = SignInViewModel()
    private val identity = IdentityViewModel()
    private val feedback by lazy { FeedbackViewModel(model.state) }
    private val fields = mutableListOf<EditText>()
    private data class OrderCardSlot(val index: Int, val originalTop: Int)
    private data class AuthContinuation(val info: org.json.JSONObject, val order: org.json.JSONObject?)
    private val orderCardSlots = IdentityHashMap<View, OrderCardSlot>()
    private var fullOrdersHeight = 0f
    private var orderRecords: List<org.json.JSONObject> = emptyList()
    private var ratingPromptShown = false
    private var payFailPromptShown = false
    private val screen get() = model.current
    private var preview: Bitmap? = null
    private val api by lazy { CediApi(this) }
    private var selfieUri: Uri? = null
    private var cameraOutputUri: Uri? = null
    private val localPhotoUris = mutableSetOf<Uri>()
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private enum class PermissionPurpose { INITIAL, APPLY }
    private var permissionPurpose: PermissionPurpose? = null
    private var permissionQueue = emptyList<String>()
    private var permissionIndex = 0
    private var afterPermissions: (() -> Unit)? = null
    private var loadingDialog: Dialog? = null
    private var pendingDocumentScreen: Screen? = null
    private var pendingRepaymentRefresh = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = JourneyViewModel(savedInstanceState?.getBundle("journey") ?: Bundle())
        if (api.hasSession()) model.state.putBoolean("signedIn", true)
        setContentView(R.layout.activity_main)
        viewport = findViewById(R.id.viewport)
        window.statusBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            findViewById<View>(R.id.window_content).setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        form = FormBinder(this, model)
        model.render = ::show
        val requested = intent.getStringExtra("screen")
        val inspect = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val route = if (inspect && savedInstanceState == null) Screen.values().find { it.name == requested } else null
        if (route == SIGN_IN_FILLED) {
            model.edit("SIGN_IN_7", "9127323821")
            model.edit("SIGN_IN_8", "9832")
            model.state.putBoolean("agreement_SIGN_IN", true)
            startOtpCooldown()
            model.root(SIGN_IN)
        } else if (route == COUNTRY || screen == COUNTRY) {
            model.root(SIGN_IN)
            openCountryDialog()
        } else if (route != null) model.root(route) else show(screen)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("journey", model.state)
    }

    override fun onDestroy() {
        activityScope.cancel()
        loadingDialog?.dismiss()
        loadingDialog = null
        clearSelfieCapture()
        cameraOutputUri?.let(::deleteLocalPhoto)
        cameraOutputUri = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized && pendingRepaymentRefresh) {
            pendingRepaymentRefresh = false
            loadOrderDetail()
            return
        }
        if (!::model.isInitialized || !model.signedIn || screen !in setOf(HOME, HOME_ACTIVE, HOME_PRODUCTS, HOME_APPLIED)) return
        refreshSignedInHome()
    }

    private fun <T> apiCall(
        message: String,
        request: suspend () -> T,
        onSuccess: (T) -> Unit
    ) {
        activityScope.launch {
            showLoading(message)
            try {
                onSuccess(request())
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (failure is ExpiredSessionException) {
                    model.state.putBoolean("signedIn", false)
                    model.root(SIGN_IN)
                    notice("Login expired. Please sign in again.")
                } else {
                    notice(failure.message ?: "Unable to connect. Please try again.")
                }
            } finally {
                hideLoading()
            }
        }
    }

    private fun showLoading(message: String) {
        loadingDialog?.dismiss()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(24, 20, 24, 20)
            addView(ProgressBar(this@MainActivity), LinearLayout.LayoutParams(40, 40))
            addView(TextView(this@MainActivity).apply {
                text = message
                textSize = 15f
                setPadding(18, 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        loadingDialog = Dialog(this).apply {
            setContentView(content)
            setCancelable(false)
            window?.setBackgroundDrawableResource(android.R.color.white)
            show()
        }
    }

    private fun hideLoading() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun requestAppPermissions(purpose: PermissionPurpose, onComplete: () -> Unit) {
        permissionPurpose = purpose
        permissionQueue = if (purpose == PermissionPurpose.INITIAL) {
            listOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_SMS,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            listOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_SMS,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        permissionIndex = 0
        afterPermissions = onComplete
        requestNextPermission()
    }

    private fun requestNextPermission() {
        while (permissionIndex < permissionQueue.size &&
            checkSelfPermission(permissionQueue[permissionIndex]) == PackageManager.PERMISSION_GRANTED
        ) permissionIndex++
        if (permissionIndex >= permissionQueue.size) {
            completePermissionFlow()
            return
        }
        requestPermissions(arrayOf(permissionQueue[permissionIndex]), PERMISSION_REQUEST)
    }

    private fun completePermissionFlow() {
        val purpose = permissionPurpose
        permissionPurpose = null
        permissionQueue = emptyList()
        permissionIndex = 0
        val phoneGranted = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val requiredForApply = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_SMS,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        when {
            purpose == PermissionPurpose.INITIAL && !phoneGranted -> showPhonePermissionPrompt()
            purpose == PermissionPurpose.APPLY && !requiredForApply -> notice("Please allow the required permissions to continue.")
            else -> afterPermissions?.invoke()
        }
        afterPermissions = null
    }

    private fun showPhonePermissionPrompt() {
        AlertDialog.Builder(this)
            .setMessage("Phone permission is required to continue. Allow it in Settings, then reopen the app.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open Settings") { _, _ ->
                val target = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                startActivity(target)
            }
            .show()
    }

    @Deprecated("Platform permission callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) takePhoto()
            else notice("Allow camera access to take this photo.")
            return
        }
        if (requestCode != PERMISSION_REQUEST) return
        permissionIndex++
        requestNextPermission()
    }

    private fun loadHomeData() {
        apiCall("Loading products…", {
            sendServiceEventQuietly("OPEN_APP", "HOME", model.signedIn)
            if (consent.getBoolean("privacyEventPending", false) &&
                sendServiceEventQuietly("CLICK_PRIVACY_BUTTO", "PRIVACY", model.signedIn)
            ) {
                consent.edit().remove("privacyEventPending").apply()
            }
            api.firstOpenIfNeeded()
            val products = api.products()
            products to if (model.signedIn) fetchUserInfo() else null
        }) { (products, userInfo) ->
            val firstProduct = products.optJSONArray(ApiField.PRODUCT_LIST)?.optJSONObject(0)
            firstProduct?.optString(ApiField.PRODUCT_ID)?.takeIf(String::isNotBlank)?.let {
                model.state.putString("selectedProductId", it)
            }
            userInfo?.let(::maybeShowServerPrompts)
        }
    }

    private fun show(page: Screen) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(viewport.windowToken, 0)
        viewport.removeAllViews()
        stage = layoutInflater.inflate(pages.getValue(page), viewport, false) as DesignStage
        fields.clear()
        for (i in 0 until stage.childCount) {
            val view = stage.getChildAt(i)
            val label = view.tag as? String ?: continue
            when (view) {
                is EditText -> {
                    fields += view
                    // Preserve the keys used by saved login forms before the layout update.
                    val fieldIndex = if (page == SIGN_IN) 6 + fields.size else i
                    form.input(view, page, fieldIndex)
                }
                is CheckBox -> {
                    val key = "agreement_" + page.name
                    view.isChecked = model.state.getBoolean(key)
                    view.setOnCheckedChangeListener { _, checked -> model.state.putBoolean(key, checked) }
                }
                is SeekBar -> form.amount(view, stage)
                else -> {
                    if (view is TextView) {
                        form.restoreChoice(view, i)
                        if (label == "+233") view.text = countryPrefix
                    }
                    view.setOnClickListener { handle(label, view, i) }
                }
            }
        }
        if (page == ORDERS) captureOrderCardSlots()
        val footer = findViewById<FrameLayout>(R.id.footer)
        footer.removeAllViews()
        val footerHeight = when (page) {
            PRIVACY -> 131
            HOME, HOME_APPLIED, HOME_PRODUCTS, HOME_ACTIVE, ELIGIBLE, ORDERS, ORDERS_EMPTY, PROFILE, PROFILE_GUEST -> 74
            else -> 0
        }
        if (footerHeight > 0) footer.addView(stage.detachFooter(footerHeight))
        viewport.addView(stage)
        if (page == ORDERS || page == ORDERS_EMPTY) {
            fullOrdersHeight = stage.artboardHeight
            val currentFilter = credit.orderStatusFilter(
                model.state.getInt("filterOrderStatus", 0).takeIf { it in 1..6 }
            )
            configureOrderTabs(currentFilter)
            if (page == ORDERS) renderOrderCards(currentFilter)
        }
        if (page == SIGN_IN) renderOtpButton()
        viewport.scrollTo(0, 0)
        if (page == DOCUMENT_PREVIEW) {
            val destination = pendingDocumentScreen ?: IDENTITY_CARD
            stage.setOnClickListener {
                pendingDocumentScreen = null
                model.go(destination, true)
            }
        }
        if (page == WELCOME) {
            stage.setOnClickListener { startJourney() }
            stage.postDelayed({ if (!isFinishing && !isDestroyed && screen == WELCOME) startJourney() }, 1500)
        }
        if (page == REVIEW_COUNTDOWN) countdown(60)
        if (page == FACE_PREVIEW && preview != null) {
            val image = ImageView(this)
            image.setImageBitmap(preview)
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            val density = resources.displayMetrics.density
            val params = android.view.ViewGroup.MarginLayoutParams((335*density).toInt(), (535*density).toInt())
            params.leftMargin = (20*density).toInt()
            params.topMargin = (85*density).toInt()
            stage.addView(image, params)
        }
    }

    private fun startOtpCooldown() {
        model.state.putLong("otpRetryAt", SystemClock.elapsedRealtime() + 60_000L)
    }

    private fun renderOtpButton() {
        val button = stage.findViewById<TextView>(AccessR.id.request_otp)
        val remaining = model.state.getLong("otpRetryAt") - SystemClock.elapsedRealtime()
        val waiting = remaining > 0
        button.isEnabled = !waiting
        button.text = if (waiting) getString(AccessR.string.otp_seconds, (remaining + 999) / 1000)
            else getString(com.creditcedi.core.R.string.text_get_otp)
        button.setTextColor(if (waiting) 0xff666666.toInt() else 0xff120d01.toInt())
        button.typeface = if (waiting) Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else Typeface.create("sans-serif-black", Typeface.ITALIC)
        if (waiting) button.postDelayed({
            if (button.parent === stage && !isDestroyed) renderOtpButton()
        }, (remaining - 1) % 1000 + 1)
    }

    private fun countdown(seconds: Int) {
        val owner = stage
        val timer = (0 until owner.childCount).map(owner::getChildAt).filterIsInstance<TextView>().find { it.tag == "60 s" }
        fun tick(left: Int) {
            if (screen != REVIEW_COUNTDOWN || owner !== stage || isDestroyed) return
            timer?.text = "$left s"
            if (left == 0) model.root(model.orders) else owner.postDelayed({ tick(left - 1) }, 1000)
        }
        tick(seconds)
    }

    private fun startJourney() {
        if (!consent.getBoolean("accepted", false)) {
            model.root(PRIVACY)
            return
        }
        requestAppPermissions(PermissionPurpose.INITIAL) {
            model.root(model.home)
            loadHomeData()
        }
    }
    private fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun go(page: Screen) = model.go(page)
    private fun leave() { if (!model.back()) finish() }

    private suspend fun fetchUserInfo(recommended: Boolean = false): org.json.JSONObject {
        val info = api.userInfo(recommended = recommended, device = deviceRequestFacts())
        if (info.optInt(ApiField.NEED_COLLECT_INFO) == 1) {
            api.refreshDevice(api.compressedDevicePayload())
        }
        return info
    }

    private suspend fun prepareAuthentication(): Screen {
        val info = api.authenticationInfo()
        model.edit("WALLET_SETUP_10", info.optString(ApiField.WALLET_TYPE))
        model.edit("WALLET_SETUP_11", info.optString(ApiField.WALLET_ACCOUNT))

        val contacts = runCatching { org.json.JSONArray(info.optString(ApiField.CONTACTS)) }
            .getOrDefault(org.json.JSONArray())
        for (index in 0 until minOf(2, contacts.length())) {
            val contact = contacts.optJSONObject(index) ?: continue
            model.edit("CONTACTS_${31 + index}", contact.optString(ApiField.CONTACT_PHONE))
            model.edit("CONTACTS_${33 + index}", contact.optString(ApiField.CONTACT_NAME))
            model.edit("CONTACTS_${29 + index}", contact.optString(ApiField.CONTACT_RELATION))
        }

        model.edit("PERSONAL_INFO_35", info.optString(ApiField.EDUCATION))
        model.edit("PERSONAL_INFO_36", info.optString(ApiField.MONTHLY_INCOME))
        model.edit("PERSONAL_INFO_37", info.optString(ApiField.MARITAL_STATUS))
        model.edit("PERSONAL_INFO_38", info.optString(ApiField.JOB_TITLE))
        model.edit("PERSONAL_INFO_39", info.optString(ApiField.INDUSTRY))
        model.edit("PERSONAL_INFO_40", info.optString(ApiField.PAY_CYCLE))
        model.edit("PERSONAL_INFO_41", info.optString(ApiField.EMAIL))
        model.edit("PERSONAL_INFO_42", info.optString(ApiField.ADDRESS))
        model.edit("PERSONAL_INFO_43", info.optString(ApiField.MESSAGING_ACCOUNT))

        val cardType = info.optString(ApiField.CARD_TYPE)
        val cardScreen = when {
            cardType.contains("driving", ignoreCase = true) -> DRIVER_LICENSE
            cardType.contains("voter", ignoreCase = true) -> VOTER_CARD
            else -> IDENTITY_CARD
        }
        val idIndex = if (cardScreen == IDENTITY_CARD) 18 else 20
        val dateIndex = if (cardScreen == IDENTITY_CARD) 26 else 28
        val imagePath = when (cardScreen) {
            DRIVER_LICENSE -> info.optString(ApiField.FRONT_LICENSE)
            VOTER_CARD -> info.optString(ApiField.FRONT_VOTER_CARD)
            else -> info.optString(ApiField.FRONT_IMAGE)
        }
        model.state.putString("documentImage_${cardScreen.name}", imagePath)
        val idValue = when (cardScreen) {
            DRIVER_LICENSE -> info.optString(ApiField.LICENSE_NUMBER)
            VOTER_CARD -> info.optString(ApiField.VOTER_NUMBER)
            else -> info.optString(ApiField.ID_NUMBER)
        }
        model.edit("${cardScreen.name}_$idIndex", idValue)
        model.edit("${cardScreen.name}_${idIndex + 1}", info.optString(ApiField.FULL_NAME))
        model.edit("${cardScreen.name}_$dateIndex", info.optString(ApiField.BIRTH_DATE))
        model.edit("${cardScreen.name}_${dateIndex + 1}", info.optString(ApiField.GENDER))
        val requiredContacts = info.optInt(ApiField.CONTACT_COUNT, 1).coerceIn(1, 2)

        return when {
            info.optString(ApiField.WALLET_ACCOUNT).isBlank() || info.optString(ApiField.WALLET_TYPE).isBlank() -> WALLET_SETUP
            contacts.length() < requiredContacts -> CONTACTS
            info.optString(ApiField.EMAIL).isBlank() || info.optString(ApiField.ADDRESS).isBlank() -> PERSONAL_INFO
            else -> cardScreen
        }
    }

    private suspend fun sendServiceEventQuietly(type: String, page: String, authenticated: Boolean): Boolean = try {
        api.sendEvent(type, page, authenticated)
        true
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private fun refreshSignedInHome() {
        apiCall("Refreshing account…", { fetchUserInfo() }) { info ->
            maybeShowServerPrompts(info)
            val product = info.optJSONObject(ApiField.LOAN_PRODUCT)
            product?.optString(ApiField.PRODUCT_ID)?.takeIf(String::isNotBlank)?.let {
                model.state.putString("selectedProductId", it)
            }
        }
    }

    private fun maybeShowServerPrompts(info: org.json.JSONObject) {
        if (!payFailPromptShown && info.optInt(ApiField.USER_PAY_FAIL) == 1) {
            val message = info.optString(ApiField.PAY_FAIL_CONTENT).ifBlank { "Your previous loan payout was unsuccessful." }
            payFailPromptShown = true
            viewport.post {
                if (!isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this)
                        .setTitle("Payout update")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
        val tips = info.optJSONObject(ApiField.RATING_DIALOG)?.optString(ApiField.LOW_SCORE_TIPS).orEmpty()
        if (!ratingPromptShown && tips.isNotBlank()) {
            ratingPromptShown = true
            viewport.post { if (!isFinishing && !isDestroyed) showServerRatingPrompt(tips) }
        }
    }

    private fun showServerRatingPrompt(lowScoreTips: String) {
        var selectedScore = 0
        var callbackSent = false
        val feedbackText = EditText(this).apply {
            hint = "Tell us what you think (optional)"
            minLines = 2
            setPadding(32, 12, 32, 12)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("How would you rate Cedi Plus?")
            .setSingleChoiceItems(arrayOf("1 star", "2 stars", "3 stars", "4 stars", "5 stars"), -1) { _, which ->
                selectedScore = which + 1
            }
            .setView(feedbackText)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm", null)
            .create()
        fun send(action: String, score: Int?, text: String, after: () -> Unit = {}) {
            if (callbackSent) return
            callbackSent = true
            apiCall("Saving your feedback…", { api.submitRating(action, score, text) }) { after() }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (selectedScore == 0) {
                    notice("Please select a rating from 1 to 5.")
                    return@setOnClickListener
                }
                val score = selectedScore
                val comment = feedbackText.text?.toString().orEmpty()
                dialog.dismiss()
                send("1", score, comment) {
                    if (score >= 4) openStoreRating()
                    else AlertDialog.Builder(this)
                        .setMessage(lowScoreTips)
                        .setPositiveButton("Confirm", null)
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                dialog.dismiss()
                send("2", null, "")
            }
        }
        dialog.setOnCancelListener { send("2", null, "") }
        dialog.show()
    }

    private fun openStoreRating() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (_: android.content.ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            } catch (_: android.content.ActivityNotFoundException) {
                notice("No app store is available on this device.")
            }
        }
    }

    @Deprecated("Platform callback for API 25 and above")
    override fun onBackPressed() {
        when (screen) {
            WITHDRAW, WITHDRAW_ADJUSTED -> go(WITHDRAW_EXIT)
            IDENTITY_CARD, VOTER_CARD, DRIVER_LICENSE -> go(KYC_EXIT)
            REVIEW_COUNTDOWN -> go(REVIEW_EXIT)
            else -> leave()
        }
    }

    private companion object {
        const val PERMISSION_REQUEST = 870
        const val CAMERA_PERMISSION_REQUEST = 871
    }

    private fun applyForCredit() {
        if (!model.signedIn) {
            go(SIGN_IN)
            return
        }
        requestAppPermissions(PermissionPurpose.APPLY) {
            apiCall("Loading your application…", {
                val info = fetchUserInfo(recommended = false)
                val product = info.optJSONObject(ApiField.LOAN_PRODUCT)?.optString(ApiField.PRODUCT_ID).orEmpty()
                    .ifBlank { model.state.getString("selectedProductId").orEmpty() }
                val productId = if (product.isBlank()) {
                    api.products().optJSONArray(ApiField.PRODUCT_LIST)?.optJSONObject(0)?.optString(ApiField.PRODUCT_ID).orEmpty()
                } else product
                val status = info.optInt(ApiField.USER_STATUS)
                if (status == 1) {
                    productId to prepareAuthentication()
                } else if (productId.isNotBlank()) {
                    val space = api.spaceInfo(productId, deviceRequestFacts())
                    when (space.optInt(ApiField.SPACE_STATUS)) {
                        1 -> productId to prepareAuthentication()
                        2 -> {
                            val spaceProductId = space.optJSONObject(ApiField.LOAN_PRODUCT)
                                ?.optString(ApiField.PRODUCT_ID).orEmpty().ifBlank { productId }
                            spaceProductId to WITHDRAW
                        }
                        3 -> {
                            val orderInfo = space.optJSONObject(ApiField.ORDER_INFO)
                            val orderNumber = orderInfo?.optString(ApiField.ORDER_NO).orEmpty().ifBlank {
                                api.orderList(1, 20).optJSONArray(ApiField.ORDER_LIST)
                                    ?.optJSONObject(0)?.optString(ApiField.ORDER_NO).orEmpty()
                            }
                            if (orderNumber.isNotBlank()) {
                                model.state.putBoolean("hasOrder", true)
                                model.state.putString("activeOrderNo", orderNumber)
                                val detail = api.orderDetail(orderNumber, deviceRequestFacts())
                                productId to (detail.optJSONObject(ApiField.ORDER_INFO)?.let(::orderDestination) ?: ORDER_DETAIL)
                            } else productId to ORDERS
                        }
                        else -> productId to credit.applicationDestination(true, model.hasOrder)
                    }
                } else productId to credit.applicationDestination(true, model.hasOrder)
            }) { (productId, destination) ->
                if (productId.isNotBlank()) model.state.putString("selectedProductId", productId)
                if (productId.isBlank() && destination !in setOf(WALLET_SETUP, ORDERS) && !model.hasOrder) {
                    notice("No loan product is available right now.")
                } else if (destination == ORDERS) {
                    model.root(ORDERS)
                    loadOrders()
                } else go(destination)
            }
        }
    }

    private fun submitApplication() {
        val productId = model.state.getString("selectedProductId").orEmpty()
        if (productId.isBlank()) {
            notice("Select an available product before applying.")
            return
        }
        apiCall("Your application is being submitted. Please do not leave this screen.", {
            val userInfo = fetchUserInfo()
            if (userInfo.optInt(ApiField.USER_STATUS, 0) != 2) {
                Triple(1, null, prepareAuthentication())
            } else if (userInfo.optInt(ApiField.USER_LIVENESS, 0) != 1) {
                Triple(2, null, null)
            } else {
                val created = createOrderAndReadNumber(productId)
                Triple(0, created, null)
            }
        }) { result ->
            val response = result.second
            if (result.first == 1) {
                notice("Complete identity verification before submitting your application.")
                go(result.third ?: WALLET_SETUP)
            } else if (result.first == 2) {
                notice("Complete face verification before submitting your application.")
                go(FACE_GUIDE)
            } else if (response != null) {
                AdjustAnalytics.loanApplicationResponse(this, response.optInt(ApiField.FIRST_APPLICATION) == 1)
                model.state.putBoolean("hasOrder", true)
                response.optString(ApiField.ORDER_NO).takeIf(String::isNotBlank)?.let {
                    model.state.putString("activeOrderNo", it)
                }
                go(ORDER_SUCCESS)
            }
        }
    }

    private suspend fun createOrderAndReadNumber(productId: String): org.json.JSONObject {
        val response = api.createOrder(productId, api.compressedDevicePayload(), deviceRequestFacts())
        val orderNumber = response.optString(ApiField.ORDER_NO).takeIf(String::isNotBlank)
            ?: api.orderList(1, 20).optJSONArray(ApiField.ORDER_LIST)?.optJSONObject(0)
                ?.optString(ApiField.ORDER_NO)?.takeIf(String::isNotBlank)
        orderNumber?.let { model.state.putString("activeOrderNo", it) }
        return response
    }

    private fun configureOrderTabs(selected: OrderStatusFilter) {
        val tabs = stage.findViewById<HorizontalScrollView>(CreditR.id.order_tabs) ?: return
        val row = tabs.getChildAt(0) as? LinearLayout ?: return
        val filters = OrderStatusFilter.values()
        val tabWidthsDp = intArrayOf(100, 88, 100, 120, 86, 82, 88)
        val density = resources.displayMetrics.density
        for (index in 0 until minOf(row.childCount, filters.size)) {
            val tab = row.getChildAt(index) as? TextView ?: continue
            val filter = filters[index]
            val active = filter == selected
            val params = tab.layoutParams as LinearLayout.LayoutParams
            params.width = maxOf(
                (tabWidthsDp[index] * density).roundToInt(),
                (tab.paint.measureText(tab.text.toString()) + 32 * density).roundToInt()
            )
            params.height = (48 * density).roundToInt()
            params.rightMargin = if (index < filters.lastIndex) (8 * density).roundToInt() else 0
            params.setMarginEnd(params.rightMargin)
            tab.layoutParams = params
            tab.isClickable = true
            tab.isFocusable = true
            tab.isSelected = active
            tab.contentDescription = tab.text.toString() + if (active) ", selected" else ""
            tab.setBackgroundResource(
                if (active) CoreR.drawable.surface_df637d258387
                else CoreR.drawable.surface_5921979202e7
            )
            tab.setTextColor(if (active) 0xff120d01.toInt() else 0xff999999.toInt())
            tab.typeface = Typeface.create(
                "sans-serif",
                if (active) Typeface.BOLD else Typeface.NORMAL
            )
            tab.setOnClickListener { selectOrderFilter(filter, tabs) }
        }
        val selectedTab = row.getChildAt(filters.indexOf(selected)) ?: return
        tabs.post {
            val visibleLeft = tabs.scrollX
            val visibleRight = visibleLeft + tabs.width
            val target = when {
                selectedTab.left < visibleLeft -> selectedTab.left
                selectedTab.right > visibleRight -> selectedTab.right - tabs.width
                else -> visibleLeft
            }
            if (target != visibleLeft) tabs.smoothScrollTo(target, 0)
        }
    }

    private fun selectOrderFilter(filter: OrderStatusFilter, tabs: HorizontalScrollView) {
        model.state.remove("filterOrderStatus")
        filter.requestStatus?.let { model.state.putInt("filterOrderStatus", it) }
        configureOrderTabs(filter)
        if (screen == ORDERS) renderOrderCards(filter)
        if (model.signedIn) loadOrders(filter)
    }

    private fun loadOrders(filter: OrderStatusFilter = OrderStatusFilter.ALL) {
        apiCall("Loading orders…", { api.orderList(1, 20, filter.requestStatus) }) { response ->
            val orders = response.optJSONArray(ApiField.ORDER_LIST)
            orderRecords = (0 until (orders?.length() ?: 0)).mapNotNull { orders?.optJSONObject(it) }
            if (filter == OrderStatusFilter.ALL) {
                model.state.putBoolean("hasOrder", orderRecords.isNotEmpty())
                orderRecords.firstOrNull()?.optString(ApiField.ORDER_NO)?.takeIf(String::isNotBlank)?.let {
                    model.state.putString("activeOrderNo", it)
                }
                if (orderRecords.isEmpty() && screen == ORDERS) model.root(ORDERS_EMPTY)
                if (orderRecords.isNotEmpty() && screen == ORDERS_EMPTY) model.root(ORDERS)
            }
            if (screen == ORDERS) renderOrderCards(filter)
            if (filter != OrderStatusFilter.ALL && orderRecords.isEmpty()) notice("No orders in this status.")
        }
    }

    private fun loadFeedbacks() {
        if (!model.signedIn) {
            go(SIGN_IN)
            return
        }
        apiCall("Loading feedback…", { api.feedbackList() }) { response ->
            val entries = response.optJSONArray(ApiField.FEEDBACK_LIST)
            val hasFeedback = entries != null && entries.length() > 0
            model.state.putBoolean("hasFeedback", hasFeedback)
            model.root(if (hasFeedback) FEEDBACK_LIST else FEEDBACK_EMPTY)
        }
    }

    private fun loadOrderDetail(orderNumber: String = model.state.getString("activeOrderNo").orEmpty()) {
        if (orderNumber.isBlank()) {
            notice("Select an order first.")
            return
        }
        apiCall("Loading order details…", { api.orderDetail(orderNumber, deviceRequestFacts()) }) { response ->
            val info = response.optJSONObject(ApiField.ORDER_INFO)
            if (info == null) {
                notice("Order details are unavailable.")
                return@apiCall
            }
            model.state.putString("activeOrderNo", orderNumber)
            model.state.putString("repaymentDate", info.optString(ApiField.REPAYMENT_DATE))
            model.state.putInt("activeOrderStatus", info.optInt(ApiField.LOAN_STATUS))
            model.root(orderDestination(info))
        }
    }

    private fun orderDestination(info: org.json.JSONObject): Screen = when (info.optInt(ApiField.LOAN_STATUS)) {
        1 -> REVIEW_PENDING
        2 -> DISBURSING
        3 -> REPAYMENT
        4 -> if (info.optInt(ApiField.OVERDUE_DAYS) > 0) REPAID_LATE else REPAID
        5 -> if (info.optInt(ApiField.PAYOUT_FAILED_FLAG) == 1) PAYOUT_FAILED else DECLINED
        6 -> OVERDUE
        else -> ORDER_DETAIL
    }

    private fun captureOrderCardSlots() {
        orderCardSlots.clear()
        val density = resources.displayMetrics.density
        val firstCardTop = (105 * density).roundToInt()
        val rowHeight = (200 * density).roundToInt()
        for (index in 0 until stage.childCount) {
            val child = stage.getChildAt(index)
            val params = child.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: continue
            if (params.topMargin < firstCardTop || params.topMargin >= (1500 * density).roundToInt()) continue
            val row = (params.topMargin - firstCardTop) / rowHeight
            if (row in 0 until 7) {
                orderCardSlots[child] = OrderCardSlot(row, params.topMargin)
            }
        }
    }

    private fun renderOrderCards(filter: OrderStatusFilter) {
        val density = resources.displayMetrics.density
        val rowHeight = (200 * density).roundToInt()
        val visibleRows = orderRecords.take(7).indices.filter { index ->
            filter.requestStatus == null || orderRecords[index].optInt(ApiField.LOAN_STATUS) == filter.requestStatus
        }
        val visiblePosition = visibleRows.withIndex().associate { it.value to it.index }

        for ((view, slot) in orderCardSlots) {
            val position = visiblePosition[slot.index]
            if (position == null) {
                view.visibility = View.GONE
                continue
            }
            view.visibility = View.VISIBLE
            val params = view.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.topMargin = slot.originalTop + (position - slot.index) * rowHeight
            view.layoutParams = params
        }

        for ((_, slot) in orderCardSlots) {
            val order = orderRecords.getOrNull(slot.index) ?: continue
            val density = resources.displayMetrics.density
            val status = orderStatusLabel(order)
            for (index in 0 until stage.childCount) {
                val child = stage.getChildAt(index) as? TextView ?: continue
                val childSlot = orderCardSlots[child] ?: continue
                if (childSlot.index != slot.index) continue
                val params = child.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: continue
                val topDp = (childSlot.originalTop / density).roundToInt()
                val leftDp = (params.leftMargin / density).roundToInt()
                val relativeTop = topDp - (105 + slot.index * 200)
                when {
                    relativeTop == 17 && leftDp < 100 -> child.text = order.optString(ApiField.PRODUCT_NAME)
                    relativeTop == 17 && leftDp >= 200 -> child.text = status
                    relativeTop == 52 && leftDp >= 90 -> child.text = order.optString(ApiField.LOAN_AMOUNT)
                    relativeTop == 121 && leftDp >= 250 -> child.text = order.optString(ApiField.ORDER_APPLY_DATE)
                    relativeTop == 149 && leftDp >= 150 -> child.text = order.optString(ApiField.ORDER_NO)
                }
            }
        }

        val contentHeight = if (filter.requestStatus == null) {
            fullOrdersHeight
        } else {
            maxOf(96f, 302f + (visibleRows.size - 1).coerceAtLeast(0) * 200f)
        }
        stage.setArtboardHeight(contentHeight)
        stage.requestLayout()
        viewport.scrollTo(0, 0)
    }

    private fun orderStatusLabel(order: org.json.JSONObject): String = when (order.optInt(ApiField.LOAN_STATUS)) {
        1 -> "Waiting For Review"
        2 -> "Disbursing"
        3 -> "Pending Repayment"
        4 -> "Repayment Completed"
        5 -> if (order.optInt(ApiField.PAYOUT_FAILED_FLAG) == 1) "Payout Failed" else "Rejected"
        6 -> "Overdue"
        else -> "Order"
    }

    private fun validate(): Boolean {
        val missing = fields.firstOrNull { it.text.isNullOrBlank() }
        if (missing == null) return true
        missing.requestFocus()
        missing.error = "Please complete this field"
        return false
    }

    internal val selectedCountry get() = model.field("country").ifEmpty { "Ghana" }
    private val countryPrefix get() = if (selectedCountry == "Tanzania") "+255" else "+233"

    private fun openCountryDialog() {
        if (fragmentManager.findFragmentByTag("country") == null) {
            CountryDialog().show(fragmentManager, "country")
        }
    }

    internal fun applyCountry(country: String) {
        model.edit("country", country)
        stage.findViewWithTag<TextView>("+233")?.text = countryPrefix
    }

    private fun signIn() {
        if (fields.size < 2) { go(SIGN_IN); return }
        if (!validate()) return
        val phone = fields.first().text.toString()
        val code = fields.last().text.toString()
        val error = signInForm.validationMessage(phone, code,
            model.state.getBoolean("agreement_SIGN_IN"))
        if (error != null) { notice(error); return }
        model.state.remove("otpRetryAt")
        apiCall("Signing in…", {
            val response = api.login(phone, code, deviceRequestFacts())
            sendServiceEventQuietly("LOGIN_APP", "SIGN_IN", authenticated = true)
            response
        }) { response ->
            AdjustAnalytics.loginResponse(this, response.optInt(ApiField.REGISTERED) == 1)
            model.login()
            viewport.post {
                apiCall("Loading account…", { fetchUserInfo() }) { }
            }
        }
    }

    private fun handle(label: String, view: View, index: Int) {
        val modalActions = when (screen) {
            LOGOUT, ORDER_SUCCESS, WITHDRAW_SUCCESS, WITHDRAW_EXIT, REVIEW_EXIT, FEEDBACK_SUCCESS,
            ATTACHMENT_DELETE, EXTENSION_CONFIRM, KYC_EXIT, RATING_FEEDBACK -> setOf("Cancel", "Confirm")
            ATTACHMENT_SOURCE -> setOf("Local photo album", "Photograph", "Cancel")
            PERMISSION_PROMPT -> setOf("Set Up", "Cancel")
            FACE_FAILED -> setOf("Reshoot", "Cancel")
            else -> emptySet()
        }
        if (modalActions.isNotEmpty() && label !in modalActions) return
        when {
            label == "Back" -> onBackPressed()
            screen == WELCOME -> startJourney()
            label.startsWith("Order ") && screen == ORDERS -> {
                val order = orderCardSlots[view]?.index?.let(orderRecords::getOrNull)
                val orderNumber = order?.optString(ApiField.ORDER_NO).orEmpty()
                if (orderNumber.isBlank()) notice("This order is no longer available.") else loadOrderDetail(orderNumber)
            }
            label.startsWith("Order ") && label.substringAfter("Order ").uppercase(Locale.US) in Screen.values().map { it.name } -> {
                if (screen == SELECT_ORDER) go(FEEDBACK_FILLED) else go(Screen.valueOf(label.substringAfter("Order ").uppercase(Locale.US)))
            }
            screen == PRIVACY && label == "Confirm Agreement" -> {
                if (!model.state.getBoolean("agreement_PRIVACY")) { notice("Please confirm your agreement"); return }
                consent.edit().putBoolean("accepted", true).apply()
                consent.edit().putBoolean("privacyEventPending", true).apply()
                startJourney()
            }
            screen == PRIVACY && label == "Reject and Exit" -> finish()
            label == "Agreement" || label.startsWith("By proceeding") -> {
                val container = view.parent as DesignStage
                val box = (0 until container.childCount).map(container::getChildAt).filterIsInstance<CheckBox>().firstOrNull()
                box?.toggle()
            }
            label == "Home" || label == "Start Application Now" -> model.root(model.home)
            label == "My Orders" -> {
                if (!model.signedIn) go(SIGN_IN)
                else { model.root(ORDERS); loadOrders() }
            }
            label == "Profile Center" -> model.root(model.profile)
            label == "Please sign in" -> go(SIGN_IN)
            label == "+233" -> openCountryDialog()
            label == "Get OTP" -> {
                AdjustAnalytics.otpButtonTapped()
                if (fields.firstOrNull()?.text?.length in 9..10) {
                    val phone = fields.firstOrNull()?.text?.toString().orEmpty()
                    apiCall("Sending verification code…", { api.requestCode(phone, deviceRequestFacts()) }) {
                        startOtpCooldown()
                        renderOtpButton()
                        notice("Verification code sent.")
                    }
                } else notice("Please enter a valid phone number")
            }
            label == "Sign In" -> {
                AdjustAnalytics.loginButtonTapped()
                signIn()
            }
            label == "Privacy Policy" -> go(PRIVACY)
            label == "About Us" -> go(ABOUT)
            label == "Sign Out" -> go(LOGOUT)
            label == "Confirm" && screen == LOGOUT -> apiCall("Signing out…", { api.logout() }) {
                feedback.clear()
                api.clearSession()
                model.signOut()
            }
            label.contains("Wallet Account") || label == "Receiving Account" -> go(if (model.signedIn) WALLET_SAVED else SIGN_IN)
            label == "Edit Now" -> go(WALLET_EDIT)
            label == "Feedback" || label.contains("feedback list") -> loadFeedbacks()
            label == "Write feedback" || label.startsWith("Please submit feedback") -> go(FEEDBACK_FORM)
            label.startsWith("Please describe your problem") -> go(FEEDBACK_PENDING)
            label == "Choose PDF" -> attachmentOptions(false)
            label == "Choose image" -> attachmentOptions(true)
            label == "Local photo album" -> pickFile("image/*")
            label == "Photograph" || label.endsWith(" photo") -> takePhoto()
            label == "Capture selfie" || label == "Upload a clear selfie" -> go(FACE_GUIDE)
            label == "Capture document" -> { pendingDocumentScreen = screen; go(DOCUMENT_GUIDE) }
            screen == DOCUMENT_PREVIEW -> {
                val destination = pendingDocumentScreen ?: IDENTITY_CARD
                pendingDocumentScreen = null
                go(destination)
            }
            label == "Reshoot" -> { clearSelfieCapture(); go(FACE_GUIDE) }
            label == "Submit application" -> submitApplication()
            label == "Go Apply" || label == "Begin Application" || label == "Apply Now" -> applyForCredit()
            label == "Withdraw Now" -> if (screen == HOME_ACTIVE) go(WITHDRAW) else go(WITHDRAW_SUCCESS)
            label == "Set Up" && screen == PERMISSION_PROMPT -> go(WALLET_SETUP)
            label == "Choose" && view is TextView -> form.choose(view, index, stage)
            screen in setOf(WITHDRAW, WITHDRAW_ADJUSTED) && label.matches(Regex("[0-9]+ Days")) && view is TextView ->
                form.select(view, index, "Loan term", listOf("7 Days", "20 Days", "365 Days"))
            label == "Next" -> {
                AdjustAnalytics.identitySubmitTapped(screen)
                if (validate()) go(identity.next(screen))
            }
            label == "Submit" -> {
                AdjustAnalytics.identitySubmitTapped(screen)
                submit()
            }
            label == "Cancel" -> if (screen == WITHDRAW_EXIT || screen == REVIEW_EXIT) model.root(model.home) else leave()
            label == "Confirm" -> confirm()
            label == "Repay Extension" -> {
                val orderNumber = model.state.getString("activeOrderNo").orEmpty()
                if (orderNumber.isBlank()) notice("Select an order first.")
                else apiCall("Loading extension details…", { api.extensionInfo(orderNumber) }) { details ->
                    model.state.putString("extensionRepaymentDate", details.optString(ApiField.EXTENSION_REPAY_DATE))
                    go(if (screen == EXTENSION) EXTENSION_CONFIRM else EXTENSION)
                }
            }
            label == "Repay Now" -> requestRepayment(extend = false)
            label == "View Order" -> loadOrderDetail()
            else -> credit.orderDestination(label)?.let(::go)
        }
    }

    private fun submit() {
        if (!validate()) return
        when (screen) {
            FACE_PREVIEW -> submitLiveness()
            IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD -> submitAuthentication()
            IDENTITY_FILLED -> go(ORDER_DETAIL)
            WALLET_EDIT -> saveWallet()
            FEEDBACK_FORM, FEEDBACK_FILLED, FEEDBACK_ATTACHMENTS -> submitFeedbackRemote()
            RATING, RATING_CHANGED -> go(RATING_FEEDBACK)
            else -> leave()
        }
    }

    private fun submitLiveness() {
        val selfie = selfieUri
        if (selfie == null) {
            notice("Please take a selfie before submitting.")
            return
        }
        apiCall("Uploading and verifying selfie…", {
            val imagePath = S3ImageUploader(this, api).uploadUri(selfie, true)
            api.saveLiveness(imagePath)
            val info = fetchUserInfo()
            val accepted = info.optInt(ApiField.USER_LIVENESS) == 1
            val continuation = if (accepted) continueAfterIdentity(info) else AuthContinuation(info, null)
            Triple(imagePath, accepted, continuation)
        }) { (imagePath, accepted, continuation) ->
            if (accepted) {
                model.state.putString("livenessImage", imagePath)
                clearSelfieCapture()
                if (continuation.order != null) {
                    AdjustAnalytics.loanApplicationResponse(this, continuation.order.optInt(ApiField.FIRST_APPLICATION) == 1)
                    model.state.putBoolean("hasOrder", true)
                    model.root(model.home)
                } else {
                    val productId = continuationProductId(continuation.info)
                    if (productId.isBlank()) model.root(model.home)
                    else {
                        model.state.putString("selectedProductId", productId)
                        model.root(WITHDRAW)
                    }
                }
            } else {
                clearSelfieCapture()
                notice("The selfie could not be verified. Please try again.")
                go(FACE_FAILED)
            }
        }
    }

    private fun saveWallet() {
        val account = model.field("WALLET_EDIT_10")
        val walletType = model.field("WALLET_EDIT_9")
        if (account.isBlank() || walletType.isBlank()) {
            notice("Choose a wallet and enter the account number.")
            return
        }
        apiCall("Saving receiving account…", { api.updateWallet(account, walletType) }) {
            go(WALLET_SAVED)
        }
    }

    private fun submitFeedbackRemote() {
        val feedbackText = model.field("FEEDBACK_FORM_19").ifBlank { fields.firstOrNull()?.text?.toString().orEmpty() }
        val feedbackType = model.field("FEEDBACK_FORM_18")
        if (feedbackType.isBlank() || feedbackText.isBlank()) {
            notice("Choose a feedback type and describe the issue.")
            return
        }
        apiCall("Submitting feedback…", {
            val orderNumber = model.state.getString("activeOrderNo").orEmpty().ifBlank {
                api.orderList(1, 20).optJSONArray(ApiField.ORDER_LIST)
                    ?.optJSONObject(0)?.optString(ApiField.ORDER_NO).orEmpty()
            }
            if (orderNumber.isBlank()) throw ApiException("Choose an order before submitting feedback.")
            suspend fun prepare(values: List<String>, isImage: Boolean): org.json.JSONArray {
                val result = org.json.JSONArray()
                for (value in values) {
                    val uri = Uri.parse(value)
                    val path = if (uri.scheme == "content" || uri.scheme == "file") {
                        S3ImageUploader(this, api).uploadUri(uri, isImage)
                    } else value
                    result.put(path)
                }
                return result
            }
            val images = prepare(feedback.attachments(images = true), true)
            val files = prepare(feedback.attachments(images = false), false)
            api.saveFeedback(orderNumber, feedbackType, feedbackText, images.toString(), files.toString())
        }) {
            model.submitFeedback()
        }
    }

    private fun submitAuthentication() {
        val cardScreen = screen
        val imagePath = model.state.getString("documentImage_${cardScreen.name}").orEmpty()
        if (imagePath.isBlank()) {
            notice("Upload a clear photo of the front of your document.")
            return
        }
        val idIndex = if (cardScreen == IDENTITY_CARD) 18 else 20
        val nameIndex = idIndex + 1
        val dateIndex = if (cardScreen == IDENTITY_CARD) 26 else 28
        val genderIndex = dateIndex + 1
        val idKey = when (cardScreen) {
            DRIVER_LICENSE -> ApiField.LICENSE_NUMBER
            VOTER_CARD -> ApiField.VOTER_NUMBER
            else -> ApiField.ID_NUMBER
        }
        val imageKey = when (cardScreen) {
            DRIVER_LICENSE -> ApiField.FRONT_LICENSE
            VOTER_CARD -> ApiField.FRONT_VOTER_CARD
            else -> ApiField.FRONT_IMAGE
        }
        val contacts = org.json.JSONArray()
        for ((phoneIndex, nameIndexInPage, relationIndex) in listOf(
            Triple(31, 33, 29), Triple(32, 34, 30)
        )) {
            val phone = model.field("CONTACTS_$phoneIndex")
            val name = model.field("CONTACTS_$nameIndexInPage")
            val relation = model.field("CONTACTS_$relationIndex")
            if (phone.isNotBlank() || name.isNotBlank()) {
                contacts.put(org.json.JSONObject().apply {
                    put(ApiField.CONTACT_PHONE, phone)
                    put(ApiField.CONTACT_NAME, name)
                    put(ApiField.CONTACT_RELATION, relation)
                })
            }
        }
        val parameters = buildMap {
            put(ApiField.AUTH_STEP, "4")
            put(ApiField.FRONT_IMAGE, imagePath)
            if (imageKey != ApiField.FRONT_IMAGE) put(imageKey, imagePath)
            put(ApiField.CARD_TYPE, when (cardScreen) {
                DRIVER_LICENSE -> "Driving License"
                VOTER_CARD -> "Voter Card"
                else -> "ID Card"
            })
            put(idKey, model.field("${cardScreen.name}_$idIndex"))
            put(ApiField.FULL_NAME, model.field("${cardScreen.name}_$nameIndex"))
            put(ApiField.BIRTH_DATE, model.field("${cardScreen.name}_$dateIndex"))
            put(ApiField.GENDER, model.field("${cardScreen.name}_$genderIndex"))
            put(ApiField.WALLET_ACCOUNT, model.field("WALLET_SETUP_11"))
            put(ApiField.WALLET_TYPE, model.field("WALLET_SETUP_10"))
            put(ApiField.CONTACTS, contacts.toString())
            put(ApiField.EDUCATION, model.field("PERSONAL_INFO_35"))
            put(ApiField.MONTHLY_INCOME, model.field("PERSONAL_INFO_36"))
            put(ApiField.MARITAL_STATUS, model.field("PERSONAL_INFO_37"))
            put(ApiField.JOB_TITLE, model.field("PERSONAL_INFO_38"))
            put(ApiField.INDUSTRY, model.field("PERSONAL_INFO_39"))
            put(ApiField.PAY_CYCLE, model.field("PERSONAL_INFO_40"))
            put(ApiField.EMAIL, model.field("PERSONAL_INFO_41"))
            put(ApiField.ADDRESS, model.field("PERSONAL_INFO_42"))
            put(ApiField.MESSAGING_ACCOUNT, model.field("PERSONAL_INFO_43"))
        }.filterValues(String::isNotBlank)
        apiCall("Submitting identity information…", {
            api.saveAuthentication(parameters)
            continueAfterIdentity(fetchUserInfo())
        }) { continuation ->
            val info = continuation.info
            if (info.optInt(ApiField.USER_STATUS) != 2) {
                notice("Your identity information was saved. Complete the remaining steps to continue.")
                model.root(model.home)
            } else if (info.optInt(ApiField.USER_LIVENESS) != 1) {
                model.state.putBoolean("identityVerified", true)
                continuationProductId(info)?.takeIf(String::isNotBlank)?.let {
                    model.state.putString("selectedProductId", it)
                }
                go(FACE_GUIDE)
            } else if (continuation.order != null) {
                AdjustAnalytics.loanApplicationResponse(this, continuation.order.optInt(ApiField.FIRST_APPLICATION) == 1)
                model.state.putBoolean("hasOrder", true)
                model.root(model.home)
            } else {
                model.state.putBoolean("identityVerified", true)
                val productId = continuationProductId(info)
                if (productId.isBlank()) model.root(model.home)
                else {
                    model.state.putString("selectedProductId", productId)
                    go(WITHDRAW)
                }
            }
        }
    }

    private suspend fun continueAfterIdentity(info: org.json.JSONObject): AuthContinuation {
        if (info.optInt(ApiField.USER_STATUS) != 2 || info.optInt(ApiField.USER_LIVENESS) != 1) {
            return AuthContinuation(info, null)
        }
        val recommended = fetchUserInfo(recommended = true)
        val product = recommended.optJSONObject(ApiField.LOAN_PRODUCT)
        val productId = product?.optString(ApiField.PRODUCT_ID).orEmpty()
        if (productId.isBlank()) return AuthContinuation(recommended, null)
        model.state.putString("selectedProductId", productId)
        val hasProductDetails = product.keys().asSequence()
            .filter { it != ApiField.PRODUCT_ID }
            .any { key -> product.optString(key).isNotBlank() }
        val order = if (hasProductDetails) null else createOrderAndReadNumber(productId)
        return AuthContinuation(recommended, order)
    }

    private fun continuationProductId(info: org.json.JSONObject): String =
        info.optJSONObject(ApiField.LOAN_PRODUCT)?.optString(ApiField.PRODUCT_ID).orEmpty()

    private fun confirm() {
        when (screen) {
            ORDER_SUCCESS -> model.root(REVIEW_COUNTDOWN)
            FEEDBACK_SUCCESS -> loadFeedbacks()
            WITHDRAW_SUCCESS -> go(WALLET_SETUP)
            EXTENSION_CONFIRM -> requestRepayment(extend = true)
            KYC_EXIT -> model.root(model.home)
            else -> leave()
        }
    }

    private fun requestRepayment(extend: Boolean) {
        val orderNumber = model.state.getString("activeOrderNo").orEmpty()
        val date = model.state.getString(if (extend) "extensionRepaymentDate" else "repaymentDate").orEmpty()
        if (orderNumber.isBlank() || date.isBlank()) {
            notice("Refresh the order details before opening the repayment page.")
            return
        }
        apiCall("Opening repayment page…", {
            api.repaymentPath(orderNumber, if (extend) "extend" else "all", date)
                .optString(ApiField.REPAYMENT_PATH)
        }) { path ->
            val uri = runCatching { Uri.parse(path) }.getOrNull()
            if (uri == null || uri.scheme !in setOf("http", "https")) {
                notice("The repayment link is unavailable.")
                return@apiCall
            }
            try {
                pendingRepaymentRefresh = true
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: android.content.ActivityNotFoundException) {
                pendingRepaymentRefresh = false
                notice("No browser is available to open the repayment page.")
            }
        }
    }

    private fun attachmentOptions(images: Boolean) {
        val items = feedback.attachments(images)
        fun add() { if (images) go(ATTACHMENT_SOURCE) else pickFile("application/pdf") }
        if (items.isEmpty()) { add(); return }
        val labels = items.indices.map { "Remove attachment ${it + 1}" }.toMutableList()
        if (items.size < 9) labels += "Add attachment"
        AlertDialog.Builder(this).setItems(labels.toTypedArray()) { _, index ->
            if (index == items.size) add()
            else AlertDialog.Builder(this).setMessage("Remove this attachment?")
                .setNegativeButton("Cancel", null).setPositiveButton("Confirm") { _, _ ->
                    feedback.remove(items[index], images)
                }.show()
        }.show()
    }

    private fun pickFile(type: String) {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(picker, 31)
    }

    private fun takePhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        val filename = "cedi_${System.currentTimeMillis()}.jpg"
        val photoDirectory = java.io.File(cacheDir, "camera-photos")
        if (!photoDirectory.exists() && !photoDirectory.mkdirs()) {
            notice("Unable to prepare a photo. Please try again.")
            return
        }
        val photoFile = java.io.File(photoDirectory, filename)
        if (!runCatching { photoFile.createNewFile() }.getOrDefault(false)) {
            notice("Unable to prepare a photo. Please try again.")
            return
        }
        val uri = Uri.Builder().scheme("content").authority("$packageName.camera-files")
            .appendPath("camera").appendPath(filename).build()
        localPhotoUris += uri
        cameraOutputUri = uri
        try {
            val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                clipData = ClipData.newUri(contentResolver, "Cedi Plus photo", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            startActivityForResult(capture, 32)
        } catch (_: android.content.ActivityNotFoundException) {
            cameraOutputUri = null
            deleteLocalPhoto(uri)
            notice("No camera is available")
        }
    }

    @Deprecated("Platform callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            cameraOutputUri?.let(::deleteLocalPhoto)
            cameraOutputUri = null
            return
        }
        if (requestCode == 31 && data?.data != null) {
            val file = data.data!!
            val isImage = contentResolver.getType(file)?.startsWith("image/") == true
            apiCall("Uploading attachment…", { S3ImageUploader(this, api).uploadUri(file, isImage) }) { path ->
                val added = feedback.attach(path, isImage)
                if (screen == ATTACHMENT_SOURCE) leave()
                notice(if (added) "File uploaded" else "Up to 9 attachments are allowed")
            }
        } else if (requestCode == 32) {
            val imageUri = cameraOutputUri ?: data?.data
            cameraOutputUri = null
            if (imageUri == null) {
                notice("The camera did not return a photo. Please try again.")
                return
            }
            when (screen) {
                DOCUMENT_GUIDE -> {
                    val owner = pendingDocumentScreen ?: IDENTITY_CARD
                    apiCall("Uploading document photo…", { S3ImageUploader(this, api).uploadUri(imageUri, true) }) { path ->
                        deleteLocalPhoto(imageUri)
                        model.state.putString("documentImage_${owner.name}", path)
                        go(DOCUMENT_PREVIEW)
                    }
                }
                ATTACHMENT_SOURCE -> {
                    apiCall("Uploading photo…", { S3ImageUploader(this, api).uploadUri(imageUri, true) }) { path ->
                        deleteLocalPhoto(imageUri)
                        val added = feedback.attach(path, true)
                        leave()
                        notice(if (added) "Photo uploaded" else "Up to 9 attachments are allowed")
                    }
                }
                else -> {
                    clearSelfieCapture()
                    val capturedPreview = decodePreview(imageUri)
                    if (capturedPreview == null) {
                        deleteLocalPhoto(imageUri)
                        notice("Unable to read the selfie. Please try again.")
                    } else {
                        preview = capturedPreview
                        selfieUri = imageUri
                        go(FACE_PREVIEW)
                    }
                }
            }
        }
    }

    private fun decodePreview(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1600) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun clearSelfieCapture() {
        preview?.takeUnless(Bitmap::isRecycled)?.recycle()
        preview = null
        selfieUri?.let(::deleteLocalPhoto)
        selfieUri = null
    }

    private fun deleteLocalPhoto(uri: Uri) {
        if (localPhotoUris.remove(uri)) runCatching { contentResolver.delete(uri, null, null) }
    }
}
