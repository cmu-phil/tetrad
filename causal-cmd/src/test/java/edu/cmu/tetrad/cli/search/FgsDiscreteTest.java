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
package edu.cmu.tetrad.cli.search;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * Mar 29, 2016 6:21:45 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FgsDiscreteTest {

    @ClassRule
    public static TemporaryFolder tmpDir = new TemporaryFolder();

    public FgsDiscreteTest() {
    }

    @AfterClass
    public static void tearDownClass() {
        tmpDir.delete();
    }

    /**
     * Test of main method, of class FgsDiscrete.
     *
     * @throws IOException
     */
    @Ignore
    @Test
    public void testMain() throws IOException {
        System.out.println("main");

        Path dataFile = Paths.get("test", "data", "diff_delim", "sim_discrete_data_20vars_100cases.csv");

        String data = dataFile.toAbsolutePath().toString();
        String delimiter = ",";
        String dirOut = tmpDir.newFolder("fgs").toString();
        String outputPrefix = "fgs";
        String[] args = {
            "--data", data,
            "--delimiter", delimiter,
            "--out", dirOut,
            "--verbose",
            "--output-prefix", outputPrefix
        };
        FgsDiscrete.main(args);

        Path outFile = Paths.get(dirOut, outputPrefix + ".txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));
    }

}
