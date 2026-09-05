package src.main.java;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class MiniRedisServer {
    private final MiniRedis store = new MiniRedis();
    private final int port;

    public MiniRedisServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("MiniRedis listening on port " + port);
            while (true) {
                Socket client = serverSocket.accept();
                pool.submit(() -> handleClient(client));
            }
        }
    }

    private void handleClient(Socket client) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                String response = handleCommand(line.trim());
                out.println(response);
            }
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private String handleCommand(String line) {
        if (line.isEmpty()) return "ERR empty command";

        String[] parts = line.split("\\s+");
        String cmd = parts[0].toUpperCase();

        switch (cmd) {
            case "SET":
                if (parts.length != 3) return "ERR wrong number of arguments for 'set'";
                store.set(parts[1], parts[2]);
                return "OK";

            case "GET":
                if (parts.length != 2) return "ERR wrong number of arguments for 'get'";
                String value = store.get(parts[1]);
                return value != null ? value : "(nil)";

            case "DEL":
                if (parts.length != 2) return "ERR wrong number of arguments for 'del'";
                boolean removed = store.del(parts[1]);
                return removed ? "1" : "0";

            default:
                return "ERR unknown command '" + cmd + "'";
        }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6380;
        new MiniRedisServer(port).start();
    }
}