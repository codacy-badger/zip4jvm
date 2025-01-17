package com.cop.zip4j.model.entry;

import com.cop.zip4j.exception.Zip4jException;
import com.cop.zip4j.utils.ZipUtils;
import lombok.NonNull;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Oleg Cherednik
 * @since 26.07.2019
 */
public abstract class ZipEntry {

    // TODO should be ZipEntry
    public static PathZipEntry of(Path path) {
        if (Files.isDirectory(path)) {
            try {
                int lastModifiedTime = ZipUtils.javaToDosTime(Files.getLastModifiedTime(path).toMillis());
                return new DirectoryZipEntry(path, lastModifiedTime);
            } catch(IOException e) {
                throw new Zip4jException(e);
            }
        }

        if (Files.isRegularFile(path)) {
            try {
                long size = Files.size(path);
                long checksum = FileUtils.checksumCRC32(path.toFile());
                int lastModifiedTime = ZipUtils.javaToDosTime(Files.getLastModifiedTime(path).toMillis());
                return new RegularFileZipEntry(path, size, checksum, lastModifiedTime);
            } catch(IOException e) {
                throw new Zip4jException(e);
            }
        }

        throw new Zip4jException("Cannot add neither directory nor regular file to zip");
    }

    public abstract String getAbsolutePath();

    public boolean isRegularFile() {
        return false;
    }

    public boolean isDirectory() {
        return false;
    }

    public long getUncompressedSize() {
        return 0;
    }

    public long checksum() {
        return 0;
    }

    public long write(@NonNull OutputStream out) throws IOException {
        return 0;
    }

    public abstract int getLastModifiedTime();

    @Override
    public String toString() {
        return getAbsolutePath();
    }

}
