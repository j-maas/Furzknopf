package com.y0hy0h.furzknopf;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.Fragment;
import android.util.Log;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Locale;

public class SoundControlFragment extends Fragment {
    // tag for use in Log-statements
    private static final String LOG_TAG = SoundControlFragment.class.getSimpleName();

    private Vibrator mVibrator;

    private static SoundPool mSoundPool;
    // contains the IDs of the regular farts
    private static LinkedList<Integer> mLoadedSoundIDs;
    private static int mBigFartID = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.v(LOG_TAG, "Recreating SoundControl");

        // Retain fragment when runtime change occurs in Activity.
        setRetainInstance(true);

        // Load vibrator.
        mVibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);

        // Initialize SoundPool depending on API version,
        // initialize queue and Random and load sounds.
        createSoundPoolCompatibly(6);
        mLoadedSoundIDs = new LinkedList<>();
        loadSounds();
    }

    /**
     * Initializes the SoundPool with the preferred method depending on the API leve.
     * @param maxStreams The number of sounds that can be played back simultaneously.
     */
    @TargetApi(21)
    @SuppressWarnings("deprecation")
    private void createSoundPoolCompatibly(int maxStreams) {
        if (Build.VERSION.SDK_INT >= 21) {
            // Set up attributes.
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            // Create SoundPool.
            mSoundPool = new SoundPool.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setMaxStreams(maxStreams)
                    .build();
        } else {
            // Create SoundPool and set VolumeControl.
            mSoundPool = new SoundPool(maxStreams, AudioManager.STREAM_MUSIC, 0);

            getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
        }
    }

    /**
     * Loads the default fart sounds into the SoundPool and enqueues the sound's IDs.
     */
    private void loadSounds() {

        final AssetManager assetManager = getActivity().getAssets();

        new Thread(new Runnable() {
            private int tempBigFartID;

            @Override
            public void run() {
                mSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                    @Override
                    public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                        if (sampleId == tempBigFartID) {
                            mBigFartID = sampleId;
                        } else {
                            mLoadedSoundIDs.add(sampleId);
                        }
                    }
                });

                try {
                    // Load all standard sounds and store IDs in queue.
                    for (int i = 1; i <= 15; i++) {
                        String pathToSound = String.format(Locale.US, "fart%02d.ogg", i);
                        mSoundPool.load(assetManager.openFd(pathToSound), 1);
                    }

                    tempBigFartID = mSoundPool.load(assetManager.openFd("fart_big.ogg"), 1);
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Default sounds could not be loaded.", e);
                }
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.v(LOG_TAG, "Destroying SoundControl");

        // Stop vibration.
        mVibrator.cancel();

        // Activity was stopped, release SoundPool's and queue's resources.
        mSoundPool.release();
        mLoadedSoundIDs.clear();
        mLoadedSoundIDs = null;
        mBigFartID = -1;
    }

    protected int getRegularSoundsLoaded() {
        return mLoadedSoundIDs.size();
    }

    /**
     * Plays a regular fart and vibrates.
     * Assumes at least one file is loaded.
     *
     * @see SoundControlFragment#playBigFart()
     */
    public void playRegularFart() {

        int nextSoundID = 0;

        // Skip up to 5 files randomly.
        if (mLoadedSoundIDs.size() >= 5) { // at least 5 sounds loaded
            int skipAmount = Utility.getMappedRandomInt(5, 2);

            // Skip to chosen soundID, move it to tail of list.
            nextSoundID = mLoadedSoundIDs.remove(skipAmount);
            mLoadedSoundIDs.addLast(nextSoundID);
        }

        // Choose random frequency.
        float freq = Utility.getFloatBetween(0.75f, 1.5f);

        // Play chosen sound with chosen frequency.
        mSoundPool.play(nextSoundID, 1, 1, 0, 0, freq);
    }

    /**
     * Plays a big fart and vibrates.
     *
     * @return amount of milliseconds that the big fart will last
     * @see SoundControlFragment#playRegularFart()
     */
    protected long playBigFart() {
        // Choose random frequency.
        float freq = Utility.getFloatBetween(0.9f, 1.2f);

        // Play chosen sound with chosen frequency.
        mSoundPool.play(mBigFartID, 1, 1, 0, 0, freq);

        // vibration pattern: pause first, because recording does not begin immediately
        final long[] duration = {(long) (55 * freq), (long) (3758 / freq)};

        // Vibrate, add audio attributes depending on API level.
        if (Build.VERSION.SDK_INT >= 21)
            mVibrator.vibrate(
                    duration,
                    -1,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            );
        else
            mVibrator.vibrate(duration, -1);

        // Return whole duration.
        return duration[0] + duration[1];
    }

    protected boolean bigFartLoaded() {
        return mBigFartID != -1;
    }
}