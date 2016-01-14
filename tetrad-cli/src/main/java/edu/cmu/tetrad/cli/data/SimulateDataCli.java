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

import edu.cmu.tetrad.cli.ExtendedCommandLineParser;
import edu.cmu.tetrad.cli.graph.GraphFactory;
import edu.cmu.tetrad.cli.graph.GraphIO;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Simulate Dataset Command-line Interface.
 *
 * Dec 3, 2015 8:51:28 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class SimulateDataCli {

    private static final String USAGE = "java -cp tetrad-cli.jar edu.cmu.tetrad.cli.data.SimulateDataCli";

    private static final Options MAIN_OPTIONS = new Options();
    private static final Option HELP_OPTION = new Option("h", "help", false, "Show help.");

    static {
        // added required option
        Option requiredOption = new Option("c", "case", true, "Number of cases to generate.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        requiredOption = new Option("v", "var", true, "Number of variables to generate.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        MAIN_OPTIONS.addOption(HELP_OPTION);
        MAIN_OPTIONS.addOption("e", "edge", true, "Number of edges per node. Default is 1.");
        MAIN_OPTIONS.addOption("n", "name", true, "Name of output file.");
        MAIN_OPTIONS.addOption("d", "delimiter", true, "Data file delimiter.");
        MAIN_OPTIONS.addOption("g", "graph", false, "Save graph to file.");
        MAIN_OPTIONS.addOption("o", "dir-out", true, "Directory where results is written to. Default is the current working directory");
    }

    private static int numOfCases;
    private static int numOfVariables;
    private static int numOfEdges;
    private static char delimiter;
    private static boolean saveGraph;
    private static Path dirOut;
    private static String outputFileName;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            ExtendedCommandLineParser cmdParser = new ExtendedCommandLineParser(new DefaultParser());
            if (cmdParser.hasOption(HELP_OPTION, args)) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(USAGE, MAIN_OPTIONS, true);
                return;
            }

            CommandLine cmd = cmdParser.parse(MAIN_OPTIONS, args);
            numOfCases = Args.parseInteger(cmd.getOptionValue("c"));
            numOfVariables = Args.parseInteger(cmd.getOptionValue("v"));
            numOfEdges = Args.parseInteger(cmd.getOptionValue("e", "1"));
            delimiter = Args.getCharacter(cmd.getOptionValue("d", "\t"));
            saveGraph = cmd.hasOption("g");
            dirOut = Args.getPathDir(cmd.getOptionValue("o", "./"), false);
            outputFileName = cmd.getOptionValue("n", String.format("sim_data_%dvars_%dcases_%d", numOfVariables, numOfCases, System.currentTimeMillis()));
        } catch (ParseException | FileNotFoundException exception) {
            System.err.println(exception.getMessage());
            return;
        }

        try {
            if (!Files.exists(dirOut)) {
                Files.createDirectory(dirOut);
            }

            Graph graph = GraphFactory.createRandomForwardEdges(numOfVariables, numOfEdges);
            DataSet dataSet = DataSetFactory.buildSemSimulateDataAcyclic(graph, numOfCases);

            DataSetIO.write(dataSet, delimiter, Paths.get(dirOut.toString(), outputFileName + ".txt"));

            if (saveGraph) {
                GraphIO.write(graph, Paths.get(dirOut.toString(), outputFileName + ".graph"));
            }
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

}
