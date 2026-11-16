package com.airplay.android.dlna;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSDP discovery for a UPnP MediaRenderer: answers M-SEARCH probes and announces the device
 * with periodic NOTIFY-alive packets on 239.255.255.250:1900.
 *
 * <p>Runs one receive loop on its own thread; senders expect unicast replies after a random
 * delay bounded by the MX header they sent.
 */
final class SsdpServer {

    private static final String TAG = "SsdpServer";
    private static final String SSDP_ADDRESS = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    /** Matches the CACHE-CONTROL advertised in every packet. */
    private static final String CACHE_AGE = "max-age=1800";

    private final String location;
    private final String uuid;
    private final Random random = new Random();
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread listenerThread;
    private MulticastSocket socket;

    /** Service types this device answers to, in the order NOTIFY packets announce them. */
    private static final String[] SEARCH_TARGETS = {
            "upnp:rootdevice",
            "uuid:%s",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:AVTransport:1",
            "urn:schemas-upnp-org:service:RenderingControl:1",
            "urn:schemas-upnp-org:service:ConnectionManager:1",
    };

    SsdpServer(String location, String uuid) {
        this.location = location;
        this.uuid = uuid;
    }

    void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;
        socket = new MulticastSocket(SSDP_PORT);
        socket.setReuseAddress(true);
        socket.joinGroup(InetAddress.getByName(SSDP_ADDRESS));
        socket.setSoTimeout(500);

        listenerThread = new Thread(this::receiveLoop, "ssdp-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        announceAlive();
        Log.i(TAG, "SSDP started (uuid " + uuid + ")");
    }

    void stop() {
        if (!running.compareAndSet(true, false)) return;
        try {
            announceByeBye();
        } catch (IOException e) {
            Log.w(TAG, "byebye failed", e);
        }
        if (socket != null) socket.close();
        if (listenerThread != null) listenerThread.interrupt();
        Log.i(TAG, "SSDP stopped");
    }

    private void receiveLoop() {
        byte[] buffer = new byte[2048];
        while (running.get()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                continue;
            } catch (IOException e) {
                if (running.get()) Log.w(TAG, "receive failed", e);
                return;
            }
            String message = new String(packet.getData(), 0, packet.getLength());
            if (!message.startsWith("M-SEARCH")) continue;

            String target = headerValue(message, "ST");
            if (target == null) continue;
            int maxDelayMs = parseMx(message) * 100;
            for (String nt : SEARCH_TARGETS) {
                String expanded = String.format(nt, uuid);
                if (!matches(target, expanded)) continue;
                scheduleReply(packet, expanded, maxDelayMs);
            }
        }
    }

    /** ssdp:all means "everything you have"; anything else must match one advertised target. */
    private static boolean matches(String target, String advertised) {
        return target.equals("ssdp:all") || target.equals(advertised)
                || (target.equals("ssdp:rootdevice") && advertised.equals("upnp:rootdevice"))
                || (target.startsWith("uuid:") && advertised.startsWith("uuid:"));
    }

    private void scheduleReply(DatagramPacket query, String target, int maxDelayMs) {
        int delay = maxDelayMs > 0 ? random.nextInt(maxDelayMs) : 0;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delay);
                if (!running.get()) return;
                sendUnicast(query.getAddress(), query.getPort(), buildResponse(target));
            } catch (InterruptedException | IOException e) {
                // socket closing or thread interrupted during shutdown
            }
        }, "ssdp-reply");
        t.setDaemon(true);
        t.start();
    }

    private String buildResponse(String st) {
        return "HTTP/1.1 200 OK\r\n"
                + "CACHE-CONTROL: " + CACHE_AGE + "\r\n"
                + "DATE: " + HttpDate.now() + "\r\n"
                + "EXT:\r\n"
                + "LOCATION: " + location + "\r\n"
                + "SERVER: " + DlnaXmls.SERVER + "\r\n"
                + "ST: " + st + "\r\n"
                + "USN: " + usnFor(st) + "\r\n"
                + "Content-Length: 0\r\n"
                + "\r\n";
    }

    /** uuid-type pairs keep the uuid as the USN prefix, plain types append it. */
    private String usnFor(String nt) {
        String expanded = String.format(nt, uuid);
        return expanded.equals("uuid:" + uuid) ? expanded : "uuid:" + uuid + "::" + expanded;
    }

    void announceAlive() throws IOException {
        for (String nt : SEARCH_TARGETS) {
            String expanded = String.format(nt, uuid);
            multicast(buildNotify(expanded, usnFor(expanded), "ssdp:alive"));
        }
    }

    void announceByeBye() throws IOException {
        for (String nt : SEARCH_TARGETS) {
            String expanded = String.format(nt, uuid);
            multicast(buildNotify(expanded, usnFor(expanded), "ssdp:byebye"));
        }
    }

    private String buildNotify(String nt, String usn, String nts) {
        return "NOTIFY * HTTP/1.1\r\n"
                + "CACHE-CONTROL: " + CACHE_AGE + "\r\n"
                + "LOCATION: " + location + "\r\n"
                + "NT: " + nt + "\r\n"
                + "NTS: " + nts + "\r\n"
                + "SERVER: " + DlnaXmls.SERVER + "\r\n"
                + "USN: " + usn + "\r\n"
                + "\r\n";
    }

    private void multicast(String message) throws IOException {
        byte[] data = message.getBytes();
        socket.send(new DatagramPacket(data, data.length,
                InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT));
    }

    private void sendUnicast(InetAddress address, int port, String message) throws IOException {
        byte[] data = message.getBytes();
        socket.send(new DatagramPacket(data, data.length, address, port));
    }

    private static String headerValue(String message, String header) {
        for (String line : message.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && header.equalsIgnoreCase(line.substring(0, colon).trim())) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static int parseMx(String message) {
        String mx = headerValue(message, "MX");
        int value = 0;
        if (mx != null) {
            try {
                value = Integer.parseInt(mx);
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.min(Math.max(value, 1), 5);
    }
}
