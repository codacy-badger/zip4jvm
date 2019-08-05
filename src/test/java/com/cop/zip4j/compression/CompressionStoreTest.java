package com.cop.zip4j.compression;

import com.cop.zip4j.TestUtils;
import com.cop.zip4j.UnzipIt;
import com.cop.zip4j.Zip4jSuite;
import com.cop.zip4j.ZipIt;
import com.cop.zip4j.exception.Zip4jException;
import com.cop.zip4j.model.Compression;
import com.cop.zip4j.model.CompressionLevel;
import com.cop.zip4j.model.ZipParameters;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static com.cop.zip4j.assertj.Zip4jAssertions.assertThatDirectory;
import static com.cop.zip4j.assertj.Zip4jAssertions.assertThatZipFile;

/**
 * @author Oleg Cherednik
 * @since 15.03.2019
 */
@Test
@SuppressWarnings("FieldNamingConvention")
public class CompressionStoreTest {

    private static final Path rootDir = Zip4jSuite.generateSubDirNameWithTime(CompressionStoreTest.class);

    @BeforeClass
    public static void createDir() throws IOException {
        Files.createDirectories(rootDir);
    }

    @AfterClass(enabled = Zip4jSuite.clear)
    public static void removeDir() throws IOException {
        Zip4jSuite.removeDir(rootDir);
    }

    public void shouldCreateNewZipWithFilesWhenStoreCompression() throws IOException, Zip4jException {
        Path destDir = Zip4jSuite.subDirNameAsMethodName(rootDir);
        Path zipFile = destDir.resolve("src.zip");

        ZipParameters parameters = ZipParameters.builder()
                                                .compressionMethod(Compression.STORE)
                                                .compressionLevel(CompressionLevel.NORMAL)
                                                .defaultFolderPath(Zip4jSuite.srcDir).build();

        Path bentley = Zip4jSuite.carsDir.resolve("bentley-continental.jpg");
        Path ferrari = Zip4jSuite.carsDir.resolve("ferrari-458-italia.jpg");
        Path wiesmann = Zip4jSuite.carsDir.resolve("wiesmann-gt-mf5.jpg");
        List<Path> files = Arrays.asList(bentley, ferrari, wiesmann);

        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();
        zip.add(files, parameters);

        assertThatDirectory(zipFile.getParent()).exists().hasSubDirectories(0).hasFiles(1);
        assertThatZipFile(zipFile).exists().rootEntry().hasSubDirectories(1).hasFiles(0);
        assertThatZipFile(zipFile).directory("cars/").matches(TestUtils.zipCarsDirAssert);
    }

    public void shouldUnzipWhenStoreCompression() throws IOException {
        Path destDir = Zip4jSuite.subDirNameAsMethodName(rootDir);
        UnzipIt unzip = UnzipIt.builder()
                               .zipFile(Paths.get("d:/zip4j/tmp/a.zip"))
//                               .zipFile(Zip4jSuite.wirRarStoreZip)
                               .build();
        unzip.extract(destDir);
//        assertThatDirectory(destDir).matches(TestUtils.dirAssert);
    }

//    public void shouldUnzipWhenStoreCompressionAesEncryption() throws IOException {
//        Path destDir = Zip4jSuite.subDirNameAsMethodName(rootDir);
//        UnzipIt unzip = UnzipIt.builder()
//                               .zipFile(Zip4jSuite.storeAesZip)
//                               .password(Zip4jSuite.password)
//                               .build();
//        unzip.extract(destDir);
//        assertThatDirectory(destDir).matches(TestUtils.dirAssert);
//    }

}
