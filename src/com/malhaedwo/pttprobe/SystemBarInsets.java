package com.malhaedwo.pttprobe;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;

/** Platform-only safe viewport for the two full-screen, scrolling activities (min API 31). */
final class SystemBarInsets {
    private SystemBarInsets() {}

    static void setContentView(Activity activity, View content, int backgroundColor) {
        Window window = activity.getWindow();
        // Explicit on API 31-34 as well as target-35's enforced edge-to-edge behavior.
        window.setDecorFitsSystemWindows(false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        // Insets belong to a non-scrolling parent, not ScrollView padding. Its clipped
        // content viewport prevents scrolling children/overscroll from painting over bars.
        FrameLayout safeRoot = new FrameLayout(activity);
        safeRoot.setBackgroundColor(backgroundColor);
        safeRoot.setClipChildren(true);
        safeRoot.setClipToPadding(true);
        safeRoot.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        safeRoot.setOnApplyWindowInsetsListener((view, insets) -> {
            int handled = WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout();
            Insets safe = insets.getInsets(handled);
            // The dedicated wrapper starts with zero padding. Assign absolute values so
            // redispatch, rotation and navigation-mode changes never accumulate padding.
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            // Only remove the types handled here; preserve IME and other inset dispatch.
            return new WindowInsets.Builder(insets)
                    .setInsets(handled, Insets.NONE)
                    .setInsetsIgnoringVisibility(handled, Insets.NONE)
                    .setDisplayCutout(null)
                    .build();
        });
        safeRoot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                view.requestApplyInsets();
            }
            @Override public void onViewDetachedFromWindow(View view) {}
        });
        activity.setContentView(safeRoot);
        // PhoneWindow.getInsetsController() dereferences its DecorView. setContentView
        // must create that decor first (verified on Galaxy S25 / Android 16).
        WindowInsetsController controller = window.getInsetsController();
        if (controller != null) {
            int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(lightBars, lightBars);
        }
        if (safeRoot.isAttachedToWindow()) safeRoot.requestApplyInsets();
    }
}
