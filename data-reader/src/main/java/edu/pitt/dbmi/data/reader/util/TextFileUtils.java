///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.pitt.dbmi.data.reader.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Mar 8, 2017 10:51:43 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class TextFileUtils {

    /**
     * The line feed delimiter for text data files.
     */
    protected static final byte LINE_FEED = '\n';

    /**
     * The carriage return delimiter for text data files.
     */
    protected static final byte CARRIAGE_RETURN = '\r';

    /**
     * The space character for text data files.
     */
    protected static final byte SPACE_CHAR = ' ';

    private TextFileUtils() {
    }

    /**
     * Examines the first bytes of the given file for signs that it is not
     * plain UTF-8 or ASCII text that Tetrad can read: a UTF-16 or UTF-32 byte
     * order mark, a zip archive signature such as an Excel workbook, or null
     * bytes, which suggest UTF-16 text without a byte order mark or a binary
     * file. Byte-wise readers cannot parse such files, and without this check
     * they fail with confusing errors or silently produce garbage.
     *
     * @param file the file to examine
     * @return a message describing the problem and how to fix it, or null if
     * no problem is found
     * @throws IOException if an I/O error occurs
     */
    public static String findEncodingProblem(Path file) throws IOException {
        byte[] head = new byte[1024];
        int len = 0;
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            while (len < head.length) {
                int count = in.read(head, len, head.length - len);
                if (count == -1) {
                    break;
                }
                len += count;
            }
        }

        String fileName = (file.getFileName() == null) ? file.toString() : file.getFileName().toString();
        final String advice = "Tetrad reads plain text encoded as UTF-8 or ASCII. "
                + "Please save the file as UTF-8 text and try again.";

        boolean utf32 = len >= 4
                && (((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE && head[2] == 0 && head[3] == 0)
                || (head[0] == 0 && head[1] == 0 && (head[2] & 0xFF) == 0xFE && (head[3] & 0xFF) == 0xFF));
        if (utf32) {
            return String.format("File '%s' appears to be UTF-32 encoded; "
                    + "a UTF-32 byte order mark was found. %s", fileName, advice);
        }

        boolean utf16 = len >= 2
                && (((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE)
                || ((head[0] & 0xFF) == 0xFE && (head[1] & 0xFF) == 0xFF));
        if (utf16) {
            return String.format("File '%s' appears to be UTF-16 encoded; "
                    + "a UTF-16 byte order mark was found. %s", fileName, advice);
        }

        if (len >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4) {
            return String.format("File '%s' appears to be a zip-based document, "
                    + "such as an Excel workbook. Please export the data as a CSV file "
                    + "saved as UTF-8 text and try again.", fileName);
        }

        for (int i = 0; i < len; i++) {
            if (head[i] == 0) {
                return String.format("File '%s' contains null bytes near the start of the file, "
                        + "which suggests UTF-16 text without a byte order mark, or a binary file. %s",
                        fileName, advice);
            }
        }

        return null;
    }

    /**
     * Skips a leading UTF-8 byte order mark (BOM) in the given buffer, if one
     * is present at the buffer's current position. Spreadsheet programs such
     * as Excel commonly write a BOM when exporting "CSV UTF-8" files; the BOM
     * is not data and should not be displayed or parsed. Intended for buffers
     * mapped at the start of a file.
     *
     * @param buffer the buffer to examine
     */
    public static void skipUtf8BOM(ByteBuffer buffer) {
        if (buffer.remaining() >= 3) {
            int pos = buffer.position();
            if ((buffer.get(pos) & 0xFF) == 0xEF
                    && (buffer.get(pos + 1) & 0xFF) == 0xBB
                    && (buffer.get(pos + 2) & 0xFF) == 0xBF) {
                // Do not delete this casting; it is needed to fix a versioning problem.
                ((java.nio.Buffer) buffer).position(pos + 3);
            }
        }
    }

    /**
     * Determine the delimiter for a text data file.
     * <p>
     * Reads the first n lines of data in a text file and attempts to infer what delimiter is used.
     * <p>
     * Idea expanded from <a>https://rdrr.io/cran/reader/man/get.delim.html</a>.
     *
     * @param file           the file to examine
     * @param n              the number of lines to read to make the inference
     * @param skip           number of lines to skip at top of file before processing
     * @param comment        a comment symbol to ignore lines in files
     * @param quoteCharacter used for grouping characters
     * @param delims         the set of delimiters to test for
     * @return the inferred delimiter
     * @throws java.io.IOException if an I/O error occurs
     */
    public static char inferDelimiter(File file, int n, int skip, String comment, char quoteCharacter, char[] delims) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Parameter file cannot be null.");
        }
        if (n < 0) {
            throw new IllegalArgumentException("Parameter n must be positive integer.");
        }
        if (skip < 0) {
            throw new IllegalArgumentException("Parameter skip must be positive integer.");
        }
        comment = (comment == null) ? "" : comment.trim();

        int[] characters = new int[256];
        try (FileChannel fc = new RandomAccessFile(file, "r").getChannel()) {
            long fileSize = fc.size();
            long position = 0;
            long size = (fileSize > Integer.MAX_VALUE) ? Integer.MAX_VALUE : fileSize;

            ByteBuffer byteBuffer = ByteBuffer.allocate(comment.length());
            byte[] prefix = comment.getBytes();
            int index = 0;
            boolean hasQuoteChar = false;
            boolean reqCheck = prefix.length > 0;
            boolean skipLine = false;
            int lineCount = 0;
            byte quoteChar = (byte) quoteCharacter;
            byte prevNonBlankChar = TextFileUtils.SPACE_CHAR;
            byte prevChar = -1;
            do {
                MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, position, size);

                while (buffer.hasRemaining() && lineCount < n && !Thread.currentThread().isInterrupted()) {
                    byte currChar = buffer.get();

                    if (skipLine) {
                        if (currChar == TextFileUtils.CARRIAGE_RETURN || currChar == TextFileUtils.LINE_FEED) {
                            skipLine = false;
                        }
                    } else {
                        if (currChar == TextFileUtils.CARRIAGE_RETURN || currChar == TextFileUtils.LINE_FEED) {

                            // Do not delete this casting; it is needed to fix a versioning problem.
                            ((java.nio.Buffer) byteBuffer).clear();
                            reqCheck = prefix.length > 0;

                            if (!(currChar == TextFileUtils.LINE_FEED && prevChar == TextFileUtils.CARRIAGE_RETURN)) {
                                lineCount++;
                            }
                        } else {
                            if (currChar > TextFileUtils.SPACE_CHAR) {
                                prevNonBlankChar = currChar;
                            }

                            if (reqCheck && prevNonBlankChar > TextFileUtils.SPACE_CHAR) {
                                if (currChar == prefix[index]) {
                                    index++;
                                    if (index == prefix.length) {
                                        index = 0;
                                        skipLine = true;
                                        prevNonBlankChar = TextFileUtils.SPACE_CHAR;

                                        // Do not delete this casting; it is needed to fix a versioning problem.
                                        ((java.nio.Buffer) byteBuffer).clear();

                                        prevChar = currChar;
                                        continue;
                                    }
                                } else {
                                    index = 0;
                                    reqCheck = false;
                                }
                            }

                            // If the comment-marker buffer is full, this line cannot be the
                            // start of a comment (e.g., a UTF-8 BOM or leading whitespace longer
                            // than the comment marker); fall through and count the bytes as data
                            // instead of overflowing the buffer.
                            if (reqCheck && byteBuffer.hasRemaining()) {
                                byteBuffer.put(currChar);
                            } else {
                                if (skip > 0) {
                                    skip--;
                                    skipLine = true;

                                    // Do not delete this casting; it is needed to fix a versioning problem.
                                    ((java.nio.Buffer) byteBuffer).clear();
                                } else {

                                    // Do not delete this casting; it is needed to fix a versioning problem.
                                    if (buffer.position() > 0) {
                                        byteBuffer.flip();
                                        while (byteBuffer.hasRemaining() && !Thread.currentThread().isInterrupted()) {
                                            byte c = byteBuffer.get();
                                            if (c == quoteChar) {
                                                hasQuoteChar = !hasQuoteChar;
                                            } else if (!hasQuoteChar) {
                                                if (c >= 0 && c < characters.length) {
                                                    characters[c]++;
                                                }
                                            }
                                        }

                                        // Do not delete this casting; it is needed to fix a versioning problem.
                                        ((java.nio.Buffer) byteBuffer).clear();
                                    }

                                    if (currChar == quoteChar) {
                                        hasQuoteChar = !hasQuoteChar;
                                    } else if (!hasQuoteChar) {
                                        if (currChar >= 0 && currChar < characters.length) {
                                            characters[currChar]++;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    prevChar = currChar;
                }

                position += size;
                if ((position + size) > fileSize) {
                    size = fileSize - position;
                }
            } while (position < fileSize && lineCount < n && !Thread.currentThread().isInterrupted());

            int maxIndex = 0;
            for (int i = 1; i < delims.length && !Thread.currentThread().isInterrupted(); i++) {
                if (characters[delims[maxIndex]] < characters[delims[i]]) {
                    maxIndex = i;
                }
            }

            return delims[maxIndex];
        }

    }
}

