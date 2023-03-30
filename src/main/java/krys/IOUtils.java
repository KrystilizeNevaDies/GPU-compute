package krys;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.lwjgl.BufferUtils;

/**
 * @author Kai Burjack
 */
public class IOUtils {
    private static ByteBuffer resizeBuffer(ByteBuffer buffer, int newCapacity) {
        ByteBuffer newBuffer = BufferUtils.createByteBuffer(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }

    public static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
        URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (url == null)
            throw new IOException("Classpath resource not found: " + resource);

        File file = new File(url.getFile());
        if (file.isFile()) {
            FileInputStream fis = new FileInputStream(file);
            FileChannel fc = fis.getChannel();
            ByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            fc.close();
            fis.close();
            return buffer;
        }

        ByteBuffer buffer = BufferUtils.createByteBuffer(bufferSize);
        try (InputStream source = url.openStream()) {
            if (source == null)
                throw new FileNotFoundException(resource);
            byte[] buf = new byte[8192];
            while (true) {
                int bytes = source.read(buf, 0, buf.length);
                if (bytes == -1)
                    break;
                if (buffer.remaining() < bytes)
                    buffer = resizeBuffer(buffer, Math.max(buffer.capacity() * 2, buffer.capacity() - buffer.remaining() + bytes));
                buffer.put(buf, 0, bytes);
            }
            buffer.flip();
        }
        return buffer;
    }
}