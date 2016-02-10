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
package edu.cmu.tetrad.cli.search;

import edu.cmu.tetrad.cli.FileIO;
import edu.cmu.tetrad.cli.SimulatedDatasets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * Nov 30, 2015 9:20:03 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FgsCliTest implements SimulatedDatasets {

    @ClassRule
    public static TemporaryFolder tmpDir = new TemporaryFolder();

    private static final Path DATA_FILE = Paths.get("test", "data", "sim_data_20vars_100cases.csv");
    private static final Path ZERO_VARIANCE_DATA_FILE = Paths.get("test", "data", "zero_variance_sim_data_20vars_100cases.csv");
    private static final Path NON_UNIQUE_DATA_FILE = Paths.get("test", "data", "non_unique_sim_data_21vars_100cases.csv");

    public FgsCliTest() {
    }

    @AfterClass
    public static void tearDownClass() {
        tmpDir.delete();
    }

    /**
     * Test of main method, of class FgsCli.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Ignore
    @Test
    public void testMain() throws IOException {
        System.out.println("main");

        String data = ZERO_VARIANCE_DATA_FILE.toAbsolutePath().toString();
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
        FgsCli.main(args);

        Path outFile = Paths.get(dirOut, outputPrefix + ".txt");
        Path zeroVarOutFile = Paths.get(dirOut, outputPrefix + "_zero-variance.txt");
        Path nonUnique = Paths.get(dirOut, outputPrefix + "_non-unique.txt");
        System.out.println("================================================================================");
        FileIO.printFile(outFile);
        System.out.println("--------------------------------------------------------------------------------");
        if (Files.exists(nonUnique, LinkOption.NOFOLLOW_LINKS)) {
            FileIO.printFile(nonUnique);
        }
        if (Files.exists(zeroVarOutFile, LinkOption.NOFOLLOW_LINKS)) {
            FileIO.printFile(zeroVarOutFile);
        }
        System.out.println("================================================================================");
    }

}
