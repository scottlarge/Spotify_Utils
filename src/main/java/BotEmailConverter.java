public class BotEmailConverter {
    public static void main(String[] args) {
        String emailText = "User: Lauren in Liverpool\n" +
                "\n" +
                "Playlist: Open playlist\n" +
                "\n" +
                "    Song About the Moon — Paul Simon (track)\n" +
                "    Pink Moon — Nick Drake (track)\n" +
                "    Power of the Moon — Ezra Furman (track)\n" +
                "    Sisters of the Moon - 2015 Remaster — Fleetwood Mac (track)\n" +
                "    Moon — Sia (track)\n" +
                "\n";
        int startNumber = 55;

        String user = "nouser";
        boolean openFound = false;
        int rowCount = 0;

        for (String line : emailText.split("\n")) {
            if (line.startsWith("User: ")) {
                user = line.replace("User: ", "");
            } else if (line.equals("Playlist: Open playlist")) {
                openFound = true;
            } else if (openFound && !line.isEmpty()) {
                rowCount++;

                System.out.println("|-");
                System.out.print("|");
                System.out.print(++startNumber);
                System.out.print("||");
                System.out.print(line.replace(" — ", "||").replace(" (track)", ""));
                System.out.print("||");
                System.out.println(user);

                if (rowCount == 1) {
                    System.out.println("| rowspan=\"5\" |");
                }
            }
        }
    }
}
