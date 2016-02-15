/*
 * Copyright (C) 2015 University of Pittsburgh.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * Dec 3, 2015 9:07:13 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class SimulateDataCliTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public SimulateDataCliTest() {
    }

    /**
     * Test of main method, of class SimulateDataCli.
     *
     * @throws IOException
     */
    @Test
    public void testMain() throws IOException {
        System.out.println("main");

        String dirOut = tempFolder.newFolder("simulate").toString();
        String cases = "100";
        String variables = "20";
        String outputFileName = String.format("sim_data_%svars_%scases", variables, cases);
        String[] args = {
            "-c", cases,
            "-v", variables,
            "-d", "\t",
            "-g",
            "-o", dirOut,
            "-n", outputFileName
        };
        SimulateDataCli.main(args);

        Path outFile = Paths.get(dirOut, outputFileName + ".txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));

        outFile = Paths.get(dirOut, outputFileName + ".graph");
        errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));
    }

}
