package com.airplay.android.dlna;

import android.util.Log;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

/**
 * Ties the SSDP announcer and the control HTTP server into one UPnP MediaRenderer.
 *
 * <p>The renderer lives exactly as long as the AirPlay receiver (both are owned by the
 * foreground service), so the device shows up in cast lists only while the app is on screen.
 */
public final class DlnaRenderer {

    private static final String TAG = "DlnaRenderer";
    /** UPnP dynamic port range starts here; a few slots are tried in case one is taken. */
    private static final int BASE_PORT = 49152;
    private static final int PORT_ATTEMPTS = 16;
    /** Alive packets are re-sent so sleepy Wi-Fi clients rediscover the renderer. */
    private static final long ALIVE_PERIOD_SECONDS = 45;

    private final String friendlyName;
    private final String uuid;
    private final DlnaController controller = DlnaController.get();

    private NioEventLoopGroup eventLoop;
    private Channel httpChannel;
    private SsdpServer ssdp;
    private ScheduledExecutorService aliveTimer;
    private int httpPort;

    public DlnaRenderer(String friendlyName, String uuid) {
        this.friendlyName = friendlyName;
        this.uuid = uuid;
    }

    public void start() throws Exception {
        eventLoop = new NioEventLoopGroup(1);
        httpChannel = bindHttp();
        if (httpChannel == null) {
            throw new IllegalStateException("No free port in " + BASE_PORT + "+"
                    + PORT_ATTEMPTS + " for the DLNA HTTP server");
        }

        String ip = localIp();
        String urlBase = "http://" + ip + ":" + httpPort + "/";
        ssdp = new SsdpServer(urlBase + "description.xml", uuid);
        ssdp.start();

        aliveTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ssdp-alive");
            t.setDaemon(true);
            return t;
        });
        aliveTimer.scheduleAtFixedRate(() -> {
            try {
                ssdp.announceAlive();
            } catch (Exception e) {
                Log.w(TAG, "alive refresh failed", e);
            }
        }, ALIVE_PERIOD_SECONDS, ALIVE_PERIOD_SECONDS, TimeUnit.SECONDS);

        Log.i(TAG, "DLNA renderer '" + friendlyName + "' at " + urlBase);
    }

    public void stop() {
        if (aliveTimer != null) {
            aliveTimer.shutdownNow();
            aliveTimer = null;
        }
        if (ssdp != null) {
            ssdp.stop();
            ssdp = null;
        }
        if (httpChannel != null) {
            httpChannel.close();
            httpChannel = null;
        }
        if (eventLoop != null) {
            eventLoop.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS);
            eventLoop = null;
        }
        Log.i(TAG, "DLNA renderer stopped");
    }

    private Channel bindHttp() throws InterruptedException {
        for (int attempt = 0; attempt < PORT_ATTEMPTS; attempt++) {
            final int port = BASE_PORT + attempt;
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(eventLoop)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(
                                    new HttpRequestDecoder(),
                                    new HttpResponseEncoder(),
                                    new HttpObjectAggregator(256 * 1024),
                                    new DlnaHttpHandler(friendlyName, uuid,
                                            "http://" + localIp() + ":" + port + "/",
                                            controller));
                        }
                    })
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);
            try {
                Channel bound = bootstrap.bind(port).sync().channel();
                httpPort = port;
                return bound;
            } catch (Exception e) {
                Log.w(TAG, "Port " + port + " busy, trying next");
            }
        }
        return null;
    }

    private static String localIp() {
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "0.0.0.0";
    }
}
