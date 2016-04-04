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
import edu.cmu.tetrad.cli.data.IKnowledgeFactory;
import edu.cmu.tetrad.cli.data.TabularContinuousDataReader;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.cli.util.DateTime;
import edu.cmu.tetrad.cli.util.FileIO;
import edu.cmu.tetrad.cli.util.GraphmlSerializer;
import edu.cmu.tetrad.cli.util.XmlPrint;
import edu.cmu.tetrad.cli.validation.DataValidation;
import edu.cmu.tetrad.cli.validation.NonZeroVariance;
import edu.cmu.tetrad.cli.validation.TabularContinuousData;
import edu.cmu.tetrad.cli.validation.UniqueVariableNames;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.util.DataUtility;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Formatter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fast Greedy Search (FGS) Command-line Interface.
 *
 * Nov 30, 2015 9:18:55 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FgsCli {

    private static final Logger LOGGER = LoggerFactory.getLogger(FgsCli.class);

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
        MAIN_OPTIONS.addOption(null, "penalty-discount", true, "Penalty discount. Default is 4.0");
        MAIN_OPTIONS.addOption(null, "depth", true, "Search depth. Must be an integer >= -1 (-1 means unlimited). Default is -1.");

        // output
        MAIN_OPTIONS.addOption(null, "out", true, "Output directory.");
        MAIN_OPTIONS.addOption(null, "output-prefix", true, "Prefix name of output files.");
        MAIN_OPTIONS.addOption(null, "no-validation-output", false, "No validation output files created.");

        // skip validation
        MAIN_OPTIONS.addOption(null, "skip-unique-var-name", false, "Skip check for unique variable names.");
        MAIN_OPTIONS.addOption(null, "skip-non-zero-variance", false, "Skip check for zero variance variables.");
    }

    private static Path dataFile;
    private static Path knowledgeFile;
    private static Path variableFile;
    private static char delimiter;
    private static double penaltyDiscount;
    private static int depth;
    private static boolean faithfulness;
    private static int numOfThreads;
    private static boolean verbose;
    private static boolean ignoreLinearDependence;
    private static boolean graphML;
    private static boolean validationOutput;
    private static Path dirOut;
    private static String outputPrefix;

    private static boolean skipUniqueVarName;
    private static boolean skipZeroVariance;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0 || Args.hasLongOption(args, "help")) {
            showHelp();
            return;
        }

        try {
            CommandLineParser cmdParser = new DefaultParser();
            CommandLine cmd = cmdParser.parse(MAIN_OPTIONS, args);
            dataFile = Args.getPathFile(cmd.getOptionValue("data"), true);
            knowledgeFile = Args.getPathFile(cmd.getOptionValue("knowledge", null), false);
            variableFile = Args.getPathFile(cmd.getOptionValue("exclude-variables", null), false);
            delimiter = Args.getDelimiterForName(cmd.getOptionValue("delimiter", "tab"));
            penaltyDiscount = Args.getDouble(cmd.getOptionValue("penalty-discount", "4.0"));
            depth = Args.getIntegerMin(cmd.getOptionValue("depth", "-1"), -1);
            faithfulness = cmd.hasOption("faithful");
            numOfThreads = Args.getInteger(cmd.getOptionValue("thread", Integer.toString(Runtime.getRuntime().availableProcessors())));
            verbose = cmd.hasOption("verbose");
            ignoreLinearDependence = cmd.hasOption("ignore-linear-dependence");
            graphML = cmd.hasOption("graphml");
            dirOut = Args.getPathDir(cmd.getOptionValue("out", "."), false);
            outputPrefix = cmd.getOptionValue("output-prefix", String.format("fgs_%s_%d", dataFile.getFileName(), System.currentTimeMillis()));
            validationOutput = !cmd.hasOption("no-validation-output");

            skipUniqueVarName = cmd.hasOption("skip-unique-var-name");
            skipZeroVariance = cmd.hasOption("skip-non-zero-variance");
        } catch (ParseException | FileNotFoundException exception) {
            System.err.println(exception.getLocalizedMessage());
            showHelp();
            System.exit(-127);
        }

        printArgs(System.out);

        Set<String> variables = new HashSet<>();
        try {
            variables.addAll(FileIO.extractUniqueLine(variableFile));
        } catch (IOException exception) {
            String errMsg = String.format("Failed to read variable file '%s'.", variableFile.getFileName());
            System.err.println(errMsg);
            LOGGER.error(errMsg, exception);
            System.exit(-128);
        }

        DataValidation dataValidation = new TabularContinuousData(variables, dataFile, delimiter);
        if (!dataValidation.validate(System.err, verbose)) {
            System.exit(-128);
        }

        try {
            DataReader dataReader = new TabularContinuousDataReader(dataFile, delimiter);
            System.out.printf("%s: Start reading in data.%n", DateTime.printNow());
            LOGGER.info("Start reading in data.");
            DataSet dataSet = dataReader.readInData(variables);
            System.out.printf("%s: End reading in data.%n", DateTime.printNow());
            LOGGER.info("End reading in data.");
            if (!isValid(dataSet, System.err, verbose)) {
                System.exit(-128);
            }

            Graph graph;
            Path outputFile = Paths.get(dirOut.toString(), outputPrefix + ".txt");
            try (PrintStream writer = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
                printInfo(variables, writer);

                Fgs fgs = new Fgs(new CovarianceMatrixOnTheFly(dataSet));
                fgs.setOut(writer);
                fgs.setDepth(depth);
                fgs.setIgnoreLinearDependent(ignoreLinearDependence);
                fgs.setPenaltyDiscount(penaltyDiscount);
                fgs.setNumPatternsToStore(0);  // always set to zero
                fgs.setFaithfulnessAssumed(faithfulness);
                fgs.setParallelism(numOfThreads);
                fgs.setVerbose(verbose);
                if (knowledgeFile != null) {
                    fgs.setKnowledge(IKnowledgeFactory.readInKnowledge(knowledgeFile));
                }
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

    private static boolean isValid(DataSet dataSet, PrintStream writer, boolean verbose) {
        boolean isValid = true;

        String dir = dirOut.toString();
        List<DataValidation> validations = new LinkedList<>();
        if (!skipUniqueVarName) {
            validations.add(new UniqueVariableNames(dataSet, validationOutput ? Paths.get(dir, outputPrefix + "_duplicate_var_name.txt") : null));
        }
        if (!skipZeroVariance) {
            validations.add(new NonZeroVariance(dataSet, numOfThreads, validationOutput ? Paths.get(dir, outputPrefix + "_zero_variance.txt") : null));
        }

        for (DataValidation dataValidation : validations) {
            isValid = dataValidation.validate(writer, verbose) && isValid;
        }

        return isValid;
    }

    private static void printArgs(PrintStream writer) {
        writer.println("================================================================================");
        writer.printf("FGS (%s)\n", DateTime.printNow());
        writer.println("================================================================================");

        Formatter formatter = new Formatter();
        formatter.format("=== Starting FGS: ");
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
        writer.printf("penalty discount = %f%n", penaltyDiscount);
        writer.printf("depth = %d%n", depth);
        writer.printf("faithfulness = %s%n", faithfulness);
        writer.printf("ignore linear dependence = %s%n", ignoreLinearDependence);
        writer.printf("number of threads = %,d%n", numOfThreads);
        writer.printf("verbose = %s%n", verbose);
        writer.printf("delimiter = %s%n", Args.getDelimiterName(delimiter));
        writer.println();

        formatter.format("penalty discount=%f,depth=%s,faithfulness=%s,ignore linear dependence=%s,number of threads=%d,verbose=%s,delimiter=%s",
                penaltyDiscount, depth, faithfulness, ignoreLinearDependence, numOfThreads, verbose, Args.getDelimiterName(delimiter));
        LOGGER.info(formatter.toString());
    }

    private static void printInfo(Set<String> variables, PrintStream writer) throws IOException {
        writer.println("Runtime Parameters:");
        writer.printf("number of threads = %,d%n", numOfThreads);
        writer.printf("verbose = %s%n", verbose);
        writer.println();
        LOGGER.info(String.format("Runtime Parameters: number of threads=%,d,verbose=%s", numOfThreads, verbose));

        writer.println("Algorithm Parameters:");
        writer.printf("penalty discount = %f%n", penaltyDiscount);
        writer.printf("depth = %s%n", depth);
        writer.printf("faithfulness = %s%n", faithfulness);
        writer.printf("ignore linear dependence = %s%n", ignoreLinearDependence);
        writer.println();
        LOGGER.info(String.format("Algorithm Parameters: penalty discount=%f,depth=%s,faithfulness=%s,ignore linear dependence=%s", penaltyDiscount, depth, faithfulness, ignoreLinearDependence));

        if (variableFile != null) {
            writer.println("Variable Exclusion:");
            writer.printf("file = %s%n", variableFile.getFileName());
            writer.printf("variables to exclude = %,d%n", variables.size());
            writer.println();
            LOGGER.info(String.format("Variable Exclusion: file=%s,variables to exclude=%d", variableFile.getFileName(), variables.size()));
        }

        if (knowledgeFile != null) {
            writer.println("Knowledge:");
            writer.printf("file = %s%n", knowledgeFile.getFileName());
            writer.println();
            LOGGER.info(String.format("Knowledge: file=%s", knowledgeFile.getFileName()));
        }

        File datasetFile = dataFile.toFile();
        int numOfCases = DataUtility.countLine(datasetFile) - 1;
        int numOfVars = DataUtility.countColumn(datasetFile, delimiter);
        writer.println("Data File:");
        writer.printf("file = %s%n", dataFile.getFileName());
        writer.printf("cases = %,d%n", numOfCases);
        writer.printf("variables = %,d%n", numOfVars);
        writer.println();
        LOGGER.info(String.format("Data File: file=%s,cases=%d,variables=%d", dataFile.getFileName(), numOfCases, numOfVars));
    }

    private static void showHelp() {
        StringBuilder sb = new StringBuilder("java -jar");
        try {
            JarFile jarFile = new JarFile(FgsCli.class.getProtectionDomain().getCodeSource().getLocation().getPath(), true);
            Manifest manifest = jarFile.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            String artifactId = attributes.getValue("Implementation-Title");
            String version = attributes.getValue("Implementation-Version");
            sb.append(String.format(" %s-%s.jar", artifactId, version));
        } catch (IOException exception) {
            sb.append(" causal-cmd.jar");
        }
        sb.append(" --algorithm fgs");

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(sb.toString(), MAIN_OPTIONS, true);
    }

}
