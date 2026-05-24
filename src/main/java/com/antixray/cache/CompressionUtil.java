package com.antixray.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class CompressionUtil {

    private CompressionUtil() {}

    static byte[] compress(byte[] data) throws IOException {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                bos.write(buffer, 0, count);
            }
            return bos.toByteArray();
        } finally {
            deflater.end();
        }
    }

    static byte[] decompress(byte[] compressed) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(compressed.length * 4);
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) break;
                bos.write(buffer, 0, count);
            }
            return bos.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("Decompression failed", e);
        } finally {
            inflater.end();
        }
    }
}
