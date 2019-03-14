/*
 * Copyright 2010 Srikanth Reddy Lingala
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lingala.zip4j.engine;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.SplitOutputStream;
import net.lingala.zip4j.io.ZipOutputStream;
import net.lingala.zip4j.model.AESStrength;
import net.lingala.zip4j.model.CentralDirectory;
import net.lingala.zip4j.model.CompressionMethod;
import net.lingala.zip4j.model.Encryption;
import net.lingala.zip4j.model.InputStreamMeta;
import net.lingala.zip4j.model.ZipModel;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.ArchiveMaintainer;
import net.lingala.zip4j.util.ChecksumCalculator;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

@RequiredArgsConstructor
public class ZipEngine {

    @NonNull
    private final ZipModel zipModel;
    private final ArchiveMaintainer archiveMaintainer = new ArchiveMaintainer();

    public void addEntries(@NonNull Collection<Path> entries, @NonNull ZipParameters parameters) throws ZipException, IOException {
        if (entries.isEmpty())
            return;

        zipModel.createEndCentralDirectoryIfNotExist();
        checkParameters(parameters);

        try (ZipOutputStream out = new ZipOutputStream(SplitOutputStream.create(zipModel), zipModel)) {
            out.seek(zipModel.getOffOfStartOfCentralDir());

            for (Path entry : entries) {
                if (entry == null)
                    continue;

                String entryName = parameters.getRelativeEntryName(entry);

                // TODO ignore root (it should be done prior)
                // TODO here could be empty directory ignored (also prior)
                if ("/".equals(entryName) || "\\".equals(entryName))
                    continue;

                removeFile(entryName);

                ZipParameters params = parameters.toBuilder().build();

                if (Files.isRegularFile(entry)) {
                    if (params.getEncryption() == Encryption.STANDARD)
                        params.setSourceFileCRC(new ChecksumCalculator(entry).calculate());

                    if (Files.size(entry) == 0)
                        params.setCompressionMethod(CompressionMethod.STORE);
                }

                out.putNextEntry(entry, params);

                if (Files.isRegularFile(entry))
                    copyLarge(entry, out);

                out.closeEntry();
            }

            out.finish();
        }
    }

    private static long copyLarge(@NonNull Path entry, @NonNull OutputStream out) throws IOException {
        try (InputStream in = new FileInputStream(entry.toFile())) {
            return IOUtils.copyLarge(in, out);
        }
    }

    public void addStreamToZip(@NonNull Collection<InputStreamMeta> files, @NonNull final ZipParameters parameters) throws ZipException {
        zipModel.createEndCentralDirectoryIfNotExist();
        checkParameters(parameters);

        try (ZipOutputStream out = new ZipOutputStream(SplitOutputStream.create(zipModel), zipModel)) {
            out.seek(zipModel.getEndCentralDirectory().getOffOfStartOfCentralDir());

            for (InputStreamMeta file : files) {
                if (file == null)
                    continue;

                // TODO should be relative to the root zip path
                String fileName = file.getRelativePath();
                removeFile(fileName);

                ZipParameters params = parameters.toBuilder().build();

                out.putNextEntry(file.getRelativePath(), params);

                if (file.isRegularFile())
                    IOUtils.copy(file.getIn(), out);

                out.closeEntry();
                out.finish();
            }
        } catch(ZipException e) {
            throw e;
        } catch(Exception e) {
            throw new ZipException(e);
        }
    }

    private static void checkParameters(ZipParameters parameters) throws ZipException {
        if ((parameters.getCompressionMethod() != CompressionMethod.STORE) && parameters.getCompressionMethod() != CompressionMethod.DEFLATE)
            throw new ZipException("unsupported compression type");

        if (parameters.getEncryption() != Encryption.OFF) {
            if (parameters.getEncryption() != Encryption.STANDARD && parameters.getEncryption() != Encryption.AES)
                throw new ZipException("unsupported encryption method");
            if (ArrayUtils.isEmpty(parameters.getPassword()))
                throw new ZipException("input password is empty or null");
        } else {
            parameters.setAesKeyStrength(AESStrength.NONE);
            parameters.setEncryption(Encryption.OFF);
        }

    }

    public void removeFile(@NonNull String fileName) throws ZipException {
        removeFile(zipModel.getFileHeader(fileName));
    }

    public void removeFile(CentralDirectory.FileHeader fileHeader) throws ZipException {
        archiveMaintainer.removeZipFile(zipModel, fileHeader);
    }

}