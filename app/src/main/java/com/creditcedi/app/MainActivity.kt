package com.creditcedi.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.Manifest
import com.creditcedi.account.FeedbackViewModel
import com.creditcedi.identity.IdentityViewModel
import com.creditcedi.identity.R as IdentityR
import com.creditcedi.credit.CreditViewModel
import com.creditcedi.credit.OrderStatusFilter
import com.creditcedi.credit.R as CreditR
import com.creditcedi.access.SignInViewModel
import android.content.Intent
import android.content.ClipData
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.content.pm.ActivityInfo
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.provider.MediaStore
import android.os.SystemClock
import android.graphics.Typeface
import com.creditcedi.access.R as AccessR
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.creditcedi.access.AccessLayouts
import com.creditcedi.account.AccountLayouts
import com.creditcedi.credit.CreditLayouts
import com.creditcedi.identity.IdentityLayouts
import androidx.constraintlayout.widget.ConstraintLayout
import com.creditcedi.core.R as CoreR
import com.creditcedi.core.JourneyViewModel
import com.creditcedi.core.Screen
import com.creditcedi.core.Screen.*
import java.util.Locale
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private val homeScreens = setOf(HOME, HOME_ACTIVE, HOME_PRODUCTS, HOME_APPLIED)
    private val personalInfoChoiceOrder = listOf(35, 36, 11, 37, 38, 39, 40)
    private val personalInfoChoiceViewIds = mapOf(
        35 to IdentityR.id.personal_info_education_value,
        36 to IdentityR.id.personal_info_monthly_remuneration_value,
        11 to IdentityR.id.personal_info_wage_disbursement_cycle_value,
        37 to IdentityR.id.personal_info_marriage_situation_value,
        38 to IdentityR.id.personal_info_work_title_value,
        39 to IdentityR.id.personal_info_industry_value,
        40 to IdentityR.id.personal_info_payroll_cycle_value
    )
    private lateinit var model: JourneyViewModel
    private lateinit var viewport: ScrollView
    private lateinit var statusBarBackground: StatusBarBackground
    private lateinit var stage: ConstraintLayout
    private lateinit var pageConstraints: ScreenConstraints
    private var pageScrim: View? = null
    private var defaultNavigationBarColor = Color.WHITE
    private var stickyAppBarViews = emptyList<View>()
    private var stickyAppBarBackground: View? = null
    private var footerConstraints: ScreenConstraints? = null
    private data class CachedTabPage(
        val screen: Screen,
        val stage: ConstraintLayout,
        val pageConstraints: ScreenConstraints,
        val footerConstraints: ScreenConstraints?,
        val pageScrim: View?,
        val stickyAppBarViews: List<View>,
        val stickyAppBarBackground: View?,
        var scrollY: Int
    )
    private val cachedTabPages = mutableMapOf<Screen, CachedTabPage>()
    private var renderedScreen: Screen? = null
    private lateinit var form: FormBinder
    private val pages = AccessLayouts.screens + AccountLayouts.screens + CreditLayouts.screens + IdentityLayouts.screens
    private val consent by lazy { getSharedPreferences("preferences", MODE_PRIVATE) }
    private val credit = CreditViewModel()
    private val signInForm = SignInViewModel()
    private val identity = IdentityViewModel()
    private val feedback by lazy { FeedbackViewModel(model.state) }
    private val fields = mutableListOf<EditText>()
    private val contactFieldIndices = listOf(
        Triple(31, 33, 29),
        Triple(32, 34, 30),
        Triple(50, 51, 48),
        Triple(63, 64, 61)
    )
    private data class OrderCardSlot(val index: Int, val originalTop: Float)
    private data class AuthContinuation(val info: org.json.JSONObject, val order: org.json.JSONObject?)
    private val orderCardSlots = IdentityHashMap<View, OrderCardSlot>()
    private val orderSkeletonViews = mutableListOf<View>()
    private var fullOrdersHeight = 0f
    private var orderRecords: List<org.json.JSONObject> = emptyList()
    private var ordersLoading = false
    private var ordersInitialLoadComplete = false
    private var ratingPromptShown = false
    private var payFailPromptShown = false
    private val screen get() = model.current
    private var preview: Bitmap? = null
    private var documentCapturePreview: Bitmap? = null
    private val api by lazy { CediApi(this) }
    private var selfieUri: Uri? = null
    private var cameraOutputUri: Uri? = null
    private val localPhotoUris = mutableSetOf<Uri>()
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val serviceEventMutex = Mutex()
    private enum class PermissionPurpose { INITIAL, APPLY }
    private var permissionPurpose: PermissionPurpose? = null
    private var permissionQueue = emptyList<String>()
    private var permissionIndex = 0
    private var afterPermissions: (() -> Unit)? = null
    private var settingsReturnPurpose: PermissionPurpose? = null
    private var loadingDialog: Dialog? = null
    private var pendingDocumentScreen: Screen? = null
    private var pendingRepaymentRefresh = false
    private var homeProducts = org.json.JSONArray()
    private var homeInfoSignedIn = false
    private var homeDataLoading = false
    private var homeDataResolved = false
    private var forceHomePageRebuild = false
    private var layoutPreview = false
    private var renderedPageOrientation = Configuration.ORIENTATION_UNDEFINED
    private var pendingOrientationPage: Screen? = null
    private var pendingOrientationRenderPosted = false
    private val authFieldEditCounts = mutableMapOf<String, Int>()
    private var activeAuthEvent: AuthEventStage? = null
    private var activeAuthEventStartedAt = 0L

    private data class AuthEventStage(val step: String, val page: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = JourneyViewModel(savedInstanceState?.getBundle("journey") ?: Bundle())
        model.state.putBoolean("signedIn", api.hasSession())
        setContentView(R.layout.activity_main)
        viewport = findViewById(R.id.viewport)
        defaultNavigationBarColor = window.navigationBarColor
        viewport.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val offset = scrollY.toFloat()
            stickyAppBarBackground?.translationY = offset
            stickyAppBarViews.forEach { it.translationY = offset }
            updateFullScreenScrim()
        }
        statusBarBackground = findViewById(R.id.status_bar_background)
        viewport.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (pendingOrientationPage == null) updatePageWidth()
            renderPendingOrientationPage()
        }
        window.statusBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isStatusBarContrastEnforced = false
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        findViewById<View>(R.id.window_content).setOnApplyWindowInsetsListener { view, insets ->
            val statusHeight = if (android.os.Build.VERSION.SDK_INT >= 35) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, 0, bars.right, bars.bottom)
                bars.top
            } else {
                insets.systemWindowInsetTop
            }
            val referenceWidth = (view.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels).toFloat()
            val designStatusHeight = (44f * referenceWidth / 375f).roundToInt()
            val contentTop = maxOf(statusHeight, designStatusHeight)
            val params = statusBarBackground.layoutParams
            if (params.height != contentTop) {
                params.height = contentTop
                statusBarBackground.layoutParams = params
            }
            insets
        }
        form = FormBinder(this, model) { page, index, _, _ -> trackAuthFieldChange(page, index) }
        form.setChoiceHandlers(
            options = ::authChoiceOptions,
            onChosen = ::onAuthChoiceSelected,
            onMissing = { notice("Options are unavailable. Please try again later.") }
        )
        model.render = ::show
        val requested = intent.getStringExtra("screen")
        val inspect = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        layoutPreview = inspect && intent.getBooleanExtra("layout_preview", false)
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
        } else if (route != null) model.root(route)
        else if (!layoutPreview && screen in homeScreens) model.root(HOME)
        else show(screen)
        if (screen == ORDERS && model.signedIn && !layoutPreview) loadOrders()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("journey", model.state)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewport.post { renderPendingOrientationPage() }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus as? EditText
            if (focused != null) {
                val bounds = android.graphics.Rect()
                focused.getGlobalVisibleRect(bounds)
                if (!bounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(focused.windowToken, 0)
                    focused.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        activityScope.cancel()
        loadingDialog?.dismiss()
        loadingDialog = null
        clearSelfieCapture()
        clearDocumentCapturePreview()
        cameraOutputUri?.let(::deleteLocalPhoto)
        cameraOutputUri = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (layoutPreview) return
        settingsReturnPurpose?.let { purpose ->
            settingsReturnPurpose = null
            val phoneGranted = permissionGranted(Manifest.permission.READ_PHONE_STATE)
            val applyGranted = applyPermissionList.all(::permissionGranted)
            when (purpose) {
                PermissionPurpose.INITIAL -> if (phoneGranted) completeInitialPermissionSetup() else {
                    afterPermissions = null
                    notice("Phone permission is required to continue. Allow it in Settings.")
                }
                PermissionPurpose.APPLY -> if (applyGranted) {
                    val continuation = afterPermissions
                    afterPermissions = null
                    continuation?.invoke()
                } else {
                    afterPermissions = null
                    notice("Please allow the required permissions in Settings to continue.")
                }
            }
            return
        }
        if (::model.isInitialized && pendingRepaymentRefresh) {
            pendingRepaymentRefresh = false
            loadOrderDetail()
            return
        }
        if (permissionPurpose != null) return
        if (!::model.isInitialized || screen !in setOf(HOME, HOME_ACTIVE, HOME_PRODUCTS, HOME_APPLIED)) return
        if (model.signedIn) refreshSignedInHome()
        else if (!homeDataResolved) loadHomeData()
    }

    private fun <T> apiCall(
        message: String,
        request: suspend () -> T,
        onFailure: ((Exception) -> Unit)? = null,
        showLoadingDialog: Boolean = true,
        onSuccess: (T) -> Unit
    ) {
        activityScope.launch {
            if (showLoadingDialog) showLoading(message)
            try {
                onSuccess(request())
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                onFailure?.invoke(failure)
                if (failure is ExpiredSessionException) {
                    model.state.putBoolean("signedIn", false)
                    model.root(SIGN_IN)
                    notice("Login expired. Please sign in again.")
                } else {
                    notice(failure.message ?: "Unable to connect. Please try again.")
                }
            } finally {
                if (showLoadingDialog) hideLoading()
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
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.READ_SMS,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else applyPermissionList
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
        when {
            purpose == PermissionPurpose.INITIAL && !permissionGranted(Manifest.permission.READ_PHONE_STATE) -> showPhonePermissionPrompt()
            purpose == PermissionPurpose.INITIAL -> completeInitialPermissionSetup()
            purpose == PermissionPurpose.APPLY && !applyPermissionList.all(::permissionGranted) -> openSettingsForPermissions(PermissionPurpose.APPLY)
            purpose == PermissionPurpose.APPLY -> {
                val continuation = afterPermissions
                afterPermissions = null
                continuation?.invoke()
            }
        }
    }

    private fun showPhonePermissionPrompt() {
        AlertDialog.Builder(this)
            .setMessage("Phone permission is required to continue. Allow it in Settings, then reopen the app.")
            .setNegativeButton("Cancel") { _, _ -> afterPermissions = null }
            .setPositiveButton("Open Settings") { _, _ ->
                openSettingsForPermissions(PermissionPurpose.INITIAL)
            }
            .create()
            .showWithPopupButtonStyle()
    }

    private val applyPermissionList = listOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.READ_SMS,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private fun permissionGranted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun completeInitialPermissionSetup() {
        afterPermissions = null
        consent.edit().putBoolean("initialPermissionsHandled", true).apply()
        model.root(HOME)
        loadHomeData()
    }

    private fun skipOptionalInitialPermissions() {
        permissionPurpose = null
        permissionQueue = emptyList()
        permissionIndex = 0
        if (permissionGranted(Manifest.permission.READ_PHONE_STATE)) {
            completeInitialPermissionSetup()
        } else {
            afterPermissions = { completeInitialPermissionSetup() }
            showPhonePermissionPrompt()
        }
    }

    private fun openSettingsForPermissions(purpose: PermissionPurpose) {
        settingsReturnPurpose = purpose
        try {
            startActivity(Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ))
        } catch (_: android.content.ActivityNotFoundException) {
            settingsReturnPurpose = null
            afterPermissions = null
            notice("Unable to open app settings. Please change the permissions manually.")
        }
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
        if (homeDataLoading) return
        homeDataLoading = true
        val signedIn = model.signedIn
        // Start the state-specific home request before first-open and OPEN_APP requests.
        apiCall("Loading products…", {
            fetchHomeData(signedIn)
        }, onFailure = { finishHomeDataLoadAfterFailure() }, showLoadingDialog = false) { (products, userInfo, _) ->
            applyHomeData(products, signedIn, userInfo)
        }
        requestFirstOpenForHomeResume()
        activityScope.launch {
            serviceEventMutex.withLock { sendServiceEventQuietly("OPEN_APP", emptyMap(), signedIn) }
        }
    }

    private suspend fun fetchHomeData(signedIn: Boolean): Triple<org.json.JSONArray, org.json.JSONObject?, Boolean> {
        // Home uses one data endpoint per session state: guests load products, signed-in users
        // load their account data (which includes the product list).
        if (signedIn) {
            val info = fetchUserInfo()
            val products = info.optJSONArray(ApiField.PRODUCT_LIST) ?: org.json.JSONArray()
            return Triple(products, info, true)
        }

        val response = api.products()
        val products = response.optJSONArray(ApiField.PRODUCT_LIST) ?: org.json.JSONArray()
        return Triple(products, null, false)
    }

    private fun applyHomeData(
        products: org.json.JSONArray,
        signedIn: Boolean,
        userInfo: org.json.JSONObject? = null
    ) {
        homeDataLoading = false
        homeDataResolved = true
        homeProducts = products
        homeInfoSignedIn = signedIn
        val hasOrders = signedIn && (0 until products.length()).any { index ->
            val status = products.optJSONObject(index)?.optJSONObject(ApiField.ORDER_INFO)
                ?.optInt(ApiField.LOAN_STATUS, 0) ?: 0
            status in 1..6
        }
        model.state.putBoolean("hasOrder", hasOrders)
        products.optJSONObject(0)?.optString(ApiField.PRODUCT_ID)?.takeIf(String::isNotBlank)?.let {
            model.state.putString("selectedProductId", it)
        }
        userInfo?.let(::maybeShowServerPrompts)
        invalidateCachedHomePages()

        val destination = when {
            hasOrders -> HOME_ACTIVE
            signedIn && products.length() <= 1 -> HOME_APPLIED
            products.length() > 1 -> HOME_PRODUCTS
            else -> HOME
        }
        model.rememberHomeDestination(destination)
        if (screen in homeScreens) {
            forceHomePageRebuild = true
            try {
                model.root(destination)
            } finally {
                forceHomePageRebuild = false
            }
        }
    }

    private fun finishHomeDataLoadAfterFailure() {
        homeDataLoading = false
        if (homeDataResolved) return
        homeDataResolved = true
        if (::stage.isInitialized) setHomeSkeletonVisibility(false)
    }

    private fun bindHomePage(page: Screen) {
        if (page !in setOf(HOME, HOME_ACTIVE, HOME_PRODUCTS, HOME_APPLIED)) return
        when (page) {
            HOME, HOME_APPLIED -> {
                val product = homeProducts.optJSONObject(0)
                val showLoadingSkeleton = page == HOME && !homeDataResolved
                setHomeBandVisibility(176f, 458f, product != null || showLoadingSkeleton)
                bindProductCard(176f, product, showStatus = homeInfoSignedIn)
            }
            HOME_PRODUCTS -> {
                val starts = listOf(176f, 478f, 780f)
                starts.forEachIndexed { index, top ->
                    val product = homeProducts.optJSONObject(index)
                    setHomeBandVisibility(top, top + 282f, product != null)
                    if (product != null) bindProductCard(top, product, showStatus = homeInfoSignedIn)
                }
                if (homeProducts.length() == 2) compactHomeFooter(760f)
            }
            HOME_ACTIVE -> {
                val first = homeProducts.optJSONObject(0)
                bindProductCard(176f, first, showStatus = true)
                bindActiveOrderCards()
            }
            else -> Unit
        }
    }

    private fun setHomeSkeletonVisibility(visible: Boolean) {
        for (index in 0 until stage.childCount) {
            val child = stage.getChildAt(index)
            if (child.tag == HOME_SKELETON_TAG) {
                child.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }
    }

    private fun bindProductCard(top: Float, product: org.json.JSONObject?, showStatus: Boolean) {
        if (product == null) return
        val bottom = top + 282f
        val textViews = (0 until stage.childCount).map(stage::getChildAt)
            .filterIsInstance<TextView>()
            .filter { dpTop(it) >= top && dpTop(it) < bottom }
        val name = product.optString(ApiField.PRODUCT_NAME).ifBlank {
            product.optJSONObject(ApiField.ORDER_INFO)?.optString(ApiField.PRODUCT_NAME).orEmpty()
        }
        val amount = product.optString(ApiField.PRODUCT_AMOUNT)
        val amountText = formatProductAmount(amount)
        val days = formatProductDays(product.optString(ApiField.PRODUCT_DAYS))
        val rate = product.optString(ApiField.PRODUCT_RATE)
        val apr = product.optString(ApiField.PRODUCT_APR)

        textViews.forEach { view ->
            when (view.tag as? String) {
                getString(CoreR.string.text_name) -> if (name.isNotBlank()) {
                    view.text = name
                    view.maxLines = 1
                    view.ellipsize = android.text.TextUtils.TruncateAt.END
                    view.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    pageConstraints.setWidth(view, 126f)
                }
                getString(CoreR.string.text_ghs_100_000_ghs_3_000_000),
                getString(CoreR.string.text_ghs_100_000) -> if (amountText.isNotBlank()) {
                    view.text = amountText
                    view.gravity = Gravity.CENTER
                    view.maxLines = 1
                    view.ellipsize = android.text.TextUtils.TruncateAt.END
                }
                getString(CoreR.string.text_amount_up_to_1_000_000_ghs) -> {
                    if (amountText.isNotBlank()) {
                        val amountRange = amountText.removePrefix("GHS ")
                        val currencySuffix = if (amountRange.contains(Regex("(?i)\\bGHS\\b"))) "" else " GHS"
                        view.text = "Amount up to $amountRange$currencySuffix"
                    }
                }
                getString(CoreR.string.text_91_360_days) -> if (days.isNotBlank()) view.text = days
                getString(CoreR.string.text_2_charge) -> if (rate.isNotBlank()) view.text = rate
                getString(CoreR.string.text_22) -> if (apr.isNotBlank()) view.text = apr else view.visibility = View.GONE
                getString(CoreR.string.text_highest_apr) -> if (apr.isBlank()) view.visibility = View.GONE
            }
        }
        val logoTag = "home-product-logo-${(top + 22f).toInt()}"
        val existingLogo = (0 until stage.childCount).map(stage::getChildAt)
            .firstOrNull { it.tag == logoTag }
        val logo = product.optString(ApiField.PRODUCT_LOGO)
        if (logo.isNotBlank()) addProductLogo(top + 22f, logo)
        else existingLogo?.visibility = View.GONE
        if (showStatus) {
            val badgeTop = top + 14f
            val badgeTag = "home-status-badge-${badgeTop.toInt()}"
            val existingBadge = (0 until stage.childCount).map(stage::getChildAt)
                .firstOrNull { it.tag == badgeTag }
            val order = product.optJSONObject(ApiField.ORDER_INFO)
            val label = order?.let(::loanStatusLabel)
            if (order != null && label != null) {
                addStatusBadge(badgeTop, label, order.optInt(ApiField.LOAN_STATUS))
            } else existingBadge?.visibility = View.GONE
        }
    }

    private fun bindActiveOrderCards() {
        val slots = listOf(478f, 780f, 966f, 1188f, 1410f, 1596f)
        val ends = listOf(780f, 966f, 1188f, 1410f, 1596f, 1815f)
        var contentBottom = 458f
        slots.forEachIndexed { index, top ->
            val product = homeProducts.optJSONObject(index)
            if (product == null) {
                setHomeBandVisibility(top, ends[index], false)
                return@forEachIndexed
            }
            val order = product.optJSONObject(ApiField.ORDER_INFO)
            if (order == null || order.optInt(ApiField.LOAN_STATUS, 0) !in 1..6) {
                setHomeBandVisibility(top, ends[index], false)
                return@forEachIndexed
            }
            setHomeBandVisibility(top, ends[index], true)
            contentBottom = ends[index]
            val status = order.optInt(ApiField.LOAN_STATUS)

            val statusLabel = loanStatusLabel(order).orEmpty()
            val name = order.optString(ApiField.PRODUCT_NAME).ifBlank {
                product.optString(ApiField.PRODUCT_NAME)
            }
            val orderNumber = order.optString(ApiField.ORDER_NO)
            val applyDate = order.optString(ApiField.ORDER_APPLY_DATE)
            val amount = formatProductAmount(order.optString(ApiField.LOAN_AMOUNT)
                .ifBlank { product.optString(ApiField.PRODUCT_AMOUNT) })
            val views = (0 until stage.childCount).map(stage::getChildAt)
                .filter { dpTop(it) >= top && dpTop(it) < ends[index] }
            views.filterIsInstance<TextView>().forEach { view ->
                when (view.tag as? String) {
                    getString(CoreR.string.text_name) -> if (name.isNotBlank()) {
                        view.text = name
                        view.maxLines = 1
                        view.ellipsize = android.text.TextUtils.TruncateAt.END
                        view.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        view.layoutParams = view.layoutParams.apply { width = dp(126f) }
                    }
                    getString(CoreR.string.text_jkd1232312312312312312) -> if (orderNumber.isNotBlank()) view.text = orderNumber
                    getString(CoreR.string.text_30_12_2020) -> if (applyDate.isNotBlank()) view.text = applyDate
                    getString(CoreR.string.text_ghs_500_000),
                    getString(CoreR.string.text_ghs_100_000_ghs_3_000_000) -> if (amount.isNotBlank()) view.text = amount
                    getString(CoreR.string.text_pending_withdrawal),
                    getString(CoreR.string.text_waiting_for_review),
                    getString(CoreR.string.text_disbursing),
                    getString(CoreR.string.text_pending_repayment),
                    getString(CoreR.string.text_rejected),
                    getString(CoreR.string.text_overdue) -> {
                        view.text = statusLabel
                        view.contentDescription = statusLabel
                    }
                    getString(CoreR.string.text_almost_done_the_system_is_calculating_your_credit) ->
                        if (status != 1) view.visibility = View.GONE
                    getString(CoreR.string.text_sadly_your_application_was_not_approved_you_may_tr) ->
                        if (status != 5 || order.optInt(ApiField.PAYOUT_FAILED_FLAG) == 1) view.visibility = View.GONE
                }
            }
            product.optString(ApiField.PRODUCT_LOGO).takeIf(String::isNotBlank)?.let {
                addProductLogo(top + 22f, it)
            }
        }
        compactHomeFooter(contentBottom)
    }

    private fun compactHomeFooter(contentBottom: Float) {
        val footerHeight = 74f
        pageConstraints.setContentHeight(minOf(pageConstraints.referenceHeight, contentBottom + 16f + footerHeight))
    }

    private fun setHomeBandVisibility(top: Float, bottom: Float, visible: Boolean) {
        for (index in 0 until stage.childCount) {
            val child = stage.getChildAt(index)
            val childTop = dpTop(child)
            if (childTop >= top && childTop < bottom) child.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun dpTop(view: View): Float =
        pageConstraints.top(view).roundToInt().toFloat()

    private fun formatProductAmount(value: String): String {
        val amount = value.trim()
        if (amount.isBlank()) return ""
        return if (amount.contains(Regex("[a-zA-Z]"))) amount else "GHS $amount"
    }

    private fun formatProductDays(value: String): String {
        val days = value.trim()
        if (days.isBlank()) return ""
        return if (days.contains("day", ignoreCase = true)) days else "$days Days"
    }

    private fun loanStatusLabel(order: org.json.JSONObject): String? = when (order.optInt(ApiField.LOAN_STATUS, 0)) {
        1 -> getString(CoreR.string.text_waiting_for_review)
        2 -> getString(CoreR.string.text_disbursing)
        3 -> getString(CoreR.string.text_pending_repayment)
        4 -> getString(CoreR.string.text_repayment_completed)
        5 -> if (order.optInt(ApiField.PAYOUT_FAILED_FLAG, 0) == 1) {
            getString(CoreR.string.text_payout_failed)
        } else getString(CoreR.string.text_rejected)
        6 -> getString(CoreR.string.text_overdue)
        else -> null
    }

    private fun addStatusBadge(top: Float, label: String, status: Int) {
        val badgeTag = "home-status-badge-${top.toInt()}"
        val existing = (0 until stage.childCount).map(stage::getChildAt)
            .firstOrNull { it.tag == badgeTag } as? TextView
        val badge = existing ?: TextView(this).apply {
            text = label
            tag = badgeTag
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        badge.apply {
            text = label
            visibility = View.VISIBLE
            setTextColor(if (status == 5 || status == 6) Color.rgb(180, 55, 43) else Color.rgb(56, 86, 151))
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(if (status == 5 || status == 6) Color.rgb(255, 239, 235) else Color.rgb(237, 243, 255))
            }
            contentDescription = label
        }
        if (existing == null) pageConstraints.add(badge, left = 230f, top = top, width = 120f, height = 24f)
    }

    private fun addProductLogo(top: Float, url: String) {
        val logoTag = "home-product-logo-${top.toInt()}"
        val existing = (0 until stage.childCount).map(stage::getChildAt)
            .firstOrNull { it.tag == logoTag } as? ImageView
        val existingUrl = existing?.contentDescription?.toString()
        if (existing != null && existingUrl == url) {
            existing.visibility = View.VISIBLE
            return
        }
        val image = existing ?: ImageView(this).apply {
            tag = logoTag
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        image.contentDescription = url
        image.visibility = View.VISIBLE
        if (existing == null) pageConstraints.add(image, left = 42f, top = top, width = 40f, height = 40f)
        val owner = stage
        activityScope.launch {
            val bitmap = api.productLogo(url) ?: return@launch
            if (!isFinishing && !isDestroyed && image.parent === owner) image.setImageBitmap(bitmap)
        }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun isBottomTabPage(page: Screen) =
        page in homeScreens || page in setOf(ORDERS, ORDERS_EMPTY, PROFILE, PROFILE_GUEST)

    private fun rememberRenderedTabPage() {
        val page = renderedScreen ?: return
        if (!isBottomTabPage(page) || !::stage.isInitialized || !::pageConstraints.isInitialized) return
        cachedTabPages[page] = CachedTabPage(
            screen = page,
            stage = stage,
            pageConstraints = pageConstraints,
            footerConstraints = footerConstraints,
            pageScrim = pageScrim,
            stickyAppBarViews = stickyAppBarViews,
            stickyAppBarBackground = stickyAppBarBackground,
            scrollY = viewport.scrollY
        )
    }

    private fun restoreTabPage(page: Screen, cached: CachedTabPage) {
        form.beginScreen()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(viewport.windowToken, 0)
        stickyAppBarViews = cached.stickyAppBarViews
        stickyAppBarBackground = cached.stickyAppBarBackground
        pageScrim = cached.pageScrim
        stage = cached.stage
        pageConstraints = cached.pageConstraints
        footerConstraints = cached.footerConstraints
        fields.clear()
        viewport.removeAllViews()
        viewport.addView(stage)
        val footer = findViewById<FrameLayout>(R.id.footer)
        footer.removeAllViews()
        footerConstraints?.let { footer.addView(it.layout) }
        statusBarBackground.page = stage
        statusBarBackground.visibility = View.VISIBLE
        val decor = window.decorView
        decor.systemUiVisibility = decor.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setHomeSkeletonVisibility(page == HOME && !homeDataResolved)
        if (page == ORDERS || page == ORDERS_EMPTY) configureOrderTabs(selectedOrderFilter())
        updatePageWidth()
        renderedScreen = page
        val targetScroll = cached.scrollY
        viewport.post {
            if (renderedScreen != page || stage !== cached.stage) return@post
            viewport.scrollTo(0, targetScroll)
            stickyAppBarViews.forEach { it.translationY = targetScroll.toFloat() }
            stickyAppBarBackground?.translationY = targetScroll.toFloat()
            updateFullScreenScrim()
        }
    }

    private fun invalidateCachedHomePages() {
        cachedTabPages.keys.removeAll(homeScreens)
    }

    private fun show(page: Screen) {
        if (forceHomePageRebuild) {
            renderedScreen?.takeIf { it in homeScreens }?.let(cachedTabPages::remove)
        } else {
            rememberRenderedTabPage()
        }
        val isLandscapePage = page == DOCUMENT_GUIDE || page == DOCUMENT_PREVIEW
        val requiredOrientation = if (isLandscapePage) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
        requestedOrientation = if (isLandscapePage) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val changingFromRenderedOrientation = renderedPageOrientation != Configuration.ORIENTATION_UNDEFINED &&
            renderedPageOrientation != requiredOrientation
        if (resources.configuration.orientation != requiredOrientation ||
            (changingFromRenderedOrientation && !viewportMatchesOrientation(isLandscapePage))
        ) {
            pendingOrientationPage = page
            viewport.isEnabled = false
            return
        }
        pendingOrientationPage = null
        viewport.isEnabled = true
        renderedPageOrientation = requiredOrientation
        if (!layoutPreview && !forceHomePageRebuild) {
            cachedTabPages[page]?.let { cached ->
                restoreTabPage(page, cached)
                return
            }
        }
        if (page != DOCUMENT_PREVIEW) clearDocumentCapturePreview()
        form.beginScreen()
        if (!layoutPreview) {
            trackAuthScreenTransition(page)
            if (page == SIGN_IN) trackServiceEvent("OPEN_LOGIN_PAGE")
            if (page == PRIVACY) trackServiceEvent("CLICK_PRIVACY_BUTTO")
        }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(viewport.windowToken, 0)
        clearOrderSkeletonViews()
        stickyAppBarViews = emptyList()
        stickyAppBarBackground = null
        pageScrim = null
        viewport.removeAllViews()
        val contentPage = if (page == LOGOUT) PROFILE else page
        stage = layoutInflater.inflate(pages.getValue(contentPage), viewport, false) as ConstraintLayout
        pageConstraints = ScreenConstraints(stage)
        if (page == LOGOUT) addLogoutOverlay()
        pageScrim = (0 until stage.childCount)
            .map(stage::getChildAt)
            .firstOrNull { child ->
                val params = child.layoutParams as? ConstraintLayout.LayoutParams
                child.isClickable && child.background != null && abs(child.alpha - 0.4f) < 0.01f &&
                    params != null && params.topMargin < 0
            }
        if (!layoutPreview) {
            bindHomePage(page)
            setHomeSkeletonVisibility(page == HOME && !homeDataResolved)
        }
        val fullBleed = page == WELCOME
        statusBarBackground.visibility = if (fullBleed) View.GONE else View.VISIBLE
        val decor = window.decorView
        val darkIcons = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        val appearance = decor.systemUiVisibility
        decor.systemUiVisibility = if (fullBleed) appearance and darkIcons.inv() else appearance or darkIcons
        statusBarBackground.page = stage
        stage.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> statusBarBackground.invalidate() }
        fields.clear()
        for (i in 0 until stage.childCount) {
            val view = stage.getChildAt(i)
            val label = view.tag as? String ?: continue
            if (label == HOME_SKELETON_TAG) continue
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
            HOME, HOME_APPLIED, HOME_PRODUCTS, HOME_ACTIVE, ELIGIBLE, ORDERS, ORDERS_EMPTY,
            PROFILE, PROFILE_GUEST, LOGOUT -> 74
            else -> 0
        }
        footerConstraints = if (footerHeight > 0) pageConstraints.detachFooter(footerHeight) else null
        footerConstraints?.let { footer.addView(it.layout) }
        if (page == LOGOUT) {
            footerConstraints?.add(View(this).apply {
                setBackgroundResource(CoreR.drawable.surface_8590b20b986a)
                alpha = 0.4f
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, left = 0f, top = 0f, width = 375f, height = 74f)
        }
        if (page == DOCUMENT_GUIDE || page == DOCUMENT_PREVIEW) {
            viewport.addView(stage, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ))
        } else {
            viewport.addView(stage)
        }
        updatePageWidth()
        if (page == DOCUMENT_PREVIEW) {
            documentCapturePreview?.takeUnless(Bitmap::isRecycled)?.let { bitmap ->
                val image = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(Color.WHITE)
                    contentDescription = "Captured document photo"
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                }
                pageConstraints.add(image, left = 45f, top = 69f, width = 548f, height = 280f)
            }
        }
        configureStickyAppBar()
        if (page == ORDERS || page == ORDERS_EMPTY) {
            fullOrdersHeight = pageConstraints.referenceHeight - footerHeight
            val currentFilter = selectedOrderFilter()
            configureOrderTabs(currentFilter)
            if (page == ORDERS && !layoutPreview) renderOrderCards(currentFilter)
        }
        if (page == SIGN_IN) {
            renderOtpButton()
            fields.getOrNull(1)?.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length != 4) return
                    val codeField = fields.getOrNull(1) ?: return
                    codeField.post {
                        if (screen != SIGN_IN || fields.getOrNull(1) !== codeField || codeField.text.length != 4) return@post
                        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                            .hideSoftInputFromWindow(codeField.windowToken, 0)
                        signIn()
                    }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
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
            if (!layoutPreview) {
                stage.postDelayed({ if (!isFinishing && !isDestroyed && screen == WELCOME) startJourney() }, 1500)
            }
        }
        if (page == REVIEW_COUNTDOWN) countdown(60)
        if (page == FACE_PREVIEW && preview != null) {
            val image = ImageView(this)
            image.setImageBitmap(preview)
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            pageConstraints.add(image, left = 20f, top = 85f, width = 335f, height = 535f)
        }
        stage.expandActionHitAreas()
        footerConstraints?.layout?.expandActionHitAreas()
        renderedScreen = page
        if (!layoutPreview && isBottomTabPage(page)) {
            cachedTabPages[page] = CachedTabPage(
                screen = page,
                stage = stage,
                pageConstraints = pageConstraints,
                footerConstraints = footerConstraints,
                pageScrim = pageScrim,
                stickyAppBarViews = stickyAppBarViews,
                stickyAppBarBackground = stickyAppBarBackground,
                scrollY = viewport.scrollY
            )
        }
    }

    private fun updatePageWidth() {
        if (!::pageConstraints.isInitialized) return
        val viewportWidth = viewport.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        var contentWidth = viewportWidth
        if (screen in setOf(DOCUMENT_GUIDE, DOCUMENT_PREVIEW) && viewport.height > 0) {
            contentWidth = pageConstraints.widthFittingHeight(viewportWidth, viewport.height)
            val params = stage.layoutParams
            if (params.width != contentWidth) {
                params.width = contentWidth
                stage.layoutParams = params
            }
        }
        pageConstraints.updateWidth(contentWidth)
        footerConstraints?.updateWidth(viewportWidth)
        updateFullScreenScrim()
    }

    private fun viewportMatchesOrientation(isLandscape: Boolean): Boolean {
        if (viewport.width <= 0 || viewport.height <= 0) return false
        return (viewport.width > viewport.height) == isLandscape
    }

    private fun renderPendingOrientationPage() {
        val page = pendingOrientationPage ?: return
        if (page != screen) {
            pendingOrientationPage = null
            viewport.isEnabled = true
            return
        }
        val isLandscapePage = page == DOCUMENT_GUIDE || page == DOCUMENT_PREVIEW
        val requiredOrientation = if (isLandscapePage) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
        if (resources.configuration.orientation != requiredOrientation ||
            !viewportMatchesOrientation(isLandscapePage) || pendingOrientationRenderPosted
        ) return

        pendingOrientationRenderPosted = true
        viewport.post {
            pendingOrientationRenderPosted = false
            if (pendingOrientationPage == page && page == screen &&
                resources.configuration.orientation == requiredOrientation &&
                viewportMatchesOrientation(isLandscapePage)
            ) {
                pendingOrientationPage = null
                viewport.isEnabled = true
                show(page)
            } else if (pendingOrientationPage != null) {
                renderPendingOrientationPage()
            }
        }
    }

    private fun updateFullScreenScrim() {
        val scrim = pageScrim
        val navigationBarColor = if (scrim != null) Color.rgb(160, 158, 153) else defaultNavigationBarColor
        if (window.navigationBarColor != navigationBarColor) window.navigationBarColor = navigationBarColor
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        scrim ?: return
        val params = scrim.layoutParams as? ConstraintLayout.LayoutParams ?: return
        var changed = false
        if (params.width != 0) {
            params.width = 0
            changed = true
        }
        if (params.height != 0) {
            params.height = 0
            changed = true
        }
        if (params.dimensionRatio != null) {
            params.dimensionRatio = null
            changed = true
        }
        if (params.leftToLeft != ConstraintLayout.LayoutParams.PARENT_ID ||
            params.rightToRight != ConstraintLayout.LayoutParams.PARENT_ID ||
            params.topToTop != ConstraintLayout.LayoutParams.PARENT_ID ||
            params.bottomToBottom != ConstraintLayout.LayoutParams.PARENT_ID ||
            params.bottomMargin != 0
        ) {
            params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            params.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.leftMargin = 0
            params.rightMargin = 0
            params.bottomMargin = 0
            changed = true
        }
        if (changed) scrim.layoutParams = params
        val scrollOffset = viewport.scrollY.toFloat()
        if (scrim.translationY != scrollOffset) scrim.translationY = scrollOffset
    }

    private fun addLogoutOverlay() {
        val overlay = layoutInflater.inflate(AccountLayouts.screens.getValue(LOGOUT), stage, false) as ConstraintLayout
        val overlayConstraints = ScreenConstraints(overlay)
        val children = (0 until overlay.childCount).map(overlay::getChildAt)
        children.forEach { child ->
            val left = overlayConstraints.left(child)
            val top = overlayConstraints.top(child)
            val width = overlayConstraints.width(child)
            val height = overlayConstraints.height(child)
            overlay.removeView(child)
            pageConstraints.add(child, left, top, width, height)
        }
    }

    private fun configureStickyAppBar() {
        val children = (0 until stage.childCount).map(stage::getChildAt)
        val density = resources.displayMetrics.density
        fun centerY(view: View): Float {
            val height = pageConstraints.height(view).takeIf { it > 0f } ?: view.height / density
            return pageConstraints.top(view) + height / 2f
        }

        val titleCandidates = children.filterIsInstance<TextView>().filter { view ->
            val titleHeight = pageConstraints.height(view)
            pageConstraints.top(view) in 0f..80f &&
                view.textSize / density >= 18f &&
                (titleHeight == 0f || titleHeight <= 32f)
        }
        val backLabel = getString(CoreR.string.text_back)
        val explicitBackButtons = children.filter { view ->
            val tag = view.tag?.toString()
            val description = view.contentDescription?.toString()
            tag == backLabel || description == backLabel
        }
        val backIcons = children.filterIsInstance<ImageView>().filter { view ->
            pageConstraints.top(view) in 0f..80f &&
                pageConstraints.left(view) <= 28f &&
                pageConstraints.width(view) in 1f..24f &&
                pageConstraints.height(view) in 1f..24f &&
                titleCandidates.any { abs(centerY(it) - centerY(view)) <= 16f }
        }
        val backButtons = (explicitBackButtons + backIcons).distinct()
        val titles = titleCandidates.filter { view ->
            pageConstraints.top(view) <= 36f ||
                backButtons.any { abs(centerY(view) - centerY(it)) <= 16f }
        }
        val pinned = (titles + backButtons).distinct()
        if (pinned.isEmpty()) return

        val visualHeader = (titles + backButtons.filterNot { it is Button }).distinct().ifEmpty { pinned }
        val headerBottom = visualHeader.maxOf { pageConstraints.top(it) + pageConstraints.height(it) }
        val designWidth = (stage.tag as? String)?.substringBefore(',')?.toFloatOrNull()
            ?: pageConstraints.referenceHeight
        val rowCenter = visualHeader.map(::centerY).average().toFloat()
        val appBarTop = if (designWidth > pageConstraints.referenceHeight) 0f else maxOf(0f, rowCenter - 28f)
        val firstContentTop = children.asSequence()
            .filterNot { it in pinned }
            .map(pageConstraints::top)
            .filter { it >= headerBottom - 0.5f }
            .minOrNull() ?: appBarTop + 56f
        val appBarHeight = maxOf(
            headerBottom - appBarTop,
            minOf(56f, firstContentTop - appBarTop)
        ).coerceAtLeast(1f)
        val appBarBottom = appBarTop + appBarHeight
        val hasArtworkBehindHeader = children.any { view ->
            if (view in pinned) return@any false
            val top = pageConstraints.top(view)
            val height = pageConstraints.height(view)
            pageConstraints.left(view) <= 0.5f &&
                pageConstraints.width(view) >= designWidth - 1f &&
                top <= appBarTop + 0.5f &&
                top + height >= appBarBottom - 0.5f &&
                (view is ImageView || view.background != null)
        }
        val background = View(this).apply {
            setBackgroundColor(if (hasArtworkBehindHeader) Color.TRANSPARENT else Color.WHITE)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val backgroundParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
            pageConstraints.scaledPixels(appBarHeight)
        ).apply {
            leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = pageConstraints.scaledPixels(appBarTop)
        }
        // Keep the header fixed with the page's own backdrop; no divider or elevation is added.
        stage.addView(background, backgroundParams)
        pinned.forEach(stage::bringChildToFront)
        stickyAppBarViews = pinned
        stickyAppBarBackground = background
    }

    private fun startOtpCooldown() {
        model.state.putLong("otpRetryAt", SystemClock.elapsedRealtime() + 60_000L)
    }

    private fun renderOtpButton() {
        val button = stage.findViewById<TextView>(AccessR.id.request_otp)
        val remaining = model.state.getLong("otpRetryAt") - SystemClock.elapsedRealtime()
        val waiting = remaining > 0
        button.isEnabled = true
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
        if (consent.getBoolean("initialPermissionsHandled", false)) {
            model.root(HOME)
            loadHomeData()
        } else {
            model.root(PERMISSION_PROMPT)
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

    private fun applyAuthenticationOptions(info: org.json.JSONObject): Boolean {
        val minimum = info.optString(ApiField.AMOUNT_MIN).replace(",", "").toLongOrNull()
        val maximum = info.optString(ApiField.AMOUNT_MAX).replace(",", "").toLongOrNull()
        val rangeValid = minimum != null && maximum != null && minimum >= 0L && maximum >= minimum
        model.state.putBoolean("amountRangeValid", rangeValid)
        if (rangeValid) {
            model.state.putLong("amountMin", minimum!!)
            model.state.putLong("amountMax", maximum!!)
            val oldValue = model.state.getLong("applyAmountValue", 0L)
            if (model.state.getBoolean("amountTouched", false)) {
                model.state.putLong("applyAmountValue", oldValue.coerceIn(minimum, maximum))
            }
        } else {
            model.state.remove("amountMin")
            model.state.remove("amountMax")
        }
        val arrays = mapOf(
            "gender" to ApiField.USER_GENDER_LIST,
            "education" to ApiField.EDUCATION_LIST,
            "marriage" to ApiField.MARRIAGE_STATUS_LIST,
            "workTitle" to ApiField.WORK_TITLE_LIST,
            "salaryType" to ApiField.SALARY_TYPE_LIST,
            "industry" to ApiField.INDUSTRY_LIST,
            "monthlySalary" to ApiField.MONTHLY_SALARY_LIST,
            "relation" to ApiField.RELATION_LIST,
            "walletType" to ApiField.WALLET_TYPE_LIST
        )
        arrays.forEach { (name, apiKey) ->
            model.state.putString("authOptions.$name", info.optJSONArray(apiKey)?.toString().orEmpty())
        }
        return rangeValid
    }

    private fun savedAuthOptions(name: String): List<String> = runCatching {
        val array = org.json.JSONArray(model.state.getString("authOptions.$name").orEmpty())
        (0 until array.length()).map { array.optString(it).trim() }.filter(String::isNotBlank)
    }.getOrDefault(emptyList())

    private fun authChoiceOptions(page: Screen, index: Int, label: String): List<String>? = when (page) {
        PERSONAL_INFO -> when (index) {
            35 -> savedAuthOptions("education")
            36 -> savedAuthOptions("monthlySalary")
            37 -> savedAuthOptions("marriage")
            38 -> savedAuthOptions("workTitle")
            39 -> savedAuthOptions("industry")
            11, 40 -> savedAuthOptions("salaryType")
            else -> null
        }
        WALLET_SETUP, WALLET_EDIT -> if (label.contains("Wallet", ignoreCase = true)) savedAuthOptions("walletType") else null
        CONTACTS -> if (contactFieldIndices.any { it.third == index }) {
            savedAuthOptions("relation").ifEmpty { listOf("Parent", "Spouse", "Sibling", "Friend") }
        } else null
        IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD -> if (label.contains("Gender", ignoreCase = true)) savedAuthOptions("gender") else null
        else -> null
    }

    private fun onAuthChoiceSelected(page: Screen, index: Int, value: String, owner: android.view.ViewGroup) {
        if (page != PERSONAL_INFO) return
        val position = personalInfoChoiceOrder.indexOf(index)
        if (position < 0) return
        val nextIndex = personalInfoChoiceOrder.drop(position + 1)
            .firstOrNull { model.field("PERSONAL_INFO_$it").isBlank() } ?: return
        val targetId = personalInfoChoiceViewIds[nextIndex] ?: return
        val target = owner.findViewById<TextView>(targetId) ?: return
        target.post {
            if (screen == PERSONAL_INFO && model.field("PERSONAL_INFO_$nextIndex").isBlank()) {
                form.choose(target, nextIndex, owner)
            }
        }
    }

    private fun amountRangeValid() = model.state.getBoolean("amountRangeValid", false) &&
        model.state.getLong("amountMin", -1L) >= 0L &&
        model.state.getLong("amountMax", -1L) >= model.state.getLong("amountMin", -1L)

    private suspend fun requireAmountRange() {
        if (!amountRangeValid()) applyAuthenticationOptions(api.authenticationInfo())
        if (!amountRangeValid()) throw ApiException("Loan amount limits are unavailable. Please try again later.")
    }

    private fun openAmountScreen() {
        apiCall("Loading available loan amounts…", {
            prepareAuthentication()
            requireAmountRange()
        }) {
            go(WITHDRAW)
        }
    }

    private fun saveSelectedAmount() {
        if (!amountRangeValid()) {
            notice("Loan amount limits are unavailable. Please try again later.")
            return
        }
        if (!model.state.getBoolean("amountTouched", false)) {
            notice("Please select a loan amount.")
            return
        }
        val cardScreen = runCatching {
            Screen.valueOf(model.state.getString("authenticationCardScreen").orEmpty())
        }.getOrDefault(IDENTITY_CARD)
        val imagePath = model.state.getString("documentImage_${cardScreen.name}").orEmpty()
        if (imagePath.isBlank()) {
            notice("Complete identity verification before selecting a loan amount.")
            return
        }
        apiCall("Saving your selected loan amount…", {
            api.saveAuthentication(authenticationParameters(cardScreen, imagePath, includeApplyAmount = true))
        }) {
            go(WITHDRAW_SUCCESS)
        }
    }

    private suspend fun prepareAuthentication(): Screen {
        val info = api.authenticationInfo()
        applyAuthenticationOptions(info)
        model.edit("WALLET_SETUP_10", info.optString(ApiField.WALLET_TYPE))
        model.edit("WALLET_SETUP_11", info.optString(ApiField.WALLET_ACCOUNT))

        val contacts = runCatching { org.json.JSONArray(info.optString(ApiField.CONTACTS)) }
            .getOrDefault(org.json.JSONArray())
        for (index in 0 until minOf(contactFieldIndices.size, contacts.length())) {
            val contact = contacts.optJSONObject(index) ?: continue
            val (phoneIndex, nameIndex, relationIndex) = contactFieldIndices[index]
            model.edit("CONTACTS_$phoneIndex", contact.optString(ApiField.CONTACT_PHONE))
            model.edit("CONTACTS_$nameIndex", contact.optString(ApiField.CONTACT_NAME))
            model.edit("CONTACTS_$relationIndex", contact.optString(ApiField.CONTACT_RELATION))
        }

        model.edit("PERSONAL_INFO_35", info.optString(ApiField.EDUCATION))
        model.edit("PERSONAL_INFO_36", info.optString(ApiField.MONTHLY_INCOME))
        model.edit("PERSONAL_INFO_37", info.optString(ApiField.MARITAL_STATUS))
        model.edit("PERSONAL_INFO_38", info.optString(ApiField.JOB_TITLE))
        model.edit("PERSONAL_INFO_39", info.optString(ApiField.INDUSTRY))
        model.edit("PERSONAL_INFO_40", info.optString(ApiField.PAY_CYCLE))
        model.edit("PERSONAL_INFO_11", info.optString(ApiField.PAY_CYCLE))
        model.edit("PERSONAL_INFO_41", info.optString(ApiField.EMAIL))
        model.edit("PERSONAL_INFO_42", info.optString(ApiField.ADDRESS))
        model.edit("PERSONAL_INFO_43", info.optString(ApiField.MESSAGING_ACCOUNT))

        val cardType = info.optString(ApiField.CARD_TYPE)
        val cardScreen = when {
            cardType.contains("driving", ignoreCase = true) -> DRIVER_LICENSE
            cardType.contains("voter", ignoreCase = true) -> VOTER_CARD
            else -> IDENTITY_CARD
        }
        model.state.putString("authenticationCardScreen", cardScreen.name)
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
        val requiredContacts = info.optInt(ApiField.CONTACT_NUM, 1).coerceIn(1, contactFieldIndices.size)
        model.state.putInt("authentication.contactNum", requiredContacts)

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

    private suspend fun sendServiceEventQuietly(
        type: String,
        parameters: Map<String, String>,
        authenticated: Boolean
    ): Boolean = try {
        api.sendEvent(type, parameters, authenticated)
        true
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private fun trackServiceEvent(type: String, parameters: Map<String, String> = emptyMap()) {
        if (layoutPreview) return
        val authenticated = model.signedIn
        activityScope.launch {
            serviceEventMutex.withLock { sendServiceEventQuietly(type, parameters, authenticated) }
        }
    }

    private fun authEventStage(page: Screen): AuthEventStage? = when (page) {
        WALLET_SETUP, WALLET_EDIT -> AuthEventStage("1", "BANKINFO")
        CONTACTS -> AuthEventStage("2", "CONTACT")
        PERSONAL_INFO -> AuthEventStage("3", "PERSONINFO")
        IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD -> AuthEventStage("4", "OCR")
        FACE_GUIDE, FACE_PREVIEW, FACE_FAILED -> AuthEventStage("5", "FACE")
        else -> null
    }

    private fun trackAuthScreenTransition(page: Screen) {
        val next = authEventStage(page)
        if (next == activeAuthEvent) return

        val now = SystemClock.elapsedRealtime()
        activeAuthEvent?.let { previous ->
            val seconds = ((now - activeAuthEventStartedAt).coerceAtLeast(0L) / 1000L).toString()
            trackServiceEvent("TIME_AUTH", mapOf(
                ApiField.EVENT_PARAMETER to previous.step,
                ApiField.EVENT_PARAMETER_2 to previous.page,
                ApiField.EVENT_PARAMETER_3 to seconds
            ))
        }
        activeAuthEvent = next
        if (next != null) {
            activeAuthEventStartedAt = now
            trackServiceEvent("OPEN_AUTH", mapOf(
                ApiField.EVENT_PARAMETER to next.step,
                ApiField.EVENT_PARAMETER_2 to next.page
            ))
        }
    }

    private fun trackAuthSubmission(page: Screen, succeeded: Boolean) {
        val stage = authEventStage(page) ?: return
        trackServiceEvent("SUB_AUTH", mapOf(
            ApiField.EVENT_PARAMETER to stage.step,
            ApiField.EVENT_PARAMETER_2 to stage.page,
            ApiField.EVENT_PARAMETER_3 to if (succeeded) "1" else "0"
        ))
    }

    private fun authFieldChangeType(page: Screen, index: Int): String? = when (page) {
        WALLET_SETUP -> when (index) {
            10 -> "CHANGE_BANKNAME"
            11 -> "CHANGE_BANKACCOUNT"
            else -> null
        }
        WALLET_EDIT -> when (index) {
            9 -> "CHANGE_BANKNAME"
            10 -> "CHANGE_BANKACCOUNT"
            else -> null
        }
        CONTACTS -> when (index) {
            29, 30, 48, 61 -> "CHANGE_RELATION"
            31, 32, 50, 63 -> "CHANGE_PHONENUMBER"
            else -> null
        }
        PERSONAL_INFO -> when (index) {
            11, 40 -> "CHANGE_PAYROLL_CYCLE"
            35 -> "CHANGE_EDUCATION"
            36 -> "CHANGE_MONTHLY"
            37 -> "CHANGE_MARRY"
            38 -> "CHANGE_WORKTITLE"
            39 -> "CHANGE_INDUSTRY"
            41 -> "CHANGE_EMAIL"
            42 -> "CHANGE_ADDRESS"
            43 -> "CHANGE_WHATSAPP"
            else -> null
        }
        IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD -> {
            val idIndex = if (page == IDENTITY_CARD) 18 else 20
            val dateIndex = if (page == IDENTITY_CARD) 26 else 28
            when (index) {
                idIndex -> when (page) {
                    DRIVER_LICENSE -> "CHANGE_DRIVING_LICENSE_NUMBER"
                    VOTER_CARD -> "CHANGE_VOTER_NUMBER"
                    else -> "CHANGE_CARD_NUMBER"
                }
                idIndex + 1 -> "CHANGE_NAME"
                dateIndex -> "CHANGE_BIRTHDAY"
                dateIndex + 1 -> "CHANGE_GENDER"
                else -> null
            }
        }
        else -> null
    }

    private fun trackAuthFieldChange(page: Screen, index: Int) {
        val changeType = authFieldChangeType(page, index) ?: return
        trackAuthChange(page, changeType)
    }

    private fun trackAuthChange(page: Screen, changeType: String) {
        val stage = authEventStage(page) ?: return
        val counterKey = "${stage.step}:$changeType"
        val count = (authFieldEditCounts[counterKey] ?: 0) + 1
        authFieldEditCounts[counterKey] = count
        trackServiceEvent("UPDATE_AUTH", mapOf(
            ApiField.EVENT_PARAMETER to stage.step,
            ApiField.EVENT_PARAMETER_2 to stage.page,
            ApiField.EVENT_PARAMETER_3 to changeType,
            ApiField.EVENT_PARAMETER_4 to count.toString()
        ))
    }

    private fun trackDocumentCameraTap() {
        val event = when (pendingDocumentScreen) {
            IDENTITY_CARD -> "CLICK_ID_CARD_FRONT_CAMERA"
            DRIVER_LICENSE -> "CLICK_DRIVING_LICENSE_CAMERA"
            VOTER_CARD -> "CLICK_VOTER_CARD_CAMERA"
            else -> null
        } ?: return
        trackServiceEvent("CLICK_BUTTON", mapOf(ApiField.EVENT_PARAMETER to event))
    }

    private fun refreshSignedInHome() {
        if (homeDataLoading) return
        homeDataLoading = true
        requestFirstOpenForHomeResume()
        apiCall("Refreshing account…", { fetchHomeData(signedIn = true) },
            onFailure = { finishHomeDataLoadAfterFailure() }, showLoadingDialog = false) { info ->
            applyHomeData(info.first, true, info.second)
        }
    }

    private fun requestFirstOpenForHomeResume() {
        activityScope.launch {
            try {
                api.firstOpenIfNeeded()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                notice(failure.message ?: "Unable to connect. Please try again.")
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
                        .create()
                        .showWithPopupButtonStyle()
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
            dialog.applyPopupButtonStyle()
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
                        .create()
                        .showWithPopupButtonStyle()
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
            WITHDRAW, WITHDRAW_ADJUSTED -> {
                val origin = model.state.getString("applicationEntryPage").orEmpty()
                model.state.putString(
                    "cancelOrderPage",
                    if (origin == "ORDERDETAIL_ORDER_PAGE") origin else "RECOMMENDED_ORDER_PAGE"
                )
                go(WITHDRAW_EXIT)
            }
            FACE_GUIDE, FACE_PREVIEW, FACE_FAILED -> {
                trackServiceEvent("CLICK_BUTTON", mapOf(ApiField.EVENT_PARAMETER to "BACK_OCR"))
                go(KYC_EXIT)
            }
            WALLET_SETUP -> leave()
            CONTACTS, PERSONAL_INFO, IDENTITY_CARD, VOTER_CARD, DRIVER_LICENSE -> go(KYC_EXIT)
            REVIEW_COUNTDOWN -> go(REVIEW_EXIT)
            else -> leave()
        }
    }

    private companion object {
        const val PERMISSION_REQUEST = 870
        const val CAMERA_PERMISSION_REQUEST = 871
        const val HOME_SKELETON_TAG = "home-skeleton"
    }

    private fun applyForCredit() {
        val amountPage = when (screen) {
            HOME, HOME_ACTIVE, HOME_PRODUCTS, HOME_APPLIED -> "HOME"
            ORDER_DETAIL -> "ORDERDETAIL_ORDER_PAGE"
            ORDER_SUCCESS -> "ORDER_SUCCESS_PAGE"
            else -> "RECOMMENDED_ORDER_PAGE"
        }
        trackServiceEvent("CLICK_AMOUNT_BUTTON", mapOf(
            ApiField.EVENT_PARAMETER to amountPage,
            ApiField.EVENT_PARAMETER_2 to model.state.getString("selectedProductId").orEmpty()
        ))
        model.state.putString("applicationEntryPage", amountPage)
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
                val destination = if (status == 1) {
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
                if (productId.isNotBlank() && destination.second == WITHDRAW) {
                    prepareAuthentication()
                    requireAmountRange()
                }
                destination
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
        val orderPage = if (screen == ORDER_DETAIL) "ORDERDETAIL_ORDER_PAGE" else "RECOMMENDED_ORDER_PAGE"
        trackServiceEvent("CLICK_ORDER_BUTTON", mapOf(
            ApiField.EVENT_PARAMETER to orderPage,
            ApiField.EVENT_PARAMETER_2 to model.state.getString("selectedProductId").orEmpty()
        ))
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
        for (index in 0 until minOf(row.childCount, filters.size)) {
            val tab = row.getChildAt(index) as? TextView ?: continue
            val filter = filters[index]
            val active = filter == selected
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
            if (target != visibleLeft) tabs.scrollTo(target, 0)
        }
    }

    private fun selectedOrderFilter() = credit.orderStatusFilter(
        model.state.getInt("filterOrderStatus", 0).takeIf { it in 1..6 }
    )

    private fun selectOrderFilter(filter: OrderStatusFilter, tabs: HorizontalScrollView) {
        model.state.remove("filterOrderStatus")
        filter.requestStatus?.let { model.state.putInt("filterOrderStatus", it) }
        configureOrderTabs(filter)
        if (screen == ORDERS) renderOrderCards(filter)
        if (model.signedIn) loadOrders(filter)
    }

    private fun loadOrders(filter: OrderStatusFilter = OrderStatusFilter.ALL) {
        val showSkeleton = screen == ORDERS && !ordersInitialLoadComplete && orderRecords.isEmpty()
        if (showSkeleton) {
            ordersLoading = true
            renderOrderCards(filter)
        }
        apiCall(
            "Loading orders…",
            { api.orderList(1, 20, filter.requestStatus) },
            onFailure = {
                if (showSkeleton) {
                    ordersLoading = false
                    if (screen == ORDERS) renderOrderCards(filter)
                }
            },
            showLoadingDialog = screen !in setOf(ORDERS, ORDERS_EMPTY)
        ) { response ->
            val orders = response.optJSONArray(ApiField.ORDER_LIST)
            orderRecords = (0 until (orders?.length() ?: 0)).mapNotNull { orders?.optJSONObject(it) }
            if (filter == OrderStatusFilter.ALL) ordersInitialLoadComplete = true
            ordersLoading = false
            if (filter == OrderStatusFilter.ALL) {
                model.state.putBoolean("hasOrder", orderRecords.isNotEmpty())
                orderRecords.firstOrNull()?.optString(ApiField.ORDER_NO)?.takeIf(String::isNotBlank)?.let {
                    model.state.putString("activeOrderNo", it)
                }
                if (orderRecords.isEmpty() && screen == ORDERS) model.root(ORDERS_EMPTY)
                if (orderRecords.isNotEmpty() && screen == ORDERS_EMPTY) model.root(ORDERS)
            } else if (orderRecords.isNotEmpty()) {
                model.state.putBoolean("hasOrder", true)
                if (screen == ORDERS_EMPTY) model.root(ORDERS)
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
        val replaceCurrent = screen == FEEDBACK_SUCCESS
        apiCall("Loading feedback…", { api.feedbackList() }) { response ->
            val entries = response.optJSONArray(ApiField.FEEDBACK_LIST)
            val hasFeedback = entries != null && entries.length() > 0
            model.state.putBoolean("hasFeedback", hasFeedback)
            model.go(if (hasFeedback) FEEDBACK_LIST else FEEDBACK_EMPTY, replace = replaceCurrent)
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
        for (index in 0 until stage.childCount) {
            val child = stage.getChildAt(index)
            val top = dpTop(child)
            if (top < 105 || top >= 1500) continue
            val row = (top.toInt() - 105) / 200
            if (row in 0 until 7) orderCardSlots[child] = OrderCardSlot(row, pageConstraints.top(child))
        }
    }

    private fun renderOrderCards(filter: OrderStatusFilter) {
        if (ordersLoading) {
            for (view in orderCardSlots.keys) view.visibility = View.GONE
            if (orderSkeletonViews.isEmpty()) addOrderSkeletons()
            pageConstraints.setContentHeight(705f)
            viewport.scrollTo(0, 0)
            return
        }
        clearOrderSkeletonViews()

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
            pageConstraints.moveTo(view, slot.originalTop + (position - slot.index) * 200f)
            if (view !is TextView) continue
            val order = orderRecords[slot.index]
            val left = pageConstraints.left(view).roundToInt()
            val relativeTop = slot.originalTop.roundToInt() - (105 + slot.index * 200)
            when {
                relativeTop == 17 && left < 100 -> view.text = order.optString(ApiField.PRODUCT_NAME)
                relativeTop == 17 && left >= 200 -> view.text = orderStatusLabel(order)
                relativeTop == 52 && left >= 90 -> view.text = order.optString(ApiField.LOAN_AMOUNT)
                relativeTop == 121 && left >= 250 -> view.text = order.optString(ApiField.ORDER_APPLY_DATE)
                relativeTop == 149 && left >= 150 -> view.text = order.optString(ApiField.ORDER_NO)
            }
        }

        val contentHeightDp = if (filter.requestStatus == null) {
            fullOrdersHeight
        } else {
            maxOf(96f, 302f + (visibleRows.size - 1).coerceAtLeast(0) * 200f)
        }
        pageConstraints.setContentHeight(contentHeightDp)
        viewport.scrollTo(0, 0)
    }

    private fun addOrderSkeletons() {
        val cardColor = Color.rgb(247, 248, 250)
        val blockColor = Color.rgb(231, 234, 238)
        repeat(3) { index ->
            val top = 105f + index * 200f
            addOrderSkeletonBlock(20f, top, 335f, 180f, 10f, cardColor)
            addOrderSkeletonBlock(39f, top + 17f, 104f, 16f, 5f, blockColor)
            addOrderSkeletonBlock(257f, top + 18f, 67f, 14f, 7f, blockColor)
            addOrderSkeletonBlock(39f, top + 52f, 40f, 40f, 20f, blockColor)
            addOrderSkeletonBlock(99f, top + 52f, 139f, 23f, 6f, blockColor)
            addOrderSkeletonBlock(99f, top + 76f, 87f, 14f, 5f, blockColor)
            addOrderSkeletonBlock(30f, top + 106f, 315f, 1f, 0f, Color.rgb(232, 234, 237))
            addOrderSkeletonBlock(36f, top + 121f, 71f, 14f, 5f, blockColor)
            addOrderSkeletonBlock(268f, top + 121f, 74f, 14f, 5f, blockColor)
            addOrderSkeletonBlock(36f, top + 149f, 91f, 14f, 5f, blockColor)
            addOrderSkeletonBlock(163f, top + 149f, 179f, 14f, 5f, blockColor)
        }
    }

    private fun addOrderSkeletonBlock(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int
    ) {
        val block = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(radius).toFloat()
                setColor(color)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        pageConstraints.add(block, left, top, width, height)
        orderSkeletonViews += block
    }

    private fun clearOrderSkeletonViews() {
        orderSkeletonViews.forEach { view ->
            (view.parent as? android.view.ViewGroup)?.removeView(view)
        }
        orderSkeletonViews.clear()
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
        val fieldsToValidate = if (screen == CONTACTS) {
            contactFieldIndices.take(requiredContactCount()).flatMap { (phoneIndex, nameIndex, _) ->
                listOf(phoneIndex, nameIndex)
            }.mapNotNull { stage.getChildAt(it) as? EditText }
        } else fields
        val missing = fieldsToValidate.firstOrNull { it.text.isNullOrBlank() }
        if (missing == null) return true
        missing.requestFocus()
        missing.error = "Please complete this field"
        return false
    }

    private fun requiredContactCount() = model.state
        .getInt("authentication.contactNum", 1)
        .coerceIn(1, contactFieldIndices.size)

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
            sendServiceEventQuietly("LOGIN_APP", emptyMap(), authenticated = true)
            response
        }) { response ->
            AdjustAnalytics.loginResponse(this, response.optInt(ApiField.REGISTERED) == 1)
            homeDataResolved = false
            homeDataLoading = false
            model.login()
            viewport.post { refreshSignedInHome() }
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
                startJourney()
            }
            screen == PRIVACY && label == "Reject and Exit" -> finish()
            label == "Agreement" || label.startsWith("By proceeding") -> {
                (view.parent as? android.view.ViewGroup)?.let { container ->
                    val box = (0 until container.childCount).map(container::getChildAt)
                        .filterIsInstance<CheckBox>().firstOrNull()
                    box?.toggle()
                }
            }
            label == "Home" || label == "Start Application Now" -> model.root(model.home)
            label == "My Orders" -> {
                if (!model.signedIn) go(SIGN_IN)
                else {
                    val filter = selectedOrderFilter()
                    model.root(model.orders)
                    loadOrders(filter)
                }
            }
            label == "Profile Center" -> model.root(model.profile)
            label == "Please sign in" -> go(SIGN_IN)
            label == "+233" -> openCountryDialog()
            label == "Get OTP" -> {
                AdjustAnalytics.otpButtonTapped()
                val remaining = model.state.getLong("otpRetryAt") - SystemClock.elapsedRealtime()
                if (remaining > 0) {
                    notice("Please wait ${(remaining + 999) / 1000} seconds before requesting another code.")
                    return
                }
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
                homeProducts = org.json.JSONArray()
                homeInfoSignedIn = false
                homeDataResolved = false
                homeDataLoading = false
                model.signOut()
                loadHomeData()
            }
            label.contains("Wallet Account") || label == "Receiving Account" -> go(if (model.signedIn) WALLET_SAVED else SIGN_IN)
            label == "Edit Now" -> {
                if (!model.signedIn) {
                    go(SIGN_IN)
                } else {
                    apiCall("Checking your account…", { fetchUserInfo() }) { info ->
                        if (info.optInt(ApiField.USER_STATUS) == 2) go(WALLET_EDIT)
                        else notice("Cannot change your receiving account now.")
                    }
                }
            }
            label == "Feedback" || label.contains("feedback list") -> loadFeedbacks()
            label == "Write feedback" || label.startsWith("Please submit feedback") -> go(FEEDBACK_FORM)
            label.startsWith("Please describe your problem") -> go(FEEDBACK_PENDING)
            label == "Choose PDF" -> attachmentOptions(false)
            label == "Choose image" -> attachmentOptions(true)
            label == "Local photo album" -> pickFile("image/*")
            label == "Photograph" || label.endsWith(" photo") -> {
                if (screen == DOCUMENT_GUIDE) trackDocumentCameraTap()
                takePhoto()
            }
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
            label == "Withdraw Now" -> when (screen) {
                HOME_ACTIVE -> openAmountScreen()
                WITHDRAW, WITHDRAW_ADJUSTED -> saveSelectedAmount()
                else -> go(WITHDRAW_SUCCESS)
            }
            label == "Set Up" && screen == PERMISSION_PROMPT -> requestAppPermissions(PermissionPurpose.INITIAL) {}
            label == "Choose" && view is TextView -> form.choose(view, index, stage)
            screen in setOf(WITHDRAW, WITHDRAW_ADJUSTED) && label.matches(Regex("[0-9]+ Days")) && view is TextView ->
                form.select(view, index, "Loan term", listOf("7 Days", "20 Days", "365 Days"))
            label == "Next" -> {
                form.flushPendingChanges()
                AdjustAnalytics.identitySubmitTapped(screen)
                val valid = validate()
                trackAuthSubmission(screen, valid)
                if (valid) go(identity.next(screen))
            }
            label == "Submit" -> {
                form.flushPendingChanges()
                AdjustAnalytics.identitySubmitTapped(screen)
                submit()
            }
            label == "Cancel" -> when (screen) {
                WITHDRAW_EXIT -> {
                    trackServiceEvent("CLICK_CANCEL_BUTTON", mapOf(
                        ApiField.EVENT_PARAMETER to model.state.getString("cancelOrderPage").orEmpty()
                            .ifBlank { "RECOMMENDED_ORDER_PAGE" }
                    ))
                    model.root(model.home)
                }
                REVIEW_EXIT -> model.root(model.home)
                KYC_EXIT -> {
                    trackServiceEvent("CLICK_BUTTON", mapOf(ApiField.EVENT_PARAMETER to "BACK_OCR_CANCEL"))
                    leave()
                }
                PERMISSION_PROMPT -> skipOptionalInitialPermissions()
                else -> leave()
            }
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
            try {
                val imagePath = S3ImageUploader(this, api).uploadUri(selfie, true)
                api.saveLiveness(imagePath)
                val info = fetchUserInfo()
                val accepted = info.optInt(ApiField.USER_LIVENESS) == 1
                trackAuthSubmission(FACE_PREVIEW, accepted)
                val continuation = if (accepted) continueAfterIdentity(info) else AuthContinuation(info, null)
                Triple(imagePath, accepted, continuation)
            } catch (failure: Exception) {
                trackAuthSubmission(FACE_PREVIEW, false)
                throw failure
            }
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
        val parameters = authenticationParameters(cardScreen, imagePath, includeApplyAmount = true)
        apiCall("Submitting identity information…", {
            try {
                api.saveAuthentication(parameters)
                trackAuthSubmission(cardScreen, true)
            } catch (failure: Exception) {
                trackAuthSubmission(cardScreen, false)
                throw failure
            }
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

    private fun authenticationParameters(
        cardScreen: Screen,
        imagePath: String,
        includeApplyAmount: Boolean = false
    ): Map<String, String> {
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
        for ((phoneIndex, nameIndexInPage, relationIndex) in contactFieldIndices) {
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
        return buildMap {
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
            if (includeApplyAmount && model.state.getBoolean("amountTouched", false)) {
                put(ApiField.APPLY_AMOUNT, model.state.getLong("applyAmountValue", 0L).toString())
            }
        }.filterValues(String::isNotBlank)
    }

    private suspend fun continueAfterIdentity(info: org.json.JSONObject): AuthContinuation {
        if (info.optInt(ApiField.USER_STATUS) != 2 || info.optInt(ApiField.USER_LIVENESS) != 1) {
            return AuthContinuation(info, null)
        }
        val recommended = fetchUserInfo(recommended = true)
        val product = recommended.optJSONObject(ApiField.LOAN_PRODUCT)
            ?: return AuthContinuation(recommended, null)
        val productId = product.optString(ApiField.PRODUCT_ID)
        if (productId.isBlank()) return AuthContinuation(recommended, null)
        model.state.putString("selectedProductId", productId)
        val hasProductDetails = product.keys().asSequence()
            .filter { it != ApiField.PRODUCT_ID }
            .any { key -> product.optString(key).isNotBlank() }
        if (hasProductDetails) requireAmountRange()
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
            KYC_EXIT -> {
                trackServiceEvent("CLICK_BUTTON", mapOf(ApiField.EVENT_PARAMETER to "BACK_OCR_CONFIRM"))
                model.root(model.home)
            }
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
                }.create().showWithPopupButtonStyle()
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
                    apiCall("Uploading document photo…", {
                        val capturedPreview = withContext(Dispatchers.IO) { decodePreview(imageUri) }
                            ?: throw ApiException("Unable to read the captured photo. Please try again.")
                        try {
                            val path = S3ImageUploader(this, api).uploadUri(imageUri, true)
                            path to capturedPreview
                        } catch (failure: Exception) {
                            capturedPreview.recycle()
                            throw failure
                        }
                    }, onFailure = { deleteLocalPhoto(imageUri) }) { (path, capturedPreview) ->
                        deleteLocalPhoto(imageUri)
                        clearDocumentCapturePreview()
                        documentCapturePreview = capturedPreview
                        model.state.putString("documentImage_${owner.name}", path)
                        val changeType = when (owner) {
                            IDENTITY_CARD -> "CHANGE_IDCARD_FONT"
                            DRIVER_LICENSE -> "CHANGE_DRIVING_LICENSE_IMG"
                            VOTER_CARD -> "CHANGE_VOTER_CARD_IMG"
                            else -> null
                        }
                        if (changeType != null) trackAuthChange(owner, changeType)
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

    private fun clearDocumentCapturePreview() {
        documentCapturePreview?.takeUnless(Bitmap::isRecycled)?.recycle()
        documentCapturePreview = null
    }

    private fun deleteLocalPhoto(uri: Uri) {
        if (localPhotoUris.remove(uri)) runCatching { contentResolver.delete(uri, null, null) }
    }
}
