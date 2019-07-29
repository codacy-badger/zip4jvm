package com.cop.zip4j.model;

import com.cop.zip4j.crypto.Decoder;
import com.cop.zip4j.crypto.Encoder;
import com.cop.zip4j.crypto.aes.AesDecoder;
import com.cop.zip4j.crypto.aes.AesEncoder;
import com.cop.zip4j.crypto.pkware.PkwareDecoder;
import com.cop.zip4j.crypto.pkware.PkwareEncoder;
import com.cop.zip4j.exception.ZipException;
import com.cop.zip4j.io.LittleEndianRandomAccessFile;
import com.cop.zip4j.model.entry.PathZipEntry;
import lombok.NonNull;

import java.io.IOException;

/**
 * @author Oleg Cherednik
 * @since 09.03.2019
 */
@SuppressWarnings("MethodCanBeVariableArityMethod")
public enum Encryption {
    OFF {
        @Override
        public Encoder encoder(@NonNull LocalFileHeader localFileHeader, @NonNull PathZipEntry entry) {
            return Encoder.NULL;
        }

        @Override
        public Decoder decoder(@NonNull LittleEndianRandomAccessFile in, @NonNull LocalFileHeader localFileHeader, char[] password)
                throws IOException {
            return null;
        }
    },
    PKWARE {
        @Override
        public Encoder encoder(@NonNull LocalFileHeader localFileHeader, @NonNull PathZipEntry entry) {
            return PkwareEncoder.create(localFileHeader, entry);
        }

        @Override
        public Decoder decoder(@NonNull LittleEndianRandomAccessFile in, @NonNull LocalFileHeader localFileHeader, char[] password)
                throws IOException {
//            in.seek(localFileHeader.getOffs());
//            byte[] header = in.readBytes(PkwareEncoder.SIZE_HEADER);
//            return new PkwareDecoder(localFileHeader, password, header);
            return PkwareDecoder.create(in, localFileHeader, password);
        }
    },
    STRONG,
    AES {
        @Override
        public Encoder encoder(@NonNull LocalFileHeader localFileHeader, @NonNull PathZipEntry entry) {
            return new AesEncoder(entry.getPassword(), entry.getAesStrength());
        }

        @Override
        public Decoder decoder(@NonNull LittleEndianRandomAccessFile in, @NonNull LocalFileHeader localFileHeader, char[] password)
                throws IOException {
            byte[] salt = getSalt(in, localFileHeader);
            byte[] passwordVerifier = in.readBytes(2);
            return new AesDecoder(localFileHeader, password, salt, passwordVerifier);
        }

        private byte[] getSalt(@NonNull LittleEndianRandomAccessFile in, @NonNull LocalFileHeader localFileHeader) throws IOException {
            if (localFileHeader.getEncryption() != AES)
                return null;

            in.seek(localFileHeader.getOffs());
            AesStrength aesStrength = localFileHeader.getExtraField().getAesExtraDataRecord().getAesStrength();
            return in.readBytes(aesStrength.getSaltLength());
        }
    };

    public Encoder encoder(@NonNull LocalFileHeader localFileHeader, @NonNull PathZipEntry entry) {
        throw new ZipException("invalid encryption method");
    }

    public Decoder decoder(@NonNull LittleEndianRandomAccessFile in, @NonNull LocalFileHeader localFileHeader, char[] password) throws IOException {
        throw new ZipException("unsupported encryption method");
    }

    public static Encryption get(@NonNull ExtraField extraField, @NonNull GeneralPurposeFlag generalPurposeFlag) {
        if (extraField.getAesExtraDataRecord() != AesExtraDataRecord.NULL)
            return AES;
        if (generalPurposeFlag.isStrongEncryption())
            return STRONG;
        return generalPurposeFlag.isEncrypted() ? PKWARE : OFF;
    }

}


