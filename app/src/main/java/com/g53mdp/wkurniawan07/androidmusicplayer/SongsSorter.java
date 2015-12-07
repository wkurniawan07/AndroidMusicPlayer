package com.g53mdp.wkurniawan07.androidmusicplayer;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SongsSorter {

    public static void sortBy(List<Song> songList, SongSortingOrder sortingOrder) {
        switch (sortingOrder) {
            case TITLE_FIRST_ASC:
                sortByTitleFirst(songList, true);
                break;
            case TITLE_FIRST_DESC:
                sortByTitleFirst(songList, false);
                break;
            case ARTIST_FIRST_ASC:
                sortByArtistFirst(songList, true);
                break;
            case ARTIST_FIRST_DESC:
                sortByArtistFirst(songList, false);
                break;
        }
    }

    private static void sortByTitleFirst(List<Song> songList, final boolean isAscending) {
        Collections.sort(songList, new Comparator<Song>() {
            public int compare(Song firstSong, Song secondSong) {
                if (firstSong.getTitle().compareTo(secondSong.getTitle()) == 0) {
                    return compareArtist(firstSong, secondSong, isAscending);
                } else {
                    return compareTitle(firstSong, secondSong, isAscending);
                }
            }
        });
    }

    private static void sortByArtistFirst(List<Song> songList, final boolean isAscending) {
        Collections.sort(songList, new Comparator<Song>() {
            public int compare(Song firstSong, Song secondSong) {
                if (firstSong.getArtist().compareTo(secondSong.getArtist()) == 0) {
                    return compareTitle(firstSong, secondSong, isAscending);
                } else {
                    return compareArtist(firstSong, secondSong, isAscending);
                }
            }
        });
    }

    private static int compareTitle(Song firstSong, Song secondSong, boolean isAscending) {
        return isAscending ? firstSong.getTitle().compareTo(secondSong.getTitle())
                : secondSong.getTitle().compareTo(firstSong.getTitle());
    }

    private static int compareArtist(Song firstSong, Song secondSong, boolean isAscending) {
        return isAscending ? firstSong.getArtist().compareTo(secondSong.getArtist())
                : secondSong.getArtist().compareTo(firstSong.getArtist());
    }

    public enum SongSortingOrder {
        TITLE_FIRST_ASC,    // sort by title first then artist, ascending
        TITLE_FIRST_DESC,   // sort by title first then artist, descending
        ARTIST_FIRST_ASC,   // sort by artist first then title, ascending
        ARTIST_FIRST_DESC   // sort by artist first then title, descending
    }

}
