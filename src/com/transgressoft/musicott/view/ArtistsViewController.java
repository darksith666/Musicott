/*
 * This file is part of Musicott software.
 *
 * Musicott software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Musicott library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Musicott. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2015 - 2017 Octavio Calleya
 */

package com.transgressoft.musicott.view;

import com.google.common.collect.*;
import com.transgressoft.musicott.model.*;
import com.transgressoft.musicott.view.custom.*;
import javafx.application.Platform;
import javafx.beans.binding.*;
import javafx.beans.value.*;
import javafx.collections.*;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.Map.*;
import java.util.stream.*;

/**
 * Controller class that isolates the behaviour of the artists view.
 *
 * @author Octavio Calleya
 * @version 0.10-b
 * @since 0.10-b
 */
public class ArtistsViewController {

    @FXML
    private ListView<String> artistsListView;
    @FXML
    private VBox trackSetsAreaVBox;
    @FXML
    private Label nameLabel;
    @FXML
    private Label totalAlbumsLabel;
    @FXML
    private Label totalTracksLabel;
    @FXML
    private Button artistRandomButton;

    private ObservableMap<String, TrackSetAreaRow> albumTrackSets;

    private MusicLibrary musicLibrary = MusicLibrary.getInstance();

    @FXML
    public void initialize() {
        albumTrackSets = FXCollections.observableMap(new TreeMap<String, TrackSetAreaRow>());
        albumTrackSets.addListener(albumTrackSetsListener());
        artistsListView.setItems(musicLibrary.artistsProperty());
        artistsListView.getSelectionModel().selectedItemProperty().addListener(selectedArtistListener());

        totalAlbumsLabel.setText(String.valueOf(0) + " albums");
        totalTracksLabel.setText(String.valueOf(0) + " tracks");
        artistRandomButton.visibleProperty()
                          .bind(Bindings.createBooleanBinding(() -> nameLabel.textProperty().isEmpty().not().get(),
                                                              nameLabel.textProperty()));
        artistRandomButton.setOnAction(e -> musicLibrary.makeRandomArtistPlaylist(nameLabel.getText()));
    }

    private MapChangeListener<String, TrackSetAreaRow> albumTrackSetsListener() {
        return change -> {
            if (change.wasAdded())
                trackSetsAreaVBox.getChildren().add(change.getValueAdded());
            else if (change.wasRemoved())
                trackSetsAreaVBox.getChildren().remove(change.getValueRemoved());
            totalTracksLabel.setText(getTotalArtistTracksString());
            totalAlbumsLabel.setText(getAlbumString());
            checkSelectedTracks();
        };
    }

    private ChangeListener<String> selectedArtistListener() {
        return (observable, oldArtist, newArtist) -> {
            if (newArtist != null) {
                if (! nameLabel.getText().equals(newArtist)) {
                    nameLabel.setText(newArtist);
                    musicLibrary.showArtist(newArtist);
                }
            }
            else {
                if (artistsListView.getItems().isEmpty()) {
                    nameLabel.setText("");
                    albumTrackSets.clear();
                }
                else
                    checkSelectedTracks();
            }
        };
    }

    private String getTotalArtistTracksString() {
        int totalArtistTracks = albumTrackSets.values().stream()
                                              .mapToInt(trackEntry -> trackEntry.containedTracksProperty().size())
                                              .sum();
        String appendix = totalArtistTracks == 1 ? " track" : " tracks";
        return String.valueOf(totalArtistTracks) + appendix;
    }

    private String getAlbumString() {
        int numberOfTrackSets = albumTrackSets.size();
        String appendix = numberOfTrackSets == 1 ? " album" : " albums";
        return String.valueOf(numberOfTrackSets) + appendix;
    }

    void checkSelectedTracks() {
        if (artistsListView.getSelectionModel().getSelectedItem() == null) {
            Platform.runLater(() -> {
                artistsListView.getSelectionModel().select(0);
                artistsListView.getFocusModel().focus(0);
                artistsListView.scrollTo(0);
            });
        }
    }

