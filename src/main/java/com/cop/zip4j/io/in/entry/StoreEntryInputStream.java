package com.cop.zip4j.io.in.entry;

import com.cop.zip4j.crypto.Decoder;
import com.cop.zip4j.exception.Zip4jException;
import com.cop.zip4j.io.in.MarkDataInput;
import com.cop.zip4j.model.LocalFileHeader;
import com.cop.zip4j.model.ZipModel;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * @author Oleg Cherednik
 * @since 04.08.2019
 */
final class StoreEntryInputStream extends InputStream {

    private static final String MARK = StoreEntryInputStream.class.getSimpleName();

    private final ZipModel zipModel;
    private final LocalFileHeader localFileHeader;
    private final Decoder decoder;
    private final long compressedSize;
    private final MarkDataInput in;

    private final Checksum checksum = new CRC32();

    public StoreEntryInputStream(ZipModel zipModel, LocalFileHeader localFileHeader, Decoder decoder, MarkDataInput in) {
        this.zipModel = zipModel;
        this.decoder = decoder;
        this.localFileHeader = localFileHeader;
        compressedSize = decoder.getCompressedSize(localFileHeader);
        this.in = in;

        in.mark(MARK);
    }

    @Override
    public int available() {
        return (int)Math.max(0, compressedSize - in.getWrittenBytesAmount(MARK));
    }

    @Override
    public int read() throws IOException {
        int b = available() == 0 ? IOUtils.EOF : in.readByte();

        if (b != IOUtils.EOF)
            checksum.update(b);

        return b;
    }

    private void checkChecksum() {
        if (checksum.getValue() != localFileHeader.getCrc32())
            throw new Zip4jException("Checksum is not match for entry: " + localFileHeader.getFileName());
    }

    @Override
    public void close() throws IOException {
        checkChecksum();
    }

}
