package com.g53mdp.wkurniawan07.androidmusicplayer;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore.Audio.Media;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.util.List;

public class MusicPlayService extends Service implements
        OnPreparedListener, OnCompletionListener {

    private MediaPlayer mediaPlayer;
    private List<Song> songsList;
    private int posOfSongPlayed;

    private boolean isPlaybackPaused;
    private boolean isShufflePlay;
    private boolean isLoopingPlaylist;
    private boolean isLoopingSong;

    private static final int NOTIFICATION_ID = 7; // Favorite number :p

    public class MusicBinder extends Binder {
        public MusicPlayService getService() {
            return MusicPlayService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new MusicBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (mediaPlayer.isPlaying() || isPlaybackPaused) {
            mediaPlayer.stop();
        }
        mediaPlayer.release();
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        MediaPlayer mp = new MediaPlayer();
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mp.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnPreparedListener(this);
        mp.setOnCompletionListener(this);
        this.mediaPlayer = mp;

        this.posOfSongPlayed = 0;

        this.isPlaybackPaused = false;
        this.isShufflePlay = false; // Not shuffling by default
        this.isLoopingPlaylist = false; // Not looping by default
        this.isLoopingSong = false; // Ditto
    }

    public void setSongsList(List<Song> songsList) {
        this.songsList = songsList;
    }

    public long getIdOfSongPlayed() {
        return songsList.get(posOfSongPlayed).getId();
    }

    public void setPosOfSongPlayed(int posOfSongPlayed) {
        this.posOfSongPlayed = posOfSongPlayed;
    }

    public void setSongPositionBasedOnId(Long songId) {
        for (int i = 0; i < songsList.size(); i++) {
            Song song = songsList.get(i);
            if (song.getId() == songId) {
                setPosOfSongPlayed(i);
                break;
            }
        }
    }

    @Override
    public void onDestroy() {
        stopForeground(true); // Kill the notifications
        super.onDestroy();
    }

    public void prepareSong() {
        mediaPlayer.reset();
        long idOfSongPlayed = songsList.get(posOfSongPlayed).getId();
        Uri songUri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, idOfSongPlayed);
        try {
            mediaPlayer.setDataSource(getApplicationContext(), songUri);
            mediaPlayer.prepare();
        } catch (IOException e) {
            Log.e("MusicPlayService", "I/O error");
            mediaPlayer.reset(); // Reset again to idle state
        } catch (IllegalArgumentException e) {
            Log.e("MusicPlayService", "Argument error");
            mediaPlayer.reset(); // Reset again to idle state
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        // Broadcast to MainActivity that the media player is prepared
        Intent mpPreparedIntent = new Intent(Const.MEDIA_PLAYER_PREPARED_INTENT);
        mpPreparedIntent.putExtra(Const.SONG_TITLE, songsList.get(posOfSongPlayed).getTitle());
        LocalBroadcastManager.getInstance(MusicPlayService.this).
                sendBroadcast(mpPreparedIntent);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        boolean shouldStop = !isLoopingSong && !isLoopingPlaylist
                && !isShufflePlay && posOfSongPlayed == songsList.size() - 1;
        if (shouldStop) {
            stop();

            /*
             * Don't need to broadcast song finished because it will be overridden
             * by the playback stopped broadcast
             */
        } else {
            if (!isLoopingSong) {
                playNext();
            } else {
                start(); // Same song, we don't need to re-prepare
            }

            // Broadcast to MainActivity that the song is finished
            Intent songFinishedIntent = new Intent(Const.SONG_FINISHED_INTENT);
            songFinishedIntent.putExtra(Const.NEW_SONG_ID, songsList.get(posOfSongPlayed).getId());
            LocalBroadcastManager.getInstance(MusicPlayService.this).sendBroadcast(songFinishedIntent);
        }
    }

    public void setPlayNotification() {
        setNotification(R.drawable.play_, "Playing: " + songsList.get(posOfSongPlayed).getTitle());
    }

    public void setPauseNotification() {
        setNotification(R.drawable.pause_, "Paused: " + songsList.get(posOfSongPlayed).getTitle());
    }

    public void setStopNotification() {
        setNotification(R.drawable.stop_, "Stopped");
    }

    public void setNotification(int icon, String messageToShow) {
        Intent notifIntent = new Intent(this, MainActivity.class);
        notifIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        Builder builder = new Builder(this);

        builder.setContentIntent(pendingIntent)
                .setSmallIcon(icon)
                .setOngoing(true)
                .setContentTitle("AndroidMusicPlayer")
                .setContentText(messageToShow)
                .setTicker(messageToShow);
        Notification notification = builder.build();

        startForeground(NOTIFICATION_ID, notification);
    }

    /*
     * The custom functions attached to the MediaPlayerControl's prev and next
     * buttons. Will also consider shuffle play.
     */

    public void playPrev() {
        if (isShufflePlay) {
            posOfSongPlayed = getRandomNum();
        } else {
            posOfSongPlayed = (posOfSongPlayed - 1 + songsList.size()) % songsList.size();
        }
        navigateToNewSong();
    }

    public void playNext() {
        if (isShufflePlay) {
            posOfSongPlayed = getRandomNum();
        } else {
            posOfSongPlayed = (posOfSongPlayed + 1) % songsList.size();
        }
        navigateToNewSong();
    }

    private int getRandomNum() {
        int randomNum;
        do {
            randomNum = (int) Math.floor(Math.random() * songsList.size());
        } while (randomNum == posOfSongPlayed); // Cannot return the original number
        return randomNum;
    }

    public void navigateToNewSong() {
        prepareSong();
        if (!isPlaybackPaused) {
            // If paused, just prepare the new song without actually playing
            start();
        }
    }

    public void toggleShufflePlay() {
        isShufflePlay = !isShufflePlay;
    }

    public void toggleLoop() {

        // not looping -> looping playlist -> looping song -> not looping -> ...

        if (!isLoopingSong) {
            if (!isLoopingPlaylist) {
                // Toggle from not looping -> looping playlist
                isLoopingPlaylist = true;
            } else {
                // Toggle from looping playlist -> looping song
                isLoopingSong = true;
            }
        } else {
            // Toggle from looping song -> not looping
            isLoopingSong = false;
            isLoopingPlaylist = false;
        }
    }

    /*
     * The next few functions are the bridge between MainActivity and
     * the MediaPlayer used by this service.
     * Extensions are included when necessary.
     */

    public void start() {
        mediaPlayer.start();
        isPlaybackPaused = false;
        setPlayNotification();
    }

    public void pause() {
        mediaPlayer.pause();
        isPlaybackPaused = true;
        setPauseNotification();
    }

    public void stop() {
        if (mediaPlayer.isPlaying() || isPlaybackPaused) {
            mediaPlayer.stop();
            isPlaybackPaused = false;
        }
        // Set notification that the playback has stopped
        setStopNotification();

        // Broadcast to MainActivity that the playback has stopped
        Intent playbackStoppedIntent = new Intent(Const.PLAYBACK_STOPPED_INTENT);
        LocalBroadcastManager.getInstance(MusicPlayService.this).
                sendBroadcast(playbackStoppedIntent);
    }

    public int getDuration() {
        return mediaPlayer.getDuration();
    }

    public int getCurrentPosition() {
        return mediaPlayer.getCurrentPosition();
    }

    public void seekTo(int pos) {
        mediaPlayer.seekTo(pos);
    }

    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    // Accessors for the private boolean flags of this class

    public boolean isLoopingSong() {
        return isLoopingSong;
    }

    public boolean isLoopingPlaylist() {
        return isLoopingPlaylist;
    }

    public boolean isShufflePlay() {
        return isShufflePlay;
    }

    public boolean isPlaybackPaused() {
        return isPlaybackPaused;
    }

}
