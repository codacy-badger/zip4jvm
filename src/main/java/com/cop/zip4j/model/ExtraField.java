package com.cop.zip4j.model;

import com.cop.zip4j.model.aes.AesExtraDataRecord;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * @author Oleg Cherednik
 * @since 14.04.2019
 */
@Getter
@Setter
public class ExtraField {

    public static final int NO_DATA = -1;
    @SuppressWarnings("StaticInitializerReferencesSubClass")
    public static final ExtraField NULL = new NullExtraField();

    @NonNull
    private Zip64.ExtendedInfo extendedInfo = Zip64.ExtendedInfo.NULL;
    @NonNull
    private AesExtraDataRecord aesExtraDataRecord = AesExtraDataRecord.NULL;

    public boolean isEmpty() {
        return extendedInfo == null && aesExtraDataRecord == null;
    }

    public int getSize() {
        return extendedInfo.getBlockSize() + aesExtraDataRecord.getBlockSize();
    }

}
