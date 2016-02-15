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

import edu.cmu.tetrad.cli.search.AbstractAlgorithmTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import org.junit.Test;

/**
 *
 * Jan 8, 2016 12:41:51 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class CovarianceMatrixCliTest extends AbstractAlgorithmTest {

    public CovarianceMatrixCliTest() {
    }

    /**
     * Test of main method, of class CovarianceMatrixCli.
     *
     * @throws IOException
     */
    @Test
    public void testMain() throws IOException {
        System.out.println("main");

        // create dataset file
        String dataDir = tempFolder.newFolder("data").toString();
        Path dataFile = Paths.get(dataDir, "sim_data_20vars_100cases.txt");
        Files.write(dataFile, Arrays.asList(dataset20var100case), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        // create folder for results
        String outDir = tempFolder.newFolder("results").toString();
        String fileName = "sim_data_20vars_100cases.cov";

        String[] args = {
            "-d", dataFile.toAbsolutePath().toString(),
            "-o", outDir,
            "-n", fileName
        };
        CovarianceMatrixCli.main(args);
    }

}
