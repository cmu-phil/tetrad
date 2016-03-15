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

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * Mar 4, 2016 4:42:29 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularContinuousDataTest {

    @ClassRule
    public static TemporaryFolder tmpDir = new TemporaryFolder();

    private static final Path goodFile = Paths.get("test", "data", "diff_delim", "sim_data_20vars_100cases.csv");
    private static final Path badFile = Paths.get("test", "data", "missing_var_name_and_value", "sim_data_20vars_100cases.csv");

    public TabularContinuousDataTest() {
    }

    @AfterClass
    public static void tearDownClass() {
        tmpDir.delete();
    }

    /**
     * Test of validate method, of class TabularContinuousData.
     */
    @Test
    public void testValidate() {
        System.out.println("validate: TabularContinuousDataTest");

        char delimiter = ',';
        DataValidation dataValidation = new TabularContinuousData(badFile, delimiter);
        Assert.assertTrue(!dataValidation.validate(System.err, false));

        dataValidation = new TabularContinuousData(goodFile, delimiter);
        Assert.assertTrue(dataValidation.validate(System.err, false));
    }

}
