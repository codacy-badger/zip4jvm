package com.cop.zip4j.io;

import com.cop.zip4j.engine.UnzipEngine;
import com.cop.zip4j.utils.InternalZipConstants;

import java.io.EOFException;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class InflaterInputStream extends PartInputStream {

    private final Inflater inflater = new Inflater(true);
    private byte[] buff;
    private byte[] oneByteBuff = new byte[1];
    private UnzipEngine unzipEngine;
    private long bytesWritten;
    private long uncompressedSize;

    public InflaterInputStream(LittleEndianRandomAccessFile in, long len, UnzipEngine unzipEngine) {
        super(in, len, unzipEngine);
        this.buff = new byte[InternalZipConstants.BUFF_SIZE];
        this.unzipEngine = unzipEngine;
        bytesWritten = 0;
        uncompressedSize = unzipEngine.getFileHeader().getUncompressedSize();
    }

    @Override
    public int read() throws IOException {
        return read(oneByteBuff, 0, 1) == -1 ? -1 : oneByteBuff[0] & 0xff;
    }

    public int read(byte[] b, int off, int len) throws IOException {

        if (b == null) {
            throw new NullPointerException("input buffer is null");
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        try {
            int n;
            if (bytesWritten >= uncompressedSize) {
                finishInflating();
                return -1;
            }
            while ((n = inflater.inflate(b, off, len)) == 0) {
                if (inflater.finished() || inflater.needsDictionary()) {
                    finishInflating();
                    return -1;
                }
                if (inflater.needsInput()) {
                    fill();
                }
            }
            bytesWritten += n;
            return n;
        } catch(DataFormatException e) {
            String s = "Invalid ZLIB data format";
            if (e.getMessage() != null) {
                s = e.getMessage();
            }
            // TODO fix it, localFileHeader is not belong to UnzipEngine
//            if (unzipEngine != null)
//                if (unzipEngine.getLocalFileHeader().getEncryption() == Encryption.STANDARD)
//                    s += " - Wrong Password?";
            throw new IOException(s);
        }
    }

    private void finishInflating() throws IOException {
        //In some cases, compelte data is not read even though inflater is complete
        //make sure to read complete data before returning -1
        byte[] b = new byte[1024];
        while (super.read(b, 0, 1024) != -1) {
            //read all data
        }
        checkAndReadAESMacBytes();
    }

    private void fill() throws IOException {
        int len = super.read(buff, 0, buff.length);
        if (len == -1) {
            throw new EOFException("Unexpected end of ZLIB input stream");
        }
        inflater.setInput(buff, 0, len);
    }

    /**
     * Skips specified number of bytes of uncompressed data.
     *
     * @param n the number of bytes to skip
     * @return the actual number of bytes skipped.
     * @throws IOException              if an I/O error has occurred
     * @throws IllegalArgumentException if n < 0
     */
    @Override
    public long skip(long n) throws IOException {
        if (n < 0) {
            throw new IllegalArgumentException("negative skip length");
        }
        int max = (int)Math.min(n, Integer.MAX_VALUE);
        int total = 0;
        byte[] b = new byte[512];
        while (total < max) {
            int len = max - total;
            if (len > b.length) {
                len = b.length;
            }
            len = read(b, 0, len);
            if (len == -1) {
                break;
            }
            total += len;
        }
        return total;
    }

    /**
     * Returns 0 after EOF has been reached, otherwise always return 1.
     * <p>
     * Programs should not count on this method to return the actual number
     * of bytes that could be read without blocking.
     *
     * @return 1 before EOF and 0 after EOF.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public int available() {
        return inflater.finished() ? 0 : 1;
    }

    @Override
    public void close() throws IOException {
        super.close();
        inflater.end();
    }

}