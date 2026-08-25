 ///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.pitt.dbmi.data.reader.covariance;

import edu.pitt.dbmi.data.reader.DataReaderException;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A tolerant parser for full (square) covariance matrix files, shared by
 * {@link FullCovarianceDataFileReader} and
 * {@link edu.pitt.dbmi.data.reader.validation.covariance.FullCovarianceDataFileValidation} so that the reader and
 * the validator agree on the accepted formats by construction.
 * <p>
 * The classical Tetrad format is accepted as before:
 * <pre>
 *   line 1: sample size (a single integer)
 *   line 2: variable names (p entries)
 *   line 3+: the p x p covariance matrix, p values per row
 * </pre>
 * In addition, the following relaxations are accepted, in any combination:
 * <ul>
 * <li><b>Missing sample size.</b> If the first data-bearing line is not a single integer, it is taken to be the
 * variable-name header, the sample size is assumed to be {@value #DEFAULT_SAMPLE_SIZE}, and a warning is recorded
 * (retrievable from the parse result) telling the user to put the sample size alone on the first line if a
 * different value is intended.</li>
 * <li><b>Row names.</b> Data rows may begin with a non-numeric name column. The row names must match the header's
 * variable names in the same order; a mismatch is an error, not a warning, since a reordered or transposed matrix
 * loaded positionally would be silently wrong.</li>
 * <li><b>Corner cell.</b> When row names are present, the header may begin with an extra corner label (as written,
 * for example, by R's {@code write.csv} on a matrix with row names). The corner label is detected by counting: with
 * a corner cell, the header has exactly as many entries as each data row (one label plus p names against one name
 * plus p values); without it, the header has one fewer. The corner label is not treated as a variable name.</li>
 * </ul>
 * Comment lines (lines whose first nonblank characters are the comment marker), blank lines, quoted fields, and all
 * delimiters (including whitespace) are handled with the same semantics as the other data-reader classes. Bytes are
 * decoded as ISO-8859-1, matching the byte-to-char behavior of the legacy readers. The matrix must be exactly
 * symmetric as text, as before.
 *
 * @author josephramsey
 */
public final class FullCovarianceFormat {

    /**
     * The sample size assumed when the file does not state one.
     */
    public static final int DEFAULT_SAMPLE_SIZE = 1000;

    private FullCovarianceFormat() {
    }

    /**
     * The result of parsing a full covariance file: the sample size (stated or assumed), the variable names, the
     * p x p matrix, whether the sample size was actually stated in the file, and any warnings.
     *
     * @param numberOfCases   the sample size, stated or assumed.
     * @param variables       the variable names, in order.
     * @param data            the p x p covariance matrix.
     * @param sampleSizeInFile whether the sample size was stated in the file (false if assumed).
     * @param warnings        human-readable warnings, empty if none.
     */
    public record ParseResult(int numberOfCases, List<String> variables, double[][] data,
                              boolean sampleSizeInFile, List<String> warnings) {
    }

    /**
     * Parses the given full covariance file.
     *
     * @param dataFile      the file to parse; may not be null.
     * @param delimiter     the delimiter; may not be null.
     * @param quoteCharacter the quote character (0 for none).
     * @param commentMarker the comment marker (empty for none); may not be null.
     * @return the parse result.
     * @throws IOException         if the file cannot be read.
     * @throws DataReaderException if the file does not conform to any accepted format.
     */
    public static ParseResult parse(Path dataFile, Delimiter delimiter, byte quoteCharacter, String commentMarker)
            throws IOException {
        List<String> warnings = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(dataFile, StandardCharsets.ISO_8859_1)) {
            LineSource lines = new LineSource(reader, delimiter, quoteCharacter, commentMarker);

            // First data-bearing line: either the sample size (a single integer, tolerating trailing empty
            // tokens from a trailing delimiter) or the variable-name header.
            Line first = lines.next();

            if (first == null) {
                throw new DataReaderException("Covariance file contains no data.");
            }

            int numberOfCases;
            boolean sampleSizeInFile;
            Line headerLine;

            Integer maybeN = asSampleSize(first.tokens);

            if (maybeN != null) {
                numberOfCases = maybeN;
                sampleSizeInFile = true;
                headerLine = lines.next();

                if (headerLine == null) {
                    throw new DataReaderException("Covariance file ends after the sample size line; no variable "
                            + "names found.");
                }
            } else {
                numberOfCases = DEFAULT_SAMPLE_SIZE;
                sampleSizeInFile = false;
                headerLine = first;
                warnings.add("No sample size line found; the sample size was assumed to be " + DEFAULT_SAMPLE_SIZE
                        + ". If this is not the intended sample size, put the sample size alone on the first line "
                        + "of the file.");
            }

            List<String> headerTokens = headerLine.tokens;

            for (int i = 0; i < headerTokens.size(); i++) {
                if (headerTokens.get(i).isEmpty()) {
                    throw new DataReaderException(String.format("Missing variable name on line %d at column %d.",
                            headerLine.lineNumber, i + 1));
                }
            }

            // Peek the first data row to determine the layout by counting (see the class Javadoc).
            Line firstDataRow = lines.next();

            if (firstDataRow == null) {
                throw new DataReaderException("Covariance file contains variable names but no matrix rows.");
            }

            int h = headerTokens.size();
            int m = firstDataRow.tokens.size();
            boolean rowNames;
            List<String> variables;

            if (m == h && isNumeric(firstDataRow.tokens.get(0))) {
                // Classical layout: p names, p values per row.
                rowNames = false;
                variables = List.copyOf(headerTokens);
            } else if (m == h && !isNumeric(firstDataRow.tokens.get(0))) {
                // Row names with a corner cell: the header is one corner label plus p names, each data row is one
                // name plus p values.
                rowNames = true;
                variables = List.copyOf(headerTokens.subList(1, h));
            } else if (m == h + 1 && !isNumeric(firstDataRow.tokens.get(0))) {
                // Row names without a corner cell: the header is p names, each data row is one name plus p values.
                rowNames = true;
                variables = List.copyOf(headerTokens);
            } else if (m == h + 1) {
                throw new DataReaderException(String.format("Excess data on line %d.  Extracted %d value(s) but "
                        + "expected %d.", firstDataRow.lineNumber, m, h));
            } else if (m == h - 1 && isNumeric(firstDataRow.tokens.get(0))) {
                throw new DataReaderException(String.format("Data rows have one fewer value (%d) than the header "
                        + "has names (%d) (line %d). If the first header entry is a corner label rather than a "
                        + "variable name, data rows must then begin with row names; otherwise remove the extra "
                        + "header entry.", m, h, firstDataRow.lineNumber));
            } else {
                throw new DataReaderException(String.format("Line %d has %d value(s) but the header has %d "
                        + "name(s); no accepted covariance file layout matches.", firstDataRow.lineNumber, m, h));
            }

            int p = variables.size();

            if (p == 0) {
                throw new DataReaderException("Covariance file does not contain variable names.");
            }

            double[][] data = new double[p][p];
            Line row = firstDataRow;
            int rowIndex = 0;

            while (row != null) {
                if (rowIndex >= p) {
                    throw new DataReaderException(String.format("Excess data on line %d.  Extracted %d rows but "
                            + "expected %d.", row.lineNumber, rowIndex + 1, p));
                }

                List<String> tokens = row.tokens;
                int expected = rowNames ? p + 1 : p;

                if (tokens.size() > expected) {
                    throw new DataReaderException(String.format("Excess data on line %d.  Extracted %d value(s) "
                            + "but expected %d.", row.lineNumber, tokens.size(), expected));
                } else if (tokens.size() < expected) {
                    throw new DataReaderException(String.format("Insufficent data on line %d.  Extracted %d "
                            + "value(s) but expected %d.", row.lineNumber, tokens.size(), expected));
                }

                int offset = 0;

                if (rowNames) {
                    String rowName = tokens.get(0);

                    if (!rowName.equals(variables.get(rowIndex))) {
                        throw new DataReaderException(String.format("Row name mismatch on line %d: expected %s "
                                + "(from the header, in order) but found %s. Row names must match the header's "
                                + "variable names in the same order; a reordered matrix cannot be loaded "
                                + "positionally.", row.lineNumber, variables.get(rowIndex), rowName));
                    }

                    offset = 1;
                }

                for (int j = 0; j < p; j++) {
                    String value = tokens.get(j + offset);

                    if (value.isEmpty()) {
                        throw new DataReaderException(String.format("Missing value on line %d at column %d.",
                                row.lineNumber, j + offset + 1));
                    }

                    try {
                        data[rowIndex][j] = Double.parseDouble(value);
                    } catch (NumberFormatException exception) {
                        throw new DataReaderException(String.format("Invalid number %s on line %d at column %d.",
                                value, row.lineNumber, j + offset + 1));
                    }
                }

                rowIndex++;
                row = lines.next();
            }

            if (rowIndex < p) {
                throw new DataReaderException(String.format("Insufficient data.  Expect %d rows but only read in "
                        + "%d.", p, rowIndex));
            }

            checkSymmetry(data);

            return new ParseResult(numberOfCases, variables, data, sampleSizeInFile, List.copyOf(warnings));
        }
    }

    /**
     * Returns the sample size if the given tokens are a sample size line - a single positive integer, tolerating
     * trailing empty tokens left by a trailing delimiter, and tolerating an integral decimal such as 1000.0 - or
     * null otherwise.
     */
    private static Integer asSampleSize(List<String> tokens) {
        int last = tokens.size();

        while (last > 1 && tokens.get(last - 1).isEmpty()) {
            last--;
        }

        if (last != 1) return null;

        String value = tokens.get(0);

        try {
            int n = Integer.parseInt(value);
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            // Fall through to the integral-decimal case.
        }

        try {
            double d = Double.parseDouble(value);

            if (d > 0 && d == Math.rint(d) && d <= Integer.MAX_VALUE) {
                return (int) d;
            }
        } catch (NumberFormatException e) {
            // Not a number at all.
        }

        return null;
    }

    /**
     * Returns true if the given token parses as a double.
     */
    private static boolean isNumeric(String token) {
        if (token.isEmpty()) return false;

        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Throws if the matrix is not exactly symmetric, matching the legacy reader's behavior.
     */
    private static void checkSymmetry(double[][] data) {
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                if (i != j && Double.compare(data[i][j], data[j][i]) != 0) {
                    throw new DataReaderException(String.format("Non-symmetric matrix.  COV(%d,%d)=%f is not equal "
                            + "to COV(%d,%d)=%f.", i, j, data[i][j], j, i, data[j][i]));
                }
            }
        }
    }

    /**
     * A tokenized data-bearing line and its 1-based physical line number.
     */
    private static final class Line {
        final int lineNumber;
        final List<String> tokens;

        Line(int lineNumber, List<String> tokens) {
            this.lineNumber = lineNumber;
            this.tokens = tokens;
        }
    }

    /**
     * Streams the data-bearing lines of the file, skipping blank and comment lines, tokenizing by the delimiter
     * with quote awareness. Fields are trimmed. For the whitespace delimiter, any run of whitespace outside quotes
     * separates fields and leading and trailing whitespace produces no empty fields; for character delimiters,
     * every occurrence outside quotes separates fields, so a trailing delimiter produces a trailing empty field, as
     * in the legacy readers.
     */
    private static final class LineSource {
        private final BufferedReader reader;
        private final Delimiter delimiter;
        private final char quoteCharacter;
        private final String commentMarker;
        private int lineNumber = 0;

        LineSource(BufferedReader reader, Delimiter delimiter, byte quoteCharacter, String commentMarker) {
            this.reader = reader;
            this.delimiter = delimiter;
            this.quoteCharacter = (char) quoteCharacter;
            this.commentMarker = commentMarker == null ? "" : commentMarker;
        }

        /**
         * Returns the next data-bearing line, or null at end of file.
         */
        Line next() throws IOException {
            String line;

            while ((line = this.reader.readLine()) != null) {
                this.lineNumber++;
                String trimmed = line.trim();

                if (trimmed.isEmpty()) continue;
                if (!this.commentMarker.isEmpty() && trimmed.startsWith(this.commentMarker)) continue;

                return new Line(this.lineNumber, tokenize(line));
            }

            return null;
        }

        private List<String> tokenize(String line) {
            List<String> tokens = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean inQuote = false;
            boolean whitespaceDelim = this.delimiter == Delimiter.WHITESPACE;
            char delimChar = (char) this.delimiter.getByteValue();

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);

                if (this.quoteCharacter > 0 && c == this.quoteCharacter) {
                    inQuote = !inQuote;
                    continue;
                }

                boolean isDelimiter = !inQuote
                        && (whitespaceDelim ? c <= ' ' : c == delimChar);

                if (isDelimiter) {
                    if (whitespaceDelim) {
                        // Runs of whitespace are one separator; leading whitespace produces no empty field.
                        if (field.length() > 0) {
                            tokens.add(field.toString().trim());
                            field.setLength(0);
                        }
                    } else {
                        tokens.add(field.toString().trim());
                        field.setLength(0);
                    }
                } else {
                    field.append(c);
                }
            }

            if (whitespaceDelim) {
                if (field.length() > 0) {
                    tokens.add(field.toString().trim());
                }
            } else {
                tokens.add(field.toString().trim());
            }

            return tokens;
        }
    }
}
