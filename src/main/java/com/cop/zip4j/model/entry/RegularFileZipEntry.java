package com.cop.zip4j.model.entry;

import com.cop.zip4j.model.CompressionMethod;
import com.cop.zip4j.model.Encryption;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Oleg Cherednik
 * @since 26.07.2019
 */
@Getter
public class RegularFileZipEntry extends PathZipEntry {

    public RegularFileZipEntry(Path file) {
        super(file);
    }

    @Override
    public long size() throws IOException {
        return Files.size(path);
    }

    @Override
    public long crc32() throws IOException {
        return encryption == Encryption.STANDARD ? FileUtils.checksumCRC32(path.toFile()) : 0;
    }

    @Override
    public long write(@NonNull OutputStream out) throws IOException {
        try (InputStream in = new FileInputStream(path.toFile())) {
            return IOUtils.copyLarge(in, out);
        }
    }

    @Override
    public void setCompressionMethod(@NonNull CompressionMethod compressionMethod) throws IOException {
        this.compressionMethod = size() == 0 ? CompressionMethod.STORE : compressionMethod;
    }

    @Override
    public void setEncryption(@NonNull Encryption encryption) {
        this.encryption = encryption;
    }

}
