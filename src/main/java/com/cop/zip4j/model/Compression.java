package com.cop.zip4j.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author Oleg Cherednik
 * @since 03.08.2019
 */
@Getter
@RequiredArgsConstructor
public enum Compression {
    STORE(CompressionMethod.STORE),
    DEFLATE(CompressionMethod.DEFLATE);

    private final CompressionMethod method;

    public static Compression parseCompressionMethod(CompressionMethod method) {
        for (Compression compression : values())
            if (compression.method == method)
                return compression;
        throw new EnumConstantNotPresentException(Compression.class, "method=" + method);
    }

}
