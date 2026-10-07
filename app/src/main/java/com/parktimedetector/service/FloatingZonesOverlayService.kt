package com.parktimedetector.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.parktimedetector.R
import com.parktimedetector.network.MyParkingApiClient
import com.parktimedetector.network.ZoneWithPrice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Floating Window Manager that displays the live list of nearby zones sorted by cheapest price.
 */
object FloatingZonesOverlayService {

    private const val TAG = "FloatingZonesOverlay"

    private val scope = CoroutineScope(Dispatchers.Main)
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var currentParams: WindowManager.LayoutParams? = null
    private var isPillMode = false
    private var fetchJob: Job? = null

    var onZoneSelectedCallback: ((ZoneWithPrice) -> Unit)? = null

    fun isShowing(): Boolean = overlayView != null

    /**
     * Displays the floating window over the screen with live fetched cheapest zones.
     * Automatically resolves current device GPS coordinates if lat/lng are omitted or default.
     */
    fun show(context: Context, lat: Double? = null, lng: Double? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot show floating overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        // Resolve location: use provided coordinates or query last known device location, fallback to Calgary Downtown
        val resolvedLat: Double
        val resolvedLng: Double
        if (lat != null && lng != null) {
            resolvedLat = lat
            resolvedLng = lng
        } else {
            val deviceLoc = com.parktimedetector.location.LocationHelper.getLastKnownLocation(context)
            if (deviceLoc != null) {
                resolvedLat = deviceLoc.latitude
                resolvedLng = deviceLoc.longitude
            } else {
                resolvedLat = 51.0486
                resolvedLng = -114.0708
            }
        }

        if (overlayView != null) {
            refreshData(context, resolvedLat, resolvedLng)
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_floating_zones_overlay, null)
        overlayView = view

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = context.resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost(dpToPx(context, 400))

        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (displayMetrics.widthPixels - width) / 2
            y = dpToPx(context, 100)
        }
        currentParams = params

        setupInteractions(context, view, params, resolvedLat, resolvedLng)

