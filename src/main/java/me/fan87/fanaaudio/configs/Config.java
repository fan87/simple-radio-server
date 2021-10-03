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
    public List<AudioStation> stations = new ArrayList<>(Collections.singletonList(new AudioStation("example", "Example Station", "fan87")));

}
