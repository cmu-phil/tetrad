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

import edu.cmu.tetrad.cli.data.IKnowledgeFactory;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.data.BigDataSetUtility;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fgs;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
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

    private static final Options HELP_OPTIONS = new Options();
    private static final Options MAIN_OPTIONS = new Options();

    static {
        Option helpOption = new Option("h", "help", false, "Show help.");
        HELP_OPTIONS.addOption(helpOption);

        // added required option
        Option requiredOption = new Option("d", "data", true, "Data file.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        MAIN_OPTIONS.addOption("k", "knowledge", true, "Prior knowledge file.");
        MAIN_OPTIONS.addOption("l", "delimiter", true, "Data file delimiter.");
        MAIN_OPTIONS.addOption("m", "depth", true, "Search depth.");
        MAIN_OPTIONS.addOption("f", "faithfulness", false, "Assume faithfulness.");
        MAIN_OPTIONS.addOption("v", "verbose", false, "Verbose message.");
        MAIN_OPTIONS.addOption("p", "penalty-discount", true, "Penalty discount.");
        MAIN_OPTIONS.addOption("n", "name", true, "Output file name.");
        MAIN_OPTIONS.addOption("o", "dir-out", true, "Result directory.");
        MAIN_OPTIONS.addOption(helpOption);
    }

    private static Path dataFile;
    private static Path knowledgeFile;
    private static Path dirOut;
    private static char delimiter;
    private static double penaltyDiscount;
    private static int depth;
    private static boolean faithfulness;
    private static boolean verbose;
    private static String fileOut;

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

            dataFile = Args.getPathFile(cmd.getOptionValue("d"), true);
            knowledgeFile = Args.getPathFile(cmd.getOptionValue("k", null), false);
            delimiter = Args.getCharacter(cmd.getOptionValue("l", "\t"));
            penaltyDiscount = Args.parseDouble(cmd.getOptionValue("p", "4.0"));
            depth = Args.parseInteger(cmd.getOptionValue("m", "3"), -1);
            faithfulness = cmd.hasOption("f");
            verbose = cmd.hasOption("v");
            dirOut = Args.getPathDir(cmd.getOptionValue("o", "./"), false);
            fileOut = cmd.getOptionValue("n", String.format("fgs_pd%1.2f_d%d_%d.txt", penaltyDiscount, depth, System.currentTimeMillis()));
        } catch (ParseException | FileNotFoundException | IllegalArgumentException exception) {
            System.err.println(exception.getLocalizedMessage());
            return;
        }

        Path outputFile = Paths.get(dirOut.toString(), fileOut);
        try (PrintStream stream = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
            printOutParameters(stream);
            stream.flush();

            DataSet dataSet = BigDataSetUtility.readContinuous(dataFile.toFile(), delimiter);

            Fgs fgs = new Fgs(new CovarianceMatrixOnTheFly(dataSet));
            fgs.setOut(stream);
            fgs.setDepth(depth);
            fgs.setPenaltyDiscount(penaltyDiscount);
            fgs.setNumPatternsToStore(0);  // always set to zero
            fgs.setFaithfulnessAssumed(true);
            fgs.setVerbose(verbose);
            if (knowledgeFile != null) {
                fgs.setKnowledge(IKnowledgeFactory.readInKnowledge(knowledgeFile));
            }
            stream.flush();

            Graph graph = fgs.search();
            stream.println();
            stream.println(graph.toString().trim());
            stream.flush();
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    private static void printOutParameters(PrintStream stream) {
        stream.println("Datasets:");
        stream.println(dataFile.getFileName().toString());

        if (knowledgeFile != null) {
            stream.println();
            stream.println("Knowledge:");
            stream.println(String.format("knowledge = %s", knowledgeFile.toString()));
        }
        stream.println();

        stream.println("Graph Parameters:");
        stream.println(String.format("penalty discount = %f", penaltyDiscount));
        stream.println(String.format("depth = %s", depth));
        stream.println(String.format("faithfulness = %s", faithfulness));

        stream.println();
    }

}
