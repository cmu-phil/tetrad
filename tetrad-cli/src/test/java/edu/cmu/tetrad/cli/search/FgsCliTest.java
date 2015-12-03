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
 * Nov 30, 2015 9:20:03 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FgsCliTest extends AbstractAlgorithmTest {

    public FgsCliTest() {
    }

    /**
     * Test of main method, of class FgsCli.
     *
     * @throws IOException
     */
    @Test
    public void testMain() throws IOException {
        String dataDir = tempFolder.newFolder("data").toString();
        Path dataFile = Paths.get(dataDir, "sim_data_20vars_100cases.txt");
        Files.write(dataFile, Arrays.asList(sim_data_20vars_100cases), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        String outDir = tempFolder.newFolder("results").toString();
        String fileName = "fgs.txt";

        String[] args = {
            "-d", dataFile.toAbsolutePath().toString(),
            "-o", outDir,
            "-n", fileName,
            "-v"
        };
        FgsCli.main(args);
    }

}
