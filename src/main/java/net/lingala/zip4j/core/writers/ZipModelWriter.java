/*
 * Copyright 2010 Srikanth Reddy Lingala
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lingala.zip4j.core.writers;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.lingala.zip4j.io.OutputStreamDecorator;
import net.lingala.zip4j.model.EndCentralDirectory;
import net.lingala.zip4j.model.Zip64EndCentralDirectoryLocator;
import net.lingala.zip4j.model.ZipModel;

import java.io.IOException;

@RequiredArgsConstructor
public final class ZipModelWriter {

    public static final int ZIP64_EXTRA_BUF = 50;
    private static final String MARK = "header";

    @NonNull
    private final ZipModel zipModel;

    // TODO do we really need validate flag?
    public void finalizeZipFile(@NonNull OutputStreamDecorator out, boolean validate) throws IOException {
        if (validate)
            processHeaderData(out);

        EndCentralDirectory endCentralDirectory = zipModel.getEndCentralDirectory();
        out.mark(MARK);
        new CentralDirectoryWriter(zipModel.getCentralDirectory(), zipModel).write(out);
        endCentralDirectory.setSize((int)out.getWrittenBytesAmount(MARK));

        zipModel.updateZip64();

        if (zipModel.isZip64() && validate) {
            Zip64EndCentralDirectoryLocator locator = zipModel.getZip64().getEndCentralDirectoryLocator();
            locator.setNoOfDiskStartOfZip64EndOfCentralDirRec(out.getCurrSplitFileCounter());
            locator.setTotNumberOfDiscs(out.getCurrSplitFileCounter() + 1);
        }

        new Zip64EndCentralDirectoryWriter(zipModel.getZip64EndCentralDirectory()).write(out);
        new Zip64EndCentralDirectoryLocatorWriter(zipModel.getZip64EndCentralDirectoryLocator()).write(out);
        new EndCentralDirectoryWriter(endCentralDirectory, zipModel.getCharset()).write(out);
    }

    private void processHeaderData(OutputStreamDecorator out) throws IOException {
        EndCentralDirectory endCentralDirectory = zipModel.getEndCentralDirectory();

        endCentralDirectory.setOffs(out.getFilePointer());

        if (zipModel.isZip64()) {
            zipModel.getZip64EndCentralDirectoryLocator().setNoOfDiskStartOfZip64EndOfCentralDirRec(out.getCurrSplitFileCounter());
            zipModel.getZip64EndCentralDirectoryLocator().setTotNumberOfDiscs(out.getCurrSplitFileCounter() + 1);
        }

        endCentralDirectory.setDiskNumber(out.getCurrSplitFileCounter());
        endCentralDirectory.setStartDiskNumber(out.getCurrSplitFileCounter());
    }

}