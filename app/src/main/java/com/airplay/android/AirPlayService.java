package com.airplay.android;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.airplay.android.server.AirPlayServer;
import com.airplay.android.dlna.DlnaController;
import com.airplay.android.dlna.DlnaRenderer;
import com.github.serezhka.jap2lib.AirPlay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hosts the AirPlay receiver for as long as the app is open.
 *
 * <p>The receiver only advertises itself while the UI is around, since mirroring needs the
 * Activity's Surface to decode into. Leaving the app therefore takes the device off the
 * AirPlay list. Running as a foreground service is still worthwhile: it keeps the multicast
 * lock (mDNS), the Wi-Fi lock (power save would otherwise stall the streams) and the network
 * threads at foreground priority while a session is live.
 */
public class AirPlayService extends Service {

    private static final String TAG = "AirPlayService";
    private static final String CHANNEL_ID = "airplay_channel";
    private static final int NOTIFICATION_ID = 1;

    /**
     * Start and stop are serialised on one thread that outlives the service instance, because
     * shutting the Netty groups down takes seconds. Leaving the app and coming straight back
     * would otherwise start a new instance while the old one is still tearing the server down,
     * and the new start would be dropped as a duplicate.
     */
    private static final ExecutorService LIFECYCLE = Executors.newSingleThreadExecutor();
    private static AirPlayServer sServer;
    private static DlnaRenderer sDlnaRenderer;
    /** Application context captured at onCreate for background UI launches and callbacks. */
    private static Context sAppContext;

    /** Set when the receiver was stopped by hand, so visibility alone does not restart it. */
    public static volatile boolean userStopped = false;

