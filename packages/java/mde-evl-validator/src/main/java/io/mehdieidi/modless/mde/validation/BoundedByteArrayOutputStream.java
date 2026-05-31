package io.mehdieidi.modless.mde.validation;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class BoundedByteArrayOutputStream extends OutputStream {

    private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    private final int maxBytes;
    private boolean truncated;

    BoundedByteArrayOutputStream(int maxBytes) {
        this.maxBytes = Math.max(1, maxBytes);
    }

    @Override
    public void write(int b) {
        if (delegate.size() < maxBytes) {
            delegate.write(b);
        } else {
            truncated = true;
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        if (bytes == null || length <= 0) {
            return;
        }
        int remaining = maxBytes - delegate.size();
        if (remaining > 0) {
            delegate.write(bytes, offset, Math.min(length, remaining));
        }
        if (length > remaining) {
            truncated = true;
        }
    }

    String asUtf8String() {
        String value = delegate.toString(StandardCharsets.UTF_8);
        return truncated ? value + System.lineSeparator() + "[output truncated]" : value;
    }
}
