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

import edu.cmu.tetrad.cli.graph.SimulateGraphCli;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * Dec 8, 2015 2:56:54 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class SimulateDataFromGraphCliTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public SimulateDataFromGraphCliTest() {
    }

    /**
     * Test of main method, of class SimulateDataFromGraphCli.
     *
     * @throws IOException
     */
    @Test
    public void testMain() throws IOException {
        System.out.println("main");

        String variables = "15";
        String dirOut = tempFolder.newFolder("simulate_data_graph").toString();
        String fileName = String.format("sim_data_%svars", variables);
        String[] args = {
            "-v", variables,
            "-o", dirOut,
            "-n", fileName
        };
        SimulateGraphCli.main(args);

        Path graphFile = Paths.get(dirOut, fileName + ".graph");

        String cases = "20";
        fileName = String.format("sim_data_%svars_%scases", variables, cases);
        args = new String[]{
            "-g", graphFile.toString(),
            "-c", cases,
            "-o", dirOut,
            "-n", fileName,
            "-d", "\t"
        };
        SimulateDataFromGraphCli.main(args);
    }

}
