package com.thudo.rnexoplayer;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import java.util.List;

/**
 * @author phuongtq
 * @file ....java
 * @brief ....java source file.
 * <p/>
 * File/module comments.
 * @mobile 01684499886
 * @note No Note at the moment
 * @bug No known bugs.
 * <p/>
 * <pre>
 * MODIFICATION HISTORY:
 *
 * Ver   Who  	  Date       Changes
 * ----- --------- ---------- -----------------------------------------------
 * 1.00  phuongtq   3/3/2016 First release
 *
 *
 * </pre>
 */

class ForegroundCheckTask extends AsyncTask<Context, Void, Boolean> {

    @Override
    protected Boolean doInBackground(Context... params) {
        final Context context = params[0].getApplicationContext();
        return isAppOnForeground(context);
    }

    private boolean isAppOnForeground(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        final String packageName = context.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}

public class RNExoVideoView extends ScalableExoVideoView implements ScalableExoVideoView.Listener {
    public enum Events {
        EVENT_LOAD_START("onVideoLoadStart"),
        EVENT_LOAD("onVideoLoad"),
        EVENT_ERROR("onVideoError"),
        EVENT_PROGRESS("onVideoProgress"),
        EVENT_SEEK("onVideoSeek"),
        EVENT_END("onVideoEnd");

        private final String mName;

        Events(final String name) {
            mName = name;
        }

        @Override
        public String toString() {
            return mName;
        }
    }

    public static final String EVENT_PROP_FAST_FORWARD = "canPlayFastForward";
    public static final String EVENT_PROP_SLOW_FORWARD = "canPlaySlowForward";
    public static final String EVENT_PROP_SLOW_REVERSE = "canPlaySlowReverse";
    public static final String EVENT_PROP_REVERSE = "canPlayReverse";
    public static final String EVENT_PROP_STEP_FORWARD = "canStepForward";
    public static final String EVENT_PROP_STEP_BACKWARD = "canStepBackward";

    public static final String EVENT_PROP_DURATION = "duration";
    public static final String EVENT_PROP_PLAYABLE_DURATION = "playableDuration";
    public static final String EVENT_PROP_CURRENT_TIME = "currentTime";
    public static final String EVENT_PROP_BUFFER_PERCENT = "bufferPercent";
    public static final String EVENT_PROP_SEEK_TIME = "seekTime";

    public static final String EVENT_PROP_ERROR = "error";
    public static final String EVENT_PROP_WHAT = "what";
    public static final String EVENT_PROP_EXTRA = "extra";

    Activity mActivity;
    private ThemedReactContext mThemedReactContext;
    private RCTEventEmitter mEventEmitter;

    private Handler mProgressUpdateHandler = new Handler();
    private Runnable mProgressUpdateRunnable = null;

    private boolean mRepeat = false;
    private boolean mPaused = false;
    private boolean mMuted = false;
    private float mVolume = 1.0f;
    private float mRate = 1.0f;

    private boolean mMediaPlayerValid = false; // True if mMediaPlayer is in prepared, started, or paused state.
    private long mVideoDuration = 0;
    private long mVideoBufferedDuration = 0;

    private String mSrcUriString = null;
    private String mSrcType = "mp4";
    private boolean mSrcIsNetwork = false;

    RNExoVideoView(ThemedReactContext themedReactContext ,Activity activity){
        super(themedReactContext,activity);
        mThemedReactContext = themedReactContext;
        mActivity = activity;
        mEventEmitter = themedReactContext.getJSModule(RCTEventEmitter.class);

        mProgressUpdateRunnable = new Runnable() {
            int count = 0;
            @Override
            public void run() {
                //
                if (!isValid()) {
                    mProgressUpdateHandler.removeCallbacks(mProgressUpdateRunnable);
                    return;
                }

                if (mMediaPlayerValid) {
                    this.count++;
                    if (this.count>=4) {
                        boolean foreground = false;
                        try {
                            foreground = new ForegroundCheckTask().execute(mThemedReactContext).get();
                        } catch (Exception ex) {
                            Log.e("ReactVideoView", ex.getMessage());
                        }
                        //TODO : need improve
                        //                    Log.d("ReactVideoView","foreground "+ foreground);
                        if (!foreground) {
                            mMediaPlayerValid = false;
                            pause();
                            return;
                        }
                        this.count = 0;
                    }
                    WritableMap event = Arguments.createMap();
                    mVideoDuration = getDuration();
                    event.putDouble(EVENT_PROP_CURRENT_TIME, getCurrentPosition() / 1000.0);
                    event.putDouble(EVENT_PROP_BUFFER_PERCENT, getBufferPercentage());
                    event.putDouble(EVENT_PROP_PLAYABLE_DURATION, mVideoDuration / 1000.0); //TODO:mBufferUpdateRunnable
                    mEventEmitter.receiveEvent(getId(), Events.EVENT_PROGRESS.toString(), event);
                }
                mProgressUpdateHandler.postDelayed(mProgressUpdateRunnable, 250);
            }
        };
        mProgressUpdateHandler.post(mProgressUpdateRunnable);
    }

