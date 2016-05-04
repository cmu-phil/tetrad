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
package edu.cmu.tetrad.cli.validation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.io.DataReader;
import edu.cmu.tetrad.io.VerticalTabularDiscreteDataReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * May 4, 2016 4:37:01 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class LimitDiscreteCategoryTest {

    public LimitDiscreteCategoryTest() {
    }

    /**
     * Test of validate method, of class LimitDiscreteCategory.
     *
     * @throws IOException
     */
    @Test
    public void testValidate() throws IOException {
        Path dataFile = Paths.get("test", "data", "diff_delim", "sim_discrete_data_20vars_100cases.csv");
        char delimiter = (dataFile.getFileName().toString().endsWith("csv") ? ',' : '\t');

        DataReader dataReader = new VerticalTabularDiscreteDataReader(dataFile, delimiter);
        DataSet dataSet = dataReader.readInData(variables("X1"));

        int categoryLimit = 5;
        DataValidation dataValidation = new LimitDiscreteCategory(dataSet, categoryLimit);
        Assert.assertTrue(dataValidation.validate(System.err, true));
    }

    private Set<String> variables(String... variables) {
        Set<String> set = new HashSet<>();

        Collections.addAll(set, variables);

        return set;
    }

}
