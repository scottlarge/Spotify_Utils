import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class NewReleasesSpotifyPlaylistReader {
    // Spotify Playlist ID
    private static final String PLAYLIST_ID = "11pZTCqhIJ8qLdZAe8T6P5";

    // Set to access token to use this rather than get new one. Set to null to get a new access token.
    private static String ACCESS_TOKEN = Config.ACCESS_TOKEN;

    public static void main(String[] args) {
        try {
            SpotifyPlaylistReader.init();

            if (ACCESS_TOKEN == null) {
                String authorizationCode = SpotifyPlaylistReader.getAuthorizationCode();
                ACCESS_TOKEN = SpotifyPlaylistReader.getAccessTokenFromCode(authorizationCode);
            }

            System.out.println("Access token: " + ACCESS_TOKEN);

//            fetchNewReleases(accessToken);

            Map<String, String> tracks;

            try {
               tracks = SpotifyPlaylistReader.fetchAllPlaylistTracks(PLAYLIST_ID);
            } catch (Exception e) {
                String authorizationCode = SpotifyPlaylistReader.getAuthorizationCode();
                ACCESS_TOKEN = SpotifyPlaylistReader.getAccessTokenFromCode(authorizationCode);
                tracks = SpotifyPlaylistReader.fetchAllPlaylistTracks(PLAYLIST_ID);
                System.out.println("Access token: " + ACCESS_TOKEN);
            }

            for (String trackName : tracks.keySet()) {
                System.out.println(trackName + "|" + tracks.get(trackName));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}