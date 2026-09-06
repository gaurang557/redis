package com.gaurang.redis;

import com.gaurang.redis.protocol.RespReader;
import com.gaurang.redis.protocol.RespWriter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RedisServer {
    private final MiniRedis redis = new MiniRedis();
    private final int port;
    private final String host;

    public RedisServer() {
        this(6379, "127.0.0.1");
    }

    public RedisServer(int port, String host) {
        this.port = port;
        this.host = host;
    }

    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(host, port));
            System.out.println("MiniRedis listening on " + host + ":" + server.getLocalPort());
            while (true) {
                Socket client = server.accept();
                Thread.ofVirtual().start(() -> handleClient(client));
            }
        }
    }

    private void handleClient(Socket client) {
        try (client;
             var input = new BufferedInputStream(client.getInputStream());
             var output = new BufferedOutputStream(client.getOutputStream())) {
            var reader = new RespReader(input);
            var writer = new RespWriter(output);
            Map<String, byte[]> clientInfo = new HashMap<>();
            while (true) {
                List<byte[]> arguments;
                try {
                    arguments = reader.readCommand();
                } catch (RespReader.ProtocolException | EOFException e) {
                    writer.error("ERR Protocol error: " + e.getMessage());
                    writer.flush();
                    break;
                }
                if (arguments == null) break;
                boolean keepOpen = executeCommand(arguments, writer, clientInfo);
                writer.flush();
                if (!keepOpen) break;
            }
        } catch (IOException e) {
            System.err.println("Client connection closed: " + e.getMessage());
        }
    }

    private boolean executeCommand(List<byte[]> args, RespWriter writer,
                                   Map<String, byte[]> clientInfo) throws IOException {
        String command = text(args.getFirst()).toUpperCase(Locale.ROOT);
        try {
            switch (command) {
                case "PING" -> {
                    if (args.size() == 1) writer.simple("PONG");
                    else if (args.size() == 2) writer.bulk(args.get(1));
                    else throw wrongArguments();
                }
                case "ECHO" -> {
                    requireSize(args, 2);
                    writer.bulk(args.get(1));
                }
                case "SET" -> {
                    requireSize(args, 3);
                    redis.set(args.get(1), args.get(2));
                    writer.simple("OK");
                }
                case "GET" -> {
                    requireSize(args, 2);
                    writer.bulk(redis.get(args.get(1)));
                }
                case "DEL" -> {
                    if (args.size() < 2) throw wrongArguments();
                    writer.integer(redis.delete(args.subList(1, args.size()).toArray(byte[][]::new)));
                }
                case "EXPIRE" -> {
                    requireSize(args, 3);
                    writer.integer(redis.expire(args.get(1), MiniRedis.parseInteger(args.get(2))) ? 1 : 0);
                }
                case "TTL" -> {
                    requireSize(args, 2);
                    writer.integer(redis.ttl(args.get(1)));
                }
                case "INCR" -> {
                    requireSize(args, 2);
                    writer.integer(redis.increment(args.get(1)));
                }
                case "INCRBY" -> {
                    requireSize(args, 3);
                    writer.integer(redis.incrementBy(args.get(1), MiniRedis.parseInteger(args.get(2))));
                }
                case "SELECT" -> {
                    requireSize(args, 2);
                    if (MiniRedis.parseInteger(args.get(1)) != 0) {
                        throw new IllegalArgumentException("ERR only database 0 is supported");
                    }
                    writer.simple("OK");
                }
                case "CLIENT" -> {
                    if (args.size() < 2) throw wrongArguments();
                    String subcommand = text(args.get(1)).toUpperCase(Locale.ROOT);
                    switch (subcommand) {
                        case "SETINFO" -> {
                            requireSize(args, 4);
                            String field = text(args.get(2)).toLowerCase(Locale.ROOT);
                            if (!field.equals("lib-name") && !field.equals("lib-ver")) {
                                throw new IllegalArgumentException("ERR unsupported CLIENT SETINFO attribute");
                            }
                            clientInfo.put(field, args.get(3));
                            writer.simple("OK");
                        }
                        case "SETNAME" -> {
                            requireSize(args, 3);
                            for (byte b : args.get(2)) {
                                if (b < 33 || b > 126) {
                                    throw new IllegalArgumentException("ERR client name must be printable ASCII without spaces");
                                }
                            }
                            clientInfo.put("name", args.get(2));
                            writer.simple("OK");
                        }
                        case "GETNAME" -> {
                            requireSize(args, 2);
                            byte[] name = clientInfo.get("name");
                            writer.bulk(name == null || name.length == 0 ? null : name);
                        }
                        default -> writer.error("ERR unsupported CLIENT subcommand");
                    }
                }
                case "HELLO" -> {
                    if (args.size() > 2) throw new IllegalArgumentException("ERR HELLO options are not supported");
                    if (args.size() == 2 && MiniRedis.parseInteger(args.get(1)) != 2) {
                        writer.error("NOPROTO only RESP2 is supported");
                    } else {
                        writer.array(6);
                        writer.bulk("server".getBytes(StandardCharsets.US_ASCII));
                        writer.bulk("miniredis".getBytes(StandardCharsets.US_ASCII));
                        writer.bulk("version".getBytes(StandardCharsets.US_ASCII));
                        writer.bulk("1.0.0".getBytes(StandardCharsets.US_ASCII));
                        writer.bulk("proto".getBytes(StandardCharsets.US_ASCII));
                        writer.integer(2);
                    }
                }
                case "QUIT" -> {
                    requireSize(args, 1);
                    writer.simple("OK");
                    return false;
                }
                default -> writer.error("ERR unknown command");
            }
        } catch (NumberFormatException | ArithmeticException e) {
            writer.error("ERR value is not an integer or out of range");
        } catch (IllegalArgumentException e) {
            writer.error(e.getMessage());
        }
        return true;
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static void requireSize(List<byte[]> args, int size) {
        if (args.size() != size) throw wrongArguments();
    }

    private static IllegalArgumentException wrongArguments() {
        return new IllegalArgumentException("ERR wrong number of arguments");
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 2) throw new IllegalArgumentException("Usage: java -jar target/miniredis.jar [port] [bind-address]");
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6379;
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        new RedisServer(port, host).start();
    }
}
