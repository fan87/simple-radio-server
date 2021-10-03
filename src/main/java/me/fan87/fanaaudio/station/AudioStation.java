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

public class AudioStation {

    @Expose
    public String namespace = "";

    @Expose
    public String name = "";

    @Expose
    public String owner = "";

    public List<File> tracks = new ArrayList<>();
    public Map<OutputStream, InetSocketAddress> receivers = new HashMap<>();

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
                    int s = name.indexOf('_')+1;
                    int e = name.lastIndexOf('.');
                    String number = name.substring(s, e);
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
                    try {
                        radio.getLogger().info(String.format("[%s]  Started playing track: %s", this.namespace, track.getName()));
                        ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-re", "-i", track.getAbsolutePath(), "-y", "-f", "mp3", "pipe:");
                        Process process = builder.start();
                        InputStream inputStream = process.getInputStream();
                        while (true) {
                            int read = inputStream.read();
                            if (read == -1) break;
                            for (OutputStream outputStream : new HashMap<>(receivers).keySet()) {
                                try {
                                    outputStream.write(read);
                                } catch (IOException e) {
                                    radio.getLogger().info(String.format("[%s]  Client %s has disconnected! ", namespace, receivers.get(outputStream).getHostName() + ":" + receivers.get(outputStream).getPort()));
                                    receivers.remove(outputStream);
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    /**
     * Register all Http Contexts
     * @param radio Instance of FANARadio
     */
    public void registerHandlers(FANARadio radio) {
        radio.getServer().createContext("/" + this.namespace + "/", (exchange) -> {
            if (exchange.getRequestURI().getPath().endsWith(".mp3")) {
                exchange.getResponseHeaders().add("Content-Type", "audio/mp3");
                exchange.sendResponseHeaders(200, 0);
                radio.getLogger().info(String.format("[%s]  %s has joined the stream", this.namespace, exchange.getRemoteAddress().getHostName() + ":" + exchange.getRemoteAddress().getPort()));
                receivers.put(exchange.getResponseBody(), exchange.getRemoteAddress());
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
        });
    }
}
