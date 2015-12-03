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
 * Simulate Dataset Command-line Interface.
 *
 * Dec 3, 2015 8:51:28 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class SimulateDataCli {

    private static final String USAGE = "java -cp tetrad-cli.jar edu.cmu.tetrad.cli.data.SimulateDataCli";

    private static final Options HELP_OPTIONS = new Options();
    private static final Options MAIN_OPTIONS = new Options();

    static {
        Option helpOption = new Option("h", "help", false, "Show help.");
        HELP_OPTIONS.addOption(helpOption);

        Option option;
        option = new Option("c", "case", true, "Number of cases to generate.");
        option.setRequired(true);
        MAIN_OPTIONS.addOption(option);

        option = new Option("m", "var", true, "Number of variables to generate.");
        option.setRequired(true);
        MAIN_OPTIONS.addOption(option);

        MAIN_OPTIONS.addOption("e", "edge", true, "Number of edges per node. Default is 1.");
        MAIN_OPTIONS.addOption("n", "name", true, "Name of output file.");
        MAIN_OPTIONS.addOption("l", "delimiter", true, "Data file delimiter.");
        MAIN_OPTIONS.addOption("o", "dir-out", true, "Directory where results is written to. Default is the current working directory");
        MAIN_OPTIONS.addOption(helpOption);
    }

    private static int numOfCases;
    private static int numOfVariables;
    private static int numOfEdges;
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
            numOfCases = Args.parseInteger(cmd.getOptionValue("c"));
            numOfVariables = Args.parseInteger(cmd.getOptionValue("m"));
            numOfEdges = Args.parseInteger(cmd.getOptionValue("e", "1"));
            delimiter = Args.getCharacter(cmd.getOptionValue("l", "\t"));
            dirOut = Args.getPathDir(cmd.getOptionValue("o", "./"), false);
            fileName = cmd.getOptionValue("n", String.format("sim_data_%dvars_%dcases_%d.txt", numOfVariables, numOfCases, System.currentTimeMillis()));
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

            Path fileOut = Paths.get(dirOut.toString(), fileName);
            DataSetIO.write(dataSet, delimiter, fileOut);
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

}