    void updateShowingTrackSets() {
        String showingArtist = nameLabel.getText();
        Multimap<String, Entry<Integer, Track>> updatedAlbumTrackSets = musicLibrary
                .getAlbumTracksOfArtist(showingArtist);

        Set<String> oldAlbums = albumTrackSets.keySet();
        Set<String> newAlbums = updatedAlbumTrackSets.keySet();
        Set<String> addedAlbums = Sets.difference(newAlbums, oldAlbums).immutableCopy();
        Set<String> removedAlbums = Sets.difference(oldAlbums, newAlbums).immutableCopy();
        Set<String> holdedAlbums = Sets.intersection(oldAlbums, newAlbums).immutableCopy();

        holdedAlbums.forEach(album -> {
            TrackSetAreaRow albumTrackSet = albumTrackSets.get(album);
            Set<Entry<Integer, Track>> oldTracks = ImmutableSet.copyOf(albumTrackSet.containedTracksProperty());
            Set<Entry<Integer, Track>> newTracks = ImmutableSet.copyOf(updatedAlbumTrackSets.get(album));
            Set<Entry<Integer, Track>> addedTracks = Sets.difference(newTracks, oldTracks).immutableCopy();
            Set<Entry<Integer, Track>> removedTracks = Sets.difference(oldTracks, newTracks).immutableCopy();

            Platform.runLater(() -> {
                albumTrackSets.get(album).containedTracksProperty().addAll(addedTracks);
                albumTrackSets.get(album).containedTracksProperty().removeAll(removedTracks);
            });
        });
        addedAlbums.forEach(album -> Platform.runLater(() -> addTrackSet(album, updatedAlbumTrackSets.get(album))));
        removedAlbums.forEach(album -> Platform.runLater(() -> albumTrackSets.remove(album)));

        if (albumTrackSets.isEmpty() && ! artistsListView.getItems().isEmpty())
            checkSelectedTracks();
    }

    private void addTrackSet(String album, Collection<Entry<Integer, Track>> tracks) {
        TrackSetAreaRow trackSetAreaRow = new TrackSetAreaRow(nameLabel.getText(), album, tracks);
        trackSetAreaRow.selectedTracksProperty().addListener((obs, oldList, newList) -> checkSelectedTracks());
        albumTrackSets.put(album, trackSetAreaRow);
    }

    /**
     * Puts several {@link TrackSetAreaRow}s in the view given the tracks, in form of
     * {@link Entry}, mapped by album
     *
     * @param tracksByAlbum The @{@link Multimap} containing tracks mapped by album
     */
    public void setArtistTrackSets(Multimap<String, Entry<Integer, Track>> tracksByAlbum) {
        albumTrackSets.clear();
        trackSetsAreaVBox.getChildren().clear();
        tracksByAlbum.asMap().entrySet().forEach(multimapEntry -> {
            String album = multimapEntry.getKey();
            Collection<Entry<Integer, Track>> tracks = multimapEntry.getValue();
            addTrackSet(album, tracks);
        });
    }

    /**
     * Removes a given track {@link Entry} from the {@link TrackSetAreaRow}s that
     * are shown on the view
     *
     * @param trackEntry An {@link Entry} with a {@link Track} id and itself
     */
    public void removeFromTrackSets(Entry<Integer, Track> trackEntry) {
        String trackAlbum = trackEntry.getValue().getAlbum();
        trackAlbum = trackAlbum.isEmpty() ? "Unknown album" : trackAlbum;
        if (albumTrackSets.containsKey(trackAlbum)) {
            TrackSetAreaRow albumAreaRow = albumTrackSets.get(trackAlbum);
            boolean removed = albumAreaRow.containedTracksProperty().remove(trackEntry);
            if (removed && albumAreaRow.containedTracksProperty().isEmpty())
                albumTrackSets.remove(trackAlbum);
            if (albumTrackSets.isEmpty() && ! artistsListView.getItems().isEmpty())
                checkSelectedTracks();
        }
    }

    public ObservableList<Entry<Integer, Track>> getSelectedTracks() {
        return albumTrackSets.values().stream().flatMap(entry -> entry.selectedTracksProperty().stream())
                             .collect(Collectors.toCollection(FXCollections::observableArrayList));
    }

    public void selectAllTracks() {
        albumTrackSets.values().forEach(TrackSetAreaRow::selectAllTracks);
    }

    public void deselectAllTracks() {
        albumTrackSets.values().forEach(TrackSetAreaRow::deselectAllTracks);
    }
}