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

package net.lingala.zip4j.core;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.exception.ZipExceptionConstants;
import net.lingala.zip4j.model.AESExtraDataRecord;
import net.lingala.zip4j.model.CentralDirectory;
import net.lingala.zip4j.model.DigitalSignature;
import net.lingala.zip4j.model.EndCentralDirectory;
import net.lingala.zip4j.model.ExtraDataRecord;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.LocalFileHeader;
import net.lingala.zip4j.model.Zip64EndCentralDirectoryLocator;
import net.lingala.zip4j.model.Zip64ExtendedInfo;
import net.lingala.zip4j.model.ZipModel;
import net.lingala.zip4j.util.InternalZipConstants;
import net.lingala.zip4j.util.LittleEndianRandomAccessFile;
import net.lingala.zip4j.util.Raw;
import net.lingala.zip4j.util.Zip4jConstants;
import net.lingala.zip4j.util.Zip4jUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public final class HeaderReader {

    private final RandomAccessFile zip4jRaf;

    /**
     * Reads all the header information for the zip file. File names are read with
     * input charset name. If this parameter is null, default system charset is used.
     * <br><br><b>Note:</b> This method does not read local file header information
     *
     * @return {@link ZipModel}
     * @throws ZipException
     */
    public ZipModel readAllHeaders(@NonNull Charset charset) throws ZipException, IOException {
        LittleEndianRandomAccessFile in = new LittleEndianRandomAccessFile(zip4jRaf);

        EndCentralDirectoryReader endCentralDirectoryReader = new EndCentralDirectoryReader(in);
        EndCentralDirectory dir = endCentralDirectoryReader.read();
        Zip64EndCentralDirectoryLocator locator = new Zip64EndCentralDirectoryLocatorReader(in, endCentralDirectoryReader.getOffs()).read();

        ZipModel zipModel = new ZipModel();
        zipModel.setCharset(charset);
        zipModel.setEndCentralDirectory(dir);

        if (locator != null) {
            zipModel.setZip64EndCentralDirectoryLocator(locator);
            zipModel.setZip64EndCentralDirectory(new Zip64EndCentralDirectoryReader(in, locator.getOffsetZip64EndOfCentralDirRec()).read());
        }

        zipModel.setCentralDirectory(readCentralDirectory(zipModel));

        return zipModel;
    }

    /**
     * Reads central directory information for the zip file
     *
     * @return {@link CentralDirectory}
     * @throws ZipException
     */
    private CentralDirectory readCentralDirectory(ZipModel zipModel) throws ZipException {

        if (zip4jRaf == null) {
            throw new ZipException("random access file was null", ZipExceptionConstants.randomAccessFileNull);
        }

        if (zipModel.getEndCentralDirectory() == null) {
            throw new ZipException("EndCentralRecord was null, maybe a corrupt zip file");
        }

        try {
            CentralDirectory centralDirectory = new CentralDirectory();
            List<FileHeader> fileHeaderList = new ArrayList<>();

            EndCentralDirectory endCentralDirectory = zipModel.getEndCentralDirectory();
            long offSetStartCentralDir = endCentralDirectory.getOffsetOfStartOfCentralDir();
            int centralDirEntryCount = endCentralDirectory.getTotNoOfEntriesInCentralDir();

            if (zipModel.isZip64Format()) {
                offSetStartCentralDir = zipModel.getZip64EndCentralDirectory().getOffsetStartCenDirWRTStartDiskNo();
                centralDirEntryCount = (int)zipModel.getZip64EndCentralDirectory().getTotNoOfEntriesInCentralDir();
            }

            zip4jRaf.seek(offSetStartCentralDir);

            byte[] intBuff = new byte[4];
            byte[] shortBuff = new byte[2];
            byte[] longBuff = new byte[8];

            for (int i = 0; i < centralDirEntryCount; i++) {
                FileHeader fileHeader = new FileHeader();

                //FileHeader Signature
                readIntoBuff(zip4jRaf, intBuff);
                int signature = Raw.readIntLittleEndian(intBuff, 0);
                if (signature != InternalZipConstants.CENSIG) {
                    throw new ZipException("Expected central directory entry not found (#" + (i + 1) + ")");
                }
                fileHeader.setSignature(signature);

                //version made by
                readIntoBuff(zip4jRaf, shortBuff);
                fileHeader.setVersionMadeBy(Raw.readShortLittleEndian(shortBuff, 0));

                //version needed to extract
                readIntoBuff(zip4jRaf, shortBuff);
                fileHeader.setVersionNeededToExtract(Raw.readShortLittleEndian(shortBuff, 0));

                //general purpose bit flag
                readIntoBuff(zip4jRaf, shortBuff);
                fileHeader.setFileNameUTF8Encoded((Raw.readShortLittleEndian(shortBuff, 0) & InternalZipConstants.UFT8_NAMES_FLAG) != 0);
                int firstByte = shortBuff[0];
                int result = firstByte & 1;
                if (result != 0) {
                    fileHeader.setEncrypted(true);
                }
                fileHeader.setGeneralPurposeFlag((byte[])shortBuff.clone());

                //Check if data descriptor exists for local file header
                fileHeader.setDataDescriptorExists(firstByte >> 3 == 1);

                //compression method
                readIntoBuff(zip4jRaf, shortBuff);
                fileHeader.setCompressionMethod(Raw.readShortLittleEndian(shortBuff, 0));

                //last mod file time
                readIntoBuff(zip4jRaf, intBuff);
                fileHeader.setLastModFileTime(Raw.readIntLittleEndian(intBuff, 0));

                //crc-32
                readIntoBuff(zip4jRaf, intBuff);
                fileHeader.setCrc32(Raw.readIntLittleEndian(intBuff, 0));
                fileHeader.setCrcBuff((byte[])intBuff.clone());

                //compressed size
                readIntoBuff(zip4jRaf, intBuff);
                longBuff = getLongByteFromIntByte(intBuff);
                fileHeader.setCompressedSize(Raw.readLongLittleEndian(longBuff, 0));

                //uncompressed size
                readIntoBuff(zip4jRaf, intBuff);
                longBuff = getLongByteFromIntByte(intBuff);
                fileHeader.setUncompressedSize(Raw.readLongLittleEndian(longBuff, 0));

                //file name length
                readIntoBuff(zip4jRaf, shortBuff);
                int fileNameLength = Raw.readShortLittleEndian(shortBuff, 0);
                fileHeader.setFileNameLength(fileNameLength);

                //extra field length
                readIntoBuff(zip4jRaf, shortBuff);
                int extraFieldLength = Raw.readShortLittleEndian(shortBuff, 0);
                fileHeader.setExtraFieldLength(extraFieldLength);

                //file comment length
                readIntoBuff(zip4jRaf, shortBuff);
                int fileCommentLength = Raw.readShortLittleEndian(shortBuff, 0);
                fileHeader.setFileComment(new String(shortBuff));

                //disk number start
                readIntoBuff(zip4jRaf, shortBuff);
                fileHeader.setDiskNumberStart(Raw.readShortLittleEndian(shortBuff, 0));

                //internal file attributes
                readIntoBuff(zip4jRaf, shortBuff);
                fileHeader.setInternalFileAttr((byte[])shortBuff.clone());

                //external file attributes
                readIntoBuff(zip4jRaf, intBuff);
                fileHeader.setExternalFileAttr((byte[])intBuff.clone());

                //relative offset of local header
                readIntoBuff(zip4jRaf, intBuff);
                //Commented on 26.08.2010. Revert back if any issues
                //fileHeader.setOffsetLocalHeader((Raw.readIntLittleEndian(intBuff, 0) & 0xFFFFFFFFL) + zip4jRaf.getStart());
                longBuff = getLongByteFromIntByte(intBuff);
                fileHeader.setOffsetLocalHeader((Raw.readLongLittleEndian(longBuff, 0) & 0xFFFFFFFFL));

                if (fileNameLength > 0) {
                    byte[] fileNameBuf = new byte[fileNameLength];
                    readIntoBuff(zip4jRaf, fileNameBuf);
                    // Modified after user reported an issue http://www.lingala.net/zip4j/forum/index.php?topic=2.0
//					String fileName = new String(fileNameBuf, "Cp850");
                    // Modified as per http://www.lingala.net/zip4j/forum/index.php?topic=41.0
//					String fileName = Zip4jUtil.getCp850EncodedString(fileNameBuf);

                    String fileName = new String(fileNameBuf, zipModel.getCharset());

                    if (StringUtils.isBlank(fileName))
                        throw new ZipException("fileName is null when reading central directory");

                    if (fileName.indexOf(":" + System.getProperty("file.separator")) >= 0) {
                        fileName = fileName.substring(fileName.indexOf(":" + System.getProperty("file.separator")) + 2);
                    }

                    fileHeader.setFileName(fileName);
                    fileHeader.setDirectory(fileName.endsWith("/") || fileName.endsWith("\\"));

                } else {
                    fileHeader.setFileName(null);
                }

                //Extra field
                readAndSaveExtraDataRecord(fileHeader);

                //Read Zip64 Extra data records if exists
                readAndSaveZip64ExtendedInfo(fileHeader);

                //Read AES Extra Data record if exists
                readAndSaveAESExtraDataRecord(fileHeader);

//				if (fileHeader.isEncrypted()) {
//
//					if (fileHeader.getEncryptionMethod() == ZipConstants.ENC_METHOD_AES) {
//						//Do nothing
//					} else {
//						if ((firstByte & 64) == 64) {
//							//hardcoded for now
//							fileHeader.setEncryptionMethod(1);
//						} else {
//							fileHeader.setEncryptionMethod(ZipConstants.ENC_METHOD_STANDARD);
//							fileHeader.setCompressedSize(fileHeader.getCompressedSize()
//									- ZipConstants.STD_DEC_HDR_SIZE);
//						}
//					}
//
//				}

                if (fileCommentLength > 0) {
                    byte[] fileCommentBuf = new byte[fileCommentLength];
                    readIntoBuff(zip4jRaf, fileCommentBuf);
                    fileHeader.setFileComment(new String(fileCommentBuf));
                }

                fileHeaderList.add(fileHeader);
            }
            centralDirectory.setFileHeaders(fileHeaderList);

            //Digital Signature
            DigitalSignature digitalSignature = new DigitalSignature();
            readIntoBuff(zip4jRaf, intBuff);
            int signature = Raw.readIntLittleEndian(intBuff, 0);
            if (signature != InternalZipConstants.DIGSIG) {
                return centralDirectory;
            }

            digitalSignature.setHeaderSignature(signature);

            //size of data
            readIntoBuff(zip4jRaf, shortBuff);
            int sizeOfData = Raw.readShortLittleEndian(shortBuff, 0);
            digitalSignature.setSizeOfData(sizeOfData);

            if (sizeOfData > 0) {
                byte[] sigDataBuf = new byte[sizeOfData];
                readIntoBuff(zip4jRaf, sigDataBuf);
                digitalSignature.setSignatureData(new String(sigDataBuf));
            }

            return centralDirectory;
        } catch(IOException e) {
            throw new ZipException(e);
        }
    }

    /**
     * Reads extra data record and saves it in the {@link FileHeader}
     *
     * @param fileHeader
     * @throws ZipException
     */
    private void readAndSaveExtraDataRecord(FileHeader fileHeader) throws ZipException {

        if (zip4jRaf == null) {
            throw new ZipException("invalid file handler when trying to read extra data record");
        }

        if (fileHeader == null) {
            throw new ZipException("file header is null");
        }

        int extraFieldLength = fileHeader.getExtraFieldLength();
        if (extraFieldLength <= 0) {
            return;
        }

        fileHeader.setExtraDataRecords(readExtraDataRecords(extraFieldLength));

    }

    /**
     * Reads extra data record and saves it in the {@link LocalFileHeader}
     *
     * @param localFileHeader
     * @throws ZipException
     */
    private void readAndSaveExtraDataRecord(LocalFileHeader localFileHeader) throws ZipException {

        if (zip4jRaf == null) {
            throw new ZipException("invalid file handler when trying to read extra data record");
        }

        if (localFileHeader == null) {
            throw new ZipException("file header is null");
        }

        int extraFieldLength = localFileHeader.getExtraFieldLength();
        if (extraFieldLength <= 0) {
            return;
        }

        localFileHeader.setExtraDataRecords(readExtraDataRecords(extraFieldLength));

    }

    /**
     * Reads extra data records
     *
     * @param extraFieldLength
     * @return ArrayList of {@link ExtraDataRecord}
     * @throws ZipException
     */
    private ArrayList readExtraDataRecords(int extraFieldLength) throws ZipException {

        if (extraFieldLength <= 0) {
            return null;
        }

        try {
            byte[] extraFieldBuf = new byte[extraFieldLength];
            zip4jRaf.read(extraFieldBuf);

            int counter = 0;
            ArrayList extraDataList = new ArrayList();
            while (counter < extraFieldLength) {
                ExtraDataRecord extraDataRecord = new ExtraDataRecord();
                int header = Raw.readShortLittleEndian(extraFieldBuf, counter);
                extraDataRecord.setHeader(header);
                counter = counter + 2;
                int sizeOfRec = Raw.readShortLittleEndian(extraFieldBuf, counter);

                if ((2 + sizeOfRec) > extraFieldLength) {
                    sizeOfRec = Raw.readShortBigEndian(extraFieldBuf, counter);
                    if ((2 + sizeOfRec) > extraFieldLength) {
                        //If this is the case, then extra data record is corrupt
                        //skip reading any further extra data records
                        break;
                    }
                }

                extraDataRecord.setSizeOfData(sizeOfRec);
                counter = counter + 2;

                if (sizeOfRec > 0) {
                    byte[] data = new byte[sizeOfRec];
                    System.arraycopy(extraFieldBuf, counter, data, 0, sizeOfRec);
                    extraDataRecord.setData(data);
                }
                counter = counter + sizeOfRec;
                extraDataList.add(extraDataRecord);
            }
            if (extraDataList.size() > 0) {
                return extraDataList;
            } else {
                return null;
            }
        } catch(IOException e) {
            throw new ZipException(e);
        }
    }

    /**
     * Reads Zip64 Extended info and saves it in the {@link FileHeader}
     *
     * @param fileHeader
     * @throws ZipException
     */
    private void readAndSaveZip64ExtendedInfo(FileHeader fileHeader) throws ZipException {
        if (fileHeader == null) {
            throw new ZipException("file header is null in reading Zip64 Extended Info");
        }

        if (fileHeader.getExtraDataRecords() == null || fileHeader.getExtraDataRecords().size() <= 0) {
            return;
        }

        Zip64ExtendedInfo zip64ExtendedInfo = readZip64ExtendedInfo(
                fileHeader.getExtraDataRecords(),
                fileHeader.getUncompressedSize(),
                fileHeader.getCompressedSize(),
                fileHeader.getOffsetLocalHeader(),
                fileHeader.getDiskNumberStart());

        if (zip64ExtendedInfo != null) {
            fileHeader.setZip64ExtendedInfo(zip64ExtendedInfo);
            if (zip64ExtendedInfo.getUnCompressedSize() != -1)
                fileHeader.setUncompressedSize(zip64ExtendedInfo.getUnCompressedSize());

            if (zip64ExtendedInfo.getCompressedSize() != -1)
                fileHeader.setCompressedSize(zip64ExtendedInfo.getCompressedSize());

            if (zip64ExtendedInfo.getOffsetLocalHeader() != -1)
                fileHeader.setOffsetLocalHeader(zip64ExtendedInfo.getOffsetLocalHeader());

            if (zip64ExtendedInfo.getDiskNumberStart() != -1)
                fileHeader.setDiskNumberStart(zip64ExtendedInfo.getDiskNumberStart());
        }
    }

    /**
     * Reads Zip64 Extended Info and saves it in the {@link LocalFileHeader}
     *
     * @param localFileHeader
     * @throws ZipException
     */
    private void readAndSaveZip64ExtendedInfo(LocalFileHeader localFileHeader) throws ZipException {
        if (localFileHeader == null) {
            throw new ZipException("file header is null in reading Zip64 Extended Info");
        }

        if (CollectionUtils.isEmpty(localFileHeader.getExtraDataRecords()))
            return;

        Zip64ExtendedInfo zip64ExtendedInfo = readZip64ExtendedInfo(
                localFileHeader.getExtraDataRecords(),
                localFileHeader.getUncompressedSize(),
                localFileHeader.getCompressedSize(),
                -1, -1);

        if (zip64ExtendedInfo != null) {
            localFileHeader.setZip64ExtendedInfo(zip64ExtendedInfo);

            if (zip64ExtendedInfo.getUnCompressedSize() != -1)
                localFileHeader.setUncompressedSize(zip64ExtendedInfo.getUnCompressedSize());

            if (zip64ExtendedInfo.getCompressedSize() != -1)
                localFileHeader.setCompressedSize(zip64ExtendedInfo.getCompressedSize());
        }
    }

    /**
     * Reads Zip64 Extended Info
     *
     * @param extraDataRecords
     * @param unCompressedSize
     * @param compressedSize
     * @param offsetLocalHeader
     * @param diskNumberStart
     * @return {@link Zip64ExtendedInfo}
     * @throws ZipException
     */
    private Zip64ExtendedInfo readZip64ExtendedInfo(
            List<ExtraDataRecord> extraDataRecords,
            long unCompressedSize,
            long compressedSize,
            long offsetLocalHeader,
            int diskNumberStart) throws ZipException {

        for (int i = 0; i < extraDataRecords.size(); i++) {
            ExtraDataRecord extraDataRecord = extraDataRecords.get(i);
            if (extraDataRecord == null) {
                continue;
            }

            if (extraDataRecord.getHeader() == 0x0001) {

                Zip64ExtendedInfo zip64ExtendedInfo = new Zip64ExtendedInfo();

                byte[] byteBuff = extraDataRecord.getData();

                if (extraDataRecord.getSizeOfData() <= 0) {
                    break;
                }
                byte[] longByteBuff = new byte[8];
                byte[] intByteBuff = new byte[4];
                int counter = 0;
                boolean valueAdded = false;

                if (((unCompressedSize & 0xFFFF) == 0xFFFF) && counter < extraDataRecord.getSizeOfData()) {
                    System.arraycopy(byteBuff, counter, longByteBuff, 0, 8);
                    long val = Raw.readLongLittleEndian(longByteBuff, 0);
                    zip64ExtendedInfo.setUnCompressedSize(val);
                    counter += 8;
                    valueAdded = true;
                }

                if (((compressedSize & 0xFFFF) == 0xFFFF) && counter < extraDataRecord.getSizeOfData()) {
                    System.arraycopy(byteBuff, counter, longByteBuff, 0, 8);
                    long val = Raw.readLongLittleEndian(longByteBuff, 0);
                    zip64ExtendedInfo.setCompressedSize(val);
                    counter += 8;
                    valueAdded = true;
                }

                if (((offsetLocalHeader & 0xFFFF) == 0xFFFF) && counter < extraDataRecord.getSizeOfData()) {
                    System.arraycopy(byteBuff, counter, longByteBuff, 0, 8);
                    long val = Raw.readLongLittleEndian(longByteBuff, 0);
                    zip64ExtendedInfo.setOffsetLocalHeader(val);
                    counter += 8;
                    valueAdded = true;
                }

                if (((diskNumberStart & 0xFFFF) == 0xFFFF) && counter < extraDataRecord.getSizeOfData()) {
                    System.arraycopy(byteBuff, counter, intByteBuff, 0, 4);
                    int val = Raw.readIntLittleEndian(intByteBuff, 0);
                    zip64ExtendedInfo.setDiskNumberStart(val);
                    counter += 8;
                    valueAdded = true;
                }

                if (valueAdded) {
                    return zip64ExtendedInfo;
                }

                break;
            }
        }
        return null;
    }

    /**
     * Sets the current random access file pointer at the start of signature
     * of the zip64 end of central directory record
     *
     * @throws ZipException
     */
    private void setFilePointerToReadZip64EndCentralDirLoc() throws ZipException {
        try {
            byte[] ebs = new byte[4];
            long pos = zip4jRaf.length() - EndCentralDirectory.MIN_SIZE;

            do {
                zip4jRaf.seek(pos--);
            } while (Raw.readLeInt(zip4jRaf, ebs) != InternalZipConstants.ENDSIG);

            // Now the file pointer is at the end of signature of Central Dir Rec
            // Seek back with the following values
            // 4 -> end of central dir signature
            // 4 -> total number of disks
            // 8 -> relative offset of the zip64 end of central directory record
            // 4 -> number of the disk with the start of the zip64 end of central directory
            // 4 -> zip64 end of central dir locator signature
            // Refer to Appnote for more information
            //TODO: Donot harcorde these values. Make use of ZipConstants
            zip4jRaf.seek(zip4jRaf.getFilePointer() - 4 - 4 - 8 - 4 - 4);
        } catch(IOException e) {
            throw new ZipException(e);
        }
    }

    /**
     * Reads local file header for the given file header
     *
     * @param fileHeader
     * @return {@link LocalFileHeader}
     * @throws ZipException
     */
    public LocalFileHeader readLocalFileHeader(FileHeader fileHeader) throws ZipException {
        if (fileHeader == null || zip4jRaf == null) {
            throw new ZipException("invalid read parameters for local header");
        }

        long locHdrOffset = fileHeader.getOffsetLocalHeader();

        if (fileHeader.getZip64ExtendedInfo() != null) {
            Zip64ExtendedInfo zip64ExtendedInfo = fileHeader.getZip64ExtendedInfo();
            if (zip64ExtendedInfo.getOffsetLocalHeader() > 0) {
                locHdrOffset = fileHeader.getOffsetLocalHeader();
            }
        }

        if (locHdrOffset < 0) {
            throw new ZipException("invalid local header offset");
        }

        try {
            zip4jRaf.seek(locHdrOffset);

            int length = 0;
            LocalFileHeader localFileHeader = new LocalFileHeader();

            byte[] shortBuff = new byte[2];
            byte[] intBuff = new byte[4];
            byte[] longBuff = new byte[8];

            //signature
            readIntoBuff(zip4jRaf, intBuff);
            int sig = Raw.readIntLittleEndian(intBuff, 0);
            if (sig != InternalZipConstants.LOCSIG) {
                throw new ZipException("invalid local header signature for file: " + fileHeader.getFileName());
            }
            localFileHeader.setSignature(sig);
            length += 4;

            //version needed to extract
            readIntoBuff(zip4jRaf, shortBuff);
            localFileHeader.setVersionNeededToExtract(Raw.readShortLittleEndian(shortBuff, 0));
            length += 2;

            //general purpose bit flag
            readIntoBuff(zip4jRaf, shortBuff);
            localFileHeader.setFileNameUTF8Encoded((Raw.readShortLittleEndian(shortBuff, 0) & InternalZipConstants.UFT8_NAMES_FLAG) != 0);
            int firstByte = shortBuff[0];
            int result = firstByte & 1;
            if (result != 0) {
                localFileHeader.setEncrypted(true);
            }
            localFileHeader.setGeneralPurposeFlag(shortBuff);
            length += 2;

            //Check if data descriptor exists for local file header
            String binary = Integer.toBinaryString(firstByte);
            if (binary.length() >= 4)
                localFileHeader.setDataDescriptorExists(binary.charAt(3) == '1');

            //compression method
            readIntoBuff(zip4jRaf, shortBuff);
            localFileHeader.setCompressionMethod(Raw.readShortLittleEndian(shortBuff, 0));
            length += 2;

            //last mod file time
            readIntoBuff(zip4jRaf, intBuff);
            localFileHeader.setLastModFileTime(Raw.readIntLittleEndian(intBuff, 0));
            length += 4;

            //crc-32
            readIntoBuff(zip4jRaf, intBuff);
            localFileHeader.setCrc32(Raw.readIntLittleEndian(intBuff, 0));
            localFileHeader.setCrcBuff((byte[])intBuff.clone());
            length += 4;

            //compressed size
            readIntoBuff(zip4jRaf, intBuff);
            longBuff = getLongByteFromIntByte(intBuff);
            localFileHeader.setCompressedSize(Raw.readLongLittleEndian(longBuff, 0));
            length += 4;

            //uncompressed size
            readIntoBuff(zip4jRaf, intBuff);
            longBuff = getLongByteFromIntByte(intBuff);
            localFileHeader.setUncompressedSize(Raw.readLongLittleEndian(longBuff, 0));
            length += 4;

            //file name length
            readIntoBuff(zip4jRaf, shortBuff);
            int fileNameLength = Raw.readShortLittleEndian(shortBuff, 0);
            localFileHeader.setFileNameLength(fileNameLength);
            length += 2;

            //extra field length
            readIntoBuff(zip4jRaf, shortBuff);
            int extraFieldLength = Raw.readShortLittleEndian(shortBuff, 0);
            localFileHeader.setExtraFieldLength(extraFieldLength);
            length += 2;

            //file name
            if (fileNameLength > 0) {
                byte[] fileNameBuf = new byte[fileNameLength];
                readIntoBuff(zip4jRaf, fileNameBuf);
                // Modified after user reported an issue http://www.lingala.net/zip4j/forum/index.php?topic=2.0
//				String fileName = new String(fileNameBuf, "Cp850");
//				String fileName = Zip4jUtil.getCp850EncodedString(fileNameBuf);
                String fileName = Zip4jUtil.decodeFileName(fileNameBuf, localFileHeader.isFileNameUTF8Encoded());

                if (fileName == null) {
                    throw new ZipException("file name is null, cannot assign file name to local file header");
                }

                if (fileName.indexOf(":" + System.getProperty("file.separator")) >= 0) {
                    fileName = fileName.substring(fileName.indexOf(":" + System.getProperty("file.separator")) + 2);
                }

                localFileHeader.setFileName(fileName);
                length += fileNameLength;
            } else {
                localFileHeader.setFileName(null);
            }

            //extra field
            readAndSaveExtraDataRecord(localFileHeader);
            length += extraFieldLength;

            localFileHeader.setOffsetStartOfData(locHdrOffset + length);

            //Copy password from fileHeader to localFileHeader
            localFileHeader.setPassword(fileHeader.getPassword());

            readAndSaveZip64ExtendedInfo(localFileHeader);

            readAndSaveAESExtraDataRecord(localFileHeader);

            if (localFileHeader.isEncrypted()) {

                if (localFileHeader.getEncryptionMethod() == Zip4jConstants.ENC_METHOD_AES) {
                    //Do nothing
                } else {
                    if ((firstByte & 64) == 64) {
                        //hardcoded for now
                        localFileHeader.setEncryptionMethod(1);
                    } else {
                        localFileHeader.setEncryptionMethod(Zip4jConstants.ENC_METHOD_STANDARD);
//						localFileHeader.setCompressedSize(localFileHeader.getCompressedSize()
//								- ZipConstants.STD_DEC_HDR_SIZE);
                    }
                }

            }

            if (localFileHeader.getCrc32() <= 0) {
                localFileHeader.setCrc32(fileHeader.getCrc32());
                localFileHeader.setCrcBuff(fileHeader.getCrcBuff());
            }

            if (localFileHeader.getCompressedSize() <= 0) {
                localFileHeader.setCompressedSize(fileHeader.getCompressedSize());
            }

            if (localFileHeader.getUncompressedSize() <= 0) {
                localFileHeader.setUncompressedSize(fileHeader.getUncompressedSize());
            }

            return localFileHeader;
        } catch(IOException e) {
            throw new ZipException(e);
        }
    }

    /**
     * Reads AES Extra Data Record and saves it in the {@link FileHeader}
     *
     * @param fileHeader
     * @throws ZipException
     */
    private void readAndSaveAESExtraDataRecord(FileHeader fileHeader) throws ZipException {
        if (fileHeader == null) {
            throw new ZipException("file header is null in reading Zip64 Extended Info");
        }

        if (fileHeader.getExtraDataRecords() == null || fileHeader.getExtraDataRecords().size() <= 0) {
            return;
        }

        AESExtraDataRecord aesExtraDataRecord = readAESExtraDataRecord(fileHeader.getExtraDataRecords());
        if (aesExtraDataRecord != null) {
            fileHeader.setAesExtraDataRecord(aesExtraDataRecord);
            fileHeader.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES);
        }
    }

    /**
     * Reads AES Extra Data Record and saves it in the {@link LocalFileHeader}
     *
     * @param localFileHeader
     * @throws ZipException
     */
    private void readAndSaveAESExtraDataRecord(LocalFileHeader localFileHeader) throws ZipException {
        if (localFileHeader == null) {
            throw new ZipException("file header is null in reading Zip64 Extended Info");
        }

        if (localFileHeader.getExtraDataRecords() == null || localFileHeader.getExtraDataRecords().size() <= 0) {
            return;
        }

        AESExtraDataRecord aesExtraDataRecord = readAESExtraDataRecord(localFileHeader.getExtraDataRecords());
        if (aesExtraDataRecord != null) {
            localFileHeader.setAesExtraDataRecord(aesExtraDataRecord);
            localFileHeader.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES);
        }
    }

    /**
     * Reads AES Extra Data Record
     *
     * @param extraDataRecords
     * @return {@link AESExtraDataRecord}
     * @throws ZipException
     */
    private AESExtraDataRecord readAESExtraDataRecord(List<ExtraDataRecord> extraDataRecords) throws ZipException {

        if (extraDataRecords == null) {
            return null;
        }

        for (int i = 0; i < extraDataRecords.size(); i++) {
            ExtraDataRecord extraDataRecord = extraDataRecords.get(i);
            if (extraDataRecord == null) {
                continue;
            }

            if (extraDataRecord.getHeader() == InternalZipConstants.AESSIG) {

                if (extraDataRecord.getData() == null) {
                    throw new ZipException("corruput AES extra data records");
                }

                AESExtraDataRecord aesExtraDataRecord = new AESExtraDataRecord();

                aesExtraDataRecord.setSignature(InternalZipConstants.AESSIG);
                aesExtraDataRecord.setDataSize(extraDataRecord.getSizeOfData());

                byte[] aesData = extraDataRecord.getData();
                aesExtraDataRecord.setVersionNumber(Raw.readShortLittleEndian(aesData, 0));
                byte[] vendorIDBytes = new byte[2];
                System.arraycopy(aesData, 2, vendorIDBytes, 0, 2);
                aesExtraDataRecord.setVendorID(new String(vendorIDBytes));
                aesExtraDataRecord.setAesStrength((int)(aesData[4] & 0xFF));
                aesExtraDataRecord.setCompressionMethod(Raw.readShortLittleEndian(aesData, 5));

                return aesExtraDataRecord;
            }
        }

        return null;
    }

    /**
     * Reads buf length of bytes from the input stream to buf
     *
     * @param zip4jRaf
     * @param buf
     * @return byte array
     * @throws ZipException
     */
    static byte[] readIntoBuff(RandomAccessFile zip4jRaf, byte[] buf) throws ZipException {
        try {
            if (zip4jRaf.read(buf, 0, buf.length) != -1) {
                return buf;
            } else {
                throw new ZipException("unexpected end of file when reading short buff");
            }
        } catch(IOException e) {
            throw new ZipException("IOException when reading short buff", e);
        }
    }

    static byte[] readIntoBuff(LittleEndianRandomAccessFile zip4jRaf, byte[] buf) throws ZipException {
        return readIntoBuff(zip4jRaf.getIn(), buf);
    }

    /**
     * Returns a long byte from an int byte by appending last 4 bytes as 0's
     *
     * @param intByte
     * @return byte array
     * @throws ZipException
     */
    static byte[] getLongByteFromIntByte(byte[] intByte) throws ZipException {
        if (intByte == null) {
            throw new ZipException("input parameter is null, cannot expand to 8 bytes");
        }

        if (intByte.length != 4) {
            throw new ZipException("invalid byte length, cannot expand to 8 bytes");
        }

        byte[] longBuff = { intByte[0], intByte[1], intByte[2], intByte[3], 0, 0, 0, 0 };
        return longBuff;
    }
}
