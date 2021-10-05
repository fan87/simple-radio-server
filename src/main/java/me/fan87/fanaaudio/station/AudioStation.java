package me.fan87.fanaaudio.station;

import com.google.gson.annotations.Expose;
import me.fan87.fanaaudio.FANARadio;
import okhttp3.MediaType;

import java.io.*;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

public class AudioStation {

    @Expose
    public String namespace = "";

    @Expose
    public String name = "";

    @Expose
    public String owner = "";

    private transient long lastSentTime = System.currentTimeMillis();
    private transient Thread streamingThread = null;
    private transient Process process = null;

    public transient List<File> tracks = new ArrayList<>();
    public transient Map<OutputStream, InetSocketAddress> receivers = new HashMap<>();

    AudioStation() {

    }

    public AudioStation(String namespace, String name, String owner) {
        this.namespace = namespace;
        this.name = name;
        this.owner = owner;
    }

    /**
     * Send byte to all receivers
     * @param radio Radio instance (For disconnect logging)
     * @param bytes Bytes to send (Require bytes array. You can cast int to byte)
     */
    private void sendData(FANARadio radio, byte... bytes) {
        for (OutputStream outputStream : new HashMap<>(receivers).keySet()) {
            try {
                outputStream.write(bytes);
            } catch (Exception e) {
                radio.getLogger().info(String.format("[%s]  Client %s has disconnected! ", namespace, receivers.get(outputStream).getHostName() + ":" + receivers.get(outputStream).getPort()));
                receivers.remove(outputStream);
            }
        }
    }

