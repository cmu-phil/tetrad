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

package edu.pitt.dbmi.data.reader.covariance;

import edu.pitt.dbmi.data.reader.Delimiter;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Dec 7, 2018 4:39:33 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class LowerCovarianceDataFileReaderTest {

    private final Delimiter delimiter = Delimiter.SPACE;

    private final Path[] dataFiles = {
            new File(getClass().getResource("/data/covariance/spartina.txt").getFile()).toPath(),
            new File(getClass().getResource("/data/covariance/quotes_spartina.txt").getFile()).toPath()
    };

    public LowerCovarianceDataFileReaderTest() {
    }

    /**
     * Test of readInData method, of class LowerCovarianceDataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInData() throws IOException {
        for (Path dataFile : this.dataFiles) {
            CovarianceDataReader dataFileReader = new LowerCovarianceDataFileReader(dataFile, this.delimiter);
            String commentMarker = "//";
            dataFileReader.setCommentMarker(commentMarker);
            char quoteCharacter = '"';
            dataFileReader.setQuoteCharacter(quoteCharacter);

            CovarianceData covarianceData = dataFileReader.readInData();

            int numberOfCases = covarianceData.getNumberOfCases();
            long expected = 45;
            long actual = numberOfCases;
            Assert.assertEquals(expected, actual);

            List<String> variables = covarianceData.getVariables();
            expected = 15;
            actual = variables.size();
            Assert.assertEquals(expected, actual);

            double[][] data = covarianceData.getData();
            expected = 15;
            actual = data.length;
            Assert.assertEquals(expected, actual);

            expected = 15;
            actual = data[0].length;
            Assert.assertEquals(expected, actual);
        }
    }

}

