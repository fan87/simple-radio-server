package me.fan87.fanaaudio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.fan87.fanaaudio.configs.ConfigsManager;
import me.fan87.fanaaudio.station.StationsManager;
import org.apache.log4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class FANARadio {

    public static FANARadio INSTANCE;

    public static void main(String[] args) {
        INSTANCE = new FANARadio();
    }


    private ConfigsManager configsManager;
    private StationsManager stationsManager;

    private final Logger logger;
    private HttpServer server = null;



    public FANARadio() {
        this.logger = Logger.getLogger("FANARadio");
        logger.info("Loading Configs...");
        this.configsManager = new ConfigsManager();
        try {
            logger.info("Creating Server Instance...");
            server = HttpServer.create(new InetSocketAddress(configsManager.getConfig().ip, configsManager.getConfig().publicPort), 0);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(String.format("Something went wrong while binding Public Http Server to %s. Is port already in use?", (configsManager.getConfig().ip + ":" + configsManager.getConfig().publicPort.toString())));
            System.exit(-1);
        }
        logger.info("Initializing Stations Manager...");
        stationsManager = new StationsManager(this);
        logger.info("Starting HTTP Server..");


        server.start();

    }


    /**
     * Get the HttpServer you can control
     * @return HTTP Server instance
     */
    public HttpServer getServer() {
        return server;
    }

    /**
     * Get the configs manager
     * @return The configs manager
     */
    public ConfigsManager getConfigsManager() {
        return configsManager;
    }

    /**
     * Get the logger
     * @return The logger
     */
    public Logger getLogger() {
        return logger;
    }
}
