import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class FollowedArtistsReader {
    // Set to access token to use this rather than get new one. Set to null to get a new access token.
    private static String ACCESS_TOKEN = Config.ACCESS_TOKEN;
    private static int WAIT = 2500;

    public static void main(String[] args) {
        try {
            if (ACCESS_TOKEN == null) {
                String authorizationCode = SpotifyPlaylistReader.getAuthorizationCode();
                ACCESS_TOKEN = SpotifyPlaylistReader.getAccessTokenFromCode(authorizationCode);
            }

            System.out.println("Access token: " + ACCESS_TOKEN);

            Map<String, String> artists;

            try {
                artists = fetchFollowingArtists();
            } catch (Exception e) {
                String authorizationCode = SpotifyPlaylistReader.getAuthorizationCode();
                ACCESS_TOKEN = SpotifyPlaylistReader.getAccessTokenFromCode(authorizationCode);
                System.out.println("Access token: " + ACCESS_TOKEN);
                artists = fetchFollowingArtists();
            }

            int artistFetchedCount = 0;
            String fromReleaseDate = "2025-07-30";

            if (artists != null) {
                int albumCount = 0;

                for (String artist : artists.keySet()) {
                    artistFetchedCount++;
                    String artistId = artists.get(artist);
                    artistId = artistId.replace("spotify:artist:", "");
//                    System.out.println(artistFetchedCount + ". " + artist + " (" + artistId + ")");
                    List<Album> albums = fetchArtistAlbums(artistId);

                    for (Album album : albums) {
                        if (!album.getAlbumType().equalsIgnoreCase("compilation") && album.getReleaseDate().compareTo(fromReleaseDate) >= 0) {
                            System.out.println("\t" + (++albumCount) + ". " + artist + " - " + album.getName() + " [" + album.getSpotifyId() + "] [" + album.getAlbumType() + "] (" + album.getReleaseDate() + ")");

                            List<String> trackIds = fetchAlbumTrackIDs(album.getId());
                            album.setTrackIds(trackIds);

                            boolean added = addToPlaylist(album);

                            if (added) {
                                System.out.println("Added");
                            } else {
                                System.out.println("Error adding " + album.getAlbumType() + " to playlist");
                            }
                        } else {
                            System.out.println("SKIPPING \t" + (++albumCount) + ". " + artist + " - " + album.getName() + " [" + album.getSpotifyId() + "] [" + album.getAlbumType() + "] (" + album.getReleaseDate() + ")");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class Album implements Comparable<Album> {

        private String id;
        private String name;
        private String releaseDate;
        private String spotifyId;
        private String albumType;
        private List<String> trackIds;

        public Album(String id, String name, String releaseDate, String spotifyId, String albumType) {
            this.id = id;
            this.name = name;
            this.releaseDate = releaseDate;
            this.spotifyId = spotifyId;
            this.albumType = albumType;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getReleaseDate() {
            return releaseDate;
        }

        public void setReleaseDate(String releaseDate) {
            this.releaseDate = releaseDate;
        }

        public String getSpotifyId() {
            return spotifyId;
        }

        public void setSpotifyId(String spotifyId) {
            this.spotifyId = spotifyId;
        }

        public String getAlbumType() {
            return albumType;
        }

        public void setAlbumType(String albumType) {
            this.albumType = albumType;
        }

        public List<String> getTrackIds() {
            return trackIds;
        }

        public void setTrackIds(List<String> trackIds) {
            this.trackIds = trackIds;
        }

        // override equals and hashCode
        @Override
        public int compareTo(Album album) {
            return this.releaseDate.compareTo(album.getReleaseDate());
        }
    }

    // Method to Fetch Following Artists (Handles Pagination)
    public static Map<String, String> fetchFollowingArtists() throws Exception {
        String apiUrl = "https://api.spotify.com/v1/me/following?type=artist&limit=50";
        Map<String, String> artists = new HashMap<>();

        while (apiUrl != null) {
            // Fetch artists from the current page
            String response = fetchSpotifyData(apiUrl, WAIT);
            JSONObject json = new JSONObject(response);

            // Parse artists on the current page
            JSONArray items = json.getJSONObject("artists").getJSONArray("items");

            for (int i = 0; i < items.length(); i++) {
                String artistName = items.getJSONObject(i).getString("name");
                artists.put(artistName, items.getJSONObject(i).getString("uri"));
            }

            // Get the next page URL
            apiUrl = json.getJSONObject("artists").optString("next", null);
        }

        return artists;
    }

    // Method to Fetch Artist Albums (Handles Pagination)
    public static List<Album> fetchArtistAlbums(String artistId) throws Exception {
        String apiUrl = "https://api.spotify.com/v1/artists/" + artistId + "/albums?limit=50";
        List<Album> albums = new ArrayList<>();

        while (apiUrl != null) {
            System.out.println("FETCHING Albums for " + artistId + " using URL: " + apiUrl);

            // Fetch albums for artist from the current page
            String response = fetchSpotifyData(apiUrl, WAIT);
            System.out.println(response);
            JSONObject json = new JSONObject(response);

            // Parse artists on the current page
            JSONArray items = json.getJSONArray("items");

            for (int i = 0; i < items.length(); i++) {
                String albumName = items.getJSONObject(i).getString("name");
                String albumType = items.getJSONObject(i).getString("album_type");
                String albumId = items.getJSONObject(i).getString("id");

                albums.add(new Album(albumId, albumName, items.getJSONObject(i).getString("release_date"), items.getJSONObject(i).getString("uri"), albumType));
                System.out.println(albumName + " (" + albumType + ")");
            }

            // Get the next page URL
            apiUrl = json.optString("next", null);
        }

        Collections.sort(albums);

        return albums;
    }

    // Method to Fetch Album Track IDs
    public static List<String> fetchAlbumTrackIDs(String albumId) throws Exception {
        String apiUrl = "https://api.spotify.com/v1/albums/" + albumId + "/tracks?limit=50";
        List<String> tracks = new ArrayList<>();

        while (apiUrl != null) {
            // Fetch albums for artist from the current page
            String response = fetchSpotifyData(apiUrl, WAIT);
            JSONObject json = new JSONObject(response);

            // Parse artists on the current page
            JSONArray items = json.getJSONArray("items");

            for (int i = 0; i < items.length(); i++) {
                tracks.add(items.getJSONObject(i).getString("uri"));
            }

            // Get the next page URL
            apiUrl = json.optString("next", null);
        }

        return tracks;
    }

    private static boolean addToPlaylist(Album album) {
        String apiUrl = "https://api.spotify.com/v1/playlists/11pZTCqhIJ8qLdZAe8T6P5/tracks";

        List<String> trackIds = album.getTrackIds();

        String body = "{\n" +
                "    \"uris\": [\n";

        boolean first = true;

        for (String trackId : trackIds) {
            if (first) {
                body += "\"" + trackId + "\"";
                first = false;
            } else {
                body += ",\"" + trackId + "\"";
            }
        }

        body +="    ],\n" +
                "    \"position\": 0\n" +
                "}";

        try {
            postSpotifyData(apiUrl, body, WAIT);
        } catch (Exception e) {
            return false;
        }

        return true;
    }


    // Method to Fetch Data from Spotify API
    private static String fetchSpotifyData(String apiUrl) throws Exception {
        return fetchSpotifyData(apiUrl, 0);
    }

    private static String fetchSpotifyData(String apiUrl, int wait) throws Exception {
        if (wait > 0) {
            Thread.sleep(wait);
        }

        HttpURLConnection connection = null;

        try {
            System.out.println("Fetching spotify data: " + apiUrl);
            connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);

            System.out.println("... sending");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
                System.out.println(inputLine);
            }
            in.close();

            System.out.println("...END");

            return response.toString();
        } catch (IOException e) {
            if (e.getMessage().contains("HTTP response code: 429")) {
                int retryAfterInt = -1;

                if (connection != null) {
                    String retryAfter = connection.getHeaderField("Retry-After");

                    if (retryAfter != null) {
                        try {
                            retryAfterInt = Integer.parseInt(retryAfter);
                        } catch (NumberFormatException nfe) {
                            retryAfterInt = -1;
                        }
                    }
                }

                if (retryAfterInt == -1) {
                    retryAfterInt = 5000;
                } else {
                    retryAfterInt = retryAfterInt * 1000;
                }

                System.out.println("Being throttled waiting " + retryAfterInt + " milliseconds");
                Thread.sleep(retryAfterInt);
            } else {
                String authorizationCode = SpotifyPlaylistReader.getAuthorizationCode();
                ACCESS_TOKEN = SpotifyPlaylistReader.getAccessTokenFromCode(authorizationCode);
            }

            return fetchSpotifyData(apiUrl, wait);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String postSpotifyData(String apiUrl, String body, int wait) throws Exception {
        if (wait > 0) {
            Thread.sleep(wait);
        }

        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = body.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            return response.toString();
        } catch (IOException e) {
            if (e.getMessage().contains("HTTP response code: 429")) {
                int retryAfterInt = -1;

                if (connection != null) {
                    String retryAfter = connection.getHeaderField("Retry-After");

                    if (retryAfter != null) {
                        try {
                            retryAfterInt = Integer.parseInt(retryAfter);
                        } catch (NumberFormatException nfe) {
                            retryAfterInt = -1;
                        }
                    }
                }

                if (retryAfterInt == -1) {
                    retryAfterInt = 5000;
                } else {
                    retryAfterInt = retryAfterInt * 1000;
                }

                Thread.sleep(retryAfterInt);
            } else {
                String authorizationCode = SpotifyPlaylistReader.getAuthorizationCode();
                ACCESS_TOKEN = SpotifyPlaylistReader.getAccessTokenFromCode(authorizationCode);
            }

            return postSpotifyData(apiUrl, body, wait);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}