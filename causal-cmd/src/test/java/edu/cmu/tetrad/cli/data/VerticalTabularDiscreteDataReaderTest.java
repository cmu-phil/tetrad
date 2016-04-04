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
import org.junit.Test;

/**
 *
 * Apr 1, 2016 3:43:15 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class VerticalTabularDiscreteDataReaderTest {

    public VerticalTabularDiscreteDataReaderTest() {
    }

    /**
     * Test of readInData method, of class VerticalTabularDiscreteDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInData() throws IOException {
        System.out.println("readInData");

        Path dataFile = Paths.get("test", "data", "diff_delim", "sim_discrete_data_20vars_100cases.csv");
        char delimiter = ',';

        DataReader dataReader = new VerticalTabularDiscreteDataReader(dataFile, delimiter);
        DataSet dataSet = dataReader.readInData();
        int numOfColumns = dataSet.getNumColumns();
        int numOfRows = dataSet.getNumRows();

        Assert.assertEquals(20, numOfColumns);
        Assert.assertEquals(100, numOfRows);
    }

    /**
     * Test of readInData method, of class VerticalTabularDiscreteDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInData_Set() throws IOException {
        System.out.println("readInData: exclude variables");

        Path dataFile = Paths.get("test", "data", "diff_delim", "sim_discrete_data_20vars_100cases.csv");
        char delimiter = ',';

        Path variableFile = Paths.get("test", "data", "variables.txt");
        Set<String> excludedVariables = FileIO.extractUniqueLine(variableFile);

        DataReader dataReader = new VerticalTabularDiscreteDataReader(dataFile, delimiter);
        DataSet dataSet = dataReader.readInData(excludedVariables);
        int numOfColumns = dataSet.getNumColumns();
        int numOfRows = dataSet.getNumRows();

        Assert.assertEquals(11, numOfColumns);
        Assert.assertEquals(100, numOfRows);
    }

}
