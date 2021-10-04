package me.fan87.fanaaudio.station;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import me.fan87.fanaaudio.FANARadio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StationsManager {

    public List<AudioStation> stations = new ArrayList<>();

    public StationsManager(FANARadio radio) {
        stations.addAll(radio.getConfigsManager().getConfig().stations);
        List<String> names = new ArrayList<>();
        for (AudioStation station : stations) {
            if (names.contains(station.namespace)) {
                radio.getLogger().warn("Station: " + station.namespace + " has duplicated name. Ignoring");
                continue;
            }
            names.add(station.namespace);
            station.registerHandlers(radio);
            station.startServicing(radio);
        }
        radio.getServer().createContext("/", exchange -> {
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
