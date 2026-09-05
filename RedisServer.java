import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class RedisServer {

    private static final int PORT = 6379;

    private final MiniRedis redis = new MiniRedis();

    public void start() throws IOException {

        ServerSocket serverSocket =
                new ServerSocket(PORT);

        System.out.println(
                "MiniRedis running on port " + PORT
        );

        while (true) {

            Socket client =
                    serverSocket.accept();

            Thread.ofVirtual()
                    .start(() -> handleClient(client));
        }
    }

    private void handleClient(Socket client) {

        try (
            BufferedReader reader =
                    new BufferedReader(
                        new InputStreamReader(
                            client.getInputStream()
                        )
                    );

            PrintWriter writer =
                    new PrintWriter(
                        client.getOutputStream(),
                        true
                    )
        ) {

            String command;

            while ((command = reader.readLine()) != null) {

                String response =
                        executeCommand(command);

                writer.println(response);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String executeCommand(String input) {

        String[] parts =
                input.trim().split("\\s+", 3);

        String command =
                parts[0].toUpperCase();

        return switch (command) {

            case "SET" -> {

                if (parts.length < 3)
                    yield "ERR invalid arguments";

                redis.set(parts[1], parts[2]);

                yield "OK";
            }

            case "GET" -> {

                if (parts.length < 2)
                    yield "ERR invalid arguments";

                String value =
                        redis.get(parts[1]);

                yield value == null
                        ? "(nil)"
                        : value;
            }

            case "DEL" -> {

                boolean deleted =
                        redis.delete(parts[1]);

                yield deleted ? "1" : "0";
            }

            case "EXPIRE" -> {

                long seconds =
                        Long.parseLong(parts[2]);

                boolean success =
                        redis.expire(
                                parts[1],
                                seconds
                        );

                yield success ? "1" : "0";
            }

            case "TTL" ->
                    String.valueOf(
                        redis.ttl(parts[1])
                    );

            case "INCR" ->
                    String.valueOf(
                        redis.increment(parts[1])
                    );

            default ->
                    "ERR unknown command";
        };
    }
}