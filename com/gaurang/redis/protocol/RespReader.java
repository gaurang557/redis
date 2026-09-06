package com.gaurang.redis.protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Reads one RESP2 array of bulk strings, leaving any pipelined requests unread. */
public final class RespReader {
    public static final int MAX_ARGUMENTS = 1024;
    public static final int MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;
    private final InputStream input;

    public RespReader(InputStream input) {
        this.input = input;
    }

    /** Returns null only for EOF between complete commands. */
    public List<byte[]> readCommand() throws IOException {
        int prefix = input.read();
        if (prefix == -1) return null;
        if (prefix != '*') throw new ProtocolException("expected an array of bulk strings");
        int count = readLength(MAX_ARGUMENTS);
        if (count == 0) throw new ProtocolException("empty command array");
        List<byte[]> arguments = new ArrayList<>(count);
        int remaining = MAX_PAYLOAD_BYTES;
        for (int i = 0; i < count; i++) {
            if (readByte() != '$') throw new ProtocolException("expected a bulk string");
            int length = readLength(remaining);
            remaining -= length;
            byte[] argument = input.readNBytes(length);
            if (argument.length != length) throw new EOFException("incomplete bulk string");
            expectCrlf();
            arguments.add(argument);
        }
        return arguments;
    }

    private int readLength(int limit) throws IOException {
        int value = 0;
        int digits = 0;
        while (true) {
            int next = readByte();
            if (next == '\r') {
                if (readByte() != '\n' || digits == 0) {
                    throw new ProtocolException("invalid length terminator");
                }
                return value;
            }
            if (next < '0' || next > '9' || ++digits > 10) {
                throw new ProtocolException("invalid length");
            }
            long candidate = (long) value * 10 + next - '0';
            if (candidate > limit) throw new ProtocolException("request exceeds size limit");
            value = (int) candidate;
        }
    }

    private void expectCrlf() throws IOException {
        if (readByte() != '\r' || readByte() != '\n') {
            throw new ProtocolException("expected CRLF after bulk string");
        }
    }

    private int readByte() throws IOException {
        int value = input.read();
        if (value == -1) throw new EOFException("incomplete request");
        return value;
    }

    public static final class ProtocolException extends IOException {
        public ProtocolException(String message) {
            super(message);
        }
    }
}
