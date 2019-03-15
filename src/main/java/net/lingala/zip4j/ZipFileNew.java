package net.lingala.zip4j;

import lombok.Builder;
import lombok.NonNull;
import net.lingala.zip4j.core.HeaderWriter;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.NoSplitOutputStream;
import net.lingala.zip4j.io.SplitOutputStream;
import net.lingala.zip4j.model.ZipModel;
import net.lingala.zip4j.util.InternalZipConstants;
import org.apache.commons.lang.StringUtils;

import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * @author Oleg Cherednik
 * @since 15.03.2019
 */
@Builder
public final class ZipFileNew {

    @NonNull
    private final Path zipFile;
    @NonNull
    @Builder.Default
    private final Charset charset = Charset.defaultCharset();
    private final char[] password;

    public void clearComment() throws ZipException {
        setComment(null);
    }

    public void setComment(String comment) throws ZipException {
        comment = StringUtils.isEmpty(comment) ? null : comment.trim();
        UnzipIt.checkZipFile(zipFile);

        ZipModel zipModel = ZipFile.createZipModel(zipFile, charset);
        ZipIt.checkSplitArchiveModification(zipModel);

        if (StringUtils.length(comment) > InternalZipConstants.MAX_ALLOWED_ZIP_COMMENT_LENGTH)
            throw new ZipException("comment length exceeds maximum length");

        zipModel.getEndCentralDirectory().setComment(comment);

        try (SplitOutputStream out = new NoSplitOutputStream(zipModel.getZipFile())) {
            out.seek(zipModel.getOffOfStartOfCentralDir());
            new HeaderWriter().finalizeZipFileWithoutValidations(zipModel, out);
        } catch(Exception e) {
            throw new ZipException(e);
        }
    }

    /**
     * Returns the comment set for the Zip path
     *
     * @return String
     * @throws ZipException
     */
    public String getComment() throws ZipException {
        UnzipIt.checkZipFile(zipFile);
        return ZipFile.createZipModel(zipFile, charset).getEndCentralDirectory().getComment();
    }

//    /**
//     * Sets the password for the zip path
//     *
//     * @param password
//     * @throws ZipException
//     */
//    public void setPassword(char[] password) throws ZipException {
//        if (zipModel == null) {
//            zipModel = createZipModel();
//            if (zipModel == null) {
//                throw new ZipException("Zip Model is null");
//            }
//        }
//
//        zipModel.getCentralDirectory().getFileHeaders().stream()
//                .filter(fileHeader -> fileHeader.getEncryption() != Encryption.AES)
//                .forEach(fileHeader -> fileHeader.setPassword(password));
//    }
}