    public void setSrc(String src) {
        Log.d("setSrc", "setSrc: " + src);

        mSrcUriString = src;

        Log.d("setSrc", "setSrc:setVideoPath ");
        mMediaPlayerValid = false;
        mVideoDuration = -1;
        mVideoBufferedDuration = 0;

        WritableMap srcRet = Arguments.createMap();
        srcRet.putString(RNExoVideoViewManager.PROP_SRC_URI, src);
        WritableMap event = Arguments.createMap();
        event.putMap(RNExoVideoViewManager.PROP_SRC, srcRet);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_LOAD_START.toString(), event);

        setVideoPath(src);

//        setMediaController(new MediaController(mThemedReactContext));
        requestFocus();

    }

    @Override
    public void seekTo(long msec) {

        if (mMediaPlayerValid) {
            WritableMap event = Arguments.createMap();
            event.putDouble(EVENT_PROP_CURRENT_TIME, getCurrentPosition() / 1000.0);
            event.putDouble(EVENT_PROP_SEEK_TIME, msec / 1000.0);
            mEventEmitter.receiveEvent(getId(), Events.EVENT_SEEK.toString(), event);

            super.seekTo(msec);
        }
    }

    @Override
    public void onPreparedListener() {
        mMediaPlayerValid = true;
        mVideoDuration = mMediaPlayer.getDuration();

        WritableMap event = Arguments.createMap();
        event.putDouble(EVENT_PROP_DURATION, mVideoDuration / 1000.0);
        event.putDouble(EVENT_PROP_CURRENT_TIME, mMediaPlayer.getCurrentPosition() / 1000.0);
        // TODO: Actually check if you can.
        event.putBoolean(EVENT_PROP_FAST_FORWARD, true);
        event.putBoolean(EVENT_PROP_SLOW_FORWARD, true);
        event.putBoolean(EVENT_PROP_SLOW_REVERSE, true);
        event.putBoolean(EVENT_PROP_REVERSE, true);
        event.putBoolean(EVENT_PROP_FAST_FORWARD, true);
        event.putBoolean(EVENT_PROP_STEP_BACKWARD, true);
        event.putBoolean(EVENT_PROP_STEP_FORWARD, true);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_LOAD.toString(), event);

        _activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        applyModifiers();
//        setResizeModeModifier(mResizeMode);
        class StartVideo implements Runnable {
            @Override
            public void run() {
                start();
            }
        }
        new Thread(new StartVideo()).start();
    }

    @Override
    public void onErrorListener(String errorString) {

        WritableMap event = Arguments.createMap();
        event.putString(EVENT_PROP_ERROR, errorString);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_ERROR.toString(), event);
    }

    @Override
    public void onCompletionListener() {
//        mMediaPlayerValid = false;
        mEventEmitter.receiveEvent(getId(), Events.EVENT_END.toString(), null);
        _activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDetachedFromWindow() {
//        mVideoValid = false;
        mMediaPlayerValid = false;
        _activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);



        class Release implements Runnable {
            RNExoVideoView mRNExoVideoView;
            Release(RNExoVideoView reactVideoView){mRNExoVideoView = reactVideoView;}
            @Override
            public void run() {
                try {
                    mMediaPlayer.stop();
                    mMediaPlayer.release();
                    mMediaPlayer = null;
                }
                catch(Exception ex){
                    Log.e("ReactVideoView","onDetachedFromWindow err");
                }

            }
        }

        new Thread(new Release(this)).start();

        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        try {
            super.onAttachedToWindow();
            setSrc(mSrcUriString);
        }
        catch(Exception ex){
            Log.e("ReactVideoView","onAttachedToWindow err");
        }

    }
}
