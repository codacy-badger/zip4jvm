package com.cop.zip4j.io.out;

import com.cop.zip4j.io.writers.ZipModelWriter;
import com.cop.zip4j.model.ZipModel;
import lombok.NonNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

/**
 * @author Oleg Cherednik
 * @since 08.03.2019
 */
public class SingleZipOutputStream extends BaseDataOutput {

    @NonNull
    public static SingleZipOutputStream create(@NonNull ZipModel zipModel) throws IOException {
        return new SingleZipOutputStream(zipModel.getZipFile(), zipModel);
    }

    @NonNull
    public static SingleZipOutputStream create(@NonNull Path zipFile, @NonNull ZipModel zipModel) throws FileNotFoundException {
        return new SingleZipOutputStream(zipFile, zipModel);
    }

    private SingleZipOutputStream(Path zipFile, ZipModel zipModel) throws FileNotFoundException {
        super(zipModel);
        createFile(zipFile);
    }

    @Override
    public int getDisk() {
        return 0;
    }

    @Override
    public void close() throws IOException {
        new ZipModelWriter(zipModel).write(this);
        super.close();
    }

}
