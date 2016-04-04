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

import edu.cmu.tetrad.cli.data.DataReader;
import edu.cmu.tetrad.cli.data.VerticalTabularDiscreteDataReader;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.cli.util.DateTime;
import edu.cmu.tetrad.cli.util.GraphmlSerializer;
import edu.cmu.tetrad.cli.util.XmlPrint;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BDeuScore;
import edu.cmu.tetrad.search.Fgs;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Mar 28, 2016 4:10:20 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FgsDiscrete {

    private static final Logger LOGGER = LoggerFactory.getLogger(FgsDiscrete.class);

    private static final Options MAIN_OPTIONS = new Options();

    static {
        // added required option
        Option requiredOption = new Option(null, "data", true, "Data file.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        MAIN_OPTIONS.addOption(null, "knowledge", true, "A file containing prior knowledge.");
        MAIN_OPTIONS.addOption(null, "exclude-variables", true, "A file containing variables to exclude.");
        MAIN_OPTIONS.addOption(null, "delimiter", true, "Data delimiter either comma, semicolon, space, colon, or tab. Default is tab.");
        MAIN_OPTIONS.addOption(null, "faithful", false, "Assume faithfulness.");
        MAIN_OPTIONS.addOption(null, "thread", true, "Number of threads.");
        MAIN_OPTIONS.addOption(null, "ignore-linear-dependence", false, "Ignore linear dependence.");
        MAIN_OPTIONS.addOption(null, "verbose", false, "Print additional information.");
        MAIN_OPTIONS.addOption(null, "graphml", false, "Create graphML output.");
        MAIN_OPTIONS.addOption(null, "help", false, "Show help.");

        // algorithm parameters
        MAIN_OPTIONS.addOption(null, "structure-prior", true, "Structure prior.");
        MAIN_OPTIONS.addOption(null, "sample-prior", true, "Sample prior.");

        // output
        MAIN_OPTIONS.addOption(null, "out", true, "Output directory.");
        MAIN_OPTIONS.addOption(null, "output-prefix", true, "Prefix name of output files.");
        MAIN_OPTIONS.addOption(null, "no-validation-output", false, "No validation output files created.");
    }

    private static Path dataFile;
    private static Path knowledgeFile;
    private static Path variableFile;
    private static char delimiter;
    private static double structurePrior;
    private static double samplePrior;
    private static int depth;
    private static boolean faithfulness;
    private static boolean ignoreLinearDependence;
    private static boolean graphML;
    private static boolean verbose;
    private static int numOfThreads;
    private static Path dirOut;
    private static String outputPrefix;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0 || Args.hasLongOption(args, "help")) {
            Args.showHelp("fgs-continuous", MAIN_OPTIONS);
            return;
        }

        try {
            CommandLineParser cmdParser = new DefaultParser();
            CommandLine cmd = cmdParser.parse(MAIN_OPTIONS, args);
            dataFile = Args.getPathFile(cmd.getOptionValue("data"), true);
            knowledgeFile = Args.getPathFile(cmd.getOptionValue("knowledge", null), false);
            variableFile = Args.getPathFile(cmd.getOptionValue("exclude-variables", null), false);
            delimiter = Args.getDelimiterForName(cmd.getOptionValue("delimiter", "tab"));
            structurePrior = Args.getDouble(cmd.getOptionValue("structure-prior", "1.0"));
            samplePrior = Args.getDouble(cmd.getOptionValue("sample-prior", "1.0"));
            depth = Args.getIntegerMin(cmd.getOptionValue("depth", "-1"), -1);
            faithfulness = cmd.hasOption("faithful");
            ignoreLinearDependence = cmd.hasOption("ignore-linear-dependence");
            graphML = cmd.hasOption("graphml");
            verbose = cmd.hasOption("verbose");
            numOfThreads = Args.getInteger(cmd.getOptionValue("thread", Integer.toString(Runtime.getRuntime().availableProcessors())));
            dirOut = Args.getPathDir(cmd.getOptionValue("out", "."), false);
            outputPrefix = cmd.getOptionValue("output-prefix", String.format("fgs_%s_%d", dataFile.getFileName(), System.currentTimeMillis()));
        } catch (ParseException | FileNotFoundException exception) {
            System.err.println(exception.getLocalizedMessage());
            Args.showHelp("fgs-continuous", MAIN_OPTIONS);
            System.exit(-127);
        }

        printArgs(System.out);

        Set<String> variables = new HashSet<>();

        try {
            DataReader dataReader = new VerticalTabularDiscreteDataReader(dataFile, delimiter);
            System.out.printf("%s: Start reading in data.%n", DateTime.printNow());
            LOGGER.info("Start reading in data.");
            DataSet dataSet = dataReader.readInData(variables);
            System.out.printf("%s: End reading in data.%n", DateTime.printNow());
            LOGGER.info("End reading in data.");

            Graph graph;
            Path outputFile = Paths.get(dirOut.toString(), outputPrefix + ".txt");
            try (PrintStream writer = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
                BDeuScore score = new BDeuScore(dataSet);
                score.setSamplePrior(samplePrior);
                score.setStructurePrior(structurePrior);

                Fgs fgs = new Fgs(score);
                fgs.setVerbose(verbose);
                fgs.setNumPatternsToStore(0);
                fgs.setOut(writer);
                fgs.setFaithfulnessAssumed(faithfulness);
                fgs.setDepth(depth);
                writer.flush();

                System.out.printf("%s: Start search.%n", DateTime.printNow());
                LOGGER.info("Start search.");
                graph = fgs.search();
                System.out.printf("%s: End search.%n", DateTime.printNow());
                LOGGER.info("End search.");
                writer.println();
                writer.println(graph.toString().trim());
                writer.flush();
            }
            if (graphML) {
                Path graphOutputFile = Paths.get(dirOut.toString(), outputPrefix + "_graph.txt");
                String infoMsg = String.format("%s: Writing out GraphML file '%s'.", DateTime.printNow(), graphOutputFile.getFileName().toString());
                System.out.println(infoMsg);
                LOGGER.info(infoMsg);
                try (PrintStream graphWriter = new PrintStream(new BufferedOutputStream(Files.newOutputStream(graphOutputFile, StandardOpenOption.CREATE)))) {
                    XmlPrint.printPretty(GraphmlSerializer.serialize(graph, outputPrefix), graphWriter);
                } catch (Throwable throwable) {
                    String errMsg = String.format("Failed when writting out GraphML file '%s'.", graphOutputFile.getFileName().toString());
                    System.err.println(errMsg);
                    LOGGER.error(errMsg, throwable);
                }
            }
            System.out.printf("%s: FGS finished!  Please see %s for details.%n", DateTime.printNow(), outputFile.getFileName().toString());
            LOGGER.info(String.format("FGS finished!  Please see %s for details.%n", outputFile.getFileName().toString()));
        } catch (Exception exception) {
            LOGGER.error("FGS failed.", exception);
            System.err.printf("%s: FGS failed.  Please see log file for more information.%n", DateTime.printNow());
            System.exit(-128);
        }
    }

    private static void printArgs(PrintStream writer) {
        writer.println("================================================================================");
        writer.printf("FGS Discrete (%s)\n", DateTime.printNow());
        writer.println("================================================================================");

        Formatter formatter = new Formatter();
        formatter.format("=== Starting FGS Discrete: ");
        if (dataFile != null) {
            writer.printf("data = %s%n", dataFile.getFileName());
            formatter.format("data=%s,", dataFile.getFileName());
        }
        if (variableFile != null) {
            writer.printf("excluded variables = %s%n", variableFile.getFileName());
            formatter.format("excluded variables=%s,", variableFile.getFileName());
        }
        if (knowledgeFile != null) {
            writer.printf("knowledge = %s%n", knowledgeFile.getFileName());
            formatter.format("knowledge=%s,", knowledgeFile.getFileName());
        }
        writer.printf("structure prior = %f%n", structurePrior);
        writer.printf("sample prior = %f%n", samplePrior);
        writer.printf("depth = %d%n", depth);
        writer.printf("verbose = %s%n", verbose);
        writer.printf("delimiter = %s%n", Args.getDelimiterName(delimiter));
        writer.println();

        formatter.format("depth=%s,faithfulness=%s,ignore linear dependence=%s,number of threads=%d,verbose=%s,delimiter=%s",
                depth, faithfulness, ignoreLinearDependence, numOfThreads, verbose, Args.getDelimiterName(delimiter));
        LOGGER.info(formatter.toString());
    }

}