    /**
     * The receiver runs permanently once started: both AirPlay and DLNA are useless when they
     * only exist while the app happens to be on screen (vendor TVs bury it under their home,
     * screensaver and cast overlays within seconds). Playback surfaces come later - mirroring
     * pulls the main screen up, a cast opens the player - and that UI launch from a background
     * service needs the "draw over other apps" permission, which {@link #launchUi} honours.
     */
    public static void start(Context ctx) {
        Intent intent = new Intent(ctx, AirPlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, AirPlayService.class));
    }

    /** Receiver screen asking for an immediate stop (the toggle button). */
    public static void requestStop(Context ctx) {
        userStopped = true;
        stop(ctx.getApplicationContext());
    }

    /** True while the receiver (AirPlay + DLNA) is up; lets the UI notice an outside stop. */
    public static boolean isRunning() {
        return sServer != null;
    }

    /**
     * Brings receiver UI up from wherever the TV currently is. With the overlay permission the
     * activity is started right away; without it a tappable notification is posted instead.
     *
     * @return true when the activity was started, false when only a notification was offered
     */
    public static boolean launchUi(Context ctx, Class<? extends Activity> activity, String title) {
        Context app = sAppContext != null ? sAppContext
                : (ctx != null ? ctx.getApplicationContext() : null);
        if (app == null) return false;
        if (Settings.canDrawOverlays(app)) {
            Intent intent = new Intent(app, activity);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                app.startActivity(intent);
                return true;
            } catch (Exception e) {
                Log.w(TAG, "Background activity start failed, falling back to notification", e);
            }
        }
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) return false;
        Intent open = new Intent(app, activity);
        PendingIntent contentIntent = PendingIntent.getActivity(app, activity.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(app, CHANNEL_ID)
                : new Notification.Builder(app);
        builder.setContentTitle(title)
                .setContentText(app.getString(R.string.tap_to_open))
                .setContentIntent(contentIntent)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setAutoCancel(true);
        // A separate id from the foreground notification so it does not get overwritten
        nm.notify(2000, builder.build());
        return false;
    }

    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        sAppContext = getApplicationContext();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String serverName = resolveServiceName(this);
        String deviceId = resolveDeviceId(this);
        startForeground(NOTIFICATION_ID, buildNotification(serverName + " · starting"));

        acquireLocks();
        DlnaController.get().setHostContext(this);
        LIFECYCLE.submit(() -> startServer(serverName, deviceId));
        // Stick around: the system restarts the receiver if it kills the process for memory
        return START_STICKY;
    }

    /**
     * Builds the name broadcast over mDNS from the device's own name, so two receivers on the
     * same network stay apart. A fixed name made jmDNS fall back to "name (2)" as soon as the
     * app ran on a second device.
     *
     * <p>{@code global device_name} is what the user typed in Settings; Android TV boxes that
     * leave it empty usually still carry the Bluetooth name, and the model is the last resort.
     */
    static String resolveServiceName(Context context) {
        String deviceName = Settings.Global.getString(
                context.getContentResolver(), Settings.Global.DEVICE_NAME);
        if (TextUtils.isEmpty(deviceName)) {
            deviceName = Settings.Secure.getString(context.getContentResolver(), "bluetooth_name");
        }
        if (TextUtils.isEmpty(deviceName)) {
            deviceName = Build.MODEL;
        }
        if (TextUtils.isEmpty(deviceName)) {
            deviceName = context.getString(R.string.airplay_fallback_name);
        }
        return deviceName.trim() + " " + context.getString(R.string.airplay_name_suffix);
    }

    /**
     * A stand-in MAC address, derived so it stays the same across restarts but differs between
     * installs. iOS pairs the {@code _airplay} and {@code _raop} records of one receiver by this
     * id, so the value that used to be hardcoded made two devices look like a single one.
     */
    private static String resolveDeviceId(Context context) {
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String seed = TextUtils.isEmpty(androidId) ? Build.FINGERPRINT : androidId;
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is guaranteed to be present", e);
        }
        byte[] mac = Arrays.copyOf(digest, 6);
        // Mark it locally administered and unicast, the shape a made-up MAC is meant to have
        mac[0] = (byte) ((mac[0] & 0xFE) | 0x02);
        StringBuilder hex = new StringBuilder(12);
        for (byte b : mac) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }

    /** Runs on {@link #LIFECYCLE}. */
    private void startServer(String serverName, String deviceId) {
        if (sServer != null) {
            Log.i(TAG, "Server already running, ignoring duplicate start");
            return;
        }
        try {
            // Build the Ed25519 identity keypair off to one side while mDNS comes up and the
            // sender is still finding us, so the ~0.5s it costs the first time is not spent
            // mid-handshake. The generator is shared and guarded, so a connection arriving before
            // this finishes simply waits on the same one-time build rather than starting a second.
            new Thread(AirPlay::prewarmCrypto, "airplay-crypto-prewarm").start();

            AirPlayServer server = new AirPlayServer(serverName, deviceId, 7000, 5000, new VideoCallback());
            server.start();
            sServer = server;
            Log.i(TAG, "AirPlay server started as '" + serverName + "' (id " + deviceId + ")");

            startDlna(serverName, deviceId);

            updateNotification(serverName + " · ready");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AirPlay server", e);
            updateNotification("Failed to start, will retry on next launch");
            stopSelf();
        }
    }

    /**
     * The DLNA renderer fails soft: a broken UPnP stack must not take the AirPlay receiver
     * down with it, so any startup error is logged and playback stays AirPlay-only.
     */
    private static void startDlna(String serverName, String deviceId) {
        try {
            DlnaRenderer renderer = new DlnaRenderer(serverName, resolveDlnaUuid(deviceId));
            renderer.start();
            sDlnaRenderer = renderer;
            Log.i(TAG, "DLNA renderer started as '" + serverName + "'");
        } catch (Exception e) {
            Log.e(TAG, "DLNA renderer failed to start, continuing without it", e);
        }
    }

    /** Stretches the 12-hex device id into the 8-4-4-4-12 shape UPnP device UUIDs must have. */
    private static String resolveDlnaUuid(String deviceId) {
        StringBuilder hex = new StringBuilder();
        while (hex.length() < 32) {
            hex.append(deviceId);
        }
        String full = hex.substring(0, 32);
        return String.format("%s-%s-%s-%s-%s",
                full.substring(0, 8), full.substring(8, 12), full.substring(12, 16),
                full.substring(16, 20), full.substring(20, 32));
    }

    /** Runs on {@link #LIFECYCLE}. */
    private static void stopServer() {
        if (sDlnaRenderer != null) {
            sDlnaRenderer.stop();
            sDlnaRenderer = null;
            DlnaController.get().onPlaybackStopped();
            Log.i(TAG, "DLNA renderer stopped");
        }
        if (sServer == null) return;
        sServer.stop();
        sServer = null;
        Log.i(TAG, "AirPlay server stopped");
    }

    private void acquireLocks() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi != null) {
            // mDNS is multicast: without this lock the Wi-Fi stack drops the packets
            multicastLock = wifi.createMulticastLock("airplay_mdns");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();

            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "airplay_wifi");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }

        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (power != null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "airplay:receiver");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        }
        Log.i(TAG, "Locks acquired (multicast/wifi/wake)");
    }

    private void releaseLocks() {
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        multicastLock = null;
        wifiLock = null;
        wakeLock = null;
    }

    /**
     * The user swiped the app away: stop advertising instead of lingering in the background.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "Task removed, shutting the receiver down");
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    public void onDestroy() {
        super.onDestroy();
        LIFECYCLE.submit(AirPlayService::stopServer);
        releaseLocks();
        Log.i(TAG, "AirPlay service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_channel_desc));
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return builder
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setOngoing(true)
                .build();
    }

    private static class VideoCallback implements VideoCallbackInterface {
        @Override
        public void onVideo(byte[] video, long ptsUs) {
            VideoHolder.onVideoData(video, ptsUs);
        }

        @Override
        public void onVideoFormat(int width, int height) {
            Log.i("AirPlayService", "Video format: " + width + "x" + height);
            // A mirror session produces frames whether or not the UI is up; if the sender
            // started while the app was buried, pull the main screen forward (overlay
            // permission) or offer a notification to open it
            launchUi(null, MainActivity.class, "AirPlay mirroring started");
            VideoHolder.onVideoFormat(width, height);
        }

        @Override
        public void onAudio(byte[] audio, long rtpTime) {
            VideoHolder.onAudioData(audio, rtpTime);
        }

        @Override
        public void onAudioFormat(int sampleRate, int channels) {
            Log.i("AirPlayService", "Audio format: " + sampleRate + "Hz " + channels + "ch");
        }

        @Override
        public void onSpsPps(byte[] spsPps) {
            VideoHolder.onSpsPpsData(spsPps);
        }
    }
}
