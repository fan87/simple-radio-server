package me.fan87.fanaaudio.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

public class ConfigsManager {

    private Config config;
    private final File configFile = new File("config.json");
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public ConfigsManager() {
        this.config = new Config();
        loadConfig();

    }

    /**
     * Get the configuration (Not updating)
     * @return The cached config
     */
    public Config getConfig() {
        return config;
    }

    /**
     * Reload the configuration file
     */
    public void loadConfig() {
        try {
            if (configFile.isDirectory()) configFile.delete();
            if (!configFile.exists()) configFile.createNewFile();
            this.config = gson.fromJson(new FileReader(configFile), Config.class);
            if (this.config == null) {
                this.config = new Config();
            }
            for (Field field : Config.class.getFields()) {
                if (field.get(this.config) == null) {
                    field.set(this.config, field.get(new Config()));
                }
            }
            saveConfig();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Save the configuration file
     */
    public void saveConfig() {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(configFile);
            BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream);
            outputStream.write(gson.toJson(this.config).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
