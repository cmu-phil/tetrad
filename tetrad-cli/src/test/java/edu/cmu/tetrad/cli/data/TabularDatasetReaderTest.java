/*
 * Copyright (C) 2016 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.cli.data;

import edu.cmu.tetrad.cli.util.FileIO;
import edu.cmu.tetrad.data.DataSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 * Feb 3, 2016 12:09:48 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularDatasetReaderTest {

    private final String dataDir = "test/data";

    private final Path txtSimData20vars100casesMac = Paths.get(dataDir, "mac_sim_data_20vars_100cases.txt");
    private final Path txtSimData20vars100cases = Paths.get(dataDir, "sim_data_20vars_100cases.txt");
    private final Path csvSimData20vars100cases = Paths.get(dataDir, "sim_data_20vars_100cases.csv");
    private final Path txtSimData20vars100casesNonuniqueVars = Paths.get(dataDir, "sim_data_21vars_100cases_nonunique_vars.txt");

    private final Path excludedVariables = Paths.get(dataDir, "excluded_variables.txt");

    private final char tabDelimiter = '\t';
    private final char commaDelimiter = ',';
    private final char spaceDelimiter = ' ';

    public TabularDatasetReaderTest() {
    }

    @Ignore
    @Test
    public void testCounts() throws IOException {
        System.out.println("testCounts");

        int expectedLineCount = 101;
        int expectedColumnCount = 20;

        DatasetReader reader = new TabularDatasetReader(txtSimData20vars100casesMac, tabDelimiter);
        int lineCount = reader.countNumberOfLines();
        int columnCount = reader.countNumberOfColumns();
        Assert.assertEquals(lineCount, expectedLineCount);
        Assert.assertEquals(columnCount, expectedColumnCount);

        reader = new TabularDatasetReader(csvSimData20vars100cases, commaDelimiter);
        lineCount = reader.countNumberOfLines();
        columnCount = reader.countNumberOfColumns();
        Assert.assertEquals(lineCount, expectedLineCount);
        Assert.assertEquals(columnCount, expectedColumnCount);
    }

    @Ignore
    @Test
    public void testReadInContinuousData() throws IOException {
        System.out.println("testReadInContinuousData");

        DatasetReader reader = new TabularDatasetReader(txtSimData20vars100cases, tabDelimiter);
        DataSet dataSet = reader.readInContinuousData();

        int numOfRows = dataSet.getNumRows();
        int expectedNumOfRows = 100;
        Assert.assertEquals(numOfRows, expectedNumOfRows);

        int numOfColumns = dataSet.getNumColumns();
        int expectedNumOfColumns = 20;
        Assert.assertEquals(numOfColumns, expectedNumOfColumns);
    }

    @Ignore
    @Test
    public void testReadInContinuousDataWithVariableExclusions() throws IOException {
        System.out.println("testReadInContinuousDataWithVariableExclusions");

        Set<String> excludedVars = FileIO.extractUniqueLine(excludedVariables);

        DatasetReader reader = new TabularDatasetReader(txtSimData20vars100casesNonuniqueVars, tabDelimiter);
        DataSet dataSet = reader.readInContinuousData(excludedVars);
    }

}
