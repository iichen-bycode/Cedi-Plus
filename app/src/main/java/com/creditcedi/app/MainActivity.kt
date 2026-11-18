package com.creditcedi.app

import android.app.Activity
import android.app.AlertDialog
import com.creditcedi.account.FeedbackViewModel
import com.creditcedi.identity.IdentityViewModel
import com.creditcedi.credit.CreditViewModel
import com.creditcedi.credit.OrderStatusFilter
import com.creditcedi.credit.R as CreditR
import com.creditcedi.access.SignInViewModel
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
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
    private val orderCardSlots = IdentityHashMap<View, OrderCardSlot>()
    private var fullOrdersHeight = 0f
    private val exampleOrderStatuses = listOf(1, 2, 3, 5, 5, 6, 4)
    private val screen get() = model.current
    private var preview: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = JourneyViewModel(savedInstanceState?.getBundle("journey") ?: Bundle())
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

    private fun show(page: Screen) {
        requestedOrientation = if (page == DOCUMENT_GUIDE || page == DOCUMENT_PREVIEW)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
                model.state.getInt("orderStatus", 0).takeIf { it in 1..6 }
            )
            configureOrderTabs(currentFilter)
            if (page == ORDERS) renderOrderCards(currentFilter)
        }
        if (page == SIGN_IN) renderOtpButton()
        viewport.scrollTo(0, 0)
        if (page == DOCUMENT_PREVIEW) stage.setOnClickListener { model.go(IDENTITY_CARD, true) }
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

    private fun startJourney() = model.root(if (consent.getBoolean("accepted", false)) model.home else PRIVACY)
    private fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun go(page: Screen) = model.go(page)
    private fun leave() { if (!model.back()) finish() }

    @Deprecated("Platform callback for API 25 and above")
    override fun onBackPressed() {
        when (screen) {
            WITHDRAW, WITHDRAW_ADJUSTED -> go(WITHDRAW_EXIT)
            IDENTITY_CARD, VOTER_CARD, DRIVER_LICENSE -> go(KYC_EXIT)
            REVIEW_COUNTDOWN -> go(REVIEW_EXIT)
            else -> leave()
        }
    }

    private fun applyForCredit() {
        go(credit.applicationDestination(model.signedIn, model.hasOrder))
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
        val requestParameters = credit.orderListParameters(filter)
        model.state.remove("orderStatus")
        requestParameters["orderStatus"]?.let { model.state.putInt("orderStatus", it) }
        configureOrderTabs(filter)
        if (screen == ORDERS) renderOrderCards(filter)
    }

    private fun captureOrderCardSlots() {
        orderCardSlots.clear()
        val density = resources.displayMetrics.density
        val firstCardTop = (105 * density).roundToInt()
        val rowHeight = (200 * density).roundToInt()
        for (index in 0 until stage.childCount) {
            val child = stage.getChildAt(index)
            val params = child.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: continue
            if (params.topMargin < firstCardTop) continue
            val row = (params.topMargin - firstCardTop) / rowHeight
            if (row in exampleOrderStatuses.indices) {
                orderCardSlots[child] = OrderCardSlot(row, params.topMargin)
            }
        }
    }

    private fun renderOrderCards(filter: OrderStatusFilter) {
        val density = resources.displayMetrics.density
        val rowHeight = (200 * density).roundToInt()
        val visibleRows = exampleOrderStatuses.indices.filter { index ->
            filter.requestStatus == null || exampleOrderStatuses[index] == filter.requestStatus
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

        val contentHeight = if (filter.requestStatus == null) {
            fullOrdersHeight
        } else {
            maxOf(96f, 302f + (visibleRows.size - 1).coerceAtLeast(0) * 200f)
        }
        stage.setArtboardHeight(contentHeight)
        stage.requestLayout()
        viewport.scrollTo(0, 0)
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
        val error = signInForm.validationMessage(fields.first().text.toString(), fields.last().text.toString(),
            model.state.getBoolean("agreement_SIGN_IN"))
        if (error != null) { notice(error); return }
        model.state.remove("otpRetryAt")
        model.login()
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
            label.startsWith("Order ") && label.substringAfter("Order ").uppercase(Locale.US) in Screen.values().map { it.name } -> {
                if (screen == SELECT_ORDER) go(FEEDBACK_FILLED) else go(Screen.valueOf(label.substringAfter("Order ").uppercase(Locale.US)))
            }
            screen == PRIVACY && label == "Confirm Agreement" -> {
                if (!model.state.getBoolean("agreement_PRIVACY")) { notice("Please confirm your agreement"); return }
                consent.edit().putBoolean("accepted", true).apply()
                model.root(HOME)
            }
            screen == PRIVACY && label == "Reject and Exit" -> finish()
            label == "Agreement" || label.startsWith("By proceeding") -> {
                val container = view.parent as DesignStage
                val box = (0 until container.childCount).map(container::getChildAt).filterIsInstance<CheckBox>().firstOrNull()
                box?.toggle()
            }
            label == "Home" || label == "Start Application Now" -> model.root(model.home)
            label == "My Orders" -> model.root(model.orders)
            label == "Profile Center" -> model.root(model.profile)
            label == "Please sign in" -> go(SIGN_IN)
            label == "+233" -> openCountryDialog()
            label == "Get OTP" -> {
                AdjustAnalytics.otpButtonTapped()
                if (fields.firstOrNull()?.text?.length in 9..10) {
                    startOtpCooldown()
                    renderOtpButton()
                    notice("Demo verification code: 1234")
                } else notice("Please enter a valid phone number")
            }
            label == "Sign In" -> {
                AdjustAnalytics.loginButtonTapped()
                signIn()
            }
            label == "Privacy Policy" -> go(PRIVACY)
            label == "About Us" -> go(ABOUT)
            label == "Sign Out" -> go(LOGOUT)
            label == "Confirm" && screen == LOGOUT -> { feedback.clear(); model.signOut() }
            label.contains("Wallet Account") || label == "Receiving Account" -> go(if (model.signedIn) WALLET_SAVED else SIGN_IN)
            label == "Edit Now" -> go(WALLET_EDIT)
            label == "Feedback" || label.contains("feedback list") -> go(if (model.hasFeedback) FEEDBACK_LIST else FEEDBACK_EMPTY)
            label == "Write feedback" || label.startsWith("Please submit feedback") -> go(FEEDBACK_FORM)
            label.startsWith("Please describe your problem") -> go(FEEDBACK_PENDING)
            label == "Choose PDF" -> attachmentOptions(false)
            label == "Choose image" -> attachmentOptions(true)
            label == "Local photo album" -> pickFile("image/*")
            label == "Photograph" || label.endsWith(" photo") -> takePhoto()
            label == "Capture selfie" || label == "Upload a clear selfie" -> go(FACE_GUIDE)
            label == "Capture document" -> go(DOCUMENT_GUIDE)
            screen == DOCUMENT_PREVIEW -> go(IDENTITY_CARD)
            label == "Reshoot" -> go(FACE_GUIDE)
            label == "Submit application" -> model.placeOrder()
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
            label == "Repay Extension" -> go(if (screen == EXTENSION) EXTENSION_CONFIRM else EXTENSION)
            label == "Repay Now" -> notice("A repayment link is available for live orders only")
            label == "View Order" -> go(REVIEW_PENDING)
            else -> credit.orderDestination(label)?.let(::go)
        }
    }

    private fun submit() {
        if (!validate()) return
        when (screen) {
            FACE_PREVIEW -> go(IDENTITY_FILLED)
            IDENTITY_CARD, IDENTITY_FILLED, DRIVER_LICENSE, VOTER_CARD -> go(ORDER_DETAIL)
            WALLET_EDIT -> go(WALLET_SAVED)
            FEEDBACK_FORM, FEEDBACK_FILLED, FEEDBACK_ATTACHMENTS -> model.submitFeedback()
            RATING, RATING_CHANGED -> go(RATING_FEEDBACK)
            else -> leave()
        }
    }

    private fun confirm() {
        when (screen) {
            ORDER_SUCCESS -> model.root(REVIEW_COUNTDOWN)
            FEEDBACK_SUCCESS -> model.root(FEEDBACK_LIST)
            WITHDRAW_SUCCESS -> go(WALLET_SETUP)
            EXTENSION_CONFIRM -> notice("A repayment link is available for live orders only")
            KYC_EXIT -> model.root(model.home)
            else -> leave()
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
        try { startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 32) }
        catch (_: android.content.ActivityNotFoundException) { notice("No camera is available") }
    }

    @Deprecated("Platform callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == 31 && data?.data != null) {
            val file = data.data!!
            val added = feedback.attach(file.toString(), contentResolver.getType(file)?.startsWith("image/") == true)
            if (screen == ATTACHMENT_SOURCE) leave()
            notice(if (added) "File selected" else "Up to 9 attachments are allowed")
        } else if (requestCode == 32) {
            preview = data?.extras?.get("data") as? Bitmap
            when (screen) {
                DOCUMENT_GUIDE -> go(DOCUMENT_PREVIEW)
                ATTACHMENT_SOURCE -> { leave(); notice("Photo selected") }
                else -> go(FACE_PREVIEW)
            }
        }
    }
}
