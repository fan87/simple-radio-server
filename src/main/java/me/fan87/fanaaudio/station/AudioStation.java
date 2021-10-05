package me.fan87.fanaaudio.station;


import com.google.gson.annotations.Expose;
import me.fan87.fanaaudio.FANARadio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
    private transient boolean disableWatchdog = false;

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

    private transient int promises = 0;

    /**
     * Load all tracks
     * @param radio Radio instance (For logging)
     * @return If successful
     */
    private boolean indexTracks(FANARadio radio) {
        disableWatchdog = true;
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
            promises += 1;
            new Thread(() -> {
                splitAudio(radio, file, true);
            }).start();
        }
        while (promises != 0) {

        }
        disableWatchdog = false;
        return true;
    }

    /**
     * Split a song into multiple parts to avoid FFmpeg glitch
     * @param radio Radio instance
     * @param track Track
     */
    private List<File> splitAudio(FANARadio radio, File track, boolean promise) {
        radio.getLogger().info("Preparing Track: " + track.getName() + "! If it's stuck, it means the track wasn't ready. It will take a while, but it won't take too long.");
        File cacheFolder = new File("cache/" + this.namespace + "/" + track.getName() + "/");
        if (!cacheFolder.exists()) {
            cacheFolder.mkdirs();
        } else {
            List<File> files = new ArrayList<>(Arrays.asList(cacheFolder.listFiles()));
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
            if (promise) promises -= 1;
            return files;
        }
        ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-i", String.format("%s", track.getAbsolutePath()), "-reset_timestamps", "1", "-ac", "2", "-f", "segment", "-segment_time", "300", "-y", "-acodec", "aac", "-sample_rate", "44100", "-map", "0:a", "-write_xing", "0", "-b:a", "256000", "cache/" + this.namespace + "/" + track.getName() + "/%03d_out.aac");
        try {
            Process start = builder.start();
            start.waitFor();
            List<File> files = new ArrayList<>(Arrays.asList(cacheFolder.listFiles()));
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
            if (promise) promises -= 1;
            return files;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;


    }

    /**
     * Start playing + processing tracks + sending radio data
     * @param radio Instance of FANARadio
     */
    public void startServicing(FANARadio radio) {
        new Thread(() -> {
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
                                disableWatchdog = true;
                                for (File file : splitAudio(radio, track, false)) {
                                    disableWatchdog = false;
                                    radio.getLogger().info(String.format("[%s]  Started playing track: %s", this.namespace, track.getName()));
                                    ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-re", "-i", String.format("%s", file.getAbsolutePath()), "-reset_timestamps", "1", "-ac", "2", "-y", "-f", "adts", "-acodec", "aac", "-sample_rate", "44100", "-map", "0:a", "-map_metadata", "-1", "-write_xing", "0", "-id3v2_version", "0", "-b:a", "256000", "pipe:"); // Convert Format to streamable.aac (Using FFmpeg)
                                    process = builder.start();
                                    InputStream inputStream = process.getInputStream();
                                    int read;
                                    while ((read = inputStream.read()) != -1) {
                                        this.lastSentTime = System.currentTimeMillis(); // For watchdog thread (Skip the song if it's stuck)
                                        sendData(radio, (byte) read); // Send data
                                    }
                                    if (radio.getConfigsManager().getConfig().debug) {
                                        Scanner errorScanner = new Scanner(process.getErrorStream());
                                        while (errorScanner.hasNextLine()) {
                                            System.out.println(errorScanner.nextLine()); // Send ffmpeg output to console for debugging purpose
                                        }
                                    }
                                }

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }, "Streaming Thread " + this.namespace);

                        try {                     // Start song sending (Stream Thread) thread
                            streamingThread.start();
                            while (!streamingThread.isInterrupted() && streamingThread.isAlive()) {}
                            radio.getLogger().warn(String.format("[%s]  Streaming Thread has been stopped! Song skipped!", namespace));
                            if (radio.getConfigsManager().getConfig().debug && process != null) {
                                Scanner errorScanner = new Scanner(process.getErrorStream());
                                while (errorScanner.hasNextLine()) {
                                    System.out.println(errorScanner.nextLine()); // Send ffmpeg output to console for debugging purpose
                                }
                            }
                        } catch (Exception e) {
                            radio.getLogger().warn(String.format("[%s]  Streaming Thread has been stopped! Song skipped!", namespace));
                            if (radio.getConfigsManager().getConfig().debug) {
                                e.printStackTrace();
                                if (process != null) {
                                    Scanner errorScanner = new Scanner(process.getErrorStream());
                                    while (errorScanner.hasNextLine()) {
                                        System.out.println(errorScanner.nextLine()); // Send ffmpeg output to console for debugging purpose
                                    }
                                }
                            }
                        }
                    }
                }
            }).start();

            new Thread(() -> {
                while (true) {
                    if (disableWatchdog) {
                        lastSentTime = System.currentTimeMillis();
                    }
                    long timeout = System.currentTimeMillis() - lastSentTime;
                    if ((timeout) > 10000) {
                        lastSentTime = System.currentTimeMillis();
                        radio.getLogger().error(String.format("[%s]  Send Timeout (%sms > 10000ms) Stopping Thread (Skip Song)...", namespace, Long.toString(timeout)));
                        streamingThread.stop();
                    }
                }
            }, "Watchdog Thread " + this.namespace).start();
        }, this.namespace + " Main").start();


    }

    /**
     * Register all Http Contexts
     * @param radio Instance of FANARadio
     */
    public void registerHandlers(FANARadio radio) {
        radio.getServer().createContext("/" + this.namespace + "/", (exchange) -> {
            if (exchange.getRequestURI().getPath().endsWith(".aac")) {
                new Thread(() -> {
                    try {
                        exchange.getResponseHeaders().add("Content-Type", "audio/aac");
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
                                "radio.aac";
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
                    String.format("<h2>%s</h2>\n", radio.getConfigsManager().getConfig().radioIndexSubtitle) +
                    String.format("<p>%s</p>\n", radio.getConfigsManager().getConfig().radioIndexHint));
            for (AudioStation station : radio.getStationsManager().stations) {
                builder.append(String.format("\n<li><a href=\"%s\">%s</a>  (<a href=\"%s\">M3U</a> | <a href=\"%s\">AAC</a>) </li>",
                        "/" + station.namespace + "/radio.aac", station.name, "/" + station.namespace + "/radio.m3u", "/" + station.namespace + "/radio.aac"));
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