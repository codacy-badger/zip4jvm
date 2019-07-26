package com.cop.zip4j.core.writers;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import com.cop.zip4j.io.SplitOutputStream;
import com.cop.zip4j.model.CentralDirectory;
import com.cop.zip4j.utils.InternalZipConstants;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author Oleg Cherednik
 * @since 26.04.2019
 */
@RequiredArgsConstructor
final class FileHeaderWriter {

    @NonNull
    private final List<CentralDirectory.FileHeader> fileHeaders;
    @NonNull
    private final Charset charset;

    public void write(SplitOutputStream out) throws IOException {
        for (CentralDirectory.FileHeader fileHeader : fileHeaders)
            writeFileHeader(fileHeader, out);
    }

    private void writeFileHeader(CentralDirectory.FileHeader fileHeader, SplitOutputStream out) throws IOException {
        byte[] fileName = fileHeader.getFileName(charset);
        byte[] fileComment = fileHeader.getFileComment(charset);

        out.writeDword(CentralDirectory.FileHeader.SIGNATURE);
        out.writeWord(fileHeader.getVersionMadeBy());
        out.writeWord(fileHeader.getVersionToExtract());
        out.writeWord(fileHeader.getGeneralPurposeFlag().getData());
        out.writeWord(fileHeader.getCompressionMethod().getValue());
        out.writeDword(fileHeader.getLastModifiedTime());
        out.writeDword((int)fileHeader.getCrc32());
        out.writeDword(fileHeader.isWriteZip64FileSize() ? InternalZipConstants.ZIP_64_LIMIT : fileHeader.getCompressedSize());
        out.writeDword(fileHeader.isWriteZip64FileSize() ? InternalZipConstants.ZIP_64_LIMIT : fileHeader.getUncompressedSize());
        out.writeWord(fileName.length);
        out.writeWord(fileHeader.getExtraField().getLength());
        out.writeWord(fileComment.length);
        out.writeWord(fileHeader.getDiskNumber());
        out.writeBytes(fileHeader.getInternalFileAttributes() != null ? fileHeader.getInternalFileAttributes() : new byte[2]);
        out.writeBytes(fileHeader.getExternalFileAttributes() != null ? fileHeader.getExternalFileAttributes() : new byte[4]);
        out.writeDword(fileHeader.isWriteZip64OffsetLocalHeader() ? InternalZipConstants.ZIP_64_LIMIT : fileHeader.getOffsLocalFileHeader());
        out.writeBytes(fileName);
        new ExtraFieldWriter(fileHeader.getExtraField(), charset).write(out);
        out.writeBytes(fileComment);
    }
}