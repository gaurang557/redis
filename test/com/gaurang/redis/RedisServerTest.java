package com.gaurang.redis;

import com.gaurang.redis.protocol.RespWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class RedisServerTest {
    private static Process server;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        server = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", Path.of("target/classes").toAbsolutePath().toString(),
                RedisServer.class.getName(), "0", "127.0.0.1").redirectErrorStream(true).start();
        var output = new BufferedReader(new InputStreamReader(server.getInputStream()));
        String line = output.readLine();
        assertNotNull(line, "server exited before startup");
        assertTrue(line.startsWith("MiniRedis listening on "), line);
        port = Integer.parseInt(line.substring(line.lastIndexOf(':') + 1));
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.destroy();
            if (!server.waitFor(5, TimeUnit.SECONDS)) server.destroyForcibly();
        }
    }

    @Test
    void pipelinesCommandsAndKeepsConnectionAfterCommandErrors() throws Exception {
        try (Socket socket = connect()) {
            var writer = new RespWriter(socket.getOutputStream());
            command(writer, "SET", "greeting", "hello world\r\nनमस्ते");
            command(writer, "GET", "greeting");
            command(writer, "GET", "missing");
            command(writer, "SET", "empty", "");
            command(writer, "GET", "empty");
            command(writer, "DEL");
            command(writer, "EXPIRE", "greeting", "abc");
            command(writer, "PING");
            command(writer, "QUIT");
            writer.flush();
            var expected = new java.io.ByteArrayOutputStream();
            var replies = new RespWriter(expected);
            replies.simple("OK");
            replies.bulk("hello world\r\nनमस्ते".getBytes(StandardCharsets.UTF_8));
            replies.bulk(null);
            replies.simple("OK");
            replies.bulk(new byte[0]);
            replies.error("ERR wrong number of arguments");
            replies.error("ERR value is not an integer or out of range");
            replies.simple("PONG");
            replies.simple("OK");
            assertArrayEquals(expected.toByteArray(), socket.getInputStream().readAllBytes());
        }
    }

    @Test
    void supportsClientInitializationAndRejectsUnsupportedFeatures() throws Exception {
        try (Socket socket = connect()) {
            var writer = new RespWriter(socket.getOutputStream());
            command(writer, "CLIENT", "SETINFO", "LIB-NAME", "test-client");
            command(writer, "CLIENT", "SETNAME", "my-app");
            command(writer, "CLIENT", "GETNAME");
            command(writer, "SELECT", "0");
            command(writer, "SELECT", "1");
            command(writer, "HELLO", "3");
            command(writer, "AUTH", "password");
            command(writer, "PING");
            command(writer, "QUIT");
            writer.flush();
            String actual = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("+OK\r\n+OK\r\n$6\r\nmy-app\r\n+OK\r\n"
                    + "-ERR only database 0 is supported\r\n-NOPROTO only RESP2 is supported\r\n"
                    + "-ERR unknown command\r\n+PONG\r\n+OK\r\n", actual);
        }
    }

    @Test
    void protocolErrorClosesOnlyTheOffendingConnection() throws Exception {
        try (Socket socket = connect()) {
            socket.getOutputStream().write("*1\r\n$-1\r\n".getBytes(StandardCharsets.US_ASCII));
            String reply = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            assertTrue(reply.startsWith("-ERR Protocol error:"));
        }
        try (Socket socket = connect()) {
            var writer = new RespWriter(socket.getOutputStream());
            command(writer, "PING");
            writer.flush();
            assertArrayEquals("+PONG\r\n".getBytes(StandardCharsets.US_ASCII),
                    socket.getInputStream().readNBytes(7));
        }
    }

    private static Socket connect() throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(3000);
        return socket;
    }

    private static void command(RespWriter writer, String... args) throws Exception {
        writer.array(args.length);
        for (String arg : args) writer.bulk(arg.getBytes(StandardCharsets.UTF_8));
    }
}
