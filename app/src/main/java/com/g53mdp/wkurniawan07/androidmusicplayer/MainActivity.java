package com.g53mdp.wkurniawan07.androidmusicplayer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore.Audio.Media;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.MediaController.MediaPlayerControl;
import android.widget.TextView;

import com.g53mdp.wkurniawan07.androidmusicplayer.MusicPlayService.MusicBinder;
import com.g53mdp.wkurniawan07.androidmusicplayer.SongsSorter.SongSortingOrder;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends ActionBarActivity implements MediaPlayerControl {

    private Intent serviceIntent;
    private MusicPlayService musicPlayService;

    private List<Song> songsList;
    private MediaController mediaController;
    private long idOfSongPlayed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the song list from the directory
        songsList = getSongsList();

        // Display the song list on the main screen
        renderSongList();

        // Set up the playback controller
        setupMediaController();

        /*
         * Set up button listeners for the buttons shown on UI.
         * They are the shuffle button, loop button, and quit button.
         */
        setupShuffleButtonListener();
        setupLoopButtonListener();
        setupQuitButtonListener();

        /*
         * Set up broadcast receivers for three conditions:
         * 1. whenever the media player has been prepared
         * 2. whenever a song is finished playing (and the playback continues)
         * 3. whenever a playback stopped
         */
        setupMpPreparedBroadcastReceiver();
        setupSongFinishedBroadcastReceiver();
        setupPlaybackStoppedBroadcastReceiver();
    }

    private List<Song> getSongsList() {
        ContentResolver cr = getContentResolver();

        // Get media files from external storage
        Uri uri = Media.EXTERNAL_CONTENT_URI;

        // Only the song ID, title, and artist are of particular importance
        String[] columns = { Media._ID, Media.TITLE, Media.ARTIST };

        // Default sorting order is title first, ascending
        String sortOrder = Media.TITLE + " ASC";

        // We don't need any WHERE restriction
        Cursor cursor = cr.query(uri, columns, null, null, sortOrder);

        List<Song> songsList = new ArrayList<Song>();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                // Use the column numbers specified in the variable "columns" above
                long songId = cursor.getLong(0);
                String songTitle = cursor.getString(1);
                String songArtist = cursor.getString(2);
                songsList.add(new Song(songId, songTitle, songArtist));
            } while (cursor.moveToNext());
            cursor.close();
        }
        return songsList;
    }

    private void renderSongList(SongSortingOrder sortingOrder) {
        SongsSorter.sortBy(songsList, sortingOrder); // Sort according to the specified ordering
        renderSongList();
    }

    private void renderSongList() {
        SongsListAdapter adapter = new SongsListAdapter(this, songsList);
        ListView songListView = (ListView) findViewById(R.id.songList);
        songListView.setAdapter(adapter);
        songListView.refreshDrawableState();
    }

    private void setupMediaController() {

        mediaController = new MediaController(this) {
            @Override
            public void show(int timeout) {
                // We don't want the controller to hide unless explicitly told to
                super.show(0);
            }
        };

        OnClickListener playNextListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                musicPlayService.playNext();
                idOfSongPlayed = musicPlayService.getIdOfSongPlayed();
            }
        };

        OnClickListener playPrevListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                musicPlayService.playPrev();
                idOfSongPlayed = musicPlayService.getIdOfSongPlayed();
            }
        };

        mediaController.setPrevNextListeners(playNextListener, playPrevListener);
        mediaController.setMediaPlayer(this);
        mediaController.setAnchorView(findViewById(R.id.controls));
        mediaController.setEnabled(true);
    }

    private void setupShuffleButtonListener() {
        final ImageView shuffleButton = (ImageView) findViewById(R.id.shuffleButton);
        shuffleButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                musicPlayService.toggleShufflePlay();
                // Re-render the icon accordingly
                if (musicPlayService.isShufflePlay()) {
                    shuffleButton.setImageResource(R.drawable.shuffle_);
                } else {
                    shuffleButton.setImageResource(R.drawable.unshuffle_);
                }
            }
        });
    }

    private void setupLoopButtonListener() {
        final ImageView loopButton = (ImageView) findViewById(R.id.loopButton);
        loopButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                musicPlayService.toggleLoop();
                // Re-render the icon accordingly
                if (!musicPlayService.isLoopingSong()) {
                    if (!musicPlayService.isLoopingPlaylist()) {
                        loopButton.setImageResource(R.drawable.unloop_);
                    } else {
                        loopButton.setImageResource(R.drawable.loop_album);
                    }
                } else {
                    loopButton.setImageResource(R.drawable.loop_song);
                }
            }
        });
    }

    private void setupQuitButtonListener() {
        final ImageView quitButton = (ImageView) findViewById(R.id.quitButton);
        quitButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * If there is a song playing, this functions as a stop button.
                 * Otherwise, this functions as a quit button.
                 * Their icons are programmatically changed from outside this function.
                 */
                if (musicPlayService.isPlaying() || musicPlayService.isPlaybackPaused()) {
                    musicPlayService.stop();
                } else {
                    onDestroy(); // Mimic all that is done in onDestroy()
                    System.exit(0); // And then exit with code 0 (no error)
                }
            }
        });
    }

    private BroadcastReceiver mpPreparedIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Show the media player controller if it's hidden
            if (!mediaController.isShown()) {
                mediaController.show(0);
            }

            // Display the title of song played
            String songTitle = intent.getStringExtra(Const.SONG_TITLE);
            displaySongTitle(songTitle);
        }
    };

    private void setupMpPreparedBroadcastReceiver() {
        IntentFilter mpPreparedIntentFilter = new IntentFilter(Const.MEDIA_PLAYER_PREPARED_INTENT);
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mpPreparedIntentReceiver, mpPreparedIntentFilter);
    }

    private BroadcastReceiver songFinishedIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            idOfSongPlayed = intent.getLongExtra(Const.NEW_SONG_ID, 0);
        }
    };

    private void setupSongFinishedBroadcastReceiver() {
        IntentFilter songFinishedIntentFilter = new IntentFilter(Const.SONG_FINISHED_INTENT);
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(songFinishedIntentReceiver, songFinishedIntentFilter);
    }

    private BroadcastReceiver playbackStoppedIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mediaController.hide(); // Hide the media player controller

            // Clear the title of any song played
            displaySongTitle("");

            // The quit button should now serve to quit the app
            ImageView quitButton = (ImageView) findViewById(R.id.quitButton);
            quitButton.setImageResource(R.drawable.quit_);
        }
    };

    private void setupPlaybackStoppedBroadcastReceiver() {
        IntentFilter playbackStoppedIntentFilter = new IntentFilter(Const.PLAYBACK_STOPPED_INTENT);
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(playbackStoppedIntentReceiver, playbackStoppedIntentFilter);
    }

    private void displaySongTitle(String songTitle) {
        TextView currentSong = (TextView) findViewById(R.id.currentSong);
        currentSong.setText(songTitle);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicBinder binder = (MusicBinder) service;
            musicPlayService = binder.getService();
            musicPlayService.setSongsList(songsList);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicPlayService = null;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (serviceIntent == null) {
            serviceIntent = new Intent(this, MusicPlayService.class);
            // We only need to bind and start the service once
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            startService(serviceIntent);
        }
    }

    @Override
    protected void onDestroy() {
        stopService(serviceIntent);
        unbindService(serviceConnection);
        serviceIntent = null;
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(songFinishedIntentReceiver);
        lbm.unregisterReceiver(mpPreparedIntentReceiver);
        lbm.unregisterReceiver(playbackStoppedIntentReceiver);
        super.onDestroy();
    }

    /*
     * This portion adds a menu which enables changing of song sorting order.
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.sort, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sort_tfa:
                renderSongList(SongSortingOrder.TITLE_FIRST_ASC);
                break;
            case R.id.sort_tfd:
                renderSongList(SongSortingOrder.TITLE_FIRST_DESC);
                break;
            case R.id.sort_afa:
                renderSongList(SongSortingOrder.ARTIST_FIRST_ASC);
                break;
            case R.id.sort_afd:
                renderSongList(SongSortingOrder.ARTIST_FIRST_DESC);
                break;
        }
        musicPlayService.setSongPositionBasedOnId(idOfSongPlayed);
        return super.onOptionsItemSelected(item);
    }

    /*
     * This is the action invoked every time a song is selected.
     * It is not bound to the song that is currently being played.
     */

    public void songSelected(View view) {
        // Send the information about the selected song to the service
        int selectedSongPosition = view.getId();
        musicPlayService.setPosOfSongPlayed(selectedSongPosition);
        musicPlayService.navigateToNewSong();

        idOfSongPlayed = musicPlayService.getIdOfSongPlayed();

        // The quit button should be changed to stop
        ImageView quitButton = (ImageView) findViewById(R.id.quitButton);
        quitButton.setImageResource(R.drawable.stop_);
    }

    /*
     * The next few functions are implementations of MediaPlayerControl.
     * They will simply be passed to the Service, which will
     * subsequently be passed to the MediaPlayer.
     */

    @Override
    public void start() {
        musicPlayService.start();
    }

    @Override
    public void pause() {
        musicPlayService.pause();
    }

    @Override
    public int getDuration() {
        return musicPlayService.getDuration();
    }

    @Override
    public int getCurrentPosition() {
        return musicPlayService.getCurrentPosition();
    }

    @Override
    public void seekTo(int pos) {
        musicPlayService.seekTo(pos);
    }

    @Override
    public boolean isPlaying() {
        return musicPlayService.isPlaying();
    }

    // Pause and seeks are supported

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    // Not used, but have to be here anyway due to implemented abstract methods

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

}
