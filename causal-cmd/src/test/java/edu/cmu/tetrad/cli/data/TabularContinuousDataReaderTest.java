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

import edu.cmu.tetrad.data.DataSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Feb 29, 2016 3:45:45 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularContinuousDataReaderTest {

    public TabularContinuousDataReaderTest() {
    }

    /**
     * Test of readInData method, of class TabularContinuousDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInData() throws IOException {
        System.out.println("readInData");

        Path dataFile = Paths.get("test", "data", "diff_delim", "sim_data_20vars_100cases.txt");
        char delimiter = '\t';
        ContinuousDataReader dataReader = new TabularContinuousDataReader(dataFile, delimiter);
        DataSet dataSet = dataReader.readInData();

        Assert.assertEquals(20, dataSet.getNumColumns());
        Assert.assertEquals(100, dataSet.getNumRows());
    }

    @Test(expected = IOException.class)
    public void testReadInDataWithMissingValues() throws IOException {
        System.out.println("readInData with missing values");

        Path dataFile = Paths.get("test", "data", "missing_values", "sim_data_20vars_100cases.txt");
        char delimiter = '\t';
        ContinuousDataReader dataReader = new TabularContinuousDataReader(dataFile, delimiter);
        dataReader.readInData();
    }

    /**
     * Test of readInData method, of class TabularContinuousDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInData_Set() throws IOException {
        System.out.println("readInData");

        Path dataFile = Paths.get("test", "data", "diff_delim", "sim_data_20vars_100cases.csv");
        char delimiter = ',';

        Set<String> excludedVariables = new HashSet<>();
        excludedVariables.add("X5");
        excludedVariables.add("X9");
        excludedVariables.add("X0");

        ContinuousDataReader dataReader = new TabularContinuousDataReader(dataFile, delimiter);
        DataSet dataSet = dataReader.readInData(excludedVariables);

        Assert.assertEquals(17, dataSet.getNumColumns());
        Assert.assertEquals(100, dataSet.getNumRows());
    }

}
