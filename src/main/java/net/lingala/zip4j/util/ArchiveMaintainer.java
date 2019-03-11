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

package net.lingala.zip4j.util;

import lombok.NonNull;
import net.lingala.zip4j.core.HeaderWriter;
import net.lingala.zip4j.core.readers.LocalFileHeaderReader;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.NoSplitOutputStream;
import net.lingala.zip4j.io.SplitOutputStream;
import net.lingala.zip4j.model.CentralDirectory;
import net.lingala.zip4j.model.LocalFileHeader;
import net.lingala.zip4j.model.Zip64EndCentralDirectory;
import net.lingala.zip4j.model.Zip64EndCentralDirectoryLocator;
import net.lingala.zip4j.model.ZipModel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ArchiveMaintainer {

    public void removeZipFile(ZipModel zipModel, CentralDirectory.FileHeader fileHeader, boolean runInThread) throws ZipException {
        if (fileHeader == null)
            return;

        if (runInThread) {
            Thread thread = new Thread(InternalZipConstants.THREAD_NAME) {
                public void run() {
                    try {
                        initRemoveZipFile(zipModel, fileHeader);
                    } catch(ZipException e) {
                    }
                }
            };
            thread.start();
        } else
            initRemoveZipFile(zipModel, fileHeader);
    }

    private void initRemoveZipFile(@NonNull ZipModel zipModel, @NonNull CentralDirectory.FileHeader fileHeader) throws ZipException {
        OutputStream outputStream = null;
        File zipFile = null;
        RandomAccessFile inputStream = null;
        boolean successFlag = false;
        String tmpZipFileName = null;

        try {
            int indexOfFileHeader = Zip4jUtil.getIndexOfFileHeader(zipModel, fileHeader);

            if (indexOfFileHeader < 0) {
                throw new ZipException("file header not found in zip model, cannot remove file");
            }

            if (zipModel.isSplitArchive()) {
                throw new ZipException("This is a split archive. Zip file format does not allow updating split/spanned files");
            }

            long currTime = System.currentTimeMillis();
            tmpZipFileName = zipModel.getZipFile().toString() + currTime % 1000;
            File tmpFile = new File(tmpZipFileName);

            while (tmpFile.exists()) {
                currTime = System.currentTimeMillis();
                tmpZipFileName = zipModel.getZipFile().toString() + currTime % 1000;
                tmpFile = new File(tmpZipFileName);
            }

            try {
                outputStream = new NoSplitOutputStream(Paths.get(tmpZipFileName));
            } catch(FileNotFoundException e1) {
                throw new ZipException(e1);
            }

            zipFile = zipModel.getZipFile().toFile();

            inputStream = createFileHandler(zipModel, InternalZipConstants.READ_MODE);

            LocalFileHeader localFileHeader = new LocalFileHeaderReader(new LittleEndianRandomAccessFile(inputStream), fileHeader).read();
            if (localFileHeader == null) {
                throw new ZipException("invalid local file header, cannot remove file from archive");
            }

            long offsetLocalFileHeader = fileHeader.getOffLocalHeaderRelative();

            if (fileHeader.getZip64ExtendedInfo() != null &&
                    fileHeader.getZip64ExtendedInfo().getOffsLocalHeaderRelative() != -1) {
                offsetLocalFileHeader = fileHeader.getZip64ExtendedInfo().getOffsLocalHeaderRelative();
            }

            long offsetEndOfCompressedFile = -1;

            long offsetStartCentralDir = zipModel.getEndCentralDirectory().getOffOfStartOfCentralDir();
            if (zipModel.isZip64Format()) {
                if (zipModel.getZip64EndCentralDirectory() != null) {
                    offsetStartCentralDir = zipModel.getZip64EndCentralDirectory().getOffsetStartCenDirWRTStartDiskNo();
                }
            }

            List<CentralDirectory.FileHeader> fileHeaderList = zipModel.getCentralDirectory().getFileHeaders();

            if (indexOfFileHeader == fileHeaderList.size() - 1) {
                offsetEndOfCompressedFile = offsetStartCentralDir - 1;
            } else {
                CentralDirectory.FileHeader nextFileHeader = fileHeaderList.get(indexOfFileHeader + 1);
                if (nextFileHeader != null) {
                    offsetEndOfCompressedFile = nextFileHeader.getOffLocalHeaderRelative() - 1;
                    if (nextFileHeader.getZip64ExtendedInfo() != null &&
                            nextFileHeader.getZip64ExtendedInfo().getOffsLocalHeaderRelative() != -1) {
                        offsetEndOfCompressedFile = nextFileHeader.getZip64ExtendedInfo().getOffsLocalHeaderRelative() - 1;
                    }
                }
            }

            if (offsetLocalFileHeader < 0 || offsetEndOfCompressedFile < 0) {
                throw new ZipException("invalid offset for start and end of local file, cannot remove file");
            }

            if (indexOfFileHeader == 0) {
                if (zipModel.getCentralDirectory().getFileHeaders().size() > 1) {
                    // if this is the only file and it is deleted then no need to do this
                    copyFile(inputStream, outputStream, offsetEndOfCompressedFile + 1, offsetStartCentralDir);
                }
            } else if (indexOfFileHeader == fileHeaderList.size() - 1) {
                copyFile(inputStream, outputStream, 0, offsetLocalFileHeader);
            } else {
                copyFile(inputStream, outputStream, 0, offsetLocalFileHeader);
                copyFile(inputStream, outputStream, offsetEndOfCompressedFile + 1, offsetStartCentralDir);
            }

            zipModel.getEndCentralDirectory().setOffOfStartOfCentralDir(((SplitOutputStream)outputStream).getFilePointer());
            zipModel.getEndCentralDirectory().setTotNoOfEntriesInCentralDir(
                    zipModel.getEndCentralDirectory().getTotNoOfEntriesInCentralDir() - 1);
            zipModel.getEndCentralDirectory().setTotalNumberOfEntriesInCentralDirOnThisDisk(
                    zipModel.getEndCentralDirectory().getTotalNumberOfEntriesInCentralDirOnThisDisk() - 1);

            zipModel.getCentralDirectory().getFileHeaders().remove(indexOfFileHeader);

            for (int i = indexOfFileHeader; i < zipModel.getCentralDirectory().getFileHeaders().size(); i++) {
                long offsetLocalHdr = zipModel.getCentralDirectory().getFileHeaders().get(i).getOffLocalHeaderRelative();
                if (zipModel.getCentralDirectory().getFileHeaders().get(i).getZip64ExtendedInfo() != null &&
                        zipModel.getCentralDirectory().getFileHeaders().get(i).getZip64ExtendedInfo().getOffsLocalHeaderRelative() != -1) {
                    offsetLocalHdr = zipModel.getCentralDirectory().getFileHeaders().get(i).getZip64ExtendedInfo().getOffsLocalHeaderRelative();
                }

                zipModel.getCentralDirectory().getFileHeaders().get(i).setOffLocalHeaderRelative(
                        offsetLocalHdr - (offsetEndOfCompressedFile - offsetLocalFileHeader) - 1);
            }

            HeaderWriter headerWriter = new HeaderWriter();
            headerWriter.finalizeZipFile(zipModel, outputStream);

            successFlag = true;
        } catch(ZipException e) {
            throw e;
        } catch(Exception e) {
            throw new ZipException(e);
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
                if (outputStream != null)
                    outputStream.close();
            } catch(IOException e) {
                throw new ZipException("cannot close input stream or output stream when trying to delete a file from zip file");
            }

            if (successFlag) {
                restoreFileName(zipFile, tmpZipFileName);
            } else {
                File newZipFile = new File(tmpZipFileName);
                newZipFile.delete();
            }
        }
    }

    private void restoreFileName(File zipFile, String tmpZipFileName) throws ZipException {
        if (zipFile.delete()) {
            File newZipFile = new File(tmpZipFileName);
            if (!newZipFile.renameTo(zipFile)) {
                throw new ZipException("cannot rename modified zip file");
            }
        } else {
            throw new ZipException("cannot delete old zip file");
        }
    }

    private void copyFile(RandomAccessFile inputStream, OutputStream outputStream, long start, long end) throws ZipException {

        if (inputStream == null || outputStream == null) {
            throw new ZipException("input or output stream is null, cannot copy file");
        }

        if (start < 0) {
            throw new ZipException("starting offset is negative, cannot copy file");
        }

        if (end < 0) {
            throw new ZipException("end offset is negative, cannot copy file");
        }

        if (start > end) {
            throw new ZipException("start offset is greater than end offset, cannot copy file");
        }

        if (start == end) {
            return;
        }

        try {
            inputStream.seek(start);

            int readLen = -2;
            byte[] buff;
            long bytesRead = 0;
            long bytesToRead = end - start;

            if ((end - start) < InternalZipConstants.BUFF_SIZE) {
                buff = new byte[(int)(end - start)];
            } else {
                buff = new byte[InternalZipConstants.BUFF_SIZE];
            }

            while ((readLen = inputStream.read(buff)) != -1) {
                outputStream.write(buff, 0, readLen);

                bytesRead += readLen;

                if (bytesRead == bytesToRead) {
                    break;
                } else if (bytesRead + buff.length > bytesToRead) {
                    buff = new byte[(int)(bytesToRead - bytesRead)];
                }
            }

        } catch(IOException e) {
            throw new ZipException(e);
        } catch(Exception e) {
            throw new ZipException(e);
        }
    }

    private static RandomAccessFile createFileHandler(ZipModel zipModel, String mode) throws ZipException {
        if (zipModel == null || zipModel.getZipFile() == null)
            throw new ZipException("input parameter is null in getFilePointer, cannot create file handler to remove file");

        try {
            return new RandomAccessFile(zipModel.getZipFile().toFile(), mode);
        } catch(FileNotFoundException e) {
            throw new ZipException(e);
        }
    }

    /**
     * Merges split Zip files into a single Zip file
     *
     * @param zipModel
     * @throws ZipException
     */
    public void mergeSplitZipFiles(final ZipModel zipModel, final File outputZipFile, boolean runInThread) throws ZipException {
        if (runInThread) {
            Thread thread = new Thread(InternalZipConstants.THREAD_NAME) {
                public void run() {
                    try {
                        initMergeSplitZipFile(zipModel, outputZipFile);
                    } catch(ZipException e) {
                    }
                }
            };
            thread.start();
        } else {
            initMergeSplitZipFile(zipModel, outputZipFile);
        }
    }

    private void initMergeSplitZipFile(ZipModel zipModel, File outputZipFile) throws ZipException {
        if (zipModel == null) {
            ZipException e = new ZipException("one of the input parameters is null, cannot merge split zip file");
            throw e;
        }

        if (!zipModel.isSplitArchive()) {
            ZipException e = new ZipException("archive not a split zip file");
            throw e;
        }

        OutputStream outputStream = null;
        RandomAccessFile inputStream = null;
        ArrayList fileSizeList = new ArrayList();
        long totBytesWritten = 0;
        boolean splitSigRemoved = false;
        try {

            int totNoOfSplitFiles = zipModel.getEndCentralDirectory().getNoOfDisk();

            if (totNoOfSplitFiles <= 0) {
                throw new ZipException("corrupt zip model, archive not a split zip file");
            }

            outputStream = prepareOutputStreamForMerge(outputZipFile);
            for (int i = 0; i <= totNoOfSplitFiles; i++) {
                inputStream = createSplitZipFileHandler(zipModel, i);

                int start = 0;
                Long end = new Long(inputStream.length());

                if (i == 0) {
                    if (zipModel.getCentralDirectory() != null &&
                            zipModel.getCentralDirectory().getFileHeaders() != null &&
                            zipModel.getCentralDirectory().getFileHeaders().size() > 0) {
                        byte[] buff = new byte[4];
                        inputStream.seek(0);
                        inputStream.read(buff);
                        if (Raw.readIntLittleEndian(buff, 0) == InternalZipConstants.SPLITSIG) {
                            start = 4;
                            splitSigRemoved = true;
                        }
                    }
                }

                if (i == totNoOfSplitFiles) {
                    end = new Long(zipModel.getEndCentralDirectory().getOffOfStartOfCentralDir());
                }

                copyFile(inputStream, outputStream, start, end.longValue());
                totBytesWritten += (end.longValue() - start);

                fileSizeList.add(end);

                try {
                    inputStream.close();
                } catch(IOException e) {
                    //ignore
                }
            }

            ZipModel newZipModel = (ZipModel)zipModel.clone();
            newZipModel.getEndCentralDirectory().setOffOfStartOfCentralDir(totBytesWritten);

            updateSplitZipModel(newZipModel, fileSizeList, splitSigRemoved);

            HeaderWriter headerWriter = new HeaderWriter();
            headerWriter.finalizeZipFileWithoutValidations(newZipModel, outputStream);

        } catch(Exception e) {
            throw new ZipException(e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch(IOException e) {
                    //ignore
                }
            }

            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch(IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Creates an input stream for the split part of the zip file
     *
     * @return Zip4jInputStream
     * @throws ZipException
     */

    private RandomAccessFile createSplitZipFileHandler(ZipModel zipModel, int partNumber) throws ZipException {
        if (zipModel == null) {
            throw new ZipException("zip model is null, cannot create split file handler");
        }

        if (partNumber < 0) {
            throw new ZipException("invlaid part number, cannot create split file handler");
        }

        try {
            String curZipFile = zipModel.getZipFile().toString();
            String partFile = null;

            if (partNumber == zipModel.getEndCentralDirectory().getNoOfDisk())
                partFile = zipModel.getZipFile().toString();
            else
                partFile = ZipModel.getSplitFilePath(zipModel.getZipFile(), partNumber + 1).toString();

            File tmpFile = new File(partFile);

            if (!tmpFile.exists()) {
                throw new ZipException("split file does not exist: " + partFile);
            }

            return new RandomAccessFile(tmpFile, InternalZipConstants.READ_MODE);
        } catch(FileNotFoundException e) {
            throw new ZipException(e);
        } catch(Exception e) {
            throw new ZipException(e);
        }

    }

    private OutputStream prepareOutputStreamForMerge(File outFile) throws ZipException {
        if (outFile == null) {
            throw new ZipException("outFile is null, cannot create outputstream");
        }

        try {
            return new FileOutputStream(outFile);
        } catch(FileNotFoundException e) {
            throw new ZipException(e);
        } catch(Exception e) {
            throw new ZipException(e);
        }
    }

    private void updateSplitZipModel(ZipModel zipModel, ArrayList fileSizeList, boolean splitSigRemoved) throws ZipException {
        if (zipModel == null)
            throw new ZipException("zip model is null, cannot update split zip model");

        zipModel.setNoSplitArchive();
        updateSplitFileHeader(zipModel, fileSizeList, splitSigRemoved);
        updateSplitEndCentralDirectory(zipModel);
        if (zipModel.isZip64Format()) {
            updateSplitZip64EndCentralDirLocator(zipModel, fileSizeList);
            updateSplitZip64EndCentralDirRec(zipModel, fileSizeList);
        }
    }

    private void updateSplitFileHeader(ZipModel zipModel, ArrayList fileSizeList, boolean splitSigRemoved) throws ZipException {
        try {

            if (zipModel.getCentralDirectory() == null) {
                throw new ZipException("corrupt zip model - getCentralDirectory, cannot update split zip model");
            }

            int fileHeaderCount = zipModel.getCentralDirectory().getFileHeaders().size();
            int splitSigOverhead = 0;
            if (splitSigRemoved)
                splitSigOverhead = 4;

            for (int i = 0; i < fileHeaderCount; i++) {
                long offsetLHToAdd = 0;

                for (int j = 0; j < ((CentralDirectory.FileHeader)zipModel.getCentralDirectory().getFileHeaders().get(i)).getDiskNumberStart(); j++) {
                    offsetLHToAdd += ((Long)fileSizeList.get(j)).longValue();
                }
                zipModel.getCentralDirectory().getFileHeaders().get(i).setOffLocalHeaderRelative(
                        zipModel.getCentralDirectory().getFileHeaders().get(i).getOffLocalHeaderRelative() +
                                offsetLHToAdd - splitSigOverhead);
                zipModel.getCentralDirectory().getFileHeaders().get(i).setDiskNumberStart(0);
            }

        } catch(ZipException e) {
            throw e;
        } catch(Exception e) {
            throw new ZipException(e);
        }
    }

    private void updateSplitEndCentralDirectory(ZipModel zipModel) throws ZipException {
        try {
            if (zipModel == null) {
                throw new ZipException("zip model is null - cannot update end of central directory for split zip model");
            }

            if (zipModel.getCentralDirectory() == null) {
                throw new ZipException("corrupt zip model - getCentralDirectory, cannot update split zip model");
            }

            zipModel.getEndCentralDirectory().setNoOfDisk(0);
            zipModel.getEndCentralDirectory().setNoOfDiskStartCentralDir(0);
            zipModel.getEndCentralDirectory().setTotNoOfEntriesInCentralDir(
                    zipModel.getCentralDirectory().getFileHeaders().size());
            zipModel.getEndCentralDirectory().setTotalNumberOfEntriesInCentralDirOnThisDisk(
                    zipModel.getCentralDirectory().getFileHeaders().size());

        } catch(ZipException e) {
            throw e;
        } catch(Exception e) {
            throw new ZipException(e);
        }
    }

    private void updateSplitZip64EndCentralDirLocator(ZipModel zipModel, ArrayList fileSizeList) throws ZipException {
        if (zipModel == null) {
            throw new ZipException("zip model is null, cannot update split Zip64 end of central directory locator");
        }

        if (zipModel.getZip64EndCentralDirectoryLocator() == null) {
            return;
        }

        zipModel.getZip64EndCentralDirectoryLocator().setNoOfDiskStartOfZip64EndOfCentralDirRec(0);
        long offsetZip64EndCentralDirRec = 0;

        for (int i = 0; i < fileSizeList.size(); i++) {
            offsetZip64EndCentralDirRec += ((Long)fileSizeList.get(i)).longValue();
        }
        zipModel.getZip64EndCentralDirectoryLocator().setOffsetZip64EndOfCentralDirRec(
                ((Zip64EndCentralDirectoryLocator)zipModel.getZip64EndCentralDirectoryLocator()).getOffsetZip64EndOfCentralDirRec() +
                        offsetZip64EndCentralDirRec);
        zipModel.getZip64EndCentralDirectoryLocator().setTotNumberOfDiscs(1);
    }

    private void updateSplitZip64EndCentralDirRec(ZipModel zipModel, ArrayList fileSizeList) throws ZipException {
        if (zipModel == null) {
            throw new ZipException("zip model is null, cannot update split Zip64 end of central directory record");
        }

        if (zipModel.getZip64EndCentralDirectory() == null) {
            return;
        }

        zipModel.getZip64EndCentralDirectory().setNoOfThisDisk(0);
        zipModel.getZip64EndCentralDirectory().setNoOfThisDiskStartOfCentralDir(0);
        zipModel.getZip64EndCentralDirectory().setTotNoOfEntriesInCentralDirOnThisDisk(
                zipModel.getEndCentralDirectory().getTotNoOfEntriesInCentralDir());

        long offsetStartCenDirWRTStartDiskNo = 0;

        for (int i = 0; i < fileSizeList.size(); i++) {
            offsetStartCenDirWRTStartDiskNo += ((Long)fileSizeList.get(i)).longValue();
        }

        zipModel.getZip64EndCentralDirectory().setOffsetStartCenDirWRTStartDiskNo(
                ((Zip64EndCentralDirectory)zipModel.getZip64EndCentralDirectory()).getOffsetStartCenDirWRTStartDiskNo() +
                        offsetStartCenDirWRTStartDiskNo);
    }

    public void setComment(ZipModel zipModel, String comment) throws ZipException {
        if (comment == null) {
            throw new ZipException("comment is null, cannot update Zip file with comment");
        }

        if (zipModel == null) {
            throw new ZipException("zipModel is null, cannot update Zip file with comment");
        }

        String encodedComment = comment;
        byte[] commentBytes = comment.getBytes();
        int commentLength = comment.length();

        if (Zip4jUtil.isSupportedCharset(InternalZipConstants.CHARSET_COMMENTS_DEFAULT)) {
            try {
                encodedComment = new String(comment.getBytes(InternalZipConstants.CHARSET_COMMENTS_DEFAULT),
                        InternalZipConstants.CHARSET_COMMENTS_DEFAULT);
                commentBytes = encodedComment.getBytes(InternalZipConstants.CHARSET_COMMENTS_DEFAULT);
                commentLength = encodedComment.length();
            } catch(UnsupportedEncodingException e) {
                encodedComment = comment;
                commentBytes = comment.getBytes();
                commentLength = comment.length();
            }
        }

        if (commentLength > InternalZipConstants.MAX_ALLOWED_ZIP_COMMENT_LENGTH) {
            throw new ZipException("comment length exceeds maximum length");
        }

        zipModel.getEndCentralDirectory().setComment(encodedComment);

        try (SplitOutputStream out = new NoSplitOutputStream(zipModel.getZipFile())) {
            HeaderWriter headerWriter = new HeaderWriter();

            if (zipModel.isZip64Format()) {
                out.seek(zipModel.getZip64EndCentralDirectory().getOffsetStartCenDirWRTStartDiskNo());
            } else {
                out.seek(zipModel.getEndCentralDirectory().getOffOfStartOfCentralDir());
            }

            headerWriter.finalizeZipFileWithoutValidations(zipModel, out);
        } catch(Exception e) {
            throw new ZipException(e);
        }
    }
}
