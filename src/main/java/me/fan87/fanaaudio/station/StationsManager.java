package me.fan87.fanaaudio.station;

import me.fan87.fanaaudio.FANARadio;

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
    }

}
