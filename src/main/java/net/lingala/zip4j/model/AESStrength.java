package net.lingala.zip4j.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author Oleg Cherednik
 * @since 10.03.2019
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public enum AESStrength {

    NONE((byte)0, 0),
    STRENGTH_128((byte)0x01, 8),
    STRENGTH_192((byte)0x02, 12),
    STRENGTH_256((byte)0x03, 16);

    private final byte value;
    private final int saltLength;

    public static AESStrength parseByte(byte value) {
        for (AESStrength strength : values())
            if (strength.value == value)
                return strength;
        throw new EnumConstantNotPresentException(AESStrength.class, "value=" + value);
    }

}
