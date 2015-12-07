package com.g53mdp.wkurniawan07.androidmusicplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class SongsListAdapter extends BaseAdapter {

    private List<Song> songsList;
    private LayoutInflater inflater;

    public SongsListAdapter(Context c, List<Song> songsList) {
        this.songsList = songsList;
        this.inflater = LayoutInflater.from(c);
    }

    @Override
    public int getCount() {
        return songsList.size();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.song, parent, false);
        }

        // Render the song title and artist
        Song currentSong = songsList.get(position);
        TextView songTitleView = (TextView) convertView.findViewById(R.id.songTitleView);
        TextView songArtistView = (TextView) convertView.findViewById(R.id.songArtistView);
        songTitleView.setText(currentSong.getTitle());
        songArtistView.setText(currentSong.getArtist());

        convertView.setId(position); // Append the index number of the song to the id
        return convertView;
    }

    // Not used, but have to be here anyway due to implemented abstract methods

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

}
