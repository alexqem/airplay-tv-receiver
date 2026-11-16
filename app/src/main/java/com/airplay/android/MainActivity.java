package com.airplay.android;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "MainActivity";
    /** How long the overlay stays visible after the remote is used, in ms. */
    private static final long CONTROLS_TIMEOUT_MS = 6000;
    /** Vendor TVs stop the service on their own (dream/screensaver churn); re-check at this rate. */
    private static final long SERVICE_GUARD_PERIOD_MS = 4000;

    private FrameLayout root;
    private SurfaceView surfaceView;
    /** Wallpaper shown while nothing is being mirrored, covering the surface underneath. */
    private ImageView background;
    /** Moulding drawn round the mirrored picture, sized to it whenever the geometry changes. */
    private View mirrorFrame;
    private LinearLayout controls;
    private Button btnToggle;
    private Button btnOverlay;
    private TextView statusText;
    private TextView ipText;
    private boolean serverRunning = false;
    private int videoWidth;
    private int videoHeight;
    /** Width of one side of the moulding, in pixels. */
    private int frameWidthPx;

    private final android.os.Handler mainHandler = new android.os.Handler(
            android.os.Looper.getMainLooper());

    private final Runnable serviceGuard = new Runnable() {
        @Override
        public void run() {
            if (!AirPlayService.userStopped && !AirPlayService.isRunning()) {
                Log.w(TAG, "Receiver died on its own, restarting the service");
                startServer();
            }
            mainHandler.postDelayed(this, SERVICE_GUARD_PERIOD_MS);
        }
    };

    private final Runnable hideControls = new Runnable() {
        @Override
        public void run() {
            // Only get out of the way once there is actually a picture to look at
            if (videoWidth > 0) {
                controls.setVisibility(View.GONE);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        surfaceView = findViewById(R.id.surface_view);
        background = findViewById(R.id.background);
        mirrorFrame = findViewById(R.id.mirror_frame);
        controls = findViewById(R.id.controls);
        btnToggle = findViewById(R.id.btn_toggle);
        btnOverlay = findViewById(R.id.btn_overlay);
        statusText = findViewById(R.id.status_text);
        ipText = findViewById(R.id.ip_text);

        frameWidthPx = getResources().getDimensionPixelSize(R.dimen.mirror_frame_width);

        surfaceView.getHolder().addCallback(this);

        // Keep the letterbox up to date when the window itself changes size
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyVideoAspectRatio());

        VideoHolder.setVideoSizeListener((width, height) -> runOnUiThread(() -> {
            if (width == 0 || height == 0) {
                // Session torn down: back to the wallpaper and the controls
                videoWidth = 0;
                videoHeight = 0;
                background.setVisibility(View.VISIBLE);
                mirrorFrame.setVisibility(View.GONE);
                controls.removeCallbacks(hideControls);
                controls.setVisibility(View.VISIBLE);
                statusText.setText(serverRunning ? getString(R.string.status_running)
                        : getString(R.string.status_idle));
                return;
            }
            boolean firstFrame = videoWidth == 0;
            videoWidth = width;
            videoHeight = height;
            applyVideoAspectRatio();
            statusText.setText(getString(R.string.status_connected) + " · " + width + "x" + height);
            if (firstFrame) {
                // A mirroring session just began: give the whole screen to the video
                background.setVisibility(View.GONE);
                scheduleHideControls(0);
            }
        }));

        btnToggle.setOnClickListener(v -> toggleServer());
        btnOverlay.setOnClickListener(v -> requestOverlayPermission());

        // POST_NOTIFICATIONS is deliberately not requested: the system dialog covers the
        // activity on TV, onStop then stops the service and the receiver goes offline until
        // the dialog is answered. The foreground service runs fine without it - only the
        // status notification stays hidden.

        displayIpAddress();
    }

    /**
     * The receiver is tied to the UI being on screen: mirroring can only be decoded into this
     * Activity's Surface, so the device advertises itself exactly while the app is visible.
     */
    @Override
    protected void onStart() {
        super.onStart();
        // The service is persistent (runs from first launch / boot); this only repairs a
        // receiver that was stopped by hand or killed outside our control
        if (!AirPlayService.userStopped && !AirPlayService.isRunning()) {
            startServer();
        } else {
            serverRunning = AirPlayService.isRunning();
            btnToggle.setText(getString(serverRunning
                    ? R.string.btn_stop : R.string.btn_start));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateOverlayButton();
        mainHandler.removeCallbacks(serviceGuard);
        mainHandler.postDelayed(serviceGuard, SERVICE_GUARD_PERIOD_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(serviceGuard);
    }

    private void displayIpAddress() {
        String name = AirPlayService.resolveServiceName(this);
        try {
            String ip = getLocalIpAddress();
            ipText.setText(name + "  ·  " + (ip != null ? ip : "no network"));
        } catch (Exception e) {
            ipText.setText(name);
        }
    }

    private String getLocalIpAddress() {
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP", e);
        }
        return null;
    }

    /**
     * Sizes the surface so the decoded picture keeps its own aspect ratio inside the
     * window (letterbox), and hangs the frame round it. Without this the codec stretches
     * every frame to the full screen, so any resolution change on the sender shows up as a
     * distorted image.
     *
     * <p>The picture is given the window less the width of the moulding, so the frame ends up
     * flush with the top and bottom of the screen and the wallpaper shows down the sides of it.
     */
    private void applyVideoAspectRatio() {
        if (videoWidth <= 0 || videoHeight <= 0) return;

        int inset = 2 * frameWidthPx;
        int containerWidth = root.getWidth() - inset;
        int containerHeight = root.getHeight() - inset;
        if (containerWidth <= 0 || containerHeight <= 0) return;

        float videoAspect = (float) videoWidth / videoHeight;
        float containerAspect = (float) containerWidth / containerHeight;

        int width;
        int height;
        if (videoAspect > containerAspect) {
            width = containerWidth;
            height = Math.round(containerWidth / videoAspect);
        } else {
            height = containerHeight;
            width = Math.round(containerHeight * videoAspect);
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surfaceView.getLayoutParams();
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            surfaceView.setLayoutParams(params);
            Log.i(TAG, "Video " + videoWidth + "x" + videoHeight + " letterboxed to "
                    + width + "x" + height);
        }

        FrameLayout.LayoutParams frameParams =
                (FrameLayout.LayoutParams) mirrorFrame.getLayoutParams();
        int frameWidth = width + 2 * frameWidthPx;
        int frameHeight = height + 2 * frameWidthPx;
        if (frameParams.width != frameWidth || frameParams.height != frameHeight) {
            frameParams.width = frameWidth;
            frameParams.height = frameHeight;
            mirrorFrame.setLayoutParams(frameParams);
        }
        mirrorFrame.setVisibility(View.GONE);
    }

    /** Brings the overlay back, then hides it again after a while. */
    private void revealControls() {
        controls.setVisibility(View.VISIBLE);
        scheduleHideControls(CONTROLS_TIMEOUT_MS);
    }

    private void scheduleHideControls(long delayMs) {
        controls.removeCallbacks(hideControls);
        controls.postDelayed(hideControls, delayMs);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Any remote key press brings the status/stop overlay back
        if (event.getAction() == KeyEvent.ACTION_DOWN && controls.getVisibility() != View.VISIBLE) {
            revealControls();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            scheduleHideControls(CONTROLS_TIMEOUT_MS);
        }
        return super.dispatchKeyEvent(event);
    }

    private void requestOverlayPermission() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            // Some TV builds only offer the global list
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            revealControls();
        }
        return super.onTouchEvent(event);
    }

    private void toggleServer() {
        if (!serverRunning) {
            AirPlayService.userStopped = false;
            startServer();
        } else {
            stopServer();
        }
    }

    private void startServer() {
        AirPlayService.start(this);

        serverRunning = true;
        btnToggle.setText(R.string.btn_stop);
        statusText.setText(R.string.status_running);
        displayIpAddress();
    }

    private void stopServer() {
        AirPlayService.requestStop(this);

        serverRunning = false;
        videoWidth = 0;
        videoHeight = 0;
        btnToggle.setText(R.string.btn_start);
        statusText.setText(R.string.status_idle);
        controls.setVisibility(View.VISIBLE);
        background.setVisibility(View.VISIBLE);
        mirrorFrame.setVisibility(View.GONE);
    }

    /** Overlay permission is what lets a cast or mirror open its screen from the background. */
    private void updateOverlayButton() {
        boolean granted = Settings.canDrawOverlays(this);
        btnOverlay.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "Surface created");
        VideoHolder.setSurface(holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "Surface changed: " + width + "x" + height);
        VideoHolder.setSurface(holder.getSurface());
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "Surface destroyed");
        VideoHolder.release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VideoHolder.setVideoSizeListener(null);
        controls.removeCallbacks(hideControls);
        mainHandler.removeCallbacks(serviceGuard);
    }
}
