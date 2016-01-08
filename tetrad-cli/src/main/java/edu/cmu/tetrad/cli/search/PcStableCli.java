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
package edu.cmu.tetrad.cli.search;

import edu.cmu.tetrad.cli.ExtendedCommandLineParser;
import edu.cmu.tetrad.cli.data.IKnowledgeFactory;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.data.BigDataSetUtility;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestChiSquare;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.PcStable;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
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
 *
 * Jan 5, 2016 1:27:48 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class PcStableCli {

    private static final String USAGE = "java -cp tetrad-cli.jar edu.cmu.tetrad.cli.search.PcStableCli";

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
        MAIN_OPTIONS.addOption("a", "alpha", true, "Alpha (significance level in the range [0.0, 1.0]).");
        MAIN_OPTIONS.addOption("v", "verbose", false, "Verbose message.");
        MAIN_OPTIONS.addOption("n", "name", true, "Output file name.");
        MAIN_OPTIONS.addOption("o", "dir-out", true, "Result directory.");
    }

    private static Path dataFile;
    private static Path knowledgeFile;
    private static Path dirOut;
    private static char delimiter;
    private static double alpha;
    private static int depth;
    private static boolean verbose;
    private static String fileOut;

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
            alpha = Args.parseDouble(cmd.getOptionValue("p", "0.05"));
            depth = Args.parseInteger(cmd.getOptionValue("m", "-1"), -1);
            verbose = cmd.hasOption("v");
            dirOut = Args.getPathDir(cmd.getOptionValue("o", "./"), false);
            fileOut = cmd.getOptionValue("n", String.format("pcstable_pd%1.2f_d%d_%d.txt", alpha, depth, System.currentTimeMillis()));
        } catch (ParseException | FileNotFoundException | IllegalArgumentException exception) {
            System.err.println(exception.getLocalizedMessage());
            System.exit(127);
        }

        Path outputFile = Paths.get(dirOut.toString(), fileOut);
        try (PrintStream stream = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
            printOutParameters(stream);
            stream.flush();

            DataSet dataSet = BigDataSetUtility.readContinuous(dataFile.toFile(), delimiter);

            PcStable pc = new PcStable(getIndependenceTest(dataSet, true, alpha));
            pc.setOut(stream);
            pc.setDepth(depth);
            pc.setVerbose(verbose);
            if (knowledgeFile != null) {
                pc.setKnowledge(IKnowledgeFactory.readInKnowledge(knowledgeFile));
            }
            stream.flush();

            Graph graph = pc.search();
            stream.println();
            stream.println(graph.toString().trim());
            stream.flush();
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    private static IndependenceTest getIndependenceTest(DataSet dataSet, boolean continuous, double alpha) {
        if (continuous) {
            return new IndTestFisherZ(dataSet, alpha);
        } else {
            return new IndTestChiSquare(dataSet, alpha);
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
        stream.println(String.format("alpha (significance level) = %f", alpha));
        stream.println(String.format("depth = %s", depth));
    }

}
