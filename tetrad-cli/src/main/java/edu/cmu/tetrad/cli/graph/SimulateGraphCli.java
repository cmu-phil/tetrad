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
package edu.cmu.tetrad.cli.graph;

import edu.cmu.tetrad.cli.util.Args;
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
 *
 * Dec 7, 2015 3:46:46 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class SimulateGraphCli {

    private static final String USAGE = "java -cp tetrad-cli.jar edu.cmu.tetrad.cli.graph.SimulateGraphCli";

    private static final Options HELP_OPTIONS = new Options();
    private static final Options MAIN_OPTIONS = new Options();

    static {
        Option helpOption = new Option("h", "help", false, "Show help.");
        HELP_OPTIONS.addOption(helpOption);

        Option requiredOption;
        requiredOption = new Option("v", "var", true, "Number of variables to generate.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        MAIN_OPTIONS.addOption("e", "edge", true, "Number of edges per node. Default is 1.");
        MAIN_OPTIONS.addOption("n", "name", true, "Name of output file.");
        MAIN_OPTIONS.addOption("o", "dir-out", true, "Directory where results is written to. Default is the current working directory");
        MAIN_OPTIONS.addOption(helpOption);
    }

    private static int numOfVariables;
    private static int numOfEdges;
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
            numOfVariables = Args.parseInteger(cmd.getOptionValue("v"));
            numOfEdges = Args.parseInteger(cmd.getOptionValue("e", "1"));
            dirOut = Args.getPathDir(cmd.getOptionValue("o", "./"), false);
            fileName = cmd.getOptionValue("n", String.format("sim_graph_%dvars_%d", numOfVariables, System.currentTimeMillis()));
        } catch (ParseException | FileNotFoundException exception) {
            System.err.println(exception.getMessage());
            return;
        }

        try {
            if (!Files.exists(dirOut)) {
                Files.createDirectory(dirOut);
            }

            Graph graph = GraphFactory.createRandomForwardEdges(numOfVariables, numOfEdges);
            GraphIO.write(graph, Paths.get(dirOut.toString(), fileName + ".graph"));
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

}
