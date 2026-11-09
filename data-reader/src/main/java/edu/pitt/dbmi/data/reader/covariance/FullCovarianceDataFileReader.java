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

import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads a full (square) covariance matrix file. The accepted formats, including the classical Tetrad format
 * (sample size line, variable-name line, then the square matrix) and its tolerated relaxations (missing sample-size
 * line, row names, corner cell), are documented on {@link FullCovarianceFormat}, which does the parsing; this class
 * is a thin wrapper that packages the result as {@link CovarianceData}. When the file does not state a sample size,
 * {@link FullCovarianceFormat#DEFAULT_SAMPLE_SIZE} is assumed and a warning is recorded, retrievable from
 * {@link CovarianceData#getWarnings()}.
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 * @author josephramsey
 * @see FullCovarianceFormat
 */
public class FullCovarianceDataFileReader extends AbstractCovarianceDataFileReader implements CovarianceDataReader {

    /**
     * Constructs a FullCovarianceDataFileReader with the specified data file path and delimiter.
     *
     * @param dataFile  the path to the data file to be read.
     * @param delimiter the delimiter used to parse the data file.
     */
    public FullCovarianceDataFileReader(Path dataFile, Delimiter delimiter) {
        super(dataFile, delimiter);
    }

    /**
     * Reads and processes the covariance data file, constructing a {@link CovarianceData} object containing the
     * number of cases (stated in the file, or assumed with a warning), the variables, and the covariance matrix.
     *
     * @return a {@link CovarianceData} object that encapsulates the number of cases, list of variables, covariance
     * data matrix, and any warnings.
     * @throws IOException if an error occurs while reading the data file.
     */
    @Override
    public CovarianceData readInData() throws IOException {
        FullCovarianceFormat.ParseResult result = FullCovarianceFormat.parse(this.dataFile, this.delimiter,
                this.quoteCharacter, this.commentMarker);

        return new FullCovarianceData(result.numberOfCases(), result.variables(), result.data(), result.warnings());
    }

    private static final class FullCovarianceData implements CovarianceData {

        private final int numberOfCases;
        private final List<String> variables;
        private final double[][] data;
        private final List<String> warnings;

        private FullCovarianceData(int numberOfCases, List<String> variables, double[][] data,
                                   List<String> warnings) {
            this.numberOfCases = numberOfCases;
            this.variables = variables;
            this.data = data;
            this.warnings = warnings;
        }

        /**
         * @return the number of cases in the data.
         */
        @Override
        public int getNumberOfCases() {
            return this.numberOfCases;
        }

        /**
         * @return the number of variables in the data.
         */
        @Override
        public List<String> getVariables() {
            return this.variables;
        }

        /**
         * @return the data in a 2D array.
         */
        @Override
        public double[][] getData() {
            return this.data;
        }

        /**
         * @return the warnings recorded while reading, empty if none.
         */
        @Override
        public List<String> getWarnings() {
            return this.warnings;
        }
    }

}
