package me.fan87.fanaaudio.configs;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import me.fan87.fanaaudio.station.AudioStation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Config {

    @Expose
    public String ip = "0.0.0.0";

    @Expose
    public Integer publicPort = 8080;

    @Expose
    public String stationName = "Example Radio Station";

    @Expose
    public List<AudioStation> stations = new ArrayList<>(Collections.singletonList(new AudioStation("example", "Example Station", "fan87")));

    @Expose
    public Boolean shuffle = true;

    @Expose
    public Boolean debug = false;

    @Expose
    public String radioIndexSubtitle = "Station not found! Here's all available stations:";

    @Expose
    public String radioIndexHint = "Note: M3U requires 3rd party software. If you want to play it in your browser, use AAC";

}
