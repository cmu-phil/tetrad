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

import edu.cmu.tetrad.cli.FileIO;
import edu.cmu.tetrad.cli.graph.GraphFactory;
import edu.cmu.tetrad.cli.graph.GraphIO;
import edu.cmu.tetrad.cli.graph.SimulateGraphCli;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    /**
     * This test is to demonstrate that the GraphUtils.loadGraphTxt() does not
     * read in the correct node type.
     *
     * @throws IOException whenever it fails to read from or write to a file
     */
    @Test
    public void testSimulateDatasetFromGraph() throws IOException {
        System.out.println("testSimulateDatasetFromGraph");

        // create a graph with 9 variables
        int numOfVariables = 9;
        int numOfEdges = 1;
        Graph simGraph = GraphFactory.createRandomForwardEdges(numOfVariables, numOfEdges);

        // write the graph out to a file
        String dirOut = tempFolder.newFolder("simulate_data_graph2").toString();
        String fileName = String.format("sim_data_%dvars.graph", numOfVariables);
        Path graphFile = Paths.get(dirOut, fileName);
        GraphIO.write(simGraph, Paths.get(dirOut, fileName));

        // read the graph back in from a file
        Graph graph = GraphUtils.loadGraphTxt(graphFile.toFile());

        int numOfCases = 20;
        DataSet dataSet = DataSetFactory.buildSemSimulateDataAcyclic(graph, numOfCases);

        char delimiter = '\t';
        fileName = String.format("sim_data_%dvars_%dcases.txt", numOfVariables, numOfCases);
        Path dataFile = Paths.get(dirOut, fileName);
        try (BufferedWriter writer = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
            DataWriter.writeRectangularData(dataSet, writer, delimiter);
        }

        FileIO.printFile(dataFile);
    }

}
