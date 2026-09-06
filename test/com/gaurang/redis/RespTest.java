package com.gaurang.redis;

import com.gaurang.redis.protocol.RespReader;
import com.gaurang.redis.protocol.RespWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RespTest {
    @Test
    void readsFragmentedBinaryAndPipelinedRequests() throws Exception {
        byte[] payload = new byte[] {0, -1, '\r', '\n'};
        var bytes = new ByteArrayOutputStream();
        var writer = new RespWriter(bytes);
        writer.array(3);
        writer.bulk("SET".getBytes(StandardCharsets.UTF_8));
        writer.bulk("कुंजी".getBytes(StandardCharsets.UTF_8));
        writer.bulk(payload);
        writer.array(1);
        writer.bulk("PING".getBytes(StandardCharsets.UTF_8));
        var fragmented = new ByteArrayInputStream(bytes.toByteArray()) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                return super.read(buffer, offset, Math.min(length, 1));
            }
        };
        var reader = new RespReader(fragmented);
        var command = reader.readCommand();
        assertEquals(3, command.size());
        assertArrayEquals("कुंजी".getBytes(StandardCharsets.UTF_8), command.get(1));
        assertArrayEquals(payload, command.get(2));
        assertArrayEquals("PING".getBytes(StandardCharsets.UTF_8), reader.readCommand().getFirst());
        assertNull(reader.readCommand());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET key\r\n", "*0\r\n", "*-1\r\n", "*1025\r\n",
            "*1\n", "*1\r\n$-1\r\n", "*1\r\n$8388609\r\n",
            "*1\r\n$9999999999999\r\n", "*1\r\n+PING\r\n",
            "*1\r\n$1\r\naXX"})
    void rejectsMalformedOrOversizedRequests(String request) {
        var reader = new RespReader(new ByteArrayInputStream(request.getBytes(StandardCharsets.US_ASCII)));
        assertThrows(RespReader.ProtocolException.class, reader::readCommand);
    }

    @Test
    void rejectsTruncatedRequest() {
        var reader = new RespReader(new ByteArrayInputStream("*1\r\n$4\r\nPI".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(EOFException.class, reader::readCommand);
    }

    @Test
    void writesExactReplyTypesAndUtf8ByteLengths() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var writer = new RespWriter(bytes);
        writer.simple("OK");
        writer.integer(21);
        writer.bulk(null);
        writer.bulk(new byte[0]);
        writer.bulk("é".getBytes(StandardCharsets.UTF_8));
        writer.error("ERR bad\r\ninput");
        assertEquals("+OK\r\n:21\r\n$-1\r\n$0\r\n\r\n$2\r\né\r\n-ERR bad  input\r\n",
                bytes.toString(StandardCharsets.UTF_8));
    }
}
