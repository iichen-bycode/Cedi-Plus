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
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.content.pm.ActivityInfo
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.StaticLayout
import android.text.TextWatcher
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.media.ExifInterface
import android.os.SystemClock
import android.graphics.Typeface
import com.creditcedi.access.R as AccessR
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.camera.core.CameraSelector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.creditcedi.access.AccessLayouts
import com.creditcedi.account.AccountLayouts
import com.creditcedi.account.R as AccountR
import com.creditcedi.credit.CreditLayouts
import com.creditcedi.identity.IdentityLayouts
import com.creditcedi.identity.R as IdentityR
import com.creditcedi.core.R as CoreR
import com.creditcedi.core.JourneyViewModel
import com.creditcedi.core.Screen
import com.creditcedi.core.Screen.*
import java.util.Locale
import java.util.WeakHashMap
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

class MainActivity : Activity(), LifecycleOwner {
    private val homeScreens = setOf(HOME, HOME_ACTIVE, HOME_PRODUCTS, HOME_APPLIED)
    private val orderScreens = setOf(ORDERS, ORDERS_EMPTY)
    private val kycCertificatePages = setOf(IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD, IDENTITY_FILLED)
    private val identityCardPages = setOf(IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD)
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
    private var systemStatusBarInset = 0
    private lateinit var stage: ViewGroup
    private lateinit var pageConstraints: ScreenConstraints
    private var screenDialog: Dialog? = null
    private var stickyAppBarViews = emptyList<View>()
    private var stickyAppBarBackground: View? = null
    private var footerConstraints: ScreenConstraints? = null
    private data class CachedTabPage(
        val screen: Screen,
        val stage: ViewGroup,
        val pageConstraints: ScreenConstraints,
        val footerConstraints: ScreenConstraints?,
        val stickyAppBarViews: List<View>,
        val stickyAppBarBackground: View?,
        var scrollY: Int
    )
    private data class CapturedPhoto(val uri: Uri, val preview: Bitmap)
    private data class CityRegion(val province: String, val districts: List<String>)
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
    private data class AuthContinuation(
        val info: org.json.JSONObject,
        val loanProduct: org.json.JSONObject?
    )
    private data class HomeApplicationRoute(
        val productId: String,
        val screen: Screen,
        val loanProduct: org.json.JSONObject? = null,
        val productOffer: Boolean = false
    )
    private data class LoanProductInfo(
        val productId: String,
        val name: String,
        val logo: String,
        val amount: String,
        val days: String,
        val payAmount: String,
        val verificationFee: String,
        val gstFee: String,
        val interest: String,
        val overdue: String,
        val repayAmount: String,
        val repayDate: String,
        val fullServiceFee: String
    ) {
        companion object {
            fun from(json: org.json.JSONObject) = LoanProductInfo(
                productId = json.optString(ApiField.PRODUCT_ID).trim(),
                name = json.optString(ApiField.PRODUCT_NAME).trim(),
                logo = json.optString(ApiField.PRODUCT_LOGO).trim(),
                amount = json.optString(ApiField.LOAN_AMOUNT).trim(),
                days = json.optString(ApiField.LOAN_OF_DAYS).trim(),
                payAmount = json.optString(ApiField.LOAN_PAY_AMOUNT).trim(),
                verificationFee = json.optString(ApiField.LOAN_FEE_VERIFY).trim(),
                gstFee = json.optString(ApiField.LOAN_FEE_GST).trim(),
                interest = json.optString(ApiField.LOAN_INTEREST).trim(),
                overdue = json.optString(ApiField.LOAN_OVERDUE).trim(),
                repayAmount = json.optString(ApiField.LOAN_REPAY_AMOUNT).trim(),
                repayDate = json.optString(ApiField.LOAN_REPAY_DATE).trim(),
                fullServiceFee = json.optString(ApiField.FULL_SERVICE_FEE).trim()
            )
        }
    }
    private var orderRecords: List<org.json.JSONObject> = emptyList()
    private var ordersLoading = false
    private var ordersPage = 0
    private var ordersHasMore = true
    private var ordersRequestId = 0
    private var orderListContainer: LinearLayout? = null
    private var activeOrdersFilter = OrderStatusFilter.ALL
    private var ratingPromptShown = false
    private var payFailPromptShown = false
    private val screen get() = model.current
    private var preview: Bitmap? = null
    private val documentCapturePreviews = mutableMapOf<Screen, Bitmap>()
    private val api by lazy { CediApi(this) }
    private var selfieUri: Uri? = null
    private var returnToAuthenticationAfterLiveness = true
    private var cameraOutputUri: Uri? = null
    private val activityLifecycle = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = activityLifecycle
    private var faceCameraController: CameraPreviewController? = null
    private var faceCameraContainer: FrameLayout? = null
    private var faceCameraStatus: TextView? = null
    private var faceCaptureButton: View? = null
    private var faceCaptureIcon: View? = null
    private var faceCameraReady = false
    private var faceCaptureInProgress = false
    private var documentCameraController: CameraPreviewController? = null
    private var documentCameraContainer: FrameLayout? = null
    private var documentCameraStatus: TextView? = null
    private var documentCaptureButton: View? = null
    private var documentCameraReady = false
    private var documentCaptureInProgress = false
    private var cityPickerRegions: List<CityRegion> = emptyList()
    private var cityPickerActiveProvince = ""
    private var cityProvinceRows: LinearLayout? = null
    private var cityDistrictRows: LinearLayout? = null
    private var citySearch: EditText? = null
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
    private var kycExitDialog: Dialog? = null
    private var kycExitReturnScrollY = 0
    private var pendingDocumentScreen: Screen? = null
    private var pendingDocumentCapture: CapturedPhoto? = null
    private var pendingRepaymentRefresh = false
    private var homeProducts = org.json.JSONArray()
    private var homeHasRealProducts = false
    private var homeInfoSignedIn = false
    private var homeDataLoading = false
    private var homeDataResolved = false
    private var pendingApplicationProductId: String? = null
    private val homeProductActionIds = WeakHashMap<View, String>()
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
        activityLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        model = JourneyViewModel(savedInstanceState?.getBundle("journey") ?: Bundle())
        model.state.putBoolean("signedIn", api.hasSession())
        setContentView(R.layout.activity_main)
        viewport = findViewById(R.id.viewport)
        viewport.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val offset = scrollY.toFloat()
            stickyAppBarBackground?.translationY = offset
            stickyAppBarViews.forEach { it.translationY = offset }
            loadMoreOrdersIfNeeded(scrollY)
        }
        statusBarBackground = findViewById(R.id.status_bar_background)
        val windowContent = findViewById<View>(R.id.window_content)
        windowContent.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            updateStatusBarBackgroundHeight(view)
        }
        viewport.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (pendingOrientationPage == null) updatePageWidth()
            renderPendingOrientationPage()
        }
        window.statusBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isStatusBarContrastEnforced = false
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        windowContent.setOnApplyWindowInsetsListener { view, insets ->
            systemStatusBarInset = if (android.os.Build.VERSION.SDK_INT >= 35) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, 0, bars.right, bars.bottom)
                bars.top
            } else {
                insets.systemWindowInsetTop
            }
            updateStatusBarBackgroundHeight(view)
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
        } else if (route == KYC_EXIT) {
            model.root(IDENTITY_CARD)
            model.go(KYC_EXIT)
        } else if (route != null) model.root(route)
        else if (!layoutPreview && screen in homeScreens) model.root(HOME)
        else show(screen)
        if (screen in orderScreens && model.signedIn && !layoutPreview) loadOrders(selectedOrderFilter())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("journey", model.state)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val windowContent = findViewById<View>(R.id.window_content)
        windowContent.requestApplyInsets()
        windowContent.post { updateStatusBarBackgroundHeight(windowContent) }
        viewport.post { renderPendingOrientationPage() }
    }

    private fun updateStatusBarBackgroundHeight(windowContent: View) {
        if (!::statusBarBackground.isInitialized) return

        val referenceWidth = (windowContent.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels).toFloat()
        val designStatusHeight = (44f * referenceWidth / 375f).roundToInt()
        val height = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            systemStatusBarInset
        } else {
            maxOf(systemStatusBarInset, designStatusHeight)
        }
        val params = statusBarBackground.layoutParams
        if (params.height != height) {
            params.height = height
            statusBarBackground.layoutParams = params
        }
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
        releaseFaceCamera()
        releaseDocumentCamera()
        activityLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        activityScope.cancel()
        loadingDialog?.dismiss()
        loadingDialog = null
        screenDialog?.dismiss()
        kycExitDialog?.dismiss()
        kycExitDialog = null
        clearSelfieCapture()
        clearPendingDocumentCapture()
        clearDocumentCapturePreviews()
        cameraOutputUri?.let(::deleteLocalPhoto)
        cameraOutputUri = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        activityLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        if (layoutPreview) return
        if (::model.isInitialized && screen == FACE_GUIDE &&
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            faceCameraController == null
        ) {
            ensureFaceCameraPreview()
        }
        if (::model.isInitialized && screen == DOCUMENT_GUIDE &&
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            documentCameraController == null
        ) {
            ensureDocumentCameraPreview()
        }
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

    override fun onStart() {
        super.onStart()
        activityLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onPause() {
        activityLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    override fun onStop() {
        activityLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    private fun <T> apiCall(
        message: String,
        request: suspend () -> T,
        onFailure: ((Exception) -> Unit)? = null,
        showLoadingDialog: Boolean = true,
        suppressFailureNotice: (Exception) -> Boolean = { false },
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
                } else if (!suppressFailureNotice(failure)) {
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
            model.state.putBoolean("cameraPermissionRequested", true)
            if (screen == FACE_GUIDE) {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    ensureFaceCameraPreview()
                } else {
                    setFaceCameraStatus(getString(CoreR.string.face_camera_retry_permission))
                    notice("Allow camera access to take this photo.")
                }
            } else if (screen == DOCUMENT_GUIDE) {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    ensureDocumentCameraPreview()
                } else {
                    setDocumentCameraStatus("Camera access is required. Tap here to retry.")
                    notice("Allow camera access to capture the document.")
                }
            } else if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) takePhoto()
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
        }, onFailure = { finishHomeDataLoadAfterFailure() }) { (products, userInfo, _) ->
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
        homeProducts = org.json.JSONArray().apply {
            for (index in 0 until products.length()) {
                products.optJSONObject(index)?.let(::put)
            }
        }
        homeHasRealProducts = homeProducts.length() > 0
        homeInfoSignedIn = signedIn
        if (signedIn && userInfo != null) {
            model.state.putInt(HOME_USER_STATUS, userInfo.optInt(ApiField.USER_STATUS, -1))
        } else {
            model.state.remove(HOME_USER_STATUS)
        }
        val hasOrders = signedIn && (0 until homeProducts.length()).any { index ->
            val status = homeProducts.optJSONObject(index)?.optJSONObject(ApiField.ORDER_INFO)
                ?.optInt(ApiField.LOAN_STATUS, 0) ?: 0
            status in 1..6
        }
        model.state.putBoolean("hasOrder", hasOrders)
        homeProducts.optJSONObject(0)?.optString(ApiField.PRODUCT_ID)?.takeIf(String::isNotBlank)?.let {
            model.state.putString("selectedProductId", it)
        } ?: model.state.remove("selectedProductId")
        userInfo?.let(::maybeShowServerPrompts)
        invalidateCachedHomePages()

        val destination = when {
            hasOrders -> HOME_ACTIVE
            signedIn && homeProducts.length() <= 1 -> HOME_APPLIED
            homeProducts.length() > 1 -> HOME_PRODUCTS
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
        pendingApplicationProductId?.let { productId ->
            pendingApplicationProductId = null
            val userStatus = model.state.getInt(HOME_USER_STATUS, -1)
            if (userStatus in 1..2) openApplicationForProduct(productId, userStatus)
            else notice("Unable to confirm your account status. Please try again.")
        }
    }

    private fun finishHomeDataLoadAfterFailure() {
        homeDataLoading = false
        homeDataResolved = true
        homeProducts = org.json.JSONArray()
        homeHasRealProducts = false
        model.state.remove(HOME_USER_STATUS)
        model.state.remove("selectedProductId")
        pendingApplicationProductId?.let {
            pendingApplicationProductId = null
            notice("Unable to load your account status. Please try again.")
        }
        invalidateCachedHomePages()
        if (::stage.isInitialized && screen in homeScreens) {
            forceHomePageRebuild = true
            try {
                model.root(if (model.signedIn) HOME_APPLIED else HOME)
            } finally {
                forceHomePageRebuild = false
            }
        }
    }

    private fun displayHomeProducts(): org.json.JSONArray {
        if (homeHasRealProducts) return homeProducts
        return org.json.JSONArray().put(org.json.JSONObject().apply {
            put(ApiField.PRODUCT_NAME, getString(CoreR.string.text_name))
            put(ApiField.PRODUCT_AMOUNT, getString(CoreR.string.text_ghs_100_000_ghs_3_000_000))
            put(ApiField.PRODUCT_DAYS, getString(CoreR.string.text_91_360_days))
            put(ApiField.PRODUCT_RATE, getString(CoreR.string.text_2_charge))
            put(ApiField.PRODUCT_APR, getString(CoreR.string.text_22))
        })
    }

    private fun bindHomePage(page: Screen) {
        if (page !in setOf(HOME, HOME_ACTIVE, HOME_PRODUCTS, HOME_APPLIED)) return
        when (page) {
            HOME, HOME_APPLIED -> {
                val product = displayHomeProducts().optJSONObject(0)
                setHomeBandVisibility(176f, 458f, product != null)
                bindProductCard(176f, product, showStatus = homeInfoSignedIn)
            }
            HOME_PRODUCTS -> {
                val starts = listOf(176f, 478f, 780f)
                val products = displayHomeProducts()
                starts.forEachIndexed { index, top ->
                    val product = products.optJSONObject(index)
                    setHomeBandVisibility(top, top + 282f, product != null)
                    if (product != null) bindProductCard(top, product, showStatus = homeInfoSignedIn)
                }
                if (homeHasRealProducts && homeProducts.length() == 2) compactHomeFooter(760f)
            }
            HOME_ACTIVE -> {
                val first = homeProducts.optJSONObject(0)
                bindProductCard(176f, first, showStatus = true)
                bindActiveOrderCards()
            }
            else -> Unit
        }
    }

    private fun bindProductCard(top: Float, product: org.json.JSONObject?, showStatus: Boolean) {
        if (product == null) return
        val bottom = top + 282f
        val productId = product.optString(ApiField.PRODUCT_ID).trim()
        val applyLabels = setOf(getString(CoreR.string.text_begin_application), "Apply Now", "Go Apply")
        (0 until stage.childCount).map(stage::getChildAt)
            .filter { child ->
                val childTop = dpTop(child)
                childTop >= top && childTop < bottom && (child.tag as? String) in applyLabels
            }
            .forEach { homeProductActionIds[it] = productId }
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
                        pageConstraints.setWidth(view, 126f)
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

    private fun bindOrderProductDetails() {
        if (!model.state.getBoolean(PRODUCT_APPLICATION_FLOW, false)) return
        val json = model.state.getString(LOAN_PRODUCT_JSON).orEmpty()
        val product = runCatching { LoanProductInfo.from(org.json.JSONObject(json)) }.getOrNull()
            ?: return
        if (product.productId.isBlank()) return

        val nameView = textViewsWithTag(CoreR.string.text_name).firstOrNull()
        nameView?.let {
            it.text = product.name
            it.visibility = if (product.name.isBlank()) View.GONE else View.VISIBLE
            it.contentDescription = product.name
        }
        bindOrderProductValue(
            CoreR.string.text_ghs_100_000,
            CoreR.string.text_amount,
            product.amount,
            ::formatProductAmount
        )
        bindOrderProductValue(
            CoreR.string.text_ghs_400_000_00,
            CoreR.string.text_received_amount_2db3a,
            product.payAmount,
            ::formatProductAmount
        )
        bindOrderProductValue(
            CoreR.string.text_ghs_400,
            CoreR.string.text_full_service_fee_38062,
            product.fullServiceFee,
            ::formatProductAmount
        )
        bindOrderProductValue(
            CoreR.string.text_19_10_2024,
            CoreR.string.text_repayment_date_a244a,
            product.repayDate
        )
        bindOrderProductValue(
            CoreR.string.text_7_days,
            CoreR.string.text_loan_term,
            product.days,
            ::formatProductDays
        )

        if (product.logo.isNotBlank()) addOrderProductLogo(product.logo, product.name)

        val repayTop = 443f
        if (product.repayAmount.isNotBlank()) {
            addOrderProductText(
                getString(CoreR.string.text_repayment_amount),
                40f,
                repayTop,
                150f,
                18f,
                14f,
                0xff999999.toInt(),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
            addOrderProductText(
                formatProductAmount(product.repayAmount),
                232f,
                repayTop,
                106f,
                18f,
                14f,
                0xff120d01.toInt(),
                Gravity.END or Gravity.CENTER_VERTICAL
            )
        }

        val feeDetails = listOfNotNull(
            product.verificationFee.takeIf(String::isNotBlank)?.let {
                "${getString(CoreR.string.text_verification_fee)}: $it"
            },
            product.gstFee.takeIf(String::isNotBlank)?.let { "${getString(CoreR.string.text_gst_fee)}: $it" },
            product.interest.takeIf(String::isNotBlank)?.let { "${getString(CoreR.string.text_interest_fee)}: $it" },
            product.overdue.takeIf(String::isNotBlank)?.let { "${getString(CoreR.string.text_overdue_rate)}: $it" }
        ).joinToString(" · ")
        if (feeDetails.isNotBlank()) {
            val feeTop = if (product.repayAmount.isNotBlank()) 471f else repayTop
            addOrderProductText(
                getString(CoreR.string.text_fee_details),
                40f,
                feeTop,
                80f,
                36f,
                12f,
                0xff999999.toInt(),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
            addOrderProductText(
                feeDetails,
                120f,
                feeTop,
                218f,
                36f,
                12f,
                0xff120d01.toInt(),
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                contentDescription = feeDetails
            }
        }
    }

    private fun bindOrderProductValue(
        valueTag: Int,
        labelTag: Int,
        value: String,
        format: (String) -> String = { it }
    ) {
        val visible = value.isNotBlank()
        textViewsWithTag(valueTag).forEach { view ->
            view.visibility = if (visible) View.VISIBLE else View.GONE
            if (visible) view.text = format(value)
        }
        textViewsWithTag(labelTag).forEach { view ->
            view.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun textViewsWithTag(tag: Int): List<TextView> {
        val text = getString(tag)
        return (0 until stage.childCount).map(stage::getChildAt)
            .filterIsInstance<TextView>()
            .filter { it.tag == text }
    }

    private fun addOrderProductText(
        text: String,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        sizeSp: Float,
        color: Int,
        gravity: Int
    ): TextView = TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = sizeSp
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        this.gravity = gravity
        pageConstraints.add(this, left, top, width, height)
    }

    private fun addOrderProductLogo(url: String, productName: String) {
        val image = ImageView(this).apply {
            tag = ORDER_PRODUCT_LOGO_TAG
            contentDescription = productName.takeIf(String::isNotBlank)?.let { "$it logo" } ?: "Product logo"
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        pageConstraints.add(image, left = 141f, top = 163f, width = 40f, height = 40f)
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
        stage = cached.stage
        pageConstraints = cached.pageConstraints
        footerConstraints = cached.footerConstraints
        orderListContainer = stage.findViewWithTag(ORDERS_LIST_TAG)
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
        if (page == ORDERS || page == ORDERS_EMPTY) configureOrderTabs(selectedOrderFilter())
        updatePageWidth()
        renderedScreen = page
        val targetScroll = cached.scrollY
        viewport.post {
            if (renderedScreen != page || stage !== cached.stage) return@post
            viewport.scrollTo(0, targetScroll)
            stickyAppBarViews.forEach { it.translationY = targetScroll.toFloat() }
            stickyAppBarBackground?.translationY = targetScroll.toFloat()
        }
    }

    private fun invalidateCachedHomePages() {
        cachedTabPages.keys.removeAll(homeScreens)
    }

    private fun show(page: Screen) {
        val wasShowingDialog = screenDialog != null
        screenDialog?.dismiss()
        screenDialog = null
        if (page in popupPages) {
            if (renderedScreen == null) {
                val previewBackground = if (layoutPreview && page == ATTACHMENT_SOURCE) {
                    FEEDBACK_FILLED
                } else null
                renderPage(previewBackground ?: model.previousPage(popupPages.keys) ?: popupPages.getValue(page))
            }
            if (page == RATING || page == RATING_CHANGED) {
                showRatingPanel(page, onCancel = ::leave) { score, comment ->
                    if (layoutPreview) {
                        if (score in 1..3) model.go(RATING_FEEDBACK, replace = true) else leave()
                    } else {
                        apiCall("Saving your feedback…", { api.submitRating("1", score, comment) }) {
                            if (score in 1..3) model.go(RATING_FEEDBACK, replace = true)
                            else {
                                leave()
                                openStoreRating()
                            }
                        }
                    }
                }
            } else {
                showPanel(page, onCancel = ::leave) { _, panel ->
                    bindPanelActions(panel) { label, view -> handle(label, view, panel.indexOfChild(view)) }
                }
            }
            return
        }
        if (wasShowingDialog && page == renderedScreen) return
        renderPage(page)
    }

    private fun renderPage(page: Screen) {
        if (page == KYC_EXIT) {
            showKycExitDialog()
            return
        }
        if (page != FACE_GUIDE || renderedScreen == FACE_GUIDE) releaseFaceCamera()
        if (page != DOCUMENT_GUIDE || renderedScreen == DOCUMENT_GUIDE) releaseDocumentCamera()
        if (forceHomePageRebuild) {
            renderedScreen?.takeIf { it in homeScreens }?.let(cachedTabPages::remove)
        } else {
            rememberRenderedTabPage()
        }
        val isLandscapePage = page == DOCUMENT_GUIDE
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
        form.beginScreen()
        if (!layoutPreview) {
            trackAuthScreenTransition(page)
            if (page == SIGN_IN) trackServiceEvent("OPEN_LOGIN_PAGE")
            if (page == PRIVACY) trackServiceEvent("CLICK_PRIVACY_BUTTO")
        }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(viewport.windowToken, 0)
        stickyAppBarViews = emptyList()
        stickyAppBarBackground = null
        viewport.removeAllViews()
        orderListContainer = null
        stage = layoutInflater.inflate(pages.getValue(page), viewport, false) as ViewGroup
        pageConstraints = ScreenConstraints(stage)
        updateIdentityDocumentInstruction(page)
        if (!layoutPreview) {
            bindHomePage(page)
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
            if (label == ORDERS_LIST_TAG) continue
            when (view) {
                is EditText -> {
                    fields += view
                    // Preserve the keys used by saved login forms before the layout update.
                    val fieldIndex = if (page == SIGN_IN) 6 + fields.size else i
                    val fixedPrefix = if (
                        page == IDENTITY_CARD && view.id == IdentityR.id.identity_card_number_input
                    ) "GHA-" else null
                    form.input(view, page, fieldIndex, fixedPrefix)
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
        if (page in kycCertificatePages) bindKycCertificateType(page, stage)
        if (page == ORDERS && !layoutPreview) {
            hideStaticOrderCardViews()
            createOrderListContainer()
        }
        val footer = findViewById<FrameLayout>(R.id.footer)
        footer.removeAllViews()
        val footerHeight = when (page) {
            PRIVACY -> 131
            HOME, HOME_APPLIED, HOME_PRODUCTS, HOME_ACTIVE, ELIGIBLE, ORDERS, ORDERS_EMPTY,
            PROFILE, PROFILE_GUEST -> 74
            else -> 0
        }
        footerConstraints = if (footerHeight > 0) pageConstraints.detachFooter(footerHeight) else null
        if (page == PRIVACY) pageConstraints.setContentHeight(0f)
        footerConstraints?.let { footer.addView(it.layout) }
        if (page == DOCUMENT_GUIDE) {
            viewport.addView(stage, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ))
        } else {
            viewport.addView(stage)
        }
        if (page == ORDER_DETAIL && !layoutPreview) bindOrderProductDetails()
        updatePageWidth()
        if (page == CITY) bindCityPicker()
        if (page == FACE_GUIDE && !layoutPreview) configureFaceCamera(stage)
        else if (page == FACE_GUIDE) {
            stage.findViewById<FrameLayout>(IdentityR.id.face_camera_preview_container)
                ?.setBackgroundColor(Color.WHITE)
        }
        if (page == DOCUMENT_GUIDE && !layoutPreview) configureDocumentCamera(stage)
        if (page in setOf(IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD)) {
            val bitmap = documentCapturePreviews[page]?.takeUnless(Bitmap::isRecycled)
            if (bitmap != null) {
                val anchorId = when (page) {
                    IDENTITY_CARD -> IdentityR.id.identity_card_anchor_11
                    DRIVER_LICENSE -> IdentityR.id.driver_license_anchor_13
                    else -> IdentityR.id.voter_card_anchor_13
                }
                stage.findViewById<View>(anchorId)?.let { anchor ->
                    addBitmapPreviewInFrame(
                        anchor,
                        bitmap,
                        "Captured identity document photo",
                        drawBorder = true
                    ) {
                        pendingDocumentScreen = page
                        go(DOCUMENT_GUIDE)
                    }
                }
            }
        }
        if (page == IDENTITY_FILLED) {
            val cardScreen = runCatching {
                Screen.valueOf(model.state.getString("authenticationCardScreen").orEmpty())
            }.getOrDefault(IDENTITY_CARD)
            documentCapturePreviews[cardScreen]?.takeUnless(Bitmap::isRecycled)?.let { bitmap ->
                stage.findViewById<View>(IdentityR.id.identity_filled_document_preview_frame)?.let { anchor ->
                    addBitmapPreviewInFrame(anchor, bitmap, "Captured identity document photo", onClick = null)
                }
            }
        }
        if (page == FEEDBACK_FILLED) configureFeedbackFilledAttachments()
        configureStickyAppBar()
        if (page == ORDERS || page == ORDERS_EMPTY) {
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
        if (page == WELCOME) {
            stage.setOnClickListener { startJourney() }
            if (!layoutPreview) {
                stage.postDelayed({ if (!isFinishing && !isDestroyed && screen == WELCOME) startJourney() }, 1500)
            }
        }
        if (page == REVIEW_COUNTDOWN) countdown(60)
        if (page == FACE_PREVIEW && preview != null) {
            stage.findViewById<View>(IdentityR.id.face_preview_photo_frame)?.let { anchor ->
                addBitmapPreviewInFrame(anchor, preview!!, "Selfie photo preview", onClick = null)
            }
        }
        if (page == IDENTITY_FILLED) {
            preview?.takeUnless(Bitmap::isRecycled)?.let { selfie ->
                stage.findViewById<View>(IdentityR.id.identity_filled_anchor_31)?.let { anchor ->
                    addBitmapPreviewInFrame(anchor, selfie, "Verified selfie photo", onClick = null)
                }
                stage.findViewById<View>(IdentityR.id.identity_filled_selfie_success_background)?.bringToFront()
                stage.findViewById<View>(IdentityR.id.identity_filled_selfie_success_icon)?.bringToFront()
            }
            if (!model.state.getString("livenessImage").isNullOrBlank()) {
                listOfNotNull(
                    stage.findViewWithTag<View>("Capture selfie"),
                    stage.findViewWithTag<View>("Upload a clear selfie")
                ).forEach { view ->
                    view.isEnabled = false
                    view.isClickable = false
                    view.isFocusable = false
                    view.setOnClickListener(null)
                }
            }
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
        if (screen == DOCUMENT_GUIDE && viewport.height > 0) {
            contentWidth = pageConstraints.widthFittingHeight(viewportWidth, viewport.height)
            val params = stage.layoutParams
            if (params.width != contentWidth) {
                params.width = contentWidth
                stage.layoutParams = params
            }
        }
        pageConstraints.updateWidth(contentWidth)
        if (screen in kycCertificatePages) {
            stage.findViewById<TextView>(IdentityR.id.kyc_certificate_type_value)
                ?.disableKycChoiceAutoSize()
            stage.findViewById<TextView>(IdentityR.id.kyc_birth_date_value)
                ?.disableKycChoiceAutoSize()
            stage.findViewById<TextView>(IdentityR.id.kyc_gender_value)
                ?.disableKycChoiceAutoSize()
            resizeKycGenderChoice(screen, stage)
        }
        footerConstraints?.updateWidth(viewportWidth)
    }

    private fun TextView.disableKycChoiceAutoSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
        }
        isSingleLine = false
        maxLines = Int.MAX_VALUE
        ellipsize = null
    }

    private fun selectedCertificateScreen(): Screen {
        val selected = runCatching {
            Screen.valueOf(model.state.getString("authenticationCardScreen").orEmpty())
        }.getOrNull()?.takeIf { it in identityCardPages } ?: IDENTITY_CARD
        model.state.putString("authenticationCardScreen", selected.name)
        return selected
    }

    private fun bindKycCertificateType(page: Screen, owner: ViewGroup) {
        if (page !in kycCertificatePages) return
        val value = owner.findViewById<TextView>(IdentityR.id.kyc_certificate_type_value) ?: return
        value.text = when (selectedCertificateScreen()) {
            DRIVER_LICENSE -> "Driving License"
            VOTER_CARD -> "Voter Card"
            else -> "ID Card"
        }
        value.setTextColor(Color.rgb(18, 13, 1))
        val placeholder = getString(CoreR.string.text_choose)
        listOf(
            IdentityR.id.kyc_birth_date_value,
            IdentityR.id.kyc_gender_value
        ).mapNotNull { owner.findViewById<TextView>(it) }.forEach { choice ->
            choice.setTextColor(
                if (choice.text.toString() == placeholder) Color.rgb(153, 153, 153)
                else Color.rgb(18, 13, 1)
            )
        }
    }

    private fun resizeKycGenderChoice(page: Screen, owner: ViewGroup) {
        if (page !in kycCertificatePages) return
        val value = owner.findViewById<TextView>(IdentityR.id.kyc_gender_value) ?: return
        val rowId = when (page) {
            IDENTITY_CARD -> IdentityR.id.identity_card_anchor_25
            DRIVER_LICENSE -> IdentityR.id.driver_license_anchor_27
            VOTER_CARD -> IdentityR.id.voter_card_anchor_27
            else -> IdentityR.id.identity_filled_anchor_27
        }
        val row = owner.findViewById<View>(rowId) ?: return
        val arrow = owner.findViewById<View>(IdentityR.id.kyc_gender_arrow)
        value.disableKycChoiceAutoSize()
        if (value.text.toString() != getString(CoreR.string.text_choose)) {
            value.setTextColor(Color.rgb(18, 13, 1))
        }
        value.post {
            if (owner !== stage || screen != page || value.width <= 0) return@post
            val density = resources.displayMetrics.density
            val textLayout = if (value.text.isEmpty()) null else StaticLayout.Builder
                .obtain(value.text, 0, value.text.length, value.paint, value.width)
                .setIncludePad(false)
                .build()
            val textHeight = maxOf(
                18f,
                (textLayout?.height?.div(density) ?: 18f).roundToInt().toFloat()
            )
            val rowHeight = maxOf(40f, textHeight + 22f)
            val rowParams = row.layoutParams as? ConstraintLayout.LayoutParams ?: return@post
            val oldRowHeight = rowParams.dimensionRatio.substringAfterLast(':').toFloatOrNull() ?: 40f
            val valueParams = value.layoutParams as? ConstraintLayout.LayoutParams ?: return@post
            valueParams.dimensionRatio = "H,283:$textHeight"
            value.layoutParams = valueParams
            rowParams.dimensionRatio = "H,335:$rowHeight"
            row.layoutParams = rowParams
            val rowDelta = rowHeight - oldRowHeight
            if (rowDelta != 0f) arrow?.let {
                pageConstraints.moveTo(it, pageConstraints.top(it) + rowDelta / 2f)
            }
            pageConstraints.setContentHeight(pageConstraints.referenceHeight + (rowHeight - 40f))
        }
    }

    private fun addBitmapPreviewInFrame(
        anchor: View,
        bitmap: Bitmap,
        description: String,
        drawBorder: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        val cornerRadius = 9f * resources.displayMetrics.density
        val frame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                this.cornerRadius = cornerRadius
            }
            clipToOutline = true
            contentDescription = description
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val photo = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(photo, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        if (drawBorder) {
            val border = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke((resources.displayMetrics.density).roundToInt().coerceAtLeast(1), Color.rgb(227, 227, 227))
                    this.cornerRadius = cornerRadius
                }
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            frame.addView(border, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        if (onClick != null) {
            frame.isClickable = true
            frame.isFocusable = true
            frame.setOnClickListener { onClick() }
        }
        pageConstraints.add(
            frame,
            left = pageConstraints.left(anchor),
            top = pageConstraints.top(anchor),
            width = pageConstraints.width(anchor),
            height = pageConstraints.height(anchor)
        )
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
        val isLandscapePage = page == DOCUMENT_GUIDE
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

    private fun configureFeedbackFilledAttachments() {
        val pdfHeading = getString(CoreR.string.text_please_upload_a_pdf_file)
        val imageHeading = getString(CoreR.string.text_please_upload_the_problematic_image)
        val submitLabel = getString(CoreR.string.text_submit)
        val submitBackground = stage.findViewById<View>(AccountR.id.feedback_filled_submit_background)
        val pdfHeadingView = (0 until stage.childCount).map(stage::getChildAt)
            .firstOrNull { it.tag?.toString() == pdfHeading }
        val imageHeadingView = (0 until stage.childCount).map(stage::getChildAt)
            .firstOrNull { it.tag?.toString() == imageHeading }
        val submitLabelView = (0 until stage.childCount).map(stage::getChildAt)
            .firstOrNull { it.tag?.toString() == submitLabel }
        val keepVisibleTags = setOf(pdfHeading, imageHeading, submitLabel)

        (0 until stage.childCount).map(stage::getChildAt).forEach { child ->
            val tag = child.tag?.toString()
            val top = pageConstraints.top(child)
            if (layoutPreview && (tag == "Choose PDF" || tag == "Choose image")) {
                child.visibility = View.GONE
            } else if (top >= 617f && top < 1086f && tag !in keepVisibleTags && child !== submitBackground) {
                child.visibility = View.GONE
            }
        }

        val columns = listOf(20f, 106f, 193f, 279f)
        val pdfFiles = if (layoutPreview) {
            List(intent.getIntExtra("preview_pdf_count", 0).coerceIn(0, 9)) { "preview-${it + 1}.pdf" }
        } else feedback.attachments(images = false)
        val imageFiles = if (layoutPreview) {
            List(intent.getIntExtra("preview_image_count", 0).coerceIn(0, 9)) { "preview-${it + 1}.jpg" }
        } else feedback.attachments(images = true)

        val cardWidth = 76f
        val cardHeight = 44f
        val rowStride = 60f
        val headingHeight = 19f
        val headingToGridGap = 8f
        val sectionGap = 34f
        val pdfHeadingTop = 590f
        val pdfGridTop = pdfHeadingTop + headingHeight + headingToGridGap
        val pdfRows = (pdfFiles.size + columns.size - 1) / columns.size
        val pdfGridBottom = if (pdfRows == 0) pdfHeadingTop + headingHeight
            else pdfGridTop + (pdfRows - 1) * rowStride + cardHeight
        val imageHeadingTop = pdfGridBottom + sectionGap
        val imageGridTop = imageHeadingTop + headingHeight + headingToGridGap
        val imageRows = (imageFiles.size + columns.size - 1) / columns.size
        val imageGridBottom = if (imageRows == 0) imageHeadingTop + headingHeight
            else imageGridTop + (imageRows - 1) * rowStride + cardHeight
        val submitTop = imageGridBottom + 20f

        pdfHeadingView?.let { pageConstraints.moveTo(it, pdfHeadingTop) }
        imageHeadingView?.let { pageConstraints.moveTo(it, imageHeadingTop) }
        submitLabelView?.let { pageConstraints.moveTo(it, submitTop + 11f) }
        submitBackground?.let { pageConstraints.moveTo(it, submitTop) }

        fun addImage(resourceId: Int, left: Float, top: Float, width: Float, height: Float): ImageView =
            ImageView(this).apply {
                setImageResource(resourceId)
                scaleType = ImageView.ScaleType.FIT_XY
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                pageConstraints.add(this, left, top, width, height)
            }

        pdfFiles.forEachIndexed { index, file ->
            val left = columns[index % columns.size]
            val top = pdfGridTop + (index / columns.size) * rowStride
            addImage(CoreR.drawable.art_8d77b4cefcbc, left, top, cardWidth, cardHeight)
            addImage(CoreR.drawable.art_725fcdad14de, left + 10f, top + 6f, 16f, 18f)
            addImage(CoreR.drawable.art_dac5ffb142ed, left + 60f, top, 16f, 16f)

            val filename = TextView(this).apply {
                text = if (layoutPreview) "XXXXXX" else file.substringAfterLast('/').substringBeforeLast('.').takeLast(6)
                setTextColor(Color.rgb(153, 153, 153))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                includeFontPadding = false
                maxLines = 1
                setPadding(0, 0, 0, 0)
            }
            pageConstraints.add(filename, left + 10f, top + 24f, 46f, 14f)
        }

        imageFiles.forEachIndexed { index, _ ->
            val left = columns[index % columns.size]
            val top = imageGridTop + (index / columns.size) * rowStride
            addImage(CoreR.drawable.art_acbf1c1ef515, left, top, cardWidth, cardHeight)
        }
        pageConstraints.setContentHeight(submitTop + 60f)
    }

    private fun configureStickyAppBar() {
        if (screen in setOf(SIGN_IN, DOCUMENT_GUIDE)) return
        stage.findViewById<View>(AccountR.id.about_header)?.let { header ->
            header.findViewById<View>(AccountR.id.about_back).setOnClickListener { onBackPressed() }
            stickyAppBarViews = listOf(header)
            return
        }
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
            val color = when {
                screen == FEEDBACK_FILLED -> Color.rgb(255, 249, 230)
                hasArtworkBehindHeader -> Color.TRANSPARENT
                else -> Color.WHITE
            }
            setBackgroundColor(color)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // Keep the header fixed with the page's own backdrop; no divider or elevation is added.
        pageConstraints.add(background, left = 0f, top = appBarTop, width = designWidth, height = appBarHeight)
        pinned.forEach(stage::bringChildToFront)
        stickyAppBarViews = pinned
        stickyAppBarBackground = background
    }

    private fun updateIdentityDocumentInstruction(page: Screen) {
        val certificate = when (page) {
            IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD -> page
            IDENTITY_FILLED -> runCatching {
                Screen.valueOf(model.state.getString("authenticationCardScreen").orEmpty())
            }.getOrDefault(IDENTITY_CARD)
            else -> return
        }
        val textResource = when (certificate) {
            DRIVER_LICENSE -> CoreR.string.text_upload_a_photo_of_the_front_of_your_driver_s_licen
            VOTER_CARD -> CoreR.string.text_upload_a_photo_of_the_front_of_your_voter_card
            else -> CoreR.string.text_upload_a_photo_of_the_front_of_your_id_card
        }
        stage.findViewById<TextView>(IdentityR.id.identity_document_instruction)?.setText(textResource)
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
    private fun go(page: Screen, replace: Boolean = false) = model.go(page, replace)
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
        model.state.putString("authOptions.regions", info.optJSONArray(ApiField.REGIONS)?.toString().orEmpty())
        return rangeValid
    }

    private fun authenticationRegions(): List<CityRegion> = runCatching {
        val regions = org.json.JSONArray(model.state.getString("authOptions.regions").orEmpty())
        buildList {
            for (index in 0 until regions.length()) {
                val region = regions.optJSONObject(index) ?: continue
                val province = region.optString(ApiField.PROVINCE).trim()
                if (province.isBlank()) continue
                val districtsJson = region.optJSONArray(ApiField.REGION_DISTRICTS) ?: org.json.JSONArray()
                val districts = (0 until districtsJson.length())
                    .map { districtsJson.optString(it).trim() }
                    .filter(String::isNotBlank)
                    .distinct()
                add(CityRegion(province, districts))
            }
        }.distinctBy(CityRegion::province)
    }.getOrDefault(emptyList())

    private fun openCityPicker() {
        val regions = authenticationRegions()
        if (regions.isNotEmpty()) {
            enterCityPicker(regions)
            return
        }
        notice("Location options are unavailable. Please try again later.")
    }

    private fun enterCityPicker(regions: List<CityRegion>) {
        val savedProvince = model.state.getString("authentication.province").orEmpty()
        val rememberedProvince = model.state.getString("authentication.activeProvince").orEmpty()
        val activeProvince = listOf(savedProvince, rememberedProvince)
            .firstOrNull { candidate -> regions.any { it.province == candidate } }
            ?: regions.first().province
        model.state.putString("authentication.activeProvince", activeProvince)
        go(CITY)
    }

    private fun bindCityPicker() {
        cityPickerRegions = authenticationRegions()
        cityPickerActiveProvince = model.state.getString("authentication.activeProvince").orEmpty()
            .takeIf { province -> cityPickerRegions.any { it.province == province } }
            ?: cityPickerRegions.firstOrNull()?.province.orEmpty()

        val marker = stage.findViewById<View>(IdentityR.id.city_anchor_23)
        cityPickerListTop = marker?.let(pageConstraints::top) ?: 116f
        marker?.visibility = View.INVISIBLE

        val placeholder = getString(CoreR.string.text_aaaaaaaaaaaaaaa)
        (0 until stage.childCount).map(stage::getChildAt)
            .filterIsInstance<TextView>()
            .filter { it.text.toString() == placeholder }
            .forEach { it.visibility = View.GONE }
        stage.findViewById<View>(IdentityR.id.city_selected_check_template)?.visibility = View.GONE

        val provinceScroll = createCityList(left = 20f, top = cityPickerListTop, width = 165f)
        val districtScroll = createCityList(left = 185f, top = cityPickerListTop, width = 170f)
        cityProvinceRows = provinceScroll.second
        cityDistrictRows = districtScroll.second

        citySearch = stage.findViewById<EditText>(IdentityR.id.city_search)?.also { search ->
            search.setText("")
            search.contentDescription = "Search provinces and districts"
            search.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            search.setOnEditorActionListener { field, _, _ ->
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(field.windowToken, 0)
                field.clearFocus()
                true
            }
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderCityPickerRows(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        stage.findViewById<View>(IdentityR.id.city_search_icon)?.setOnClickListener {
            citySearch?.let { search ->
                search.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        renderCityPickerRows("")
    }

    private var cityPickerListTop = 116f

    private fun createCityList(left: Float, top: Float, width: Float): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rows, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        pageConstraints.add(scroll, left, top, width, (768f - top - 20f).coerceAtLeast(100f))
        return scroll to rows
    }

    private fun renderCityPickerRows(query: String) {
        val provinceRows = cityProvinceRows ?: return
        val districtRows = cityDistrictRows ?: return
        val filter = query.trim()
        val visibleProvinces = cityPickerRegions.filter { region ->
            filter.isBlank() || region.province.contains(filter, ignoreCase = true) ||
                region.districts.any { it.contains(filter, ignoreCase = true) }
        }
        val activeRegion = cityPickerRegions.firstOrNull { it.province == cityPickerActiveProvince }
        provinceRows.removeAllViews()
        districtRows.removeAllViews()

        if (visibleProvinces.isEmpty()) {
            addCityPickerEmptyRow(provinceRows, "No matching locations")
        } else {
            visibleProvinces.forEach { region ->
                val selected = region.province == cityPickerActiveProvince
                addCityPickerRow(
                    provinceRows,
                    region.province,
                    selected,
                    icon = if (selected) CoreR.drawable.art_48d2e428ed5e else CoreR.drawable.art_cef9729d96b6,
                    iconWidth = 6,
                    iconHeight = 12,
                    showIcon = true,
                    showProvinceMarker = true,
                    description = if (selected) "${region.province}, selected" else region.province
                ) {
                    cityPickerActiveProvince = region.province
                    model.state.putString("authentication.activeProvince", region.province)
                    renderCityPickerRows(citySearch?.text?.toString().orEmpty())
                    cityProvinceScrollTo(visibleProvinces.indexOf(region))
                }
            }
        }

        if (activeRegion == null || activeRegion.districts.isEmpty()) {
            addCityPickerEmptyRow(districtRows, "No districts available")
        } else {
            val districtFilter = filter.takeUnless { activeRegion.province.contains(it, ignoreCase = true) }.orEmpty()
            val matchingDistricts = activeRegion.districts.filter {
                it.contains(districtFilter, ignoreCase = true)
            }
            if (matchingDistricts.isEmpty()) {
                addCityPickerEmptyRow(districtRows, "No matching districts")
            } else {
                matchingDistricts.forEach { district ->
                    val selected = model.state.getString("authentication.district").orEmpty() == district &&
                        model.state.getString("authentication.province").orEmpty() == activeRegion.province
                    addCityPickerRow(
                        districtRows,
                        district,
                        selected,
                        icon = CoreR.drawable.art_e896112ebee6,
                        iconWidth = 10,
                        iconHeight = 10,
                        showIcon = selected,
                        showProvinceMarker = false,
                        description = if (selected) "$district, selected" else district
                    ) {
                        model.state.putString("authentication.province", activeRegion.province)
                        model.state.putString("authentication.district", district)
                        model.edit("PERSONAL_INFO_40", "${activeRegion.province}, $district")
                        leave()
                    }
                }
            }
        }

    }

    private fun cityProvinceScrollTo(index: Int) {
        cityProvinceRows?.parent?.let { parent ->
            (parent as? View)?.post { (parent as? ScrollView)?.smoothScrollTo(0, index * dp(48)) }
        }
    }

    private fun addCityPickerRow(
        parent: LinearLayout,
        text: String,
        selected: Boolean,
        icon: Int,
        iconWidth: Int,
        iconHeight: Int,
        showIcon: Boolean,
        showProvinceMarker: Boolean,
        description: String,
        onClick: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(if (showProvinceMarker) 0 else dp(10), 0, dp(20), 0)
            minimumHeight = dp(48)
            isFocusable = true
            isClickable = true
            isSelected = selected
            setBackgroundResource(android.R.drawable.list_selector_background)
            contentDescription = description
            setOnClickListener { onClick() }
        }
        if (showProvinceMarker) {
            val marker = View(this).apply {
                setBackgroundResource(CoreR.drawable.surface_4bb301ba4c58)
                visibility = if (selected) View.VISIBLE else View.INVISIBLE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(marker, LinearLayout.LayoutParams(dp(4), dp(16)).apply {
                marginEnd = dp(6)
            })
        }
        val label = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(if (selected) Color.rgb(18, 13, 1) else Color.rgb(110, 110, 110))
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val trailingIcon = ImageView(this).apply {
            setImageResource(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = if (showIcon) View.VISIBLE else View.INVISIBLE
        }
        val iconParams = LinearLayout.LayoutParams(dp(iconWidth), dp(iconHeight)).apply {
            marginStart = dp(8)
        }
        row.addView(trailingIcon, iconParams)
        parent.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48)
        ))
    }

    private fun addCityPickerEmptyRow(parent: LinearLayout, message: String) {
        val empty = TextView(this).apply {
            text = message
            textSize = 13f
            setTextColor(Color.rgb(153, 153, 153))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(8), 0)
            minHeight = dp(48)
            isEnabled = false
        }
        parent.addView(empty, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48)
        ))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

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
            11 -> savedAuthOptions("salaryType")
            else -> null
        }
        WALLET_SETUP, WALLET_EDIT -> if (label.contains("Wallet", ignoreCase = true)) savedAuthOptions("walletType") else null
        CONTACTS -> if (contactFieldIndices.any { it.third == index }) {
            savedAuthOptions("relation").ifEmpty { listOf("Parent", "Spouse", "Sibling", "Friend") }
        } else null
        IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD, IDENTITY_FILLED ->
            if (label.contains("Gender", ignoreCase = true)) savedAuthOptions("gender") else null
        else -> null
    }

    private fun onAuthChoiceSelected(page: Screen, index: Int, value: String, owner: android.view.ViewGroup) {
        if (page in kycCertificatePages) {
            resizeKycGenderChoice(page, owner)
            return
        }
        if (page != PERSONAL_INFO) return
        val position = personalInfoChoiceOrder.indexOf(index)
        if (position < 0) return
        val nextIndex = personalInfoChoiceOrder.drop(position + 1)
            .firstOrNull { model.field("PERSONAL_INFO_$it").isBlank() } ?: return
        val targetId = personalInfoChoiceViewIds[nextIndex] ?: return
        val target = owner.findViewById<TextView>(targetId) ?: return
        target.post {
            if (screen == PERSONAL_INFO && model.field("PERSONAL_INFO_$nextIndex").isBlank()) {
                if (nextIndex == 40) openCityPicker() else form.choose(target, nextIndex, owner)
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
        model.edit("PERSONAL_INFO_11", info.optString(ApiField.PAY_CYCLE))
        model.edit("PERSONAL_INFO_41", info.optString(ApiField.EMAIL))
        model.edit("PERSONAL_INFO_42", info.optString(ApiField.ADDRESS))
        model.edit("PERSONAL_INFO_43", info.optString(ApiField.MESSAGING_ACCOUNT))
        info.optString(ApiField.PROVINCE).trim().takeIf(String::isNotBlank)?.let {
            model.state.putString("authentication.province", it)
        }
        info.optString(ApiField.DISTRICT).trim().takeIf(String::isNotBlank)?.let {
            model.state.putString("authentication.district", it)
        }
        model.edit("PERSONAL_INFO_40", listOf(
            model.state.getString("authentication.province").orEmpty(),
            model.state.getString("authentication.district").orEmpty()
        ).filter(String::isNotBlank).joinToString(", "))

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
            onFailure = { finishHomeDataLoadAfterFailure() }) { info ->
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
        showRatingPanel(RATING, onCancel = {
            apiCall("Saving your feedback…", { api.submitRating("2") }) {}
        }) { score, comment ->
            apiCall("Saving your feedback…", { api.submitRating("1", score, comment) }) {
                if (score in 1..3) {
                    showPanel(RATING_FEEDBACK, onCancel = {}) { dialog, panel ->
                        panel.findViewById<TextView>(CreditR.id.rating_followup_message).text = lowScoreTips
                        bindPanelActions(panel) { _, _ -> dialog.dismiss() }
                    }
                } else {
                    openStoreRating()
                }
            }
        }
    }

    private val popupPages = mapOf(
        WITHDRAW_SUCCESS to WITHDRAW, WITHDRAW_EXIT to WITHDRAW, LUCKY to HOME,
        PERMISSION_PROMPT to WITHDRAW, RATING to WITHDRAW, RATING_CHANGED to WITHDRAW,
        RATING_FEEDBACK to WITHDRAW, REVIEW_EXIT to REVIEW_COUNTDOWN,
        LOGOUT to PROFILE, ORDER_SUCCESS to ORDER_DETAIL, EXTENSION_CONFIRM to REPAYMENT,
        ATTACHMENT_DELETE to FEEDBACK_FORM, ATTACHMENT_SOURCE to FEEDBACK_FORM,
        SELECT_ORDER to FEEDBACK_FORM, FEEDBACK_SUCCESS to FEEDBACK_FORM,
        FACE_FAILED to FACE_GUIDE, BIRTHDAY to IDENTITY_CARD, EDUCATION to IDENTITY_CARD
    )

    private fun showPanel(
        page: Screen,
        onCancel: () -> Unit,
        configure: (Dialog, ViewGroup) -> Unit
    ) {
        if (isFinishing || isDestroyed) return
        screenDialog?.dismiss()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = page != SELECT_ORDER
        val panel = layoutInflater.inflate(pages.getValue(page), scroll, false) as ViewGroup
        val constraints = ScreenConstraints(panel)
        constraints.updateWidth(viewport.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels)
        scroll.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> constraints.updateWidth(view.width) }
        if (page == SELECT_ORDER) {
            scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                panel.findViewById<View>(AccountR.id.select_order_close_icon)?.translationY = scrollY.toFloat()
                panel.findViewById<View>(AccountR.id.select_order_close_button)?.translationY = scrollY.toFloat()
            }
        }
        scroll.addView(panel)
        dialog.setContentView(scroll)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnCancelListener { onCancel() }
        dialog.setOnDismissListener { if (screenDialog === dialog) screenDialog = null }
        configure(dialog, panel)
        dialog.setOnShowListener {
            panel.requestFocus()
            panel.expandActionHitAreas()
        }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.4f)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            if (page == ATTACHMENT_SOURCE || page == ATTACHMENT_DELETE || page == FEEDBACK_SUCCESS) {
                attributes = attributes.apply {
                    gravity = Gravity.CENTER
                    y = dp(-19)
                }
            }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        screenDialog = dialog
        dialog.show()
    }

    private fun bindPanelActions(panel: ViewGroup, action: (String, View) -> Unit) {
        val actions = setOf("Back", "Cancel", "Confirm", "Submit", "Apply Now", "Set Up",
            "Reshoot", "Local photo album", "Photograph")
        for (index in 0 until panel.childCount) {
            val child = panel.getChildAt(index)
            val label = child.tag as? String ?: continue
            if (label in actions || label.startsWith("Order ")) child.setOnClickListener { action(label, child) }
        }
    }

    private fun showRatingPanel(page: Screen, onCancel: () -> Unit, onSubmit: (Int, String) -> Unit) {
        showPanel(page, onCancel) { dialog, panel ->
            val slider = panel.findViewById<SeekBar>(CreditR.id.rating_slider)
            val marker = panel.findViewById<View>(CreditR.id.rating_marker)
            val value = panel.findViewById<TextView>(CreditR.id.rating_value)
            val comment = panel.findViewById<EditText>(CreditR.id.rating_comment)
            val track = panel.findViewById<View>(CreditR.id.rating_track)
            val filledTrack = panel.findViewById<View>(CreditR.id.rating_progress)
            val ticks = (0 until panel.childCount).map(panel::getChildAt).filter { it.tag == "rating_tick" }
            if (layoutPreview && page == RATING_CHANGED) comment.setText(comment.hint)
            val initial = if (page == RATING_CHANGED) 2 else 4
            // The first mark is the start of the track; the next five are scores 1–5.
            val stops = floatArrayOf(56f, 110f, 165f, 219f, 267f)
            value.gravity = Gravity.CENTER
            slider.progress = initial
            fun updateScore(progress: Int) {
                value.text = (progress + 1).toString()
                if (track.width == 0 || filledTrack.width == 0) return
                fun offsetFor(index: Int) = track.width * stops[index] / 268f
                val startPadding = (track.left + offsetFor(0)).roundToInt() - slider.left
                val endPadding = slider.right - (track.left + offsetFor(stops.lastIndex)).roundToInt()
                if (slider.paddingLeft != startPadding || slider.paddingRight != endPadding) {
                    slider.setPadding(startPadding, slider.paddingTop, endPadding, slider.paddingBottom)
                }
                val offset = offsetFor(progress)
                val center = track.left + offset
                marker.translationX = center - (marker.left + marker.width / 2f)
                value.translationX = center - (value.left + value.width / 2f)
                filledTrack.pivotX = 0f
                filledTrack.scaleX = offset / filledTrack.width
                for (tick in ticks) {
                    tick.setBackgroundResource(if (tick.left <= center) CoreR.drawable.surface_52651a7fb136
                        else CoreR.drawable.surface_b2e27b9c020a)
                }
            }
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) = updateScore(progress)
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
            slider.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateScore(slider.progress) }
            panel.post { updateScore(slider.progress) }
            bindPanelActions(panel) { label, _ ->
                when (label) {
                    "Cancel" -> dialog.cancel()
                    "Submit" -> {
                        val score = slider.progress + 1
                        val text = comment.text.toString()
                        dialog.dismiss()
                        onSubmit(score, text)
                    }
                }
            }
        }
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
            KYC_EXIT -> {
                val dialog = kycExitDialog
                if (dialog != null) dialog.cancel() else if (!model.back()) finish()
            }
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
                returnToAuthenticationPage()
            }
            IDENTITY_CARD, VOTER_CARD, DRIVER_LICENSE, IDENTITY_FILLED,
            WALLET_SETUP, CONTACTS, PERSONAL_INFO -> returnToPreviousAuthenticationStep(screen)
            DOCUMENT_GUIDE -> returnToKycInfoFromDocumentCapture()
            REVIEW_COUNTDOWN -> go(REVIEW_EXIT)
            ORDER_DETAIL -> {
                if (model.state.getBoolean(PRODUCT_APPLICATION_FLOW, false)) clearLoanProductContext()
                leave()
            }
            else -> leave()
        }
    }

    private fun returnToAuthenticationPage() {
        clearSelfieCapture()
        model.state.remove("livenessImage")
        returnToAuthenticationAfterLiveness = true
        model.root(authenticationCardScreen())
    }

    private fun returnToPreviousAuthenticationStep(current: Screen) {
        val previous = when (current) {
            CONTACTS -> WALLET_SETUP
            PERSONAL_INFO -> CONTACTS
            IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD -> PERSONAL_INFO
            IDENTITY_FILLED -> authenticationCardScreen()
            else -> null
        }
        if (current in setOf(FACE_GUIDE, FACE_PREVIEW, FACE_FAILED)) {
            clearSelfieCapture()
            model.state.remove("livenessImage")
            returnToAuthenticationAfterLiveness = true
            model.root(authenticationCardScreen())
        } else {
            model.root(previous ?: model.home)
        }
    }

    private fun authenticationCardScreen(): Screen = runCatching {
        Screen.valueOf(model.state.getString("authenticationCardScreen").orEmpty())
    }.getOrDefault(IDENTITY_CARD).takeIf {
        it in setOf(IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD)
    } ?: IDENTITY_CARD

    private fun returnToKycInfoFromDocumentCapture() {
        val kycScreens = setOf(IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD, IDENTITY_FILLED)
        val destination = pendingDocumentScreen?.takeIf { it in kycScreens }
            ?: model.previousPage(setOf(DOCUMENT_GUIDE))?.takeIf { it in kycScreens }
            ?: IDENTITY_CARD
        pendingDocumentScreen = null
        clearPendingDocumentCapture()
        go(destination, replace = true)
    }

    private companion object {
        const val PERMISSION_REQUEST = 870
        const val CAMERA_PERMISSION_REQUEST = 871
        const val ORDERS_LIST_TAG = "orders-list-container"
        const val ORDERS_PAGE_SIZE = 20
        const val HOME_USER_STATUS = "home.userStatus"
        const val LOAN_PRODUCT_JSON = "application.loanProduct"
        const val PRODUCT_APPLICATION_FLOW = "application.productFlow"
        const val PRODUCT_ORDER_CREATED = "application.orderCreated"
        const val ORDER_PRODUCT_LOGO_TAG = "order-product-logo"
    }

    private fun applyForCredit(clickedProductId: String? = null) {
        val productId = clickedProductId ?: model.state.getString("selectedProductId").orEmpty()
        if (productId.isBlank()) {
            notice("No loan product is available right now.")
            return
        }
        val amountPage = when (screen) {
            HOME, HOME_ACTIVE, HOME_PRODUCTS, HOME_APPLIED -> "HOME"
            ORDER_DETAIL -> "ORDERDETAIL_ORDER_PAGE"
            ORDER_SUCCESS -> "ORDER_SUCCESS_PAGE"
            else -> "RECOMMENDED_ORDER_PAGE"
        }
        trackServiceEvent("CLICK_AMOUNT_BUTTON", mapOf(
            ApiField.EVENT_PARAMETER to amountPage,
            ApiField.EVENT_PARAMETER_2 to productId
        ))
        model.state.putString("applicationEntryPage", amountPage)
        model.state.putString("selectedProductId", productId)
        clearLoanProductContext()
        if (!model.signedIn) {
            go(SIGN_IN)
            return
        }
        requestAppPermissions(PermissionPurpose.APPLY) {
            if (homeDataLoading) {
                pendingApplicationProductId = productId
                return@requestAppPermissions
            }
            val userStatus = model.state.getInt(HOME_USER_STATUS, -1)
            if (userStatus !in 1..2) {
                pendingApplicationProductId = productId
                refreshSignedInHome()
                return@requestAppPermissions
            }
            openApplicationForProduct(productId, userStatus)
        }
    }

    private fun openApplicationForProduct(productId: String, userStatus: Int) {
        model.state.putString("selectedProductId", productId)
        if (userStatus == 1) {
            apiCall("Loading your application…", { prepareAuthentication() }) { destination ->
                go(destination)
            }
            return
        }

        apiCall("Loading your application…", {
            val space = api.spaceInfo(productId, deviceRequestFacts())
            when (space.optInt(ApiField.SPACE_STATUS)) {
                1 -> HomeApplicationRoute(productId, prepareAuthentication())
                2 -> {
                    val loanProduct = space.optJSONObject(ApiField.LOAN_PRODUCT)
                    val responseProductId = loanProduct?.optString(ApiField.PRODUCT_ID).orEmpty().trim()
                    HomeApplicationRoute(responseProductId, ORDER_DETAIL, loanProduct, productOffer = true)
                }
                3 -> {
                    val orderInfo = space.optJSONObject(ApiField.ORDER_INFO)
                    val orderNumber = orderInfo?.optString(ApiField.ORDER_NO).orEmpty().ifBlank {
                        api.orderList(1, 20).optJSONArray(ApiField.ORDER_LIST)
                            ?.optJSONObject(0)?.optString(ApiField.ORDER_NO).orEmpty()
                    }
                    if (orderNumber.isBlank()) {
                        HomeApplicationRoute(productId, ORDERS)
                    } else {
                        model.state.putBoolean("hasOrder", true)
                        model.state.putString("activeOrderNo", orderNumber)
                        val detail = api.orderDetail(orderNumber, deviceRequestFacts())
                        val destination = detail.optJSONObject(ApiField.ORDER_INFO)
                            ?.let(::orderDestination)
                            ?.takeIf { it != ORDER_DETAIL }
                            ?: ORDERS
                        HomeApplicationRoute(productId, destination)
                    }
                }
                else -> throw ApiException("Unable to load an available product for this account.")
            }
        }) { route ->
            if (route.productOffer) {
                val product = route.loanProduct
                if (product == null || route.productId.isBlank()) {
                    clearLoanProductContext()
                    model.root(model.home)
                    notice("No loan product is available right now.")
                    return@apiCall
                }
                saveLoanProductContext(product)
                model.state.putString("selectedProductId", route.productId)
                go(ORDER_DETAIL)
            } else if (route.screen == ORDERS) {
                model.state.putString("selectedProductId", route.productId)
                model.root(ORDERS)
                loadOrders()
            } else {
                model.state.putString("selectedProductId", route.productId)
                go(route.screen)
            }
        }
    }

    private fun saveLoanProductContext(product: org.json.JSONObject) {
        model.state.putString(LOAN_PRODUCT_JSON, product.toString())
        model.state.putBoolean(PRODUCT_APPLICATION_FLOW, true)
        model.state.putBoolean(PRODUCT_ORDER_CREATED, false)
    }

    private fun clearLoanProductContext() {
        model.state.remove(LOAN_PRODUCT_JSON)
        model.state.putBoolean(PRODUCT_APPLICATION_FLOW, false)
        model.state.putBoolean(PRODUCT_ORDER_CREATED, false)
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
        if (model.state.getBoolean(PRODUCT_APPLICATION_FLOW, false)) {
            if (model.state.getBoolean(PRODUCT_ORDER_CREATED, false)) {
                notice("Your application has already been submitted.")
                return
            }
            apiCall("Submitting your application…", {
                api.createOrder(productId, api.compressedDevicePayload(), deviceRequestFacts())
            }) { response ->
                finishOrderCreation(response)
            }
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
                returnToAuthenticationAfterLiveness = false
                go(FACE_GUIDE)
            } else if (response != null) {
                finishOrderCreation(response)
            }
        }
    }

    private fun finishOrderCreation(response: org.json.JSONObject) {
        AdjustAnalytics.loanApplicationResponse(this, response.optInt(ApiField.FIRST_APPLICATION) == 1)
        model.state.putBoolean("hasOrder", true)
        val orderNumber = response.optString(ApiField.ORDER_NO).takeIf(String::isNotBlank)
        if (orderNumber == null) model.state.remove("activeOrderNo")
        else model.state.putString("activeOrderNo", orderNumber)
        if (model.state.getBoolean(PRODUCT_APPLICATION_FLOW, false)) {
            model.state.putBoolean(PRODUCT_ORDER_CREATED, true)
        }
        go(ORDER_SUCCESS)
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
        if (model.signedIn) loadOrders(filter)
    }

    private fun loadOrders(
        filter: OrderStatusFilter = OrderStatusFilter.ALL,
        page: Int = 1,
        append: Boolean = false
    ) {
        if (append && (ordersLoading || !ordersHasMore || filter != activeOrdersFilter)) return
        if (!append) {
            activeOrdersFilter = filter
            ordersPage = 0
            ordersHasMore = true
            orderRecords = emptyList()
            viewport.scrollTo(0, 0)
            if (screen == ORDERS) renderOrderCards(filter)
        } else if (page != ordersPage + 1) {
            return
        }

        ordersLoading = true
        val requestId = ++ordersRequestId
        showLoading("Loading orders…")
        activityScope.launch {
            try {
                val response = api.orderList(page, ORDERS_PAGE_SIZE, filter.requestStatus)
                if (requestId != ordersRequestId) return@launch

                val pageArray = response.optJSONArray(ApiField.ORDER_LIST)
                val pageRecords = (0 until (pageArray?.length() ?: 0))
                    .mapNotNull { pageArray?.optJSONObject(it) }
                    .filter { filter.requestStatus == null || it.optInt(ApiField.LOAN_STATUS) == filter.requestStatus }
                orderRecords = if (append) mergeOrderPages(orderRecords, pageRecords) else pageRecords
                ordersPage = page
                ordersHasMore = (pageArray?.length() ?: 0) >= ORDERS_PAGE_SIZE

                if (filter == OrderStatusFilter.ALL && page == 1) {
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
                if (!append && filter != OrderStatusFilter.ALL && orderRecords.isEmpty()) {
                    notice("No orders in this status.")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (requestId != ordersRequestId) return@launch
                if (failure is ExpiredSessionException) {
                    model.state.putBoolean("signedIn", false)
                    model.root(SIGN_IN)
                    notice("Login expired. Please sign in again.")
                } else {
                    notice(failure.message ?: "Unable to connect. Please try again.")
                }
            } finally {
                if (requestId == ordersRequestId) {
                    ordersLoading = false
                    hideLoading()
                    if (screen == ORDERS) renderOrderCards(activeOrdersFilter)
                }
            }
        }
    }

    private fun mergeOrderPages(
        current: List<org.json.JSONObject>,
        next: List<org.json.JSONObject>
    ): List<org.json.JSONObject> {
        val seenOrderNumbers = current.mapNotNull { it.optString(ApiField.ORDER_NO).takeIf(String::isNotBlank) }
            .toMutableSet()
        return current + next.filter { order ->
            val orderNumber = order.optString(ApiField.ORDER_NO)
            orderNumber.isBlank() || seenOrderNumbers.add(orderNumber)
        }
    }

    private fun loadMoreOrdersIfNeeded(scrollY: Int) {
        if (screen != ORDERS || ordersLoading || !ordersHasMore || ordersPage == 0) return
        val remaining = stage.height - viewport.height - scrollY
        if (remaining <= orderDp(180f)) loadOrders(activeOrdersFilter, ordersPage + 1, append = true)
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

    private fun renderOrderCards(filter: OrderStatusFilter) {
        val container = orderListContainer ?: return
        container.removeAllViews()
        orderRecords.forEach { container.addView(createOrderCard(it)) }
        val contentHeight = if (orderRecords.isEmpty()) {
            104f
        } else {
            302f + (orderRecords.size - 1) * 200f
        }
        pageConstraints.setContentHeight(contentHeight)
        container.requestLayout()
    }

    private fun hideStaticOrderCardViews() {
        for (index in 0 until stage.childCount) {
            val child = stage.getChildAt(index)
            val top = dpTop(child)
            if (top >= 105f && top < 1500f) child.visibility = View.GONE
        }
    }

    private fun createOrderListContainer() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            tag = ORDERS_LIST_TAG
        }
        val params = ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            leftMargin = orderDp(20f)
            rightMargin = orderDp(20f)
            topMargin = orderDp(105f)
        }
        stage.addView(container, params)
        orderListContainer = container
    }

    private fun createOrderCard(order: org.json.JSONObject): View {
        val card = FrameLayout(this).apply {
            setBackgroundResource(CoreR.drawable.art_d0b7bd49ac17)
            contentDescription = listOf(
                order.optString(ApiField.PRODUCT_NAME),
                orderStatusLabel(order),
                formatProductAmount(order.optString(ApiField.LOAN_AMOUNT))
            ).filter(String::isNotBlank).joinToString(", ")
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val name = order.optString(ApiField.PRODUCT_NAME).ifBlank { getString(CoreR.string.text_name) }
        val status = orderStatusLabel(order)
        val amount = formatProductAmount(order.optString(ApiField.LOAN_AMOUNT))
        addOrderCardText(card, name, 16f, 0xff120d01.toInt(), 19f, 17f, 160f, 20f, Gravity.START or Gravity.CENTER_VERTICAL)
        addOrderCardText(card, status, 14f, orderStatusColor(order), 194f, 17f, 123f, 18f, Gravity.END or Gravity.CENTER_VERTICAL)
        addOrderCardText(card, amount, 18f, 0xff120d01.toInt(), 78f, 52f, 180f, 23f, Gravity.START or Gravity.CENTER_VERTICAL)
        addOrderCardText(card, getString(CoreR.string.text_loan_amount), 14f, 0xff120d01.toInt(), 79f, 76f, 139f, 18f, Gravity.START or Gravity.CENTER_VERTICAL)
        val divider = ImageView(this).apply {
            setImageResource(CoreR.drawable.art_4337934efed9)
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        card.addView(divider, FrameLayout.LayoutParams(orderDp(315f), orderDp(1f)).apply {
            leftMargin = orderDp(10f)
            topMargin = orderDp(106f)
        })
        addOrderCardText(card, getString(CoreR.string.text_apply_date), 14f, 0xff999999.toInt(), 16f, 121f, 100f, 18f, Gravity.START or Gravity.CENTER_VERTICAL)
        addOrderCardText(card, order.optString(ApiField.ORDER_APPLY_DATE), 14f, 0xff120d01.toInt(), 151f, 121f, 168f, 18f, Gravity.END or Gravity.CENTER_VERTICAL)
        addOrderCardText(card, getString(CoreR.string.text_order_number), 14f, 0xff999999.toInt(), 16f, 149f, 110f, 18f, Gravity.START or Gravity.CENTER_VERTICAL)
        addOrderCardText(card, order.optString(ApiField.ORDER_NO), 14f, 0xff120d01.toInt(), 123f, 149f, 199f, 18f, Gravity.END or Gravity.CENTER_VERTICAL)
        card.setOnClickListener {
            val orderNumber = order.optString(ApiField.ORDER_NO)
            if (orderNumber.isBlank()) notice("This order is no longer available.") else loadOrderDetail(orderNumber)
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, orderDp(180f)).apply {
            bottomMargin = orderDp(20f)
        }
        card.layoutParams = params
        return card
    }

    private fun addOrderCardText(
        card: FrameLayout,
        value: String,
        size: Float,
        color: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        gravity: Int
    ) {
        val text = TextView(this).apply {
            text = value
            textSize = size * orderDesignScale()
            setTextColor(color)
            typeface = Typeface.create(if (size >= 16f) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            this.gravity = gravity
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(text, FrameLayout.LayoutParams(orderDp(width), orderDp(height)).apply {
            leftMargin = orderDp(left)
            topMargin = orderDp(top)
        })
    }

    private fun orderDesignScale(): Float {
        val referenceWidth = (stage.tag as? String)?.substringBefore(',')?.toFloatOrNull() ?: 375f
        val widthPx = viewport.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        return (widthPx / (resources.displayMetrics.density * referenceWidth)).coerceAtLeast(0.5f)
    }

    private fun orderDp(value: Float) = dp(value * orderDesignScale())

    private fun orderStatusColor(order: org.json.JSONObject): Int = when (order.optInt(ApiField.LOAN_STATUS)) {
        1 -> 0xffff8222.toInt()
        2 -> 0xffc48a00.toInt()
        3 -> 0xffd39a00.toInt()
        4 -> 0xff49815b.toInt()
        5 -> 0xffd95d4f.toInt()
        6 -> 0xffd44738.toInt()
        else -> 0xff999999.toInt()
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
        val missing = fieldsToValidate.firstOrNull { field -> field.text.isNullOrBlank() }
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

    private fun showKycExitDialog() {
        if (kycExitDialog?.isShowing == true || isFinishing || isDestroyed) return

        kycExitReturnScrollY = viewport.scrollY
        val dialog = Dialog(this)
        kycExitDialog = dialog
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val backdrop = layoutInflater.inflate(IdentityR.layout.kyc_exit, null, false) as FrameLayout
        val card = backdrop.findViewById<View>(IdentityR.id.kyc_exit_card)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        fun centerKycExitCard() {
            val maxWidth = minOf(backdrop.width - dp(40), dp(335))
            val maxHeight = backdrop.height - dp(40)
            if (maxWidth <= 0 || maxHeight <= 0) return
            val widthForHeight = (maxHeight * 335f / 195f).roundToInt()
            val width = minOf(maxWidth, widthForHeight)
            if (width <= 0) return
            val height = (width * 195f / 335f).roundToInt()
            val params = card.layoutParams as FrameLayout.LayoutParams
            if (params.width == width && params.height == height && params.gravity == Gravity.CENTER) return
            params.width = width
            params.height = height
            params.gravity = Gravity.CENTER
            params.leftMargin = 0
            params.rightMargin = 0
            params.marginStart = 0
            params.marginEnd = 0
            card.layoutParams = params
        }
        backdrop.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> centerKycExitCard() }
        dialog.setContentView(backdrop)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)
        backdrop.setOnClickListener { dialog.cancel() }
        card.setOnClickListener { }
        dialog.setOnCancelListener {
            if (kycExitDialog !== dialog) return@setOnCancelListener
            kycExitDialog = null
            if (screen == KYC_EXIT) {
                trackServiceEvent("CLICK_BUTTON", mapOf(ApiField.EVENT_PARAMETER to "BACK_OCR_CANCEL"))
                if (!model.back()) model.root(IDENTITY_CARD)
                viewport.post { viewport.scrollTo(0, kycExitReturnScrollY) }
            }
        }
        dialog.setOnDismissListener {
            if (kycExitDialog === dialog) kycExitDialog = null
        }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            attributes = attributes.apply {
                gravity = Gravity.CENTER
                dimAmount = 0.4f
            }
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            navigationBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility = decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = false
        }
        dialog.show()
        backdrop.post { centerKycExitCard() }
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
            homeProducts = org.json.JSONArray()
            homeHasRealProducts = false
            model.state.remove(HOME_USER_STATUS)
            pendingApplicationProductId = null
            clearLoanProductContext()
            model.login()
            viewport.post { refreshSignedInHome() }
        }
    }

    private fun handle(label: String, view: View, index: Int) {
        if (screen == IDENTITY_FILLED && !model.state.getString("livenessImage").isNullOrBlank() &&
            label in setOf("Capture selfie", "Upload a clear selfie")
        ) return
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
                homeHasRealProducts = false
                homeInfoSignedIn = false
                homeDataResolved = false
                homeDataLoading = false
                model.state.remove(HOME_USER_STATUS)
                pendingApplicationProductId = null
                clearLoanProductContext()
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
            label == "Capture selfie photo" -> captureFacePhoto()
            label == "Capture document photo" -> {
                trackDocumentCameraTap()
                captureDocumentPhoto()
            }
            label == "Photograph" || label.endsWith(" photo") -> {
                if (screen == DOCUMENT_GUIDE) {
                    trackDocumentCameraTap()
                    captureDocumentPhoto()
                } else takePhoto()
            }
            label == "Capture selfie" || label == "Upload a clear selfie" -> {
                clearSelfieCapture()
                model.state.remove("livenessImage")
                returnToAuthenticationAfterLiveness = true
                go(FACE_GUIDE)
            }
            label == "Capture document" -> { pendingDocumentScreen = screen; go(DOCUMENT_GUIDE) }
            label == "Reshoot" -> {
                clearSelfieCapture()
                model.state.remove("livenessImage")
                go(FACE_GUIDE)
            }
            label == "Submit application" -> submitApplication()
            label == "Go Apply" || label == "Begin Application" || label == "Apply Now" ->
                applyForCredit(homeProductActionIds[view] ?: model.state.getString("selectedProductId").orEmpty())
            label == "Withdraw Now" -> when (screen) {
                HOME_ACTIVE -> openAmountScreen()
                WITHDRAW, WITHDRAW_ADJUSTED -> saveSelectedAmount()
                else -> go(WITHDRAW_SUCCESS)
            }
            label == "Set Up" && screen == PERMISSION_PROMPT -> requestAppPermissions(PermissionPurpose.INITIAL) {}
            screen == PERSONAL_INFO && view.id == IdentityR.id.personal_info_payroll_cycle_value -> openCityPicker()
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
            else -> leave()
        }
    }

    private fun submitLiveness() {
        val selfie = selfieUri
        if (selfie == null) {
            notice("Please take a selfie before submitting.")
            return
        }
        val returnToAuthentication = returnToAuthenticationAfterLiveness
        apiCall("Uploading and verifying selfie…", {
            try {
                val imagePath = S3ImageUploader(this, api).uploadUri(selfie, true)
                val response = try {
                    api.saveLiveness(imagePath)
                } catch (failure: ApiException) {
                    if (failure.code == 0) null else throw failure
                }
                val accepted = response?.optInt(ApiField.RESULT) == 1
                val continuation = if (accepted && !returnToAuthentication) {
                    continueAfterIdentity()
                } else null
                trackAuthSubmission(FACE_PREVIEW, accepted)
                Triple(imagePath, accepted, continuation)
            } catch (failure: Exception) {
                trackAuthSubmission(FACE_PREVIEW, false)
                throw failure
            }
        }) { (imagePath, accepted, continuation) ->
            if (accepted) {
                model.state.putString("livenessImage", imagePath)
                if (returnToAuthentication) {
                    go(identity.next(FACE_PREVIEW), replace = true)
                } else {
                    clearSelfieCapture()
                    val product = continuation?.loanProduct
                    val productId = product?.optString(ApiField.PRODUCT_ID).orEmpty().trim()
                    if (product == null || productId.isBlank()) {
                        clearLoanProductContext()
                        model.root(model.home)
                        notice("No loan product is available right now.")
                    } else {
                        saveLoanProductContext(product)
                        model.state.putString("selectedProductId", productId)
                        go(ORDER_DETAIL)
                    }
                }
            } else {
                clearSelfieCapture()
                model.state.remove("livenessImage")
                go(FACE_GUIDE)
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
            continueAfterIdentity()
        }, suppressFailureNotice = { failure ->
            failure.message?.contains("name is out of range", ignoreCase = true) == true
        }) { continuation ->
            val info = continuation.info
            if (info.optInt(ApiField.USER_STATUS) != 2) {
                notice("Your identity information was saved. Complete the remaining steps to continue.")
                model.root(model.home)
            } else if (info.optInt(ApiField.USER_LIVENESS) != 1) {
                model.state.putBoolean("identityVerified", true)
                returnToAuthenticationAfterLiveness = false
                go(FACE_GUIDE)
            } else {
                model.state.putBoolean("identityVerified", true)
                val product = continuation.loanProduct
                val productId = product?.optString(ApiField.PRODUCT_ID).orEmpty().trim()
                if (product == null || productId.isBlank()) {
                    clearLoanProductContext()
                    model.root(model.home)
                    notice("No loan product is available right now.")
                } else {
                    saveLoanProductContext(product)
                    model.state.putString("selectedProductId", productId)
                    go(ORDER_DETAIL)
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
        val storedIdNumber = model.field("${cardScreen.name}_$idIndex")
        val idNumber = if (cardScreen == IDENTITY_CARD) {
            val suffix = if (storedIdNumber.startsWith("GHA-", ignoreCase = true)) {
                storedIdNumber.substring(4)
            } else {
                storedIdNumber
            }
            if (suffix.isBlank()) "" else "GHA-$suffix"
        } else {
            storedIdNumber
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
            put(idKey, idNumber)
            put(ApiField.FULL_NAME, model.field("${cardScreen.name}_$nameIndex").trim())
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
            put(ApiField.EMAIL, model.field("PERSONAL_INFO_41"))
            put(ApiField.ADDRESS, model.field("PERSONAL_INFO_42"))
            put(ApiField.PROVINCE, model.state.getString("authentication.province").orEmpty())
            put(ApiField.DISTRICT, model.state.getString("authentication.district").orEmpty())
            put(ApiField.MESSAGING_ACCOUNT, model.field("PERSONAL_INFO_43"))
            if (includeApplyAmount && model.state.getBoolean("amountTouched", false)) {
                put(ApiField.APPLY_AMOUNT, model.state.getLong("applyAmountValue", 0L).toString())
            }
        }.filterValues(String::isNotBlank)
    }

    private suspend fun continueAfterIdentity(): AuthContinuation {
        val recommended = fetchUserInfo(recommended = true)
        if (recommended.optInt(ApiField.USER_STATUS) != 2 || recommended.optInt(ApiField.USER_LIVENESS) != 1) {
            return AuthContinuation(recommended, null)
        }
        val product = recommended.optJSONObject(ApiField.LOAN_PRODUCT)
            ?: return AuthContinuation(recommended, null)
        if (product.optString(ApiField.PRODUCT_ID).isBlank()) return AuthContinuation(recommended, null)
        return AuthContinuation(recommended, product)
    }

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

    private fun configureFaceCamera(page: ViewGroup) {
        faceCameraContainer = page.findViewById(IdentityR.id.face_camera_preview_container)
        faceCameraStatus = page.findViewById(IdentityR.id.face_camera_status)
        faceCaptureButton = page.findViewWithTag("Capture selfie photo")
        faceCaptureIcon = page.findViewById(IdentityR.id.face_capture_icon)
        faceCaptureInProgress = false
        setFaceCaptureReady(false)
        faceCameraStatus?.setOnClickListener { retryFaceCamera() }
        ensureFaceCameraPreview()
    }

    private fun configureDocumentCamera(page: ViewGroup) {
        documentCameraContainer = page.findViewById(IdentityR.id.document_camera_preview_container)
        documentCameraStatus = page.findViewById(IdentityR.id.document_camera_status)
        documentCaptureButton = page.findViewWithTag("Capture document photo")
        documentCaptureInProgress = false
        setDocumentCaptureReady(false)
        documentCameraStatus?.setOnClickListener { retryDocumentCamera() }
        ensureDocumentCameraPreview()
    }

    private fun ensureDocumentCameraPreview() {
        if (layoutPreview || screen != DOCUMENT_GUIDE || documentCameraController != null) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestDocumentCameraPermission()
            return
        }

        val container = documentCameraContainer ?: return
        setDocumentCameraStatus(null)
        setDocumentCaptureReady(false)
        lateinit var controller: CameraPreviewController
        controller = CameraPreviewController(
            context = this,
            lifecycleOwner = this,
            lensFacing = CameraSelector.LENS_FACING_BACK,
            onPreviewStateChanged = { streaming ->
                if (documentCameraController === controller && screen == DOCUMENT_GUIDE) {
                    setDocumentCameraStatus(null)
                    setDocumentCaptureReady(streaming)
                }
            },
            onError = {
                if (documentCameraController === controller && screen == DOCUMENT_GUIDE) {
                    controller.close()
                    documentCameraController = null
                    setDocumentCaptureReady(false)
                    setDocumentCameraStatus("Camera unavailable. Tap here to retry.")
                    notice("Camera unavailable. Please try again.")
                }
            }
        )
        documentCameraController = controller
        controller.start(container)
    }

    private fun requestDocumentCameraPermission() {
        val requestedBefore = model.state.getBoolean("cameraPermissionRequested")
        if (requestedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(this)
                .setMessage("Camera access is required. Enable it in app settings to capture your document.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Open Settings") { _, _ ->
                    try {
                        startActivity(Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        ))
                    } catch (_: android.content.ActivityNotFoundException) {
                        notice("Unable to open app settings. Please change the camera permission manually.")
                    }
                }
                .show()
            return
        }

        model.state.putBoolean("cameraPermissionRequested", true)
        requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    private fun retryDocumentCamera() {
        if (screen != DOCUMENT_GUIDE) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestDocumentCameraPermission()
            return
        }
        releaseDocumentCamera()
        configureDocumentCamera(stage)
    }

    private fun setDocumentCameraStatus(message: String?) {
        documentCameraStatus?.let { status ->
            if (message.isNullOrBlank()) {
                status.visibility = View.GONE
            } else {
                status.text = message
                status.contentDescription = message
                status.visibility = View.VISIBLE
            }
        }
    }

    private fun setDocumentCaptureReady(ready: Boolean) {
        documentCameraReady = ready
        val enabled = ready && !documentCaptureInProgress
        documentCaptureButton?.isEnabled = enabled
        documentCaptureButton?.alpha = if (enabled) 1f else 0.45f
    }

    private fun releaseDocumentCamera() {
        documentCameraController?.close()
        documentCameraController = null
        documentCameraReady = false
        documentCaptureInProgress = false
        documentCameraContainer = null
        documentCameraStatus = null
        documentCaptureButton = null
    }

    private fun ensureFaceCameraPreview() {
        if (layoutPreview || screen != FACE_GUIDE || faceCameraController != null) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestFaceCameraPermission()
            return
        }

        val container = faceCameraContainer ?: return
        setFaceCameraStatus(null)
        setFaceCaptureReady(false)
        lateinit var controller: CameraPreviewController
        controller = CameraPreviewController(
            context = this,
            lifecycleOwner = this,
            lensFacing = CameraSelector.LENS_FACING_FRONT,
            onPreviewStateChanged = { streaming ->
                if (faceCameraController === controller && screen == FACE_GUIDE) {
                    setFaceCameraStatus(null)
                    setFaceCaptureReady(streaming)
                }
            },
            onError = {
                if (faceCameraController === controller && screen == FACE_GUIDE) {
                    controller.close()
                    faceCameraController = null
                    setFaceCaptureReady(false)
                    setFaceCameraStatus(getString(CoreR.string.face_camera_retry_unavailable))
                    notice("Camera unavailable. Please try again.")
                }
            }
        )
        faceCameraController = controller
        controller.start(container)
    }

    private fun requestFaceCameraPermission() {
        val requestedBefore = model.state.getBoolean("cameraPermissionRequested")
        if (requestedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(this)
                .setMessage("Camera access is required. Enable it in app settings to take your selfie.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Open Settings") { _, _ ->
                    try {
                        startActivity(Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        ))
                    } catch (_: android.content.ActivityNotFoundException) {
                        notice("Unable to open app settings. Please change the camera permission manually.")
                    }
                }
                .show()
            return
        }

        model.state.putBoolean("cameraPermissionRequested", true)
        requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    private fun retryFaceCamera() {
        if (screen != FACE_GUIDE) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestFaceCameraPermission()
            return
        }
        releaseFaceCamera()
        configureFaceCamera(stage)
    }

    private fun setFaceCameraStatus(message: String?) {
        faceCameraStatus?.let { status ->
            if (message.isNullOrBlank()) {
                status.visibility = View.GONE
            } else {
                status.text = message
                status.contentDescription = message
                status.visibility = View.VISIBLE
            }
        }
    }

    private fun setFaceCaptureReady(ready: Boolean) {
        faceCameraReady = ready
        val enabled = ready && !faceCaptureInProgress
        faceCaptureButton?.isEnabled = enabled
        faceCaptureButton?.alpha = if (enabled) 1f else 0.45f
        faceCaptureIcon?.alpha = if (enabled) 1f else 0.45f
    }

    private fun releaseFaceCamera() {
        faceCameraController?.close()
        faceCameraController = null
        faceCameraReady = false
        faceCaptureInProgress = false
        faceCameraContainer = null
        faceCameraStatus = null
        faceCaptureButton = null
        faceCaptureIcon = null
    }

    private fun captureDocumentPhoto() {
        if (screen != DOCUMENT_GUIDE || documentCaptureInProgress) return
        if (!documentCameraReady) {
            ensureDocumentCameraPreview()
            return
        }
        pendingDocumentCapture?.let {
            uploadPendingDocumentPhoto()
            return
        }
        val owner = pendingDocumentScreen?.takeIf {
            it in setOf(IDENTITY_CARD, DRIVER_LICENSE, VOTER_CARD, IDENTITY_FILLED)
        } ?: IDENTITY_CARD.also { pendingDocumentScreen = it }
        val controller = documentCameraController ?: return
        val photoDirectory = java.io.File(cacheDir, "camera-photos")
        if (!photoDirectory.exists() && !photoDirectory.mkdirs()) {
            notice("Unable to prepare a photo. Please try again.")
            return
        }
        var filename = "document_${System.currentTimeMillis()}.jpg"
        var photoFile = java.io.File(photoDirectory, filename)
        var suffix = 0
        while (!runCatching { photoFile.createNewFile() }.getOrDefault(false)) {
            suffix++
            filename = "document_${System.currentTimeMillis()}_$suffix.jpg"
            photoFile = java.io.File(photoDirectory, filename)
            if (suffix > 10) {
                notice("Unable to prepare a photo. Please try again.")
                return
            }
        }
        val uri = Uri.Builder().scheme("content").authority("$packageName.camera-files")
            .appendPath("camera").appendPath(filename).build()
        localPhotoUris += uri
        documentCaptureInProgress = true
        setDocumentCaptureReady(true)
        controller.capture(
            output = photoFile,
            onSaved = {
                if (screen != DOCUMENT_GUIDE || pendingDocumentScreen != owner) {
                    deleteLocalPhoto(uri)
                    documentCaptureInProgress = false
                    return@capture
                }
                activityScope.launch {
                    val capturedPreview = runCatching {
                        withContext(Dispatchers.IO) { decodeFacePreview(uri) }
                    }.getOrNull()
                    if (screen != DOCUMENT_GUIDE || pendingDocumentScreen != owner) {
                        capturedPreview?.takeUnless(Bitmap::isRecycled)?.recycle()
                        deleteLocalPhoto(uri)
                        documentCaptureInProgress = false
                    } else if (capturedPreview == null) {
                        deleteLocalPhoto(uri)
                        documentCaptureInProgress = false
                        setDocumentCaptureReady(true)
                        notice("Unable to read the document photo. Please try again.")
                    } else {
                        pendingDocumentCapture?.preview?.takeUnless(Bitmap::isRecycled)?.recycle()
                        pendingDocumentCapture = CapturedPhoto(uri, capturedPreview)
                        documentCaptureInProgress = false
                        uploadPendingDocumentPhoto()
                    }
                }
            },
            onFailure = {
                deleteLocalPhoto(uri)
                if (screen == DOCUMENT_GUIDE) {
                    documentCaptureInProgress = false
                    setDocumentCaptureReady(true)
                    notice("Unable to take the document photo. Please try again.")
                }
            }
        )
    }

    private fun uploadPendingDocumentPhoto() {
        val capture = pendingDocumentCapture ?: return
        val owner = pendingDocumentScreen ?: return
        if (screen != DOCUMENT_GUIDE || documentCaptureInProgress) return
        documentCaptureInProgress = true
        setDocumentCaptureReady(documentCameraReady)
        setDocumentCameraStatus("Uploading photo. Tap here to retry if it fails.")
        apiCall("Uploading document photo…", {
            S3ImageUploader(this, api).uploadUri(capture.uri, true)
        }, onFailure = {
            documentCaptureInProgress = false
            setDocumentCaptureReady(documentCameraReady)
            setDocumentCameraStatus("Upload failed. Tap the camera to retry.")
        }) { path ->
            if (pendingDocumentCapture !== capture || pendingDocumentScreen != owner) return@apiCall
            model.state.putString("documentImage_${owner.name}", path)
            documentCapturePreviews.remove(owner)?.takeUnless(Bitmap::isRecycled)?.recycle()
            documentCapturePreviews[owner] = capture.preview
            pendingDocumentCapture = null
            deleteLocalPhoto(capture.uri)
            val changeType = when (owner) {
                IDENTITY_CARD -> "CHANGE_IDCARD_FONT"
                DRIVER_LICENSE -> "CHANGE_DRIVING_LICENSE_IMG"
                VOTER_CARD -> "CHANGE_VOTER_CARD_IMG"
                else -> null
            }
            if (changeType != null) trackAuthChange(owner, changeType)
            pendingDocumentScreen = null
            documentCaptureInProgress = false
            go(owner, replace = true)
        }
    }

    private fun captureFacePhoto() {
        if (screen != FACE_GUIDE || faceCaptureInProgress) return
        if (!faceCameraReady) {
            ensureFaceCameraPreview()
            return
        }
        val controller = faceCameraController ?: return
        val photoDirectory = java.io.File(cacheDir, "camera-photos")
        if (!photoDirectory.exists() && !photoDirectory.mkdirs()) {
            notice("Unable to prepare a photo. Please try again.")
            return
        }
        var filename = "face_${System.currentTimeMillis()}.jpg"
        var photoFile = java.io.File(photoDirectory, filename)
        var suffix = 0
        while (!runCatching { photoFile.createNewFile() }.getOrDefault(false)) {
            suffix++
            filename = "face_${System.currentTimeMillis()}_$suffix.jpg"
            photoFile = java.io.File(photoDirectory, filename)
            if (suffix > 10) {
                notice("Unable to prepare a photo. Please try again.")
                return
            }
        }
        val uri = Uri.Builder().scheme("content").authority("$packageName.camera-files")
            .appendPath("camera").appendPath(filename).build()
        localPhotoUris += uri
        faceCaptureInProgress = true
        setFaceCaptureReady(true)
        controller.capture(
            output = photoFile,
            onSaved = {
                if (screen != FACE_GUIDE) {
                    deleteLocalPhoto(uri)
                } else {
                    activityScope.launch {
                        val capturedPreview = runCatching {
                            withContext(Dispatchers.IO) { decodeFacePreview(uri) }
                        }.getOrNull()
                        if (screen != FACE_GUIDE) {
                            capturedPreview?.takeUnless(Bitmap::isRecycled)?.recycle()
                            deleteLocalPhoto(uri)
                        } else if (capturedPreview == null) {
                            deleteLocalPhoto(uri)
                            faceCaptureInProgress = false
                            setFaceCaptureReady(true)
                            notice("Unable to read the selfie. Please try again.")
                        } else {
                            clearSelfieCapture()
                            preview = capturedPreview
                            selfieUri = uri
                            go(FACE_PREVIEW)
                        }
                    }
                }
            },
            onFailure = {
                deleteLocalPhoto(uri)
                if (screen == FACE_GUIDE) {
                    faceCaptureInProgress = false
                    setFaceCaptureReady(true)
                    notice("Unable to take the selfie. Please try again.")
                }
            }
        )
    }

    private fun pickFile(type: String) {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(picker, 31)
    }

    private fun takePhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            model.state.putBoolean("cameraPermissionRequested", true)
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
                else if (added && screen == FEEDBACK_FILLED) renderPage(FEEDBACK_FILLED)
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
        val boundsStream = contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1600) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun decodeFacePreview(uri: Uri): Bitmap? {
        val bitmap = decodePreview(uri) ?: return null
        val orientation = runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val (rotation, flipped) = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f to true
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f to false
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f to true
            ExifInterface.ORIENTATION_TRANSPOSE -> 90f to true
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f to false
            ExifInterface.ORIENTATION_TRANSVERSE -> 270f to true
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f to false
            else -> 0f to false
        }
        if (rotation == 0f && !flipped) return bitmap
        val transform = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
            if (flipped) postScale(-1f, 1f)
        }
        val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
        if (corrected !== bitmap) bitmap.recycle()
        return corrected
    }

    private fun clearSelfieCapture() {
        preview?.takeUnless(Bitmap::isRecycled)?.recycle()
        preview = null
        selfieUri?.let(::deleteLocalPhoto)
        selfieUri = null
    }

    private fun clearPendingDocumentCapture() {
        pendingDocumentCapture?.let { capture ->
            capture.preview.takeUnless(Bitmap::isRecycled)?.recycle()
            deleteLocalPhoto(capture.uri)
        }
        pendingDocumentCapture = null
    }

    private fun clearDocumentCapturePreviews() {
        documentCapturePreviews.values.forEach { bitmap ->
            bitmap.takeUnless(Bitmap::isRecycled)?.recycle()
        }
        documentCapturePreviews.clear()
    }

    private fun deleteLocalPhoto(uri: Uri) {
        if (localPhotoUris.remove(uri)) runCatching { contentResolver.delete(uri, null, null) }
    }
}
