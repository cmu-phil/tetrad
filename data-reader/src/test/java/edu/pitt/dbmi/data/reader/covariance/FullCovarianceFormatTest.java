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
import edu.pitt.dbmi.data.reader.validation.ValidationCode;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;
import edu.pitt.dbmi.data.reader.validation.covariance.FullCovarianceDataFileValidation;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests the tolerant full (square) covariance file formats: the classical Tetrad layout continues to load exactly as
 * before, and the relaxations (missing sample-size line with an assumed default and a warning, row names, corner
 * cell) load correctly in every combination, while structural errors (row-name mismatches, headers inconsistent
 * with the data-row width) are rejected with clear messages. Also checks that the validation class, which delegates
 * to the same parser, agrees with the reader.
 *
 * @author josephramsey
 */
public class FullCovarianceFormatTest {

    private static Path write(String content) throws IOException {
        Path file = Files.createTempFile("cov", ".txt");
        file.toFile().deleteOnExit();
        Files.write(file, content.getBytes(StandardCharsets.ISO_8859_1));
        return file;
    }

    private static CovarianceData read(Path file, Delimiter delimiter) throws IOException {
        CovarianceDataReader reader = new FullCovarianceDataFileReader(file, delimiter);
        reader.setCommentMarker("//");
        return reader.readInData();
    }

    /**
     * The classical layout (sample size, names, matrix) loads exactly as before, with no warnings.
     */
    @Test
    public void testClassicalLayout() throws IOException {
        Path file = write("100\nX1 X2 X3\n2.0 0.3 0.1\n0.3 1.5 0.2\n0.1 0.2 1.0\n");
        CovarianceData data = read(file, Delimiter.WHITESPACE);

        Assert.assertEquals(100, data.getNumberOfCases());
        Assert.assertEquals(List.of("X1", "X2", "X3"), data.getVariables());
        Assert.assertEquals(2.0, data.getData()[0][0], 0.0);
        Assert.assertEquals(0.3, data.getData()[1][0], 0.0);
        Assert.assertTrue(data.getWarnings().isEmpty());
    }

    /**
     * The pre-existing spartina fixtures (space-delimited classical layout, with and without quotes) load with the
     * same cases and dimensions as before the loader was rewritten.
     */
    @Test
    public void testLegacyFixturesStillLoad() throws IOException {
        String[] resources = {"/data/covariance/spartina_full.txt", "/data/covariance/quotes_spartina_full.txt"};

        for (String resource : resources) {
            Path file = new File(getClass().getResource(resource).getFile()).toPath();
            CovarianceDataReader reader = new FullCovarianceDataFileReader(file, Delimiter.SPACE);
            reader.setCommentMarker("//");
            reader.setQuoteCharacter('"');
            CovarianceData data = reader.readInData();

            Assert.assertEquals(45, data.getNumberOfCases());
            Assert.assertEquals(15, data.getVariables().size());
            Assert.assertEquals(15, data.getData().length);
            Assert.assertEquals(15, data.getData()[0].length);
            Assert.assertTrue(data.getWarnings().isEmpty());
        }
    }

    /**
     * An R-style file (corner label, row names, no sample size line) loads with the corner label dropped, the row
     * names verified, the default sample size assumed, and a warning recorded.
     */
    @Test
    public void testCornerAndRowNamesNoSampleSize() throws IOException {
        Path file = new File(getClass().getResource("/data/covariance/rstyle_corner_full.csv").getFile()).toPath();
        CovarianceData data = read(file, Delimiter.COMMA);

        Assert.assertEquals(FullCovarianceFormat.DEFAULT_SAMPLE_SIZE, data.getNumberOfCases());
        Assert.assertEquals(List.of("X1", "X2", "X3"), data.getVariables());
        Assert.assertEquals(1.5, data.getData()[1][1], 0.0);
        Assert.assertEquals(0.2, data.getData()[2][1], 0.0);
        Assert.assertEquals(1, data.getWarnings().size());
        Assert.assertTrue(data.getWarnings().get(0).contains("assumed to be "
                + FullCovarianceFormat.DEFAULT_SAMPLE_SIZE));
    }

    /**
     * Row names without a corner cell, with a sample size line, load with no warnings.
     */
    @Test
    public void testRowNamesNoCornerWithSampleSize() throws IOException {
        Path file = write("250\nX1,X2\nX1,2.0,0.3\nX2,0.3,1.5\n");
        CovarianceData data = read(file, Delimiter.COMMA);

        Assert.assertEquals(250, data.getNumberOfCases());
        Assert.assertEquals(List.of("X1", "X2"), data.getVariables());
        Assert.assertEquals(0.3, data.getData()[0][1], 0.0);
        Assert.assertTrue(data.getWarnings().isEmpty());
    }

    /**
     * A corner cell together with a sample size line also loads.
     */
    @Test
    public void testCornerWithSampleSize() throws IOException {
        Path file = write("77\nname,X1,X2\nX1,2.0,0.3\nX2,0.3,1.5\n");
        CovarianceData data = read(file, Delimiter.COMMA);

        Assert.assertEquals(77, data.getNumberOfCases());
        Assert.assertEquals(List.of("X1", "X2"), data.getVariables());
        Assert.assertTrue(data.getWarnings().isEmpty());
    }

