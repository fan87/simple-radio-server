package me.fan87.fanaaudio.station;

import com.google.gson.annotations.Expose;
import me.fan87.fanaaudio.FANARadio;
import okhttp3.MediaType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
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
     * Start playing + processing tracks + sending radio data
     * @param radio Instance of FANARadio
     */
    public void startServicing(FANARadio radio) {
        if (this.namespace.equals("reload")) {
//            radio.getLogger().warn("You are not allowed to use \"" + this.namespace + "\" as radio station namespace");
        }
        File folder = new File("tracks/" + this.namespace);
        if (!folder.isDirectory() || !folder.exists()) {
            try {
                folder.delete();
            } catch (Exception e) {}
            folder.mkdirs();
        }

        List<File> files = new ArrayList<>(Arrays.asList(folder.listFiles()));
        files.sort(new Comparator<File>() {
            // Credit: https://stackoverflow.com/questions/16898029/how-to-sort-file-names-in-ascending-order
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
            return;
        }
        for (File file : files) {
            radio.getLogger().info(String.format("[%s]  Added %s to Track List", namespace, file.getName()));
            tracks.add(file);
        }
        new Thread(() -> {
            while (true) {
                for (File track : tracks) {
                    streamingThread = new Thread(() -> {
                        try {
                            radio.getLogger().info(String.format("[%s]  Started playing track: %s", this.namespace, track.getName()));
                            ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-re", "-i", String.format("%s", track.getAbsolutePath()), "-y", "-f", "mp3", "-sample_rate", "44100", "-b:a", "256k", "pipe:");
                            System.out.println("ffmpeg -re -i \"" + track.getAbsolutePath() + "\" -y -f mp3 -sample_rate 44100 pipe:");
                            Process process = builder.start();
                            InputStream inputStream = process.getInputStream();
                            while (process.isAlive()) {
                                int read = inputStream.read();
                                this.lastSentTime = System.currentTimeMillis();
                                if (read == -1) break;
                                for (OutputStream outputStream : new HashMap<>(receivers).keySet()) {
                                    try {
                                        outputStream.write(read);
                                        try {
                                            outputStream.flush();
                                        } catch (Exception e) {
                                            radio.getLogger().info(String.format("[%s]  Client %s has disconnected! ", namespace, receivers.get(outputStream).getHostName() + ":" + receivers.get(outputStream).getPort()));
                                            receivers.remove(outputStream);
                                        }
                                    } catch (Error e) {
                                        radio.getLogger().info(String.format("[%s]  Client %s has disconnected! ", namespace, receivers.get(outputStream).getHostName() + ":" + receivers.get(outputStream).getPort()));
                                        receivers.remove(outputStream);
                                    }
                                }
                            }
                            Scanner scanner = new Scanner(process.getErrorStream());
                            while (scanner.hasNextLine()) {
                                System.out.println(scanner.nextLine());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }, "Streaming Thread " + this.namespace);
                    try {
                        streamingThread.start();
                        while (!streamingThread.isInterrupted() && streamingThread.isAlive()) {

                        }
                        radio.getLogger().warn(String.format("[%s]  Streaming Thread has been stopped! Song skipped!", namespace));

                    } catch (Exception e) {
                        radio.getLogger().warn(String.format("[%s]  Streaming Thread has been stopped! Song skipped!", namespace));
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
            builder.append(String.format("<h1>%s</h1>\n", radio.getConfigsManager().getConfig().stationName) +
                    "<h2>Station not found! Here's all available stations:</h2>\n" +
                    "<p>Note: M3U requires 3rd party software. If you want to play it in your browser, use MP3</p>");
            for (AudioStation station : radio.getStationsManager().stations) {
                builder.append(String.format("\n<li><a href=\"%s\">%s</a>  (<a href=\"%s\">M3U</a> | <a href=\"%s\">MP3</a>) </li>",
                        "/" + station.namespace + "/radio.mp3", station.name, "/" + station.namespace + "/radio.m3u", "/" + station.namespace + "/radio.mp3"));
            }
            String text = builder.toString();
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(404, text.length());
            exchange.getResponseBody().write(text.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            exchange.getResponseBody().close();
        });
    }
}
