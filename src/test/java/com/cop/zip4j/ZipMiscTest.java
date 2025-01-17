package com.cop.zip4j;

import com.cop.zip4j.exception.Zip4jPathNotExistsException;
import com.cop.zip4j.model.Compression;
import com.cop.zip4j.model.CompressionLevel;
import com.cop.zip4j.model.ZipParameters;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.cop.zip4j.assertj.Zip4jAssertions.assertThatDirectory;
import static com.cop.zip4j.assertj.Zip4jAssertions.assertThatZipFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Oleg Cherednik
 * @since 15.03.2019
 */
@Test
@SuppressWarnings("FieldNamingConvention")
public class ZipMiscTest {

    private static final Path rootDir = Zip4jSuite.generateSubDirNameWithTime(ZipMiscTest.class);

    @BeforeClass
    public static void createDir() throws IOException {
        Files.createDirectories(rootDir);
    }

    @AfterClass(enabled = Zip4jSuite.clear)
    public static void removeDir() throws IOException {
        Zip4jSuite.removeDir(rootDir);
    }

    public void shouldRetrieveAllEntryNamesForExistedZip() throws IOException {
        ZipMisc misc = ZipMisc.builder().zipFile(Zip4jSuite.deflateSolidZip).build();
        assertThat(misc.getEntryNames()).hasSize(13);
    }

    public void shouldRetrieveAllEntryNamesForExistedEncryptedZip() throws IOException {
        Path zipFile = Zip4jSuite.copy(rootDir, Zip4jSuite.deflateSolidPkwareZip);

        ZipMisc misc = ZipMisc.builder().zipFile(zipFile).build();
        assertThat(misc.isEncrypted()).isTrue();
        assertThat(misc.getEntryNames()).hasSize(13);
    }

    public void shouldRetrieveSingleFileWhenNoSplitZip() throws IOException {
        ZipMisc misc = ZipMisc.builder().zipFile(Zip4jSuite.deflateSolidZip).build();
        assertThat(misc.getFiles()).hasSize(1);
    }

    public void shouldRetrieveMultipleFilesWhenSplitZip() throws IOException {
        Path rootDir = Zip4jSuite.subDirNameAsMethodNameWithTme(ZipMiscTest.rootDir);

        ZipParameters parameters = ZipParameters.builder()
                                                .compression(Compression.DEFLATE)
                                                .compressionLevel(CompressionLevel.NORMAL)
                                                .splitLength(1024 * 1024).build();

        Path zipFile = rootDir.resolve("src.zip");
        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();
        zip.add(Zip4jSuite.srcDir, parameters);
        assertThatDirectory(zipFile.getParent()).exists().hasSubDirectories(0).hasFiles(10);

        ZipMisc misc = ZipMisc.builder().zipFile(zipFile).build();
        List<Path> files = misc.getFiles();

        assertThat(files).hasSize(10);
        assertThat(files.get(0).getFileName().toString()).isEqualTo("src.zip");
        assertThat(files.get(1).getFileName().toString()).isEqualTo("src.z01");
        assertThat(files.get(2).getFileName().toString()).isEqualTo("src.z02");
        assertThat(files.get(3).getFileName().toString()).isEqualTo("src.z03");
        assertThat(files.get(4).getFileName().toString()).isEqualTo("src.z04");
        assertThat(files.get(5).getFileName().toString()).isEqualTo("src.z05");
        assertThat(files.get(6).getFileName().toString()).isEqualTo("src.z06");
        assertThat(files.get(7).getFileName().toString()).isEqualTo("src.z07");
        assertThat(files.get(8).getFileName().toString()).isEqualTo("src.z08");
        assertThat(files.get(9).getFileName().toString()).isEqualTo("src.z09");
    }

    public void shouldThrowExceptionWhenAddedFileNotExists() throws IOException {
        ZipParameters parameters = ZipParameters.builder()
                                                .compression(Compression.STORE)
                                                .build();

        Path bentley = Zip4jSuite.carsDir.resolve("bentley-continental.jpg");
        Path ferrari = Zip4jSuite.carsDir.resolve("ferrari-458-italia.jpg");
        Path wiesmann = Zip4jSuite.carsDir.resolve("wiesmann-gt-mf5.jpg");
        Path notExisted = Zip4jSuite.carsDir.resolve(UUID.randomUUID().toString());
        List<Path> files = Arrays.asList(bentley, ferrari, wiesmann, notExisted);

        Path zipFile = Zip4jSuite.subDirNameAsMethodName(rootDir).resolve("src.zip");
        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();

        assertThatThrownBy(() -> zip.add(files, parameters)).isExactlyInstanceOf(Zip4jPathNotExistsException.class);
    }

    public void shouldMergeSplitZip() throws IOException {
        ZipMisc misc = ZipMisc.builder().zipFile(Zip4jSuite.deflateSplitZip).build();
        assertThat(misc.isSplit()).isTrue();

        Path mergeDir = Zip4jSuite.subDirNameAsMethodName(rootDir);
        Path mergeZipFle = mergeDir.resolve("src.zip");
        misc.merge(mergeZipFle);

        assertThatDirectory(mergeDir).exists().hasSubDirectories(0).hasFiles(1);
        // TODO it's not working under gradle build
        assertThatZipFile(mergeZipFle).exists().rootEntry().matches(TestUtils.zipRootDirAssert);
    }

//    public void shouldMergeZip64SplitZip() throws IOException {
//        ZipParameters parameters = ZipParameters.builder()
//                                                .compression(Compression.STORE)
////                                                .splitLength(1024 * 1024)
//                                                .zip64(true)
//                                                .build();
//
//        Path zipFile = Zip4jSuite.subDirNameAsMethodName(rootDir).resolve("src.zip");
//        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();
//        zip.add(Zip4jSuite.srcDir, parameters);
//
//        ZipMisc misc = ZipMisc.builder().zipFile(zipFile).build();
//        assertThat(misc.isSplit()).isTrue();
//
//        Path mergeDir = zipFile.getParent().resolve("merges");
//        Path mergeZipFle = mergeDir.resolve("src.zip");
//        misc.merge(mergeZipFle);
//
//        assertThatDirectory(mergeDir).exists().hasSubDirectories(0).hasFiles(1);
//        // TODO it's not working under gradle build
//        assertThatZipFile(mergeZipFle).exists().rootEntry().matches(TestUtils.zipRootDirAssert);
//    }

}
