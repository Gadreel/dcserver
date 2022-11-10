package dcraft.util.chars;

import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CharUtil {
    // TODO by directly reading the byte buf, no copy
    public static String decode(ByteBuf buffer) {
        if (buffer == null)
            return null;

        byte[] dest = new byte[buffer.readableBytes()];
        buffer.readBytes(dest);

        return CharUtil.decode(dest);
    }

    // TODO by directly reading the byte buf, no copy
    public static String decode(ByteBuf buffer, int max) {
        if (buffer == null)
            return null;

        byte[] dest = new byte[Math.min(buffer.readableBytes(), max)];
        buffer.readBytes(dest);

        return CharUtil.decode(dest);
    }

    // must pass in a complete buffer
    public static String decode(byte[] buffer) {
        if (buffer == null)
            return null;

        return new String(buffer, StandardCharsets.UTF_8);
    }

    public static String decode(byte[] buffer, int length) {
        if (buffer == null)
            return null;

        return new String(buffer, 0, length, StandardCharsets.UTF_8);
    }
}
