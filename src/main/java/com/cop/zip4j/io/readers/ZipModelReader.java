package com.cop.zip4j.io.readers;

import com.cop.zip4j.io.in.LittleEndianReadFile;
import com.cop.zip4j.model.CentralDirectory;
import com.cop.zip4j.model.EndCentralDirectory;
import com.cop.zip4j.model.Zip64;
import com.cop.zip4j.model.ZipModel;
import com.cop.zip4j.model.builders.ZipModelBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * Start reading from the end of the file.
 *
 * <pre>
 * ...
 * [zip64 end of central directory record]
 * [zip64 end of central directory locator]
 * [end of central directory record]
 * EOF
 * </pre>
 *
 * @author Oleg Cherednik
 * @since 06.03.2019
 */
@RequiredArgsConstructor
public final class ZipModelReader {

    @NonNull
    private final Path zipFile;
    @NonNull
    private final Charset charset;

    @NonNull
    public ZipModel read() throws IOException {
        try (LittleEndianReadFile in = new LittleEndianReadFile(zipFile)) {
            EndCentralDirectory endCentralDirectory = new EndCentralDirectoryReader().read(in);
            Zip64 zip64 = new Zip64Reader().read(in);

            long offs = ZipModelBuilder.getCentralDirectoryOffs(endCentralDirectory, zip64);
            long totalEntries = ZipModelBuilder.getTotalEntries(endCentralDirectory, zip64);
            CentralDirectory centralDirectory = new CentralDirectoryReader(offs, totalEntries).read(in);

            return new ZipModelBuilder(zipFile, charset, endCentralDirectory, zip64, centralDirectory).create();
        }
    }

}