    /**
     * A plain header with no sample size line and no row names loads with the default sample size and a warning.
     */
    @Test
    public void testPlainHeaderNoSampleSize() throws IOException {
        Path file = write("X1,X2\n2.0,0.3\n0.3,1.5\n");
        CovarianceData data = read(file, Delimiter.COMMA);

        Assert.assertEquals(FullCovarianceFormat.DEFAULT_SAMPLE_SIZE, data.getNumberOfCases());
        Assert.assertEquals(List.of("X1", "X2"), data.getVariables());
        Assert.assertEquals(1, data.getWarnings().size());
    }

    /**
     * Row names must match the header's variable names in the same order; a reordered matrix is rejected with a
     * message localizing the first mismatch.
     */
    @Test
    public void testRowNameMismatchRejected() throws IOException {
        Path file = write("var,X1,X2\nX2,1.5,0.3\nX1,0.3,2.0\n");

        try {
            read(file, Delimiter.COMMA);
            Assert.fail("Expected a DataReaderException for the row-name mismatch.");
        } catch (DataReaderException e) {
            Assert.assertTrue(e.getMessage().contains("Row name mismatch"));
            Assert.assertTrue(e.getMessage().contains("expected X1"));
        }
    }

    /**
     * A header with one more name than the data rows have values, without row names, is rejected with a message
     * pointing at the possible corner label.
     */
    @Test
    public void testExtraHeaderNameRejected() throws IOException {
        Path file = write("corner,X1,X2\n2.0,0.3\n0.3,1.5\n");

        try {
            read(file, Delimiter.COMMA);
            Assert.fail("Expected a DataReaderException for the extra header name.");
        } catch (DataReaderException e) {
            Assert.assertTrue(e.getMessage().contains("corner label"));
        }
    }

    /**
     * Comment lines and blank lines are skipped everywhere, including before the sample size line and between
     * matrix rows.
     */
    @Test
    public void testCommentsAndBlankLines() throws IOException {
        Path file = write("// a comment\n\n100\n// another\nX1 X2\n2.0 0.3\n\n0.3 1.5\n");
        CovarianceData data = read(file, Delimiter.WHITESPACE);

        Assert.assertEquals(100, data.getNumberOfCases());
        Assert.assertEquals(2, data.getVariables().size());
    }

    /**
     * The validation class agrees with the reader: the R-style file yields a WARNING (assumed sample size) and an
     * INFO summary but no ERROR, the classical file yields only the INFO summary, and a structurally broken file
     * yields an ERROR.
     */
    @Test
    public void testValidationAgreesWithReader() throws IOException {
        Path rStyle = new File(getClass().getResource("/data/covariance/rstyle_corner_full.csv").getFile()).toPath();
        FullCovarianceDataFileValidation v1 = new FullCovarianceDataFileValidation(rStyle, Delimiter.COMMA);
        v1.setCommentMarker("//");
        List<ValidationResult> r1 = v1.validate();

        Assert.assertTrue(r1.stream().anyMatch(r -> r.getCode() == ValidationCode.WARNING));
        Assert.assertTrue(r1.stream().anyMatch(r -> r.getCode() == ValidationCode.INFO));
        Assert.assertTrue(r1.stream().noneMatch(r -> r.getCode() == ValidationCode.ERROR));

        Path classical = write("100\nX1,X2\n2.0,0.3\n0.3,1.5\n");
        FullCovarianceDataFileValidation v2 = new FullCovarianceDataFileValidation(classical, Delimiter.COMMA);
        v2.setCommentMarker("//");
        List<ValidationResult> r2 = v2.validate();

        Assert.assertTrue(r2.stream().noneMatch(r -> r.getCode() == ValidationCode.WARNING));
        Assert.assertTrue(r2.stream().noneMatch(r -> r.getCode() == ValidationCode.ERROR));

        Path broken = write("var,X1,X2\nX2,1.5,0.3\nX1,0.3,2.0\n");
        FullCovarianceDataFileValidation v3 = new FullCovarianceDataFileValidation(broken, Delimiter.COMMA);
        v3.setCommentMarker("//");
        List<ValidationResult> r3 = v3.validate();

        Assert.assertTrue(r3.stream().anyMatch(r -> r.getCode() == ValidationCode.ERROR));
    }

    /**
     * The exact-symmetry check is preserved from the legacy reader.
     */
    @Test
    public void testAsymmetryRejected() throws IOException {
        Path file = write("100\nX1,X2\n2.0,0.3\n0.4,1.5\n");

        try {
            read(file, Delimiter.COMMA);
            Assert.fail("Expected a DataReaderException for the asymmetric matrix.");
        } catch (DataReaderException e) {
            Assert.assertTrue(e.getMessage().contains("Non-symmetric"));
        }
    }
}
