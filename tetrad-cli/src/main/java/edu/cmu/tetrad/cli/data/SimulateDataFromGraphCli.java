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

import edu.cmu.tetrad.cli.graph.GraphFactory;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Command-line interface for simulating dataset from graph.
 *
 * Dec 8, 2015 2:36:13 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class SimulateDataFromGraphCli {

    private static final String USAGE = "java -cp tetrad-cli.jar edu.cmu.tetrad.cli.data.SimulateDataFromGraphCli";

    private static final Options HELP_OPTIONS = new Options();
    private static final Options MAIN_OPTIONS = new Options();

    static {
        Option helpOption = new Option("h", "help", false, "Show help.");
        HELP_OPTIONS.addOption(helpOption);

        Option requiredOption;
        requiredOption = new Option("g", "graph", true, "Graph text file.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        requiredOption = new Option("c", "case", true, "Number of cases to generate.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        MAIN_OPTIONS.addOption("d", "delimiter", true, "Data file delimiter.");
        MAIN_OPTIONS.addOption("n", "name", true, "Name of output file.");
        MAIN_OPTIONS.addOption("o", "dir-out", true, "Directory where results is written to. Default is the current working directory");
        MAIN_OPTIONS.addOption(helpOption);
    }

    private static Path graphFile;
    private static int numOfCases;
    private static char delimiter;
    private static Path dirOut;
    private static String fileName;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            CommandLineParser cmdParser = new DefaultParser();
            CommandLine cmd = cmdParser.parse(HELP_OPTIONS, args, true);
            if (cmd.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(USAGE, MAIN_OPTIONS, true);
                return;
            }

            cmd = cmdParser.parse(MAIN_OPTIONS, args);
            graphFile = Args.getPathFile(cmd.getOptionValue("g"), true);
            numOfCases = Args.parseInteger(cmd.getOptionValue("c"));
            delimiter = Args.getCharacter(cmd.getOptionValue("d", "\t"));
            fileName = cmd.getOptionValue("n", null);
            dirOut = Args.getPathDir(cmd.getOptionValue("o", "./"), false);
        } catch (ParseException | FileNotFoundException exception) {
            System.err.println(exception.getMessage());
            return;
        }

        try {
            if (!Files.exists(dirOut)) {
                Files.createDirectory(dirOut);
            }

            Graph graph = GraphFactory.loadGraphAsContinuousVariables(graphFile);
            DataSet dataSet = DataSetFactory.buildSemSimulateDataAcyclic(graph, numOfCases);
            DataSetIO.write(dataSet, delimiter, Paths.get(dirOut.toString(), fileName + ".txt"));
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

}
