import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

public class CGREndlessPlaylistChecker {
    // Set to access token to use this rather than get new one. Set to null to get a new access token.
    private static String PLAYLIST_ID = "7I1dalfsi3clxqliFR6aAh";

    public static void main(String[] args) {
        boolean dryRun = true;
        boolean deletePlayedTwice = false;
        boolean deletePlayedOnce = false;
        int maxPlaylistSize = 500;

        Map<String, Integer> tracks = new HashMap<>();
        BufferedReader reader = null;

        SpotifyPlaylistReader.init();

        try {
            if (Config.ACCESS_TOKEN == null) {
                String authorizationCode = SpotifyPlaylistReader.getAuthorizationCode();
                Config.ACCESS_TOKEN = SpotifyPlaylistReader.getAccessTokenFromCode(authorizationCode);
            }

            System.out.println("Access token: " + Config.ACCESS_TOKEN);

            Map<String, String> playlistTracks;

            try {
                playlistTracks = SpotifyPlaylistReader.fetchAllPlaylistTracks(PLAYLIST_ID, true);
            } catch (Exception e) {
                String authorizationCode = SpotifyPlaylistReader.getAuthorizationCode();
                Config.ACCESS_TOKEN = SpotifyPlaylistReader.getAccessTokenFromCode(authorizationCode);
                playlistTracks = SpotifyPlaylistReader.fetchAllPlaylistTracks(PLAYLIST_ID, true);
                System.out.println("Access token: " + Config.ACCESS_TOKEN);
            }

            reader = new BufferedReader(new FileReader(new File("G:\\My Drive\\CGR Endless Playlist Played Tracks.txt")));
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.toLowerCase();

                if (!line.isEmpty() && !line.startsWith("----")) {
                    if (tracks.containsKey(line)) {
                        tracks.put(line, tracks.get(line)+1);
                    } else {
                        tracks.put(line, 1);
                    }
                }
            }

            System.out.println("\nRemoving any tracks played 3 times or more\n");
            int tracksRemoved = 0;

            for (String key : tracks.keySet()) {
                if (tracks.get(key) > 2) {
                    String[] trackKeyArr = key.split(" \\| ");

                    if (trackKeyArr.length > 1 && trackKeyArr[1].contains(",")) {
                        String[] trackKeyArrParts = trackKeyArr[1].split(", ");
                        List<String> trackKeyArrList = Arrays.asList(trackKeyArrParts);
                        Collections.sort(trackKeyArrList);

                        trackKeyArrList = trackKeyArrList.stream()
                                .map(String::toLowerCase)
                                .collect(Collectors.toList());

                        for (String plKey : playlistTracks.keySet()) {
                            String[] plKeyArr = plKey.split(" \\| ");

                            if (plKeyArr.length > 1 && plKeyArr[1].contains(",")) {
                                String[] plKeyArrParts = plKeyArr[1].split(", ");
                                List<String> plKeyArrList = Arrays.asList(plKeyArrParts);
                                Collections.sort(plKeyArrList);

                                plKeyArrList = plKeyArrList.stream()
                                        .map(String::toLowerCase)
                                        .collect(Collectors.toList());

                                List<String> differences = new ArrayList<>(trackKeyArrList);
                                differences.removeAll(plKeyArrList);

                                if (trackKeyArr[0].equalsIgnoreCase(plKeyArr[0]) && differences.size() == 0) {
                                    System.out.println(key + " = " + tracks.get(key) + " (" + playlistTracks.get(plKey) + ")");

                                    if (!dryRun) {
                                        SpotifyPlaylistReader.removeTrackFromPlaylist(PLAYLIST_ID, playlistTracks.get(plKey));
                                    }

                                    tracksRemoved++;
                                }
                            }
                        }
                    } else {
                        if (playlistTracks.containsKey(key)) {
                            System.out.println(key + " = " + tracks.get(key) + " (" + playlistTracks.get(key) + ")");

                            if (!dryRun) {
                                SpotifyPlaylistReader.removeTrackFromPlaylist(PLAYLIST_ID, playlistTracks.get(key));
                            }

                            tracksRemoved++;
                        }
                    }
                }

            }

            System.out.println(MessageFormat.format("\n{0} track(s) removed that have been played 3 times or more\n", tracksRemoved));

            playlistTracks = SpotifyPlaylistReader.fetchAllPlaylistTracks(PLAYLIST_ID);

            if (playlistTracks.size() > maxPlaylistSize || dryRun || deletePlayedTwice) {
                System.out.println("\nStill too many tracks so removing any tracks played twice\n");
                tracksRemoved = 0;

                for (String key : tracks.keySet()) {
                    if (tracks.get(key) == 2) {
                        String[] trackKeyArr = key.split(" \\| ");

                        if (trackKeyArr.length > 1 && trackKeyArr[1].contains(",")) {
                            String[] trackKeyArrParts = trackKeyArr[1].split(", ");
                            List<String> trackKeyArrList = Arrays.asList(trackKeyArrParts);
                            Collections.sort(trackKeyArrList);

                            trackKeyArrList = trackKeyArrList.stream()
                                    .map(String::toLowerCase)
                                    .collect(Collectors.toList());

                            for (String plKey : playlistTracks.keySet()) {
                                String[] plKeyArr = plKey.split(" \\| ");

                                if (plKeyArr.length > 1 && plKeyArr[1].contains(",")) {
                                    String[] plKeyArrParts = plKeyArr[1].split(", ");
                                    List<String> plKeyArrList = Arrays.asList(plKeyArrParts);
                                    Collections.sort(plKeyArrList);

                                    plKeyArrList = plKeyArrList.stream()
                                            .map(String::toLowerCase)
                                            .collect(Collectors.toList());

                                    List<String> differences = new ArrayList<>(trackKeyArrList);
                                    differences.removeAll(plKeyArrList);

                                    if (trackKeyArr[0].equalsIgnoreCase(plKeyArr[0]) && differences.size() == 0) {
                                        System.out.println(key + " = " + tracks.get(key) + " (" + playlistTracks.get(plKey) + ")");

                                        if (!dryRun) {
                                            SpotifyPlaylistReader.removeTrackFromPlaylist(PLAYLIST_ID, playlistTracks.get(plKey));
                                        }

                                        tracksRemoved++;
                                    }
                                }
                            }
                        } else {
                            if (playlistTracks.containsKey(key)) {
                                System.out.println(key + " = " + tracks.get(key) + " (" + playlistTracks.get(key) + ")");

                                if (!dryRun) {
                                    SpotifyPlaylistReader.removeTrackFromPlaylist(PLAYLIST_ID, playlistTracks.get(key));
                                }

                                tracksRemoved++;
                            }
                        }
                    }
                }

                System.out.println(MessageFormat.format("\n{0} track(s) removed that have been played twice\n", tracksRemoved));
            }

            playlistTracks = SpotifyPlaylistReader.fetchAllPlaylistTracks(PLAYLIST_ID);
            int playedOnce = 0;

            if (dryRun || deletePlayedOnce) {
                System.out.println((deletePlayedOnce ? "\nDeleting " : "Listing ") + "\ntracks played once\n");
                tracksRemoved = 0;

                for (String key : tracks.keySet()) {
                    if (tracks.get(key) == 1) {
                        String[] trackKeyArr = key.split(" \\| ");

                        if (trackKeyArr.length > 1 && trackKeyArr[1].contains(",")) {
                            String[] trackKeyArrParts = trackKeyArr[1].split(", ");
                            List<String> trackKeyArrList = Arrays.asList(trackKeyArrParts);
                            Collections.sort(trackKeyArrList);

                            trackKeyArrList = trackKeyArrList.stream()
                                    .map(String::toLowerCase)
                                    .collect(Collectors.toList());

                            for (String plKey : playlistTracks.keySet()) {
                                String[] plKeyArr = plKey.split(" \\| ");

                                if (plKeyArr.length > 1 && plKeyArr[1].contains(",")) {
                                    String[] plKeyArrParts = plKeyArr[1].split(", ");
                                    List<String> plKeyArrList = Arrays.asList(plKeyArrParts);
                                    Collections.sort(plKeyArrList);

                                    plKeyArrList = plKeyArrList.stream()
                                            .map(String::toLowerCase)
                                            .collect(Collectors.toList());

                                    List<String> differences = new ArrayList<>(trackKeyArrList);
                                    differences.removeAll(plKeyArrList);

                                    if (trackKeyArr[0].equalsIgnoreCase(plKeyArr[0]) && differences.size() == 0) {
                                        System.out.println(key + " = " + tracks.get(key) + " (" + playlistTracks.get(plKey) + ")");

                                        if (!dryRun) {
                                            SpotifyPlaylistReader.removeTrackFromPlaylist(PLAYLIST_ID, playlistTracks.get(plKey));
                                        }

                                        tracksRemoved++;
                                    }
                                }
                            }
                        } else {
                            if (playlistTracks.containsKey(key)) {
                                System.out.println(key + " = " + tracks.get(key) + " (" + playlistTracks.get(key) + ")");
                                playedOnce++;

                                if (!dryRun && deletePlayedOnce) {
                                    SpotifyPlaylistReader.removeTrackFromPlaylist(PLAYLIST_ID, playlistTracks.get(key));
                                }

                                if (deletePlayedOnce) {
                                    tracksRemoved++;
                                }
                            }
                        }
                    }
                }

                if (deletePlayedOnce) {
                    System.out.println(MessageFormat.format("\n{0} track(s) removed that have been played once\n", tracksRemoved));
                } else {
                    System.out.println(MessageFormat.format("\n{0} track(s) played once\n", playedOnce));
                }
            }

            System.out.println(MessageFormat.format("\nThe playlist contains {0} track(s)\n", playlistTracks.size()));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
