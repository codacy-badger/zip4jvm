package net.lingala.zip4j.core.writers;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.OutputStreamDecorator;
import net.lingala.zip4j.model.EndCentralDirectory;
import net.lingala.zip4j.util.InternalZipConstants;
import org.apache.commons.lang.ArrayUtils;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author Oleg Cherednik
 * @since 10.04.2019
 */
@RequiredArgsConstructor
final class EndCentralDirectoryWriter {

    @NonNull
    private final OutputStreamDecorator out;
    @NonNull
    private final Charset charset;

    public void write(@NonNull EndCentralDirectory dir) throws IOException {
        out.writeDword(dir.getSignature());
        out.writeWord((short)dir.getDiskNumber());
        out.writeWord((short)dir.getStartDiskNumber());
        out.writeWord((short)dir.getTotalEntries());
        out.writeWord((short)dir.getDiskEntries());
        out.writeDword(dir.getSize());
        out.writeDword(Math.min(dir.getOffs(), InternalZipConstants.ZIP_64_LIMIT));

        byte[] comment = dir.getComment(charset);
        out.writeWord((short)ArrayUtils.getLength(comment));
        out.writeBytes(comment);
    }

    private byte[] getComment(EndCentralDirectory dir) {
        byte[] comment = dir.getComment(charset);

        if (out.getOffs() + 2 + comment.length > 65_535)
            throw new ZipException("Comment is too long. File length is greater that 65535 bytes (see 4.4.12)");

        return comment;
    }

}
