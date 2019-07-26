package com.cop.zip4j.engine;

import com.cop.zip4j.exception.ZipException;
import com.cop.zip4j.io.SplitOutputStream;
import com.cop.zip4j.io.ZipOutputStream;
import com.cop.zip4j.io.entry.CommonEntryOutputDelegate;
import com.cop.zip4j.io.entry.DeflateEntryOutputDelegate;
import com.cop.zip4j.model.AesStrength;
import com.cop.zip4j.model.CompressionMethod;
import com.cop.zip4j.model.Encryption;
import com.cop.zip4j.model.ZipModel;
import com.cop.zip4j.model.ZipParameters;
import com.cop.zip4j.model.entry.PathZipEntry;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.ArrayUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * @author Oleg Cherednik
 * @since 17.03.2019
 */
@RequiredArgsConstructor
public class ZipEngine {

    @NonNull
    private final ZipModel zipModel;

    public void addEntries(@NonNull Collection<PathZipEntry> entries, @NonNull ZipParameters parameters) {
        checkParameters(parameters);

        if (entries.isEmpty())
            return;

        Predicate<PathZipEntry> ignoreRoot = entry -> {
            String entryName = parameters.getRelativeEntryName(entry.getPath());
            return !"/".equals(entryName) && !"\\".equals(entryName);
        };

        try (ZipOutputStream out = new ZipOutputStream(SplitOutputStream.create(zipModel), zipModel)) {
            entries.stream()
                   .filter(ignoreRoot)
                   .forEach(entry -> addEntry(entry, parameters.toBuilder().build(), out));
        } catch(IOException e) {
            throw new ZipException(e);
        }
    }

    private static void addEntry(@NonNull PathZipEntry entry, @NonNull ZipParameters parameters, @NonNull ZipOutputStream out) {
        try {
            parameters.setCrc32(entry.crc32());

            if (entry.isRegularFile()) {
                parameters.setCompressionMethod(entry.size() == 0 ? CompressionMethod.STORE : parameters.getCompressionMethod());
            } else if (entry.isDirectory()) {
                parameters.setEncryption(Encryption.OFF);
                parameters.setCompressionMethod(CompressionMethod.STORE);
            }

            try (CommonEntryOutputDelegate delegate = createOutputDelegate(parameters, out)) {
                delegate.putNextEntry(entry, parameters);
                entry.write(delegate);
            }
        } catch(ZipException e) {
            throw e;
        } catch(Exception e) {
            throw new ZipException(e);
        }
    }

    private static CommonEntryOutputDelegate createOutputDelegate(@NonNull ZipParameters parameters, @NonNull ZipOutputStream out) {
        if (parameters.getCompressionMethod() == CompressionMethod.DEFLATE)
            return new DeflateEntryOutputDelegate(out, parameters.getCompressionLevel());
        return new CommonEntryOutputDelegate(out);
    }

    private static void checkParameters(ZipParameters parameters) {
        if ((parameters.getCompressionMethod() != CompressionMethod.STORE) && parameters.getCompressionMethod() != CompressionMethod.DEFLATE)
            throw new ZipException("unsupported compression type");

        if (parameters.getEncryption() != Encryption.OFF) {
            if (parameters.getEncryption() != Encryption.STANDARD && parameters.getEncryption() != Encryption.AES)
                throw new ZipException("unsupported encryption method");
            if (ArrayUtils.isEmpty(parameters.getPassword()))
                throw new ZipException("input password is empty or null");
        } else {
            parameters.setAesStrength(AesStrength.NONE);
            parameters.setEncryption(Encryption.OFF);
        }
    }

}