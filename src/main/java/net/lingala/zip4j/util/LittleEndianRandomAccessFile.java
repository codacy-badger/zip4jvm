package net.lingala.zip4j.util;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

/**
 * @author Oleg Cherednik
 * @since 21.02.2019
 */
@RequiredArgsConstructor
public final class LittleEndianRandomAccessFile {

    // TODO temporary, should be removed
    @Getter
    private final RandomAccessFile in;
    @Getter
    private int offs;

    public void resetOffs() {
        offs = 0;
    }

    public short readShort() throws IOException {
        offs += 2;
        return convertShort(in.readShort());
    }

    public int readInt() throws IOException {
        offs += 4;
        return (int)convertInt(in.readInt());
    }

    public String readString(int length) throws IOException {
        if (length <= 0)
            return null;

        offs += length;
        byte[] buf = new byte[length];
        in.readFully(buf);
        return new String(buf, Zip4jUtil.detectCharset(buf));
    }

    public String readString(int length, @NonNull Charset charset) throws IOException {
        if (length <= 0)
            return null;

        offs += length;
        byte[] buf = new byte[length];
        in.readFully(buf);
        return new String(buf, charset);
    }

    public long readIntAsLong() throws IOException {
        offs += 4;
        return convertInt(in.readInt());
    }

    public long readLong() throws IOException {
        offs += 8;
        return convertLong(in.readLong());
    }

    public long length() throws IOException {
        return in.length();
    }

    public void seek(long pos) throws IOException {
        in.seek(pos);
    }

    public long getFilePointer() throws IOException {
        return in.getFilePointer();
    }

    private static short convertShort(short val) {
        return (short)(getByte(val, 0) << 8 | getByte(val, 1));
    }

    private static long convertInt(int val) {
        return getByte(val, 0) << 24 | getByte(val, 1) << 16 | getByte(val, 2) << 8 | getByte(val, 3);
    }

    private static long convertLong(long val) {
        return getByte(val, 0) << 56 | getByte(val, 1) << 48 | getByte(val, 2) << 40 | getByte(val, 3) << 32 |
                getByte(val, 4) << 24 | getByte(val, 5) << 16 | getByte(val, 6) << 8 | getByte(val, 7);
    }

    private static long getByte(long val, int i) {
        return (val >> i * 8) & 0xFF;
    }

}
