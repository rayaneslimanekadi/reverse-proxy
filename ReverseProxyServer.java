import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Simple Java Reverse Proxy — custom-built HTTP proxy supporting
 * request forwarding and multi-threaded handling.
 *
 * Usage:
 *   java ReverseProxyServer [listenPort] [targetHost] [targetPort]
 *
 * Example:
 *   java ReverseProxyServer 8080 localhost 8081
 */
public class ReverseProxyServer {

    private final int listenPort;
    private final String targetHost;
    private final int targetPort;

    public ReverseProxyServer(int listenPort, String targetHost, int targetPort) {
        this.listenPort = listenPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
            System.out.println("=== Simple Java Reverse Proxy ===");
            System.out.println("Listening on port " + listenPort +
                    " and forwarding to " + targetHost + ":" + targetPort);

            // Main accept loop – each client gets its own handler thread.
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, targetHost, targetPort);
                Thread t = new Thread(handler);
                t.start();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int listenPort = (args.length > 0) ? Integer.parseInt(args[0]) : 8080;
        String targetHost = (args.length > 1) ? args[1] : "localhost";
        int targetPort = (args.length > 2) ? Integer.parseInt(args[2]) : 8081;

        ReverseProxyServer proxy = new ReverseProxyServer(listenPort, targetHost, targetPort);
        proxy.start();
    }

    /**
     * Handles a single client connection: opens connection to target server,
     * and pipes data in both directions (client <-> backend).
     */
    private static class ClientHandler implements Runnable {
        private static final int TIMEOUT_MILLIS = 30_000;

        private final Socket clientSocket;
        private final String targetHost;
        private final int targetPort;

        ClientHandler(Socket clientSocket, String targetHost, int targetPort) {
            this.clientSocket = clientSocket;
            this.targetHost = targetHost;
            this.targetPort = targetPort;
        }

        @Override
        public void run() {
            Socket backendSocket = null;
            String clientInfo = clientSocket.getRemoteSocketAddress().toString();

            try {
                clientSocket.setSoTimeout(TIMEOUT_MILLIS);

                backendSocket = new Socket();
                backendSocket.connect(new InetSocketAddress(targetHost, targetPort), TIMEOUT_MILLIS);
                backendSocket.setSoTimeout(TIMEOUT_MILLIS);

                System.out.println("[INFO] New connection " + clientInfo +
                        " -> " + targetHost + ":" + targetPort);

                InputStream clientIn = clientSocket.getInputStream();
                OutputStream clientOut = clientSocket.getOutputStream();
                InputStream backendIn = backendSocket.getInputStream();
                OutputStream backendOut = backendSocket.getOutputStream();

                // Two-way piping: client -> backend and backend -> client
                Thread toBackend = new Thread(() -> {
                    try {
                        pipe(clientIn, backendOut);
                    } catch (IOException e) {
                        // Expected if client disconnects; log at debug level.
                        System.out.println("[DEBUG] client->backend closed: " + e.getMessage());
                    }
                });

                Thread toClient = new Thread(() -> {
                    try {
                        pipe(backendIn, clientOut);
                    } catch (IOException e) {
                        System.out.println("[DEBUG] backend->client closed: " + e.getMessage());
                    }
                });

                toBackend.start();
                toClient.start();

                // Wait for both directions to complete
                try {
                    toBackend.join();
                    toClient.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                System.out.println("[INFO] Connection closed for " + clientInfo);
            } catch (IOException e) {
                System.err.println("[ERROR] Error handling client " + clientInfo + ": " + e.getMessage());
            } finally {
                try {
                    if (backendSocket != null && !backendSocket.isClosed()) {
                        backendSocket.close();
                    }
                } catch (IOException ignored) { }

                try {
                    if (!clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                } catch (IOException ignored) { }
            }
        }

        /**
         * Streams bytes from 'in' to 'out' until EOF.
         */
        private static void pipe(InputStream in, OutputStream out) throws IOException {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        }
    }
}
