package com.flx_apps.digitaldetox.system_integration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.view.View
import android.view.WindowManager
import timber.log.Timber

/**
 * Manages a full-screen grayscale overlay using the WindowManager and AccessibilityService overlay type.
 *
 * This replaces the previous system-wide grayscale implementation that used Settings.Secure,
 * which required WRITE_SECURE_SETTINGS permission (only available with Shizuku or adb).
 *
 * The overlay approach:
 * - Uses TYPE_ACCESSIBILITY_OVERLAY to display without requiring special permissions
 * - Applies grayscale by drawing a semi-transparent neutral overlay
 * - Supports dynamic alpha control for brightness simulation
 * - Prevents duplicate overlays automatically
 * - Works on older Android versions (8+) and custom ROMs
 * 
 * IMPORTANT: Must be called from AccessibilityService context to work properly.
 * The AccessibilityService provides a valid window token unlike applicationContext.
 */
object GrayscaleOverlayManager {
    /**
     * The current overlay view, or null if not displayed.
     */
    private var overlayView: GrayscaleOverlayView? = null

    /**
     * The WindowManager instance used to add/remove overlays.
     */
    private var windowManager: WindowManager? = null

    /**
     * The current alpha value of the overlay (0.0f to 1.0f).
     * Used to simulate the extra dim effect.
     */
    private var currentAlpha: Float = 1.0f

    /**
     * Shows the grayscale overlay with the specified alpha.
     * If an overlay is already shown, it will be updated instead of creating a duplicate.
     *
     * CRITICAL: Context must be from AccessibilityService (not applicationContext)
     * to have a valid window token for TYPE_ACCESSIBILITY_OVERLAY.
     *
     * @param context AccessibilityService context (NOT applicationContext)
     * @param alpha Overlay opacity from 0.0f (transparent) to 1.0f (fully opaque)
     */
    fun showOverlay(context: Context, alpha: Float = 1.0f) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

            if (wm == null) {
                Timber.e("GrayscaleOverlayManager: WindowManager not available")
                return
            }

            windowManager = wm
            currentAlpha = alpha.coerceIn(0.0f, 1.0f)

            // If overlay is already shown, just update the alpha
            if (overlayView != null) {
                overlayView?.setOverlayAlpha(currentAlpha)
                Timber.d("GrayscaleOverlayManager: Updated existing overlay with alpha=$currentAlpha")
                return
            }

            Timber.d("GrayscaleOverlayManager: Creating new grayscale overlay with alpha=$currentAlpha")

            // Create a new overlay view using the service context
            overlayView = GrayscaleOverlayView(context).apply {
                setOverlayAlpha(currentAlpha)
            }

            // Configure layout parameters for accessibility overlay
            val layoutParams = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = android.graphics.PixelFormat.RGBA_8888
                flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                x = 0
                y = 0
            }

            wm.addView(overlayView, layoutParams)
            Timber.i("GrayscaleOverlayManager: ✓ Grayscale overlay successfully added to window")
        } catch (e: Exception) {
            Timber.e(e, "GrayscaleOverlayManager: ✗ Failed to show overlay - ${e.message}")
        }
    }

    /**
     * Removes the grayscale overlay from the screen.
     * Safe to call even if no overlay is currently shown.
     */
    fun removeOverlay() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                Timber.i("GrayscaleOverlayManager: ✓ Grayscale overlay removed")
            }
        } catch (e: Exception) {
            Timber.e(e, "GrayscaleOverlayManager: ✗ Failed to remove overlay - ${e.message}")
            overlayView = null
        }
    }

    /**
     * Updates the alpha (opacity) of the overlay without removing/re-adding it.
     * This is used to simulate the extra dim effect.
     *
     * @param alpha New alpha value from 0.0f (transparent) to 1.0f (fully opaque)
     */
    fun updateAlpha(alpha: Float) {
        val newAlpha = alpha.coerceIn(0.0f, 1.0f)
        if (newAlpha != currentAlpha) {
            currentAlpha = newAlpha
            overlayView?.setOverlayAlpha(currentAlpha)
            Timber.d("GrayscaleOverlayManager: Updated overlay alpha to $currentAlpha")
        }
    }

    /**
     * Checks if the grayscale overlay is currently visible.
     *
     * @return true if overlay is shown, false otherwise
     */
    fun isOverlayVisible(): Boolean = overlayView != null

    /**
     * Gets the current alpha of the overlay.
     *
     * @return alpha value from 0.0f to 1.0f
     */
    fun getCurrentAlpha(): Float = currentAlpha

    /**
     * A custom View that renders a grayscale overlay using a neutral color tint.
     * This view desaturates the screen by drawing a semi-transparent overlay that combines
     * with the screen content to create a grayscale effect.
     * This view is never interactive (FLAG_NOT_TOUCHABLE) so it doesn't consume user input.
     */
    private class GrayscaleOverlayView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var viewAlpha: Float = 1.0f

        init {
            // Use a neutral gray color that when blended with content creates grayscale effect
            // The alpha channel will be controlled via viewAlpha
            paint.color = Color.BLACK
            // Set a neutral blend mode that works on all devices
            setLayerType(LAYER_TYPE_HARDWARE, paint)
            Timber.d("GrayscaleOverlayView: Initialized")
        }

        /**
         * Set the alpha (opacity) of the overlay.
         *
         * @param alpha from 0.0f (transparent) to 1.0f (fully opaque)
         */
        fun setOverlayAlpha(alpha: Float) {
            viewAlpha = alpha.coerceIn(0.0f, 1.0f)
            // Paint alpha uses 0-255 range
            paint.alpha = (viewAlpha * 255).toInt()
            invalidate()
            Timber.d("GrayscaleOverlayView.setOverlayAlpha: alpha=$viewAlpha, paint.alpha=${paint.alpha}")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            try {
                // We need to create a grayscale effect by drawing a saturated overlay
                // The approach: draw a semi-transparent neutral gray that reduces color perception
                // This creates a visible grayscale-like effect when layered over content
                
                val saveCount = canvas.save()
                
                // Use a darker gray color with alpha blending for visible effect
                // This desaturates the underlying content when blended
                // Color: a neutral gray (128, 128, 128) with controlled alpha
                paint.color = Color.rgb(128, 128, 128)
                
                // IMPORTANT: Setting paint.color resets alpha to 255, so we must re-apply it
                paint.alpha = (viewAlpha * 255).toInt()
                
                // Clear any previous Xfermode to use standard alpha blending
                paint.setXfermode(null)
                
                // The paint now has the correct alpha set from setOverlayAlpha()
                // Draw a full screen rectangle with the semi-transparent gray
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                
                // Restore canvas state
                canvas.restoreToCount(saveCount)
                
                Timber.v("GrayscaleOverlayView.onDraw: Drew overlay at ${width}x$height with alpha=$viewAlpha, paint.alpha=${paint.alpha}")
            } catch (e: Exception) {
                Timber.e(e, "GrayscaleOverlayView.onDraw: Failed to draw - ${e.message}")
            }
        }
    }
}