        try {
            wm.addView(view, params)
            refreshData(context, resolvedLat, resolvedLng)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach floating overlay window", e)
            overlayView = null
        }
    }

    fun hide() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        overlayView = null
        isPillMode = false
        fetchJob?.cancel()
    }

    private fun setupInteractions(
        context: Context,
        view: View,
        params: WindowManager.LayoutParams,
        lat: Double,
        lng: Double
    ) {
        val layoutCard = view.findViewById<LinearLayout>(R.id.layout_floating_card)
        val layoutPill = view.findViewById<LinearLayout>(R.id.layout_floating_pill)
        val dragHandle = view.findViewById<View>(R.id.view_floating_drag_handle)
        val btnClose = view.findViewById<TextView>(R.id.btn_floating_close)
        val btnMinimize = view.findViewById<TextView>(R.id.btn_floating_minimize)
        val btnRefresh = view.findViewById<TextView>(R.id.btn_floating_refresh)

        // Make window draggable
        makeDraggable(context, view, dragHandle, params)
        makeDraggable(context, view, layoutPill, params) {
            // Clicking the pill expands back to card
            expandFromPill(context, view, params)
        }

        btnClose.setOnClickListener {
            hide()
        }

        btnMinimize.setOnClickListener {
            collapseToPill(context, view, params)
        }

        btnRefresh.setOnClickListener {
            refreshData(context, lat, lng)
        }
    }

    private fun collapseToPill(context: Context, view: View, params: WindowManager.LayoutParams) {
        val layoutCard = view.findViewById<LinearLayout>(R.id.layout_floating_card)
        val layoutPill = view.findViewById<LinearLayout>(R.id.layout_floating_pill)

        isPillMode = true
        layoutCard.visibility = View.GONE
        layoutPill.visibility = View.VISIBLE

        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    private fun expandFromPill(context: Context, view: View, params: WindowManager.LayoutParams) {
        val layoutCard = view.findViewById<LinearLayout>(R.id.layout_floating_card)
        val layoutPill = view.findViewById<LinearLayout>(R.id.layout_floating_pill)

        val displayMetrics = context.resources.displayMetrics
        val cardWidth = (displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost(dpToPx(context, 400))

        isPillMode = false
        layoutPill.visibility = View.GONE
        layoutCard.visibility = View.VISIBLE

        params.width = cardWidth
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    private fun refreshData(context: Context, lat: Double, lng: Double) {
        val view = overlayView ?: return
        val container = view.findViewById<LinearLayout>(R.id.container_floating_zones_list) ?: return
        val subtitle = view.findViewById<TextView>(R.id.tv_floating_subtitle)
        val tvPillPrice = view.findViewById<TextView>(R.id.tv_floating_pill_price)

        subtitle?.text = "Fetching live municipal zones & rates..."

        fetchJob?.cancel()
        fetchJob = scope.launch(Dispatchers.IO) {
            val zones = MyParkingApiClient.fetchNearbyZonesWithPrices(lat, lng)

            launch(Dispatchers.Main) {
                if (overlayView == null) return@launch
                container.removeAllViews()

                if (zones.isEmpty()) {
                    subtitle?.text = "No parking zones found nearby."
                    return@launch
                }

                val cheapest = zones.first()
                subtitle?.text = "Ranked ${zones.size} nearby zones • Cheapest: ${cheapest.cheapestPrice.displayPrice}"
                tvPillPrice?.text = cheapest.cheapestPrice.displayPrice

                val inflater = LayoutInflater.from(context)
                zones.forEachIndexed { index, zone ->
                    val rowView = inflater.inflate(R.layout.item_floating_zone_row, container, false)
                    bindZoneRow(rowView, zone, isFirst = index == 0)
                    rowView.setOnClickListener {
                        onZoneSelectedCallback?.invoke(zone)
                    }
                    container.addView(rowView)
                }
            }
        }
    }

    private fun bindZoneRow(view: View, zone: ZoneWithPrice, isFirst: Boolean) {
        val tvNumber = view.findViewById<TextView>(R.id.tv_row_zone_number)
        val tvType = view.findViewById<TextView>(R.id.tv_row_zone_type)
        val tvPrice = view.findViewById<TextView>(R.id.tv_row_price_pill)
        val tvAddress = view.findViewById<TextView>(R.id.tv_row_address)
        val tvMaxTime = view.findViewById<TextView>(R.id.tv_row_max_time)
        val tvStallType = view.findViewById<TextView>(R.id.tv_row_stall_type)
        val tvBrz = view.findViewById<TextView>(R.id.tv_row_brz)
        val tvTag = view.findViewById<TextView>(R.id.tv_row_tag)

        tvNumber.text = zone.zoneNumber
        tvType.text = zone.zoneType ?: if (zone.isLot) "Surface Lot" else "Parking Zone"
        tvAddress.text = zone.address

        tvPrice.text = zone.cheapestPrice.displayPrice
        if (zone.cheapestPrice.isFree) {
            tvPrice.setTextColor(view.context.getColor(R.color.emerald_green))
        } else if (zone.cheapestPrice.numericRatePerHour <= 2.0) {
            tvPrice.setTextColor(view.context.getColor(R.color.accent_cyan))
        } else {
            tvPrice.setTextColor(view.context.getColor(R.color.text_primary))
        }

        tvMaxTime.text = zone.maxTimeMinutes?.let { "⏱ ${it}m max" } ?: "⏱ Flexible"
        tvStallType.text = zone.stallType?.let { "• $it" } ?: ""
        tvBrz.text = zone.brzName?.let { "• $it" } ?: ""

        if (isFirst) {
            tvTag.visibility = View.VISIBLE
            tvTag.text = if (zone.cheapestPrice.isFree) "⭐ FREE" else "⭐ BEST RATE"
        } else {
            tvTag.visibility = View.GONE
        }
    }

    private fun makeDraggable(
        context: Context,
        rootView: View,
        touchTarget: View,
        params: WindowManager.LayoutParams,
        onClick: (() -> Unit)? = null
    ) {
        val wm = windowManager ?: return
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        touchTarget.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val displayMetrics = context.resources.displayMetrics
                        val maxX = (displayMetrics.widthPixels - params.width).coerceAtLeast(0)
                        val maxY = (displayMetrics.heightPixels - dpToPx(context, 80)).coerceAtLeast(0)
                        params.x = (initialX + dx).coerceIn(0, maxX)
                        params.y = (initialY + dy).coerceIn(dpToPx(context, 24), maxY)
                        try {
                            wm.updateViewLayout(rootView, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        if (onClick != null) {
                            onClick()
                        } else {
                            v.performClick()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
