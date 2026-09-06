package com.gaurang.redis.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Writes RESP framing explicitly, without platform-dependent line endings. */
public final class RespWriter {
    private final OutputStream output;

    public RespWriter(OutputStream output) {
        this.output = output;
    }

    public void simple(String value) throws IOException {
        line("+" + value);
    }

    public void error(String value) throws IOException {
        line("-" + value.replace('\r', ' ').replace('\n', ' '));
    }

    public void integer(long value) throws IOException {
        line(":" + value);
    }

    public void bulk(byte[] value) throws IOException {
        if (value == null) {
            line("$-1");
        } else {
            line("$" + value.length);
            output.write(value);
            output.write('\r');
            output.write('\n');
        }
    }

    public void array(int length) throws IOException {
        line("*" + length);
    }

    public void flush() throws IOException {
        output.flush();
    }

    private void line(String value) throws IOException {
        output.write((value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
}
