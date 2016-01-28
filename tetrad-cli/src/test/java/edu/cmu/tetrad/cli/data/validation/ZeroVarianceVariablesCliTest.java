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
package edu.cmu.tetrad.cli.data.validation;

import edu.cmu.tetrad.cli.SimulatedDatasets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * Jan 27, 2016 4:59:49 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ZeroVarianceVariablesCliTest implements SimulatedDatasets {

    @ClassRule
    public static TemporaryFolder tmpDir = new TemporaryFolder();

    private static final String dataFileName = "sim_20vars100cases_4_zero_vars.txt";

    private static Path dataFile;

    public ZeroVarianceVariablesCliTest() {
    }

    @BeforeClass
    public static void setUpClass() throws IOException {
        String dataDir = tmpDir.newFolder("data").toString();
        dataFile = Paths.get(dataDir, dataFileName);
        Files.write(dataFile, Arrays.asList(SIM_20VARS_100CASES_4_ZERO_VARS), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
    }

    @AfterClass
    public static void tearDownClass() {
        tmpDir.delete();
    }

    /**
     * Test of main method, of class ZeroVarianceVariablesCli.
     *
     * @throws IOException
     */
    @Test
    public void testMain() throws IOException {
        System.out.println("main");

        String dirOut = tmpDir.newFolder("results").toString();

        String[] args = {
            "--data", dataFile.toAbsolutePath().toString(),
            "--delim", "\t",
            "--dir-out", dirOut
        };
        ZeroVarianceVariablesCli.main(args);

        Path outFile = Paths.get(dirOut, dataFile.getFileName() + "_vars.txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));
    }

}
