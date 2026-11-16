package com.airplay.android.dlna;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridge between the DLNA control plane (SOAP actions arriving in {@link DlnaRenderer}) and
 * whatever is actually playing the media ({@link DlnaPlayerActivity}).
 *
 * <p>A control point sends {@code SetAVTransportURI} and {@code Play} in quick succession,
 * often before the player activity has finished starting. The controller therefore keeps the
 * last requested state and replays it to the player as soon as one attaches.
 */
public final class DlnaController {

    private static final String TAG = "DlnaController";
    /** UPnP transport states the control point polls for. */
    public static final String STATE_STOPPED = "STOPPED";
    public static final String STATE_PLAYING = "PLAYING";
    public static final String STATE_PAUSED = "PAUSED_PLAYBACK";
    public static final String STATE_TRANSITIONING = "TRANSITIONING";

    /** Implemented by the player activity while it is on screen. */
    public interface Player {
        void onPlay(String uri, String title);

        void onPauseRequested();

        void onResumeRequested();

        void onStopRequested();

        void onSeek(long positionMs);

        void onVolume(int percent);
    }

    private static final DlnaController INSTANCE = new DlnaController();
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<dc:title>(.*?)</dc:title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    /** hh:mm:ss[:frac] as the UPnP timing format. */
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(\\d+):(\\d{1,2}):(\\d{1,2})(?:\\.\\d+)?");

    private final AtomicReference<Player> player = new AtomicReference<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Application context, set once by the hosting service and used to launch the player. */
    private Context hostContext;
    private String currentUri;
    private String currentTitle = "";
    private String transportState = STATE_STOPPED;
    /** Set while an activity start is in flight, cleared once the player takes over. */
    private boolean launchPending;
    private volatile long positionMs;
    private volatile long durationMs;

    public static DlnaController get() {
        return INSTANCE;
    }

    public void setHostContext(Context context) {
        hostContext = context.getApplicationContext();
    }

    public void attachPlayer(Player p) {
        player.set(p);
        launchPending = false;
        // A fresh player always starts from the requested URI; a pause that arrives later is
        // delivered through onPause, so no paused state needs to be replayed here
        if (currentUri != null) {
            p.onPlay(currentUri, currentTitle);
        }
    }

    public void detachPlayer(Player p) {
        player.compareAndSet(p, null);
    }

    /** Called by the renderer thread. */
    public void onSetUri(String uri, String metadata) {
        currentUri = uri;
        currentTitle = extractTitle(metadata, uri);
        durationMs = 0;
        positionMs = 0;
        transportState = STATE_TRANSITIONING;
        main.post(() -> {
            Player p = player.get();
            if (p != null) {
                // A second SetURI while the player is open (next track, corrected URL):
                // switch the running player straight over to the new item
                p.onPlay(uri, currentTitle);
            } else {
                launchPlayerIfPossible();
            }
        });
        Log.i(TAG, "SetURI: " + uri);
    }

    public void onPlay() {
        if (TextUtils.isEmpty(currentUri)) return;
        transportState = STATE_PLAYING;
        main.post(() -> {
            Player p = player.get();
            if (p != null) {
                p.onResumeRequested();
            } else {
                launchPlayerIfPossible();
            }
        });
    }

    public void onPause() {
        transportState = STATE_PAUSED;
        Player p = player.get();
        if (p != null) main.post(p::onPauseRequested);
    }

    public void onStopPlayback() {
        transportState = STATE_STOPPED;
        Player p = player.get();
        if (p != null) main.post(p::onStopRequested);
    }

    /** @param positionMs absolute position within the media. */
    public void onSeek(long positionMs) {
        Player p = player.get();
        if (p != null) main.post(() -> p.onSeek(positionMs));
    }

    public void onVolume(int percent) {
        Player p = player.get();
        if (p != null) main.post(() -> p.onVolume(percent));
    }

    /** Launched on the main thread; honours the overlay permission, falls back to a notification. */
    private void launchPlayerIfPossible() {
        if (player.get() != null || launchPending || TextUtils.isEmpty(currentUri)) return;
        if (hostContext == null) {
            Log.w(TAG, "No host context yet, cannot launch the player");
            return;
        }
        launchPending = true;
        String title = TextUtils.isEmpty(currentTitle)
                ? hostContext.getString(com.airplay.android.R.string.dlna_playing)
                : currentTitle;
        // When the TV is busy with something else and the overlay permission is not granted,
        // only a notification goes out - let later SetURI calls retry instead of blocking
        boolean started = com.airplay.android.AirPlayService.launchUi(hostContext,
                DlnaPlayerActivity.class, title);
        if (!started) {
            launchPending = false;
        }
    }

    /** Called from the player once it is on screen and ready to take calls. */
    public void onPlaybackStarted(long durationMs) {
        this.durationMs = durationMs;
        transportState = STATE_PLAYING;
    }

    public void onPlaybackProgress(long positionMs, long durationMs) {
        this.positionMs = positionMs;
        if (durationMs > 0) this.durationMs = durationMs;
    }

    /** Player reached the end of the stream on its own. */
    public void onPlaybackEnded() {
        transportState = STATE_STOPPED;
        positionMs = 0;
    }

    public void onPlaybackPaused() {
        transportState = STATE_PAUSED;
    }

    public void onPlaybackStopped() {
        transportState = STATE_STOPPED;
    }

    public String getTransportState() {
        return transportState;
    }

    public String getCurrentUri() {
        return currentUri == null ? "" : currentUri;
    }

    public long getPositionMs() {
        return positionMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    /** UPnP wants {@code H:MM:SS}; unknown duration is reported as {@code 0:00:00}. */
    public static String formatClock(long ms) {
        long totalSeconds = Math.max(0, ms) / 1000;
        return String.format("%d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60,
                totalSeconds % 60);
    }

    /** Inverse of {@link #formatClock(long)}; returns -1 when the value is not a clock. */
    public static long parseClock(String value) {
        if (value == null) return -1;
        Matcher m = TIME_PATTERN.matcher(value.trim());
        if (!m.matches()) return -1;
        return Long.parseLong(m.group(1)) * 3600_000L + Long.parseLong(m.group(2)) * 60_000L
                + Long.parseLong(m.group(3)) * 1000L;
    }

    private static String extractTitle(String metadata, String fallback) {
        if (metadata == null) return fallback;
        Matcher m = TITLE_PATTERN.matcher(metadata);
        String title = m.find() ? m.group(1).trim() : "";
        if (title.isEmpty()) return fallback;
        return title.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
    }
}