    /**
     * Load all tracks
     * @param radio Radio instance (For logging)
     * @return If successful
     */
    private boolean indexTracks(FANARadio radio) {
        File folder = new File("tracks/" + this.namespace);
        if (!folder.isDirectory() || !folder.exists()) {
            try {
                folder.delete();
            } catch (Exception e) {}
            folder.mkdirs();
        }

        List<File> files = new ArrayList<>(Arrays.asList(folder.listFiles()));
        files.sort(new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                int n1 = extractNumber(o1.getName());
                int n2 = extractNumber(o2.getName());
                return n1 - n2;
            }

            private int extractNumber(String name) {
                int i = 0;
                try {
                    String number = name.split("_")[0];
                    i = Integer.parseInt(number);
                } catch(Exception e) {
                    i = 0;
                }
                return i;
            }
        });

        if (files.size() == 0) {
            radio.getLogger().warn("Radio: " + namespace + " has no track. Ignoring");
            return false;
        }
        for (File file : files) {
            radio.getLogger().info(String.format("[%s]  Added %s to Track List", namespace, file.getName()));
            tracks.add(file);
        }
        return true;
    }

    /**
     * Start playing + processing tracks + sending radio data
     * @param radio Instance of FANARadio
     */
    public void startServicing(FANARadio radio) {
        if (!indexTracks(radio)) {
            return;
        }
        new Thread(() -> { // Radio Handling Thread.  Note: There will only be one Radio Handling Tread
            while (true) {
                if (radio.getConfigsManager().getConfig().shuffle) {
                    Collections.shuffle(tracks);
                }
                for (File track : tracks) {
                    streamingThread = new Thread(() -> { // Streaming Thread. This will send a song.
                        try {
                            radio.getLogger().info(String.format("[%s]  Started playing track: %s", this.namespace, track.getName()));
                            ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-re", "-i", String.format("%s", track.getAbsolutePath()), "-reset_timestamps", "1", "-ac", "2", "-y", "-f", "mp3", "-acodec", "libmp3lame", "-sample_rate", "44100", "-map", "0:a", "-map_metadata", "-1", "-write_xing", "0", "-id3v2_version", "0", "-b:a", "256000", "pipe:")
                                    .redirectErrorStream(false); // Convert Format to streamable MP3 (Using FFmpeg)
                            process = builder.start();
                            InputStream inputStream = process.getInputStream();
                            int read = 0;
                            while ((read = inputStream.read()) != -1) {
                                this.lastSentTime = System.currentTimeMillis(); // For watchdog thread (Skip the song if it's stuck)
                                sendData(radio, (byte) read); // Send data back if it's not header
                            }
                            if (radio.getConfigsManager().getConfig().debug) {
                                Scanner errorScanner = new Scanner(process.getErrorStream());
                                while (errorScanner.hasNextLine()) {
                                    System.out.println(errorScanner.nextLine()); // Send ffmpeg output to console for debugging purpose
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }, "Streaming Thread " + this.namespace);

                    try {                     // Start song sending (Stream Thread) thread
                        streamingThread.start();
                        while (!streamingThread.isInterrupted() && streamingThread.isAlive()) {

                        }
                        radio.getLogger().warn(String.format("[%s]  Streaming Thread has been stopped! Song skipped!", namespace));
                        Scanner scanner = new Scanner(process.getErrorStream());
                        if (radio.getConfigsManager().getConfig().debug) {
                            Scanner errorScanner = new Scanner(process.getErrorStream());
                            while (errorScanner.hasNextLine()) {
                                System.out.println(errorScanner.nextLine()); // Send ffmpeg output to console for debugging purpose
                            }
                        }
                    } catch (Exception e) {
                        radio.getLogger().warn(String.format("[%s]  Streaming Thread has been stopped! Song skipped!", namespace));
                        Scanner scanner = new Scanner(process.getErrorStream());
                        if (radio.getConfigsManager().getConfig().debug) {
                            e.printStackTrace();
                            Scanner errorScanner = new Scanner(process.getErrorStream());
                            while (errorScanner.hasNextLine()) {
                                System.out.println(errorScanner.nextLine()); // Send ffmpeg output to console for debugging purpose
                            }
                        }
                    }
                }
            }
        }).start();

        new Thread(() -> {
            while (true) {
                long timeout = System.currentTimeMillis() - lastSentTime;
                if ((timeout) > 10000) {
                    lastSentTime = System.currentTimeMillis();
                    radio.getLogger().error(String.format("[%s]  Send Timeout (%sms > 10000ms) Stopping Thread (Skip Song)...", namespace, Long.toString(timeout)));
                    streamingThread.stop();
                }
            }
        }, "Watchdog Thread " + this.namespace).start();

    }

    /**
     * Register all Http Contexts
     * @param radio Instance of FANARadio
     */
    public void registerHandlers(FANARadio radio) {
        radio.getServer().createContext("/" + this.namespace + "/", (exchange) -> {
            if (exchange.getRequestURI().getPath().endsWith(".mp3")) {
                new Thread(() -> {
                    try {
                        exchange.getResponseHeaders().add("Content-Type", "audio/mp3");
                        exchange.sendResponseHeaders(200, 0);
                        radio.getLogger().info(String.format("[%s]  %s has joined the stream", this.namespace, exchange.getRemoteAddress().getHostName() + ":" + exchange.getRemoteAddress().getPort()));
                        receivers.put(exchange.getResponseBody(), exchange.getRemoteAddress());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                return;
            }
            if (exchange.getRequestURI().getPath().endsWith(".m3u")) {
                String text =
                        "#EXTM3U\n" +
                        "#EXTINF:0, " + owner + " - " + name + "\n" +
                        "radio.mp3";
                exchange.getResponseHeaders().add("Content-Type", "audio/mpegurl");
                exchange.sendResponseHeaders(200, text.getBytes(StandardCharsets.UTF_8).length);
                exchange.getResponseBody().write(text.getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                exchange.getResponseBody().close();
                return;
            }
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("" +
                    "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "<meta charset=\"UTF-8\">\n" +
                    String.format("<title>%s</title>\n", radio.getConfigsManager().getConfig().stationName) +
                    "</head>\n" +
                    "<body>\n" +
                    "<h1>%s</h1>\n", radio.getConfigsManager().getConfig().stationName) +
                    "<h2>Station not found! Here's all available stations:</h2>\n" +
                    "<p>Note: M3U requires 3rd party software. If you want to play it in your browser, use MP3</p>\n");
            for (AudioStation station : radio.getStationsManager().stations) {
                builder.append(String.format("\n<li><a href=\"%s\">%s</a>  (<a href=\"%s\">M3U</a> | <a href=\"%s\">MP3</a>) </li>",
                        "/" + station.namespace + "/radio.mp3", station.name, "/" + station.namespace + "/radio.m3u", "/" + station.namespace + "/radio.mp3"));
            }
            builder.append("</body>\n");
            builder.append("</html>\n");
            String text = builder.toString();
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, text.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(text.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            exchange.getResponseBody().close();
        });
    }
}
