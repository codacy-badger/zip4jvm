package com.cop.zip4j.crypto.aes;

import com.cop.zip4j.crypto.Decoder;
import com.cop.zip4j.exception.Zip4jException;
import com.cop.zip4j.exception.Zip4jIncorrectPasswordException;
import com.cop.zip4j.io.in.DataInput;
import com.cop.zip4j.model.LocalFileHeader;
import com.cop.zip4j.model.aes.AesStrength;
import com.cop.zip4j.model.entry.PathZipEntry;
import lombok.NonNull;
import org.apache.commons.lang.ArrayUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import java.io.IOException;

import static com.cop.zip4j.crypto.aes.AesEngine.MAX_SIZE;
import static com.cop.zip4j.crypto.aes.AesEngine.PASSWORD_CHECKSUM_SIZE;

/**
 * @author Oleg Cherednik
 * @since 13.08.2019
 */
public final class AesDecoder implements Decoder {

    private final int saltLength;
    private final AesEngine engine;

    @SuppressWarnings("MethodCanBeVariableArityMethod")
    public static AesDecoder create(@NonNull DataInput in, @NonNull LocalFileHeader localFileHeader, @NonNull char[] password) {
        try {
            AesStrength strength = localFileHeader.getExtraField().getAesExtraDataRecord().getStrength();
            byte[] salt = getSalt(in, localFileHeader);
            byte[] key = AesEngine.createKey(password, salt, strength);

            Cipher cipher = AesEngine.createCipher(strength.createSecretKeyForCipher(key));
            Mac mac = AesEngine.createMac(strength.createSecretKeyForMac(key));
            byte[] passwordChecksum = strength.createPasswordChecksum(key);

            checkPasswordChecksum(passwordChecksum, in, localFileHeader);
            in.seek(localFileHeader.getOffs() + strength.saltLength() + passwordChecksum.length);

            return new AesDecoder(cipher, mac, salt.length);
        } catch(Exception e) {
            throw new Zip4jException(e);
        }
    }

    private AesDecoder(Cipher cipher, Mac mac, int saltLength) {
        this.saltLength = saltLength;
        engine = new AesEngine(cipher, mac);
    }

    @Override
    public void decrypt(@NonNull byte[] buf, int offs, int len) {
        try {
            engine.updateMac(buf, offs, len);
            engine.cypherUpdate(buf, offs, len);
        } catch(Exception e) {
            throw new Zip4jException(e);
        }
    }

    @Override
    public long getCompressedSize(@NonNull PathZipEntry entry) {
        return entry.getCompressedSizeNew() - saltLength - PASSWORD_CHECKSUM_SIZE - MAX_SIZE;
    }

    @Override
    public void close(@NonNull DataInput in) throws IOException {
        checkMessageAuthenticationCode(in);
    }

    private static byte[] getSalt(DataInput in, LocalFileHeader localFileHeader) throws IOException {
        int saltLength = localFileHeader.getExtraField().getAesExtraDataRecord().getStrength().saltLength();
        in.seek(localFileHeader.getOffs());
        return in.readBytes(saltLength);
    }

    private static void checkPasswordChecksum(byte[] actual, DataInput in, LocalFileHeader localFileHeader) throws IOException {
        int saltLength = localFileHeader.getExtraField().getAesExtraDataRecord().getStrength().saltLength();
        in.seek(localFileHeader.getOffs() + saltLength);
        byte[] expected = in.readBytes(PASSWORD_CHECKSUM_SIZE);

        if (!ArrayUtils.isEquals(expected, actual))
            throw new Zip4jIncorrectPasswordException(localFileHeader.getFileName());
    }

    private void checkMessageAuthenticationCode(DataInput in) throws IOException {
        byte[] expected = in.readBytes(MAX_SIZE);
        byte[] actual = ArrayUtils.subarray(engine.getMac(), 0, MAX_SIZE);

        if (!ArrayUtils.isEquals(expected, actual))
            throw new Zip4jException("Message Authentication Code (MAC) is incorrect");
    }

}
