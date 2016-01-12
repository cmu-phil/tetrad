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

import edu.cmu.tetrad.cli.ExtendedCommandLineParser;
import edu.cmu.tetrad.cli.data.IKnowledgeFactory;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.cli.util.GraphmlSerializer;
import edu.cmu.tetrad.data.BigDataSetUtility;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fgs;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Fast Greedy Search (FGS) Command-line Interface.
 *
 * Nov 30, 2015 9:18:55 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FgsCli {

    private static final String USAGE = "java -cp tetrad-cli.jar edu.cmu.tetrad.cli.search.FgsCli";

    private static final Options MAIN_OPTIONS = new Options();
    private static final Option HELP_OPTION = new Option("h", "help", false, "Show help.");

    static {
        // added required option
        Option requiredOption = new Option("d", "data", true, "Data file.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        MAIN_OPTIONS.addOption(HELP_OPTION);
        MAIN_OPTIONS.addOption("k", "knowledge", true, "A file containing prior knowledge.");
        MAIN_OPTIONS.addOption("l", "delimiter", true, "Data file delimiter.");
        MAIN_OPTIONS.addOption("m", "depth", true, "Search depth. Must be an integer >= -1 (-1 means unlimited).");
        MAIN_OPTIONS.addOption("f", "faithfulness", false, "Assume faithfulness.");
        MAIN_OPTIONS.addOption("v", "verbose", false, "Verbose message.");
        MAIN_OPTIONS.addOption("p", "penalty-discount", true, "Penalty discount.");
        MAIN_OPTIONS.addOption("n", "name", true, "Output file name.");
        MAIN_OPTIONS.addOption("o", "dir-out", true, "Result directory.");
        MAIN_OPTIONS.addOption("g", "graphml", false, "Create graphML output.");
    }

    private static Path dataFile;
    private static Path knowledgeFile;
    private static Path dirOut;
    private static char delimiter;
    private static double penaltyDiscount;
    private static int depth;
    private static boolean faithfulness;
    private static boolean verbose;
    private static boolean outputGraphML;
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
            dataFile = Args.getPathFile(cmd.getOptionValue("d"), true);
            knowledgeFile = Args.getPathFile(cmd.getOptionValue("k", null), false);
            delimiter = Args.getCharacter(cmd.getOptionValue("l", "\t"));
            penaltyDiscount = Args.parseDouble(cmd.getOptionValue("p", "4.0"));
            depth = Args.parseInteger(cmd.getOptionValue("m", "3"), -1);
            faithfulness = cmd.hasOption("f");
            verbose = cmd.hasOption("v");
            dirOut = Args.getPathDir(cmd.getOptionValue("o", "./"), false);
            outputFileName = cmd.getOptionValue("n", String.format("fgs_pd%1.2f_d%d_%d", penaltyDiscount, depth, System.currentTimeMillis()));
            outputGraphML = cmd.hasOption("g");
        } catch (ParseException | FileNotFoundException | IllegalArgumentException exception) {
            System.err.println(exception.getLocalizedMessage());
            System.exit(127);
        }

        try {
            DataSet dataSet = BigDataSetUtility.readContinuous(dataFile.toFile(), delimiter);

            Graph graph;
            Path outputFile = Paths.get(dirOut.toString(), outputFileName + ".txt");
            try (PrintStream stream = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
                printOutParameters(stream);
                stream.flush();

                Fgs fgs = new Fgs(new CovarianceMatrixOnTheFly(dataSet));
                fgs.setOut(stream);
                fgs.setDepth(depth);
                fgs.setPenaltyDiscount(penaltyDiscount);
                fgs.setNumPatternsToStore(0);  // always set to zero
                fgs.setFaithfulnessAssumed(faithfulness);
                fgs.setVerbose(verbose);
                if (knowledgeFile != null) {
                    fgs.setKnowledge(IKnowledgeFactory.readInKnowledge(knowledgeFile));
                }
                stream.flush();

                graph = fgs.search();
                stream.println();
                stream.println(graph.toString().trim());
                stream.flush();
            }

            if (outputGraphML) {
                Path graphOutputFile = Paths.get(dirOut.toString(), outputFileName + ".graphml");
                try (PrintStream stream = new PrintStream(new BufferedOutputStream(Files.newOutputStream(graphOutputFile, StandardOpenOption.CREATE)))) {
                    stream.println(GraphmlSerializer.serialize(graph, outputFileName));
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

    private static void printOutParameters(PrintStream stream) {
        stream.println("Datasets:");
        stream.println(dataFile.getFileName().toString());
        stream.println();

        if (knowledgeFile != null) {
            stream.println("Knowledge:");
            stream.println(String.format("knowledge = %s", knowledgeFile.toString()));
            stream.println();
        }

        stream.println("Graph Parameters:");
        stream.println(String.format("penalty discount = %f", penaltyDiscount));
        stream.println(String.format("depth = %s", depth));
        stream.println(String.format("faithfulness = %s", faithfulness));
    }

}
