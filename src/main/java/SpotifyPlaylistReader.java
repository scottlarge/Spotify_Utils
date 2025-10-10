import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;

public class SpotifyPlaylistReader {

    // Spotify API Credentials (Replace with your actual credentials)
    private static String CLIENT_ID = null;
    private static String CLIENT_SECRET = null;

    // Spotify Redirect URI (Set in Spotify Developer Dashboard)
    private static final String REDIRECT_URI = "http://localhost:9000/";

    private SpotifyPlaylistReader() {
    }

    public static void init() {
        Properties appProps = new Properties();

        try {
            appProps.load((new SpotifyPlaylistReader()).getClass().getClassLoader().getResourceAsStream("spotify.properties"));
            Object clientId = appProps.get("CLIENT_ID");
            CLIENT_ID = (String) clientId;
            Object clientSecret = appProps.get("CLIENT_SECRET");
            CLIENT_SECRET = (String) clientSecret;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        Map<String, String> tracks;

        try {
            if (Config.ACCESS_TOKEN == null) {
                String authorizationCode = SpotifyPlaylistReader.getAuthorizationCode();
                Config.ACCESS_TOKEN = SpotifyPlaylistReader.getAccessTokenFromCode(authorizationCode);
            }

            System.out.println("Access token: " + Config.ACCESS_TOKEN);

//            fetchAllPlaylistTracks(PLAYLIST_ID);
            tracks = fetchNewReleasesAutoPlaylistTracks();
        } catch (Exception e) {
            try {
                String authorizationCode = SpotifyPlaylistReader.getAuthorizationCode();
                Config.ACCESS_TOKEN = SpotifyPlaylistReader.getAccessTokenFromCode(authorizationCode);

                tracks = fetchNewReleasesAutoPlaylistTracks();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        for (String track : tracks.keySet()) {
            System.out.println(track + " - " + tracks.get(track));
        }
    }

    static String getAuthorizationCode() throws Exception {
        String authUrl = "https://accounts.spotify.com/authorize?"
                + "client_id=" + CLIENT_ID
                + "&response_type=code"
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                + "&scope=" + URLEncoder.encode("playlist-modify-private playlist-modify-public user-follow-read", "UTF-8");

        System.out.println("Go to the following URL to authorize:");
        System.out.println(authUrl);

        // Prompt user to paste the authorization code
        Scanner scanner = new Scanner(System.in);
        System.out.println("Paste the authorization code here:");
        return scanner.nextLine().trim();
    }

    static String getAccessTokenFromCode(String authorizationCode) throws Exception {
        String tokenUrl = "https://accounts.spotify.com/api/token";

        HttpURLConnection connection = (HttpURLConnection) new URL(tokenUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setDoOutput(true);

        String body = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(authorizationCode, "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                + "&client_id=" + CLIENT_ID
                + "&client_secret=" + CLIENT_SECRET;

        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes());
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        JSONObject json = new JSONObject(response.toString());
        return json.getString("access_token");
    }

    // Method to get Spotify Access Token
    private static String getSpotifyAccessToken() throws Exception {
        String authUrl = "https://accounts.spotify.com/api/token";
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());

        HttpURLConnection connection = (HttpURLConnection) new URL(authUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setDoOutput(true);
        connection.getOutputStream().write("grant_type=client_credentials".getBytes());

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        JSONObject json = new JSONObject(response.toString());
        return json.getString("access_token");
    }

    public static Map<String, String> fetchNewReleasesAutoPlaylistTracks() throws Exception {
        String playlistId = "11pZTCqhIJ8qLdZAe8T6P5";
        return fetchAllPlaylistTracks(playlistId);
    }


    public static Map<String, String> fetchAllPlaylistTracks(String playlistId) throws Exception {
        return fetchAllPlaylistTracks(playlistId, false);
    }

    public static Map<String, String> fetchAllPlaylistTracks(String playlistId, boolean lowercase) throws Exception {
        String apiUrl = "https://api.spotify.com/v1/playlists/" + playlistId + "/tracks";
        Map<String, String> tracks = new HashMap<>();
        int trackCount = 0;

        while (apiUrl != null) {
            // Fetch tracks from the current page
            String response = fetchSpotifyData(apiUrl);
            JSONObject json = new JSONObject(response);

            // Parse tracks on the current page
            JSONArray items = json.getJSONArray("items");

            for (int i = 0; i < items.length(); i++) {
                JSONObject track = items.getJSONObject(i).getJSONObject("track");
                String trackName = track.getString("name");
                String artistName = null;

                for (int j = 0; j < track.getJSONArray("artists").length(); j++) {
                    if (artistName == null) {
                        artistName = track.getJSONArray("artists").getJSONObject(j).getString("name");
                    } else {
                        artistName = artistName + ", " + track.getJSONArray("artists").getJSONObject(j).getString("name");
                    }
                }

                String key = trackName + " | " + artistName;

                if (lowercase) {
                    key = key.toLowerCase();
                }
//                tracks.put(track.getString("uri"), trackName + " by " + artistName);
                tracks.put(key, track.getString("uri"));
//                System.out.println((++trackCount) + ". " + trackName + " by " + artistName);
            }

            // Get the next page URL
            apiUrl = json.optString("next", null);
        }

        return tracks;
    }

    public static void removeTrackFromPlaylist(String playlistId, String trackUri) throws Exception {
        String apiUrl = "https://api.spotify.com/v1/playlists/" + playlistId + "/tracks";

        // JSON body to specify the track to remove
        JSONObject requestBody = new JSONObject();
        JSONArray tracks = new JSONArray();
        JSONObject trackObject = new JSONObject();
        trackObject.put("uri", trackUri); // The Spotify URI of the track
        tracks.put(trackObject);
        requestBody.put("tracks", tracks);

        // Send DELETE request
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("DELETE");
        connection.setRequestProperty("Authorization", "Bearer " + Config.ACCESS_TOKEN);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // Write the request body
        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestBody.toString().getBytes());
            os.flush();
        }

        // Read the response
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            System.out.println("Track removed successfully.");
        } else {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            System.out.println("Error removing track: " + response);
        }
    }

    // Method to Fetch Data from Spotify API
    private static String fetchSpotifyData(String apiUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + Config.ACCESS_TOKEN);

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }
}