package com.example.bridge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loopback control/telemetry socket.
 *
 * <p>The app is the client and the bridge is the server, because only the app-to-bridge file
 * direction is reliable: the app owns its external files dir, so the bridge can read the port
 * and token the app chose, while a bridge-written file there would be shell-owned and unreadable
 * by the app. Binding to 127.0.0.1 keeps the socket off the network, and the shared token stops
 * other local apps from driving the bridge.
 */
public final class EventServer implements Runnable {

    public interface Listener {
        void onReloadRequested();

        void onShutdownRequested();

        /** Runs one chain now and answers "<ran>	<total>". */
        String onRunRequested(String payloadJson);
    }

    private static final char SEP = '\t';

    private final int port;
    private final String token;
    private final Listener listener;
    private final List<ClientConnection> clients = new CopyOnWriteArrayList<>();
    private final long startedAtMillis = System.currentTimeMillis();

    private volatile boolean running = true;
    private volatile ServerSocket serverSocket;
    private volatile String statusLine = "starting";

    public EventServer(int port, String token, Listener listener) {
        this.port = port;
        this.token = token;
        this.listener = listener;
    }

    public void setStatus(String status) {
        this.statusLine = sanitize(status);
    }

    public void stop() {
        running = false;
        ServerSocket socket = serverSocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
                // Closing is what unblocks accept().
            }
        }
        for (ClientConnection client : clients) {
            client.close();
        }
        clients.clear();
    }

    /**
     * Binds before any other subsystem starts. A refused bind means another bridge already owns
     * the port, which is how a second launch detects and defers to the running instance.
     */
    public void bind() throws Exception {
        serverSocket = new ServerSocket(port, 4, InetAddress.getByName(BridgeProtocol.HOST));
        BridgeLog.i("Control socket listening on " + BridgeProtocol.HOST + ":" + port);
    }

    @Override
    public void run() {
        if (serverSocket == null) {
            throw new IllegalStateException("bind() must be called before run()");
        }
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientConnection client = new ClientConnection(socket);
                clients.add(client);
                Thread thread = new Thread(client, "arda-bridge-client");
                thread.setDaemon(true);
                thread.start();
            } catch (Exception e) {
                if (running) {
                    BridgeLog.w("Control socket accept failed: " + e);
                }
            }
        }
    }

    public void broadcastKeyEvent(long timestamp, int keyCode, boolean down, boolean mapped, String source) {
        broadcast(BridgeProtocol.MSG_EVENT + SEP + timestamp + SEP + keyCode + SEP
                + (down ? "down" : "up") + SEP + (mapped ? 1 : 0) + SEP + sanitize(source));
    }

    public void broadcastTrigger(long timestamp, int keyCode, String pressType, String scriptId, boolean success) {
        broadcast(BridgeProtocol.MSG_TRIGGER + SEP + timestamp + SEP + keyCode + SEP + pressType
                + SEP + sanitize(scriptId) + SEP + (success ? 1 : 0));
    }

    public void broadcastDevice(String path, String name) {
        broadcast(BridgeProtocol.MSG_DEVICE + SEP + sanitize(path) + SEP + sanitize(name));
    }

    private void broadcast(String line) {
        for (ClientConnection client : clients) {
            client.send(line);
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private final class ClientConnection implements Runnable {
        private final Socket socket;
        private volatile BufferedWriter writer;
        private volatile boolean authenticated;

        ClientConnection(Socket socket) {
            this.socket = socket;
        }

        void send(String line) {
            BufferedWriter target = writer;
            if (target == null || !authenticated) {
                return;
            }
            try {
                synchronized (this) {
                    target.write(line);
                    target.write('\n');
                    target.flush();
                }
            } catch (Exception e) {
                close();
            }
        }

        void close() {
            try {
                socket.close();
            } catch (Exception ignored) {
                // Already closing.
            }
            clients.remove(this);
        }

        @Override
        public void run() {
            try {
                socket.setTcpNoDelay(true);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), Charset.forName("UTF-8")));
                writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), Charset.forName("UTF-8")));

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t");
                    String command = parts[0];

                    if (!authenticated) {
                        if (BridgeProtocol.CMD_HELLO.equals(command) && parts.length >= 2
                                && token.equals(parts[1])) {
                            authenticated = true;
                            writeDirect(BridgeProtocol.MSG_OK + SEP + BridgeProtocol.VERSION + SEP
                                    + android.os.Process.myPid() + SEP + startedAtMillis + SEP + statusLine);
                        } else {
                            writeDirect(BridgeProtocol.MSG_ERROR + SEP + "unauthorized");
                            break;
                        }
                        continue;
                    }

                    if (BridgeProtocol.CMD_PING.equals(command)) {
                        writeDirect(BridgeProtocol.MSG_PONG + SEP + System.currentTimeMillis()
                                + SEP + statusLine);
                    } else if (BridgeProtocol.CMD_RELOAD.equals(command)) {
                        listener.onReloadRequested();
                        writeDirect(BridgeProtocol.MSG_OK + SEP + "reloaded");
                    } else if (BridgeProtocol.CMD_RUN.equals(command)) {
                        writeDirect(BridgeProtocol.MSG_OK + SEP
                                + listener.onRunRequested(parts.length >= 2 ? parts[1] : ""));
                    } else if (BridgeProtocol.CMD_SHUTDOWN.equals(command)) {
                        writeDirect(BridgeProtocol.MSG_BYE);
                        listener.onShutdownRequested();
                        break;
                    } else {
                        writeDirect(BridgeProtocol.MSG_ERROR + SEP + "unknown-command");
                    }
                }
            } catch (Exception e) {
                if (running) {
                    BridgeLog.w("Control client ended: " + e);
                }
            } finally {
                close();
            }
        }

        private void writeDirect(String line) throws Exception {
            BufferedWriter target = writer;
            if (target == null) {
                return;
            }
            synchronized (this) {
                target.write(line);
                target.write('\n');
                target.flush();
            }
        }
    }
}
