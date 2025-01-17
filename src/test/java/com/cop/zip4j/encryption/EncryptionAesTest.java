package com.cop.zip4j.encryption;

import com.cop.zip4j.TestUtils;
import com.cop.zip4j.Zip4jSuite;
import com.cop.zip4j.ZipIt;
import com.cop.zip4j.model.Compression;
import com.cop.zip4j.model.CompressionLevel;
import com.cop.zip4j.model.Encryption;
import com.cop.zip4j.model.ZipParameters;
import com.cop.zip4j.model.aes.AesStrength;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.cop.zip4j.assertj.Zip4jAssertions.assertThatDirectory;
import static com.cop.zip4j.assertj.Zip4jAssertions.assertThatZipFile;

/**
 * @author Oleg Cherednik
 * @since 29.07.2019
 */
@SuppressWarnings("FieldNamingConvention")
public class EncryptionAesTest {

    private static final Path rootDir = Zip4jSuite.generateSubDirName(EncryptionAesTest.class);

    @BeforeClass
    public static void createDir() throws IOException {
        Files.createDirectories(rootDir);
    }

    @AfterClass(enabled = Zip4jSuite.clear)
    public static void removeDir() throws IOException {
        Zip4jSuite.removeDir(rootDir);
    }

    @Test
    public void shouldCreateNewZipWithFolderAndAesEncryption() throws IOException {
        ZipParameters parameters = ZipParameters.builder()
                                                .compression(Compression.STORE)
                                                .compressionLevel(CompressionLevel.NORMAL)
                                                .encryption(Encryption.AES)
                                                .strength(AesStrength.KEY_STRENGTH_256)
                                                .comment("password: " + new String(Zip4jSuite.password))
                                                .password(Zip4jSuite.password).build();

        Path dstDir = Zip4jSuite.subDirNameAsMethodName(rootDir);
        Path zipFile = dstDir.resolve("src.zip");
        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();
        zip.add(Zip4jSuite.srcDir, parameters);

        assertThatDirectory(dstDir).exists().hasSubDirectories(0).hasFiles(1);
        assertThatZipFile(zipFile, Zip4jSuite.password).exists().rootEntry().matches(TestUtils.zipRootDirAssert);
    }

//    @Test
//    public void shouldCreateNewZipWithSelectedFilesAndAesEncryption() throws IOException {
//        ZipParameters parameters = ZipParameters.builder()
//                                                .compression(Compression.STORE)
//                                                .compressionLevel(CompressionLevel.NORMAL)
//                                                .encryption(Encryption.AES)
//                                                .strength(AesStrength.KEY_STRENGTH_256)
//                                                .comment("password: " + new String(Zip4jSuite.password))
//                                                .password(Zip4jSuite.password).build();
//
//        Path dstDir = Zip4jSuite.subDirNameAsMethodNameWithTme(rootDir);
//        Path zipFile = dstDir.resolve("src.zip");
//
//        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();
//        zip.add(Zip4jSuite.filesCarsDir, parameters);
//
//        dstDir = dstDir.resolve("unzip");
//        UnzipIt unzip = UnzipIt.builder()
//                               .zipFile(zipFile)
//                               .password(Zip4jSuite.password).build();
//        unzip.extract(dstDir);
//
//        assertThatDirectory(zipFile.getParent()).exists().hasSubDirectories(0).hasFiles(1);
//        assertThatZipFile(zipFile, Zip4jSuite.password).exists().directory("/").matches(TestUtils.zipCarsDirAssert);
//    }
//
//    public void shouldThrowExceptionWhenStandardEncryptionAndNullPassword() throws IOException {
//        ZipParameters parameters = ZipParameters.builder()
//                                                .compressionMethod(CompressionMethod.DEFLATE)
//                                                .compressionLevel(CompressionLevel.NORMAL)
//                                                .encryption(Encryption.PKWARE)
//                                                .password(null).build();
//
//        Path dstDir = Zip4jSuite.subDirNameAsMethodName(rootDir);
//        Path zipFile = dstDir.resolve("src.zip");
//        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();
//
//        assertThatThrownBy(() -> zip.add(Zip4jSuite.srcDir, parameters)).isExactlyInstanceOf(Zip4jException.class);
//    }
//
//    public void shouldThrowExceptionWhenStandardEncryptionAndEmptyPassword() throws IOException {
//        ZipParameters parameters = ZipParameters.builder()
//                                                .compressionMethod(CompressionMethod.DEFLATE)
//                                                .compressionLevel(CompressionLevel.NORMAL)
//                                                .encryption(Encryption.PKWARE)
//                                                .password("".toCharArray()).build();
//
//        Path dstDir = Zip4jSuite.subDirNameAsMethodName(rootDir);
//        Path zipFile = dstDir.resolve("src.zip");
//        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();
//
//        assertThatThrownBy(() -> zip.add(Zip4jSuite.srcDir, parameters)).isExactlyInstanceOf(Zip4jException.class);
//    }
//
//    public void shouldUnzipWhenStandardEncryption() throws IOException {
//        ZipParameters parameters = ZipParameters.builder()
//                                                .compressionMethod(CompressionMethod.DEFLATE)
//                                                .compressionLevel(CompressionLevel.NORMAL)
//                                                .encryption(Encryption.PKWARE)
//                                                .comment("password: " + new String(Zip4jSuite.password))
//                                                .password(Zip4jSuite.password).build();
//
//        Path dstDir = Zip4jSuite.subDirNameAsMethodName(rootDir);
//        Path zipFile = dstDir.resolve("src.zip");
//        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();
//        zip.add(Zip4jSuite.srcDir, parameters);
//
//        dstDir = dstDir.resolve("unzip");
//        UnzipIt unzip = UnzipIt.builder()
//                               .zipFile(zipFile)
//                               .password(Zip4jSuite.password).build();
//        unzip.extract(dstDir);
//
//        assertThatDirectory(dstDir).matches(TestUtils.dirAssert);
//    }


//
//    public void shouldThrowExceptionWhenUnzipStandardEncryptedZipWithIncorrectPassword() throws IOException {
//        ZipParameters parameters = ZipParameters.builder()
//                                                .compressionMethod(CompressionMethod.DEFLATE)
//                                                .compressionLevel(CompressionLevel.NORMAL)
//                                                .encryption(Encryption.PKWARE)
//                                                .comment("password: " + new String(Zip4jSuite.password))
//                                                .password(Zip4jSuite.password).build();
//
//        Path dstDir = Zip4jSuite.subDirNameAsMethodName(rootDir);
//        Path zipFile = dstDir.resolve("src.zip");
//        ZipIt zip = ZipIt.builder().zipFile(zipFile).build();
//        zip.add(Zip4jSuite.srcDir, parameters);
//
//        Path dstDir1 = dstDir.resolve("unzip");
//        UnzipIt unzip = UnzipIt.builder()
//                               .zipFile(zipFile)
//                               .password(UUID.randomUUID().toString().toCharArray()).build();
//
//        assertThatThrownBy(() -> unzip.extract(dstDir1)).isExactlyInstanceOf(Zip4jException.class);
//    }

//    public void shouldUnzipWhenAesEncryption() throws IOException {
//        Path dstDir = Zip4jSuite.subDirNameAsMethodName(rootDir);
////        Path zipFile = dstDir.resolve("d:/zip4j/aes.zip");
//        Path zipFile = dstDir.resolve("d:/zip4j/tmp/aes.zip");
//        UnzipIt unzip = UnzipIt.builder()
//                               .zipFile(zipFile)
//                               .password(Zip4jSuite.password).build();
//        unzip.extract(dstDir);
//    }

}
