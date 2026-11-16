package com.airplay.android.dlna;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.airplay.android.R;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.interfaces.IVLCVout;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Fullscreen playback of a DLNA-cast URL through libVLC, which decodes the formats the
 * platform player gives up on (AVI/XviD above all - the whole reason this player exists).
 *
 * <p>Commands come in from {@link DlnaController} (control-point SOAP calls) and from the
 * remote: OK pauses, left/right seek, up/down volume, back stops.
 */
public class DlnaPlayerActivity extends Activity implements DlnaController.Player {

    private static final String TAG = "DlnaPlayer";
    private static final long SEEK_STEP_MS = 30_000;
    /** Progress is pushed to the controller at this rate for GetPositionInfo polls. */
    private static final long PROGRESS_PERIOD_MS = 1000;

    private final Handler main = new Handler(Looper.getMainLooper());

    private SurfaceView surfaceView;
    private LinearLayout overlay;
    private TextView titleText;
    private TextView hintText;

    private LibVLC libVlc;
    private MediaPlayer mediaPlayer;
    private boolean surfaceReady;
    private boolean pendingPlay;
    private String pendingUri;
    private boolean playing;
    private long durationMs;
    private long lastPositionMs;

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && playing) {
                long time = mediaPlayer.getTime();
                // Asked straight from the player: some demuxers report the length late, so
                // waiting for the LengthChanged event alone would leave the control point
                // showing 0:00:00 for the whole file
                long length = mediaPlayer.getLength();
                if (length > 0) {
                    durationMs = length;
                }
                lastPositionMs = time;
                DlnaController.get().onPlaybackProgress(time, durationMs);
            }
            main.postDelayed(this, PROGRESS_PERIOD_MS);
        }
    };

    private final SurfaceHolder.Callback surfaceCallbacks = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            surfaceReady = true;
            attachSurface(holder.getSurface(), holder);
            if (pendingPlay) {
                pendingPlay = false;
                startPlayback(pendingUri);
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (mediaPlayer != null) {
                mediaPlayer.getVLCVout().setWindowSize(width, height);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            if (mediaPlayer != null) {
                mediaPlayer.getVLCVout().detachViews();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        surfaceView = new SurfaceView(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        surfaceView.getHolder().addCallback(surfaceCallbacks);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(24), dp(24), dp(24), dp(16));
        overlay.setBackgroundColor(0x88000000);

        titleText = new TextView(this);
        titleText.setTextColor(0xFFFFFFFF);
        titleText.setTextSize(18);
        hintText = new TextView(this);
        hintText.setTextColor(0xFFBBBBBB);
        hintText.setTextSize(13);
        hintText.setPadding(0, dp(6), 0, 0);
        hintText.setText(getString(R.string.dlna_controls_hint));
        overlay.addView(titleText);
        overlay.addView(hintText);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM));

        setContentView(root);
        overlay.requestFocus();

        ArrayList<String> args = new ArrayList<>(Arrays.asList(
                // Local network streams: a couple of seconds of buffer absorbs Wi-Fi jitter
                "--network-caching=2000",
                "--audio-time-stretch"));
        libVlc = new LibVLC(getApplicationContext(), args);
        mediaPlayer = new MediaPlayer(libVlc);
        mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT);
        mediaPlayer.setEventListener(this::onVlcEvent);

        DlnaController.get().attachPlayer(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Counts as receiver UI: while the player is on screen the service must stay up even
        // though it covers the main screen
        com.airplay.android.AirPlayService.activityVisible(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.removeCallbacks(progressTicker);
        main.postDelayed(progressTicker, PROGRESS_PERIOD_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(progressTicker);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        com.airplay.android.AirPlayService.activityHidden(this);
        DlnaController.get().detachPlayer(this);
        if (mediaPlayer != null) {
            mediaPlayer.setEventListener(null);
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (libVlc != null) {
            libVlc.release();
            libVlc = null;
        }
    }

    // ---- DlnaController.Player ----------------------------------------------------------

    @Override
    public void onPlay(String uri, String title) {
        runOnUiThread(() -> {
            titleText.setText(title == null || title.isEmpty()
                    ? getString(R.string.dlna_playing) : title);
            revealOverlay();
            if (!surfaceReady) {
                pendingPlay = true;
                pendingUri = uri;
                return;
            }
            startPlayback(uri);
        });
    }

    @Override
    public void onPauseRequested() {
        runOnUiThread(this::applyPause);
    }

    @Override
    public void onResumeRequested() {
        runOnUiThread(() -> {
            if (mediaPlayer == null) return;
            if (pendingPlay && !surfaceReady) {
                return;
            }
            if (!pendingPlay && mediaPlayer.getMedia() != null) {
                if (!playing) {
                    mediaPlayer.play();
                }
            }
        });
    }

    @Override
    public void onStopRequested() {
        runOnUiThread(this::finishPlayback);
    }

    @Override
    public void onSeek(long positionMs) {
        runOnUiThread(() -> {
            if (mediaPlayer != null && mediaPlayer.getMedia() != null) {
                mediaPlayer.setTime(positionMs);
                lastPositionMs = positionMs;
                revealOverlay();
            }
        });
    }

    @Override
    public void onVolume(int percent) {
        runOnUiThread(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(percent);
            }
        });
    }

    // ---- Remote keys --------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (playing) {
                    applyPause();
                } else if (mediaPlayer != null && mediaPlayer.getMedia() != null) {
                    mediaPlayer.play();
                }
                revealOverlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (mediaPlayer != null && !playing) mediaPlayer.play();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                applyPause();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-SEEK_STEP_MS);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(SEEK_STEP_MS);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                stepVolume(10);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                stepVolume(-10);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void seekBy(long deltaMs) {
        if (mediaPlayer == null || mediaPlayer.getMedia() == null) return;
        long target = lastPositionMs + deltaMs;
        long limit = durationMs > 0 ? durationMs - 500 : target;
        mediaPlayer.setTime(Math.max(0, Math.min(target, limit)));
        lastPositionMs = Math.max(0, Math.min(target, limit));
        revealOverlay();
    }

    private void stepVolume(int delta) {
        if (mediaPlayer == null) return;
        int volume = Math.max(0, Math.min(100, mediaPlayer.getVolume() + delta));
        mediaPlayer.setVolume(volume);
        titleText.setText(getString(R.string.dlna_volume, volume));
        revealOverlay();
    }

    private void applyPause() {
        if (mediaPlayer != null && mediaPlayer.getMedia() != null && playing) {
            mediaPlayer.pause();
            revealOverlay();
        }
    }

    private void finishPlayback() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
        finish();
    }

    // ---- libVLC plumbing ----------------------------------------------------------------

    private void attachSurface(Surface surface, SurfaceHolder holder) {
        if (mediaPlayer == null) return;
        IVLCVout vout = mediaPlayer.getVLCVout();
        if (!vout.areViewsAttached()) {
            vout.setVideoSurface(surface, holder);
            vout.attachViews();
        }
    }

    private void startPlayback(String uri) {
        if (mediaPlayer == null || uri == null) return;
        // Uri.parse routes through media_new_location, which libVLC needs for network MRLs;
        // the String constructor treats everything as a local path and breaks http URLs
        Media media = new Media(libVlc, android.net.Uri.parse(uri));
        mediaPlayer.setMedia(media);
        media.release();
        if (!surfaceReady) {
            pendingPlay = true;
            pendingUri = uri;
            return;
        }
        pendingPlay = false;
        // A stop() tears libVLC's internal video output down while the Java-side views stay
        // registered; every fresh play must re-attach the surface or the picture stays black
        IVLCVout vout = mediaPlayer.getVLCVout();
        if (!vout.areViewsAttached()) {
            SurfaceHolder holder = surfaceView.getHolder();
            vout.setVideoSurface(holder.getSurface(), holder);
            vout.attachViews();
        }
        mediaPlayer.play();
        DlnaController.get().onPlaybackStarted(durationMs);
    }

    private void onVlcEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Playing:
                playing = true;
                DlnaController.get().onPlaybackStarted(durationMs);
                overlay.setVisibility(View.GONE);
                break;
            case MediaPlayer.Event.Paused:
                playing = false;
                DlnaController.get().onPlaybackPaused();
                break;
            case MediaPlayer.Event.Stopped:
                playing = false;
                DlnaController.get().onPlaybackStopped();
                // Release the dead video output so the next play() re-attaches it
                if (mediaPlayer != null) {
                    mediaPlayer.getVLCVout().detachViews();
                }
                break;
            case MediaPlayer.Event.EndReached:
                playing = false;
                DlnaController.get().onPlaybackEnded();
                runOnUiThread(this::finish);
                break;
            case MediaPlayer.Event.EncounteredError:
                Log.e(TAG, "libVLC failed to play the stream");
                runOnUiThread(() -> {
                    titleText.setText(getString(R.string.dlna_error));
                    hintText.setVisibility(View.VISIBLE);
                    overlay.setVisibility(View.VISIBLE);
                });
                break;
            case MediaPlayer.Event.LengthChanged:
                durationMs = event.getLengthChanged();
                DlnaController.get().onPlaybackProgress(lastPositionMs, durationMs);
                break;
            default:
                break;
        }
    }

    private void revealOverlay() {
        overlay.setVisibility(View.VISIBLE);
        overlay.removeCallbacks(hideOverlay);
        overlay.postDelayed(hideOverlay, 4000);
    }

    private final Runnable hideOverlay = () -> overlay.setVisibility(View.GONE);

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
