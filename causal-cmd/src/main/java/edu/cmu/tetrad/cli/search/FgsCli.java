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

import edu.cmu.tetrad.cli.data.IKnowledgeFactory;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.cli.util.DateTime;
import edu.cmu.tetrad.cli.util.FileIO;
import edu.cmu.tetrad.cli.validation.DataValidation;
import edu.cmu.tetrad.cli.validation.NonZeroVariance;
import edu.cmu.tetrad.cli.validation.TabularContinuousData;
import edu.cmu.tetrad.cli.validation.UniqueVariableNames;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.io.DataReader;
import edu.cmu.tetrad.io.TabularContinuousDataReader;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.SemBicScore;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
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
        // added required inputs
        Option requiredOption = new Option("f", "data", true, "Data file.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        // data file options
        MAIN_OPTIONS.addOption("d", "delimiter", true, "Data delimiter either comma, semicolon, space, colon, or tab. Default: comma for *.csv, else tab.");

        // run options
        MAIN_OPTIONS.addOption(null, "verbose", false, "Print additional information.");
        MAIN_OPTIONS.addOption(null, "thread", true, "Number of threads.");

        // algorithm parameters
        MAIN_OPTIONS.addOption(null, "penalty-discount", true, "Penalty discount. Default is 4.0");
        MAIN_OPTIONS.addOption(null, "depth", true, "Search depth. Must be an integer >= -1 (-1 means unlimited). Default is -1.");

        // search options
        MAIN_OPTIONS.addOption(null, "heuristic-speedup", false, "Heuristic speedup. Default is false.");
        MAIN_OPTIONS.addOption(null, "ignore-linear-dependence", false, "Ignore linear dependence.");

        // filter options
        MAIN_OPTIONS.addOption(null, "knowledge", true, "A file containing prior knowledge.");
        MAIN_OPTIONS.addOption(null, "exclude-variables", true, "A file containing variables to exclude.");

        // data validations
        MAIN_OPTIONS.addOption(null, "skip-unique-var-name", false, "Skip check for unique variable names.");
        MAIN_OPTIONS.addOption(null, "skip-non-zero-variance", false, "Skip check for zero variance variables.");

        // output results
        MAIN_OPTIONS.addOption(null, "graphml", false, "Create graphML output.");

        // output
        MAIN_OPTIONS.addOption("o", "out", true, "Output directory.");
        MAIN_OPTIONS.addOption(null, "output-prefix", true, "Prefix name of output files.");
        MAIN_OPTIONS.addOption(null, "no-validation-output", false, "No validation output files created.");

        MAIN_OPTIONS.addOption(null, "help", false, "Show help.");
    }

    private static Path dataFile;
    private static Path knowledgeFile;
    private static Path excludedVariableFile;
    private static char delimiter;
    private static double penaltyDiscount;
    private static int depth;
    private static boolean heuristicSpeedup;
    private static boolean ignoreLinearDependence;
    private static boolean graphML;
    private static boolean verbose;
    private static int numOfThreads;

    private static Path dirOut;
    private static String outputPrefix;
    private static boolean validationOutput;

    private static boolean skipUniqueVarName;
    private static boolean skipZeroVariance;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0 || Args.hasLongOption(args, "help")) {
            Args.showHelp("fgs", MAIN_OPTIONS);
            return;
        }

        parseArgs(args);

        System.out.println("================================================================================");
        System.out.printf("FGS Discrete (%s)%n", DateTime.printNow());
        System.out.println("================================================================================");

        String argInfo = createArgsInfo();
        System.out.println(argInfo);
        LOGGER.info("=== Starting FGS Discrete: " + Args.toString(args, ' '));
        LOGGER.info(argInfo.trim().replaceAll("\n", ",").replaceAll(" = ", "="));

        Set<String> excludedVariables = (excludedVariableFile == null) ? Collections.EMPTY_SET : getExcludedVariables();

        runPreDataValidations(excludedVariables, System.err);
        DataSet dataSet = readInDataSet(excludedVariables);
        runOptionalDataValidations(dataSet, System.err);

        Path outputFile = Paths.get(dirOut.toString(), outputPrefix + ".txt");
        try (PrintStream writer = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
            String runInfo = createOutputRunInfo(excludedVariables, dataSet);
            writer.println(runInfo);
            String[] infos = runInfo.trim().replaceAll("\n\n", ";").split(";");
            for (String s : infos) {
                LOGGER.info(s.trim().replaceAll("\n", ",").replaceAll(":,", ":").replaceAll(" = ", "="));
            }

            Graph graph = runFgs(dataSet, writer);

            writer.println();
            writer.println(graph.toString());
        } catch (IOException exception) {
            LOGGER.error("FGS failed.", exception);
            System.err.printf("%s: FGS failed.%n", DateTime.printNow());
            System.out.println("Please see log file for more information.");
            System.exit(-128);
        }
        System.out.printf("%s: FGS finished!  Please see %s for details.%n", DateTime.printNow(), outputFile.getFileName().toString());
        LOGGER.info(String.format("FGS finished!  Please see %s for details.", outputFile.getFileName().toString()));
    }

    private static Graph runFgs(DataSet dataSet, PrintStream writer) throws IOException {
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
        score.setPenaltyDiscount(penaltyDiscount);

        Fgs fgs = new Fgs(score);
        fgs.setOut(writer);
        fgs.setDepth(depth);
        fgs.setIgnoreLinearDependent(ignoreLinearDependence);
        fgs.setNumPatternsToStore(0);  // always set to zero
        fgs.setHeuristicSpeedup(heuristicSpeedup);
        fgs.setParallelism(numOfThreads);
        fgs.setVerbose(verbose);
        if (knowledgeFile != null) {
            fgs.setKnowledge(IKnowledgeFactory.readInKnowledge(knowledgeFile));
        }

        System.out.printf("%s: Start search.%n", DateTime.printNow());
        LOGGER.info("Start search.");
        Graph graph = fgs.search();
        System.out.printf("%s: End search.%n", DateTime.printNow());
        LOGGER.info("End search.");

        return graph;
    }

    private static String createOutputRunInfo(Set<String> excludedVariables, DataSet dataSet) {
        Formatter fmt = new Formatter();

        fmt.format("Runtime Parameters:%n");
        fmt.format("verbose = %s%n", verbose);
        fmt.format("number of threads = %s%n", numOfThreads);
        fmt.format("%n");

        fmt.format("Dataset:%n");
        fmt.format("file = %s%n", dataFile.getFileName());
        fmt.format("delimiter = %s%n", Args.getDelimiterName(delimiter));
        fmt.format("cases read in = %s%n", dataSet.getNumColumns());
        fmt.format("variables read in = %s%n", dataSet.getNumRows());
        fmt.format("%n");

        if (excludedVariableFile != null || knowledgeFile != null) {
            fmt.format("Filters:%n");
            if (excludedVariableFile != null) {
                fmt.format("excluded variables (%d variables) = %s%n", excludedVariables.size(), excludedVariableFile.getFileName());
            }
            if (knowledgeFile != null) {
                fmt.format("knowledge = %s%n", knowledgeFile.getFileName());
            }
            fmt.format("%n");
        }

        fmt.format("FGS Parameters:%n");
        fmt.format("penalty discount = %f%n", penaltyDiscount);
        fmt.format("depth = %d%n", depth);
        fmt.format("%n");

        fmt.format("Run Options:%n");
        fmt.format("heuristic speedup = %s%n", heuristicSpeedup);
        fmt.format("ignore linear dependence = %s%n", ignoreLinearDependence);
        fmt.format("%n");

        fmt.format("Data Validations:%n");
        fmt.format("skip unique variable name check = %s%n", skipUniqueVarName);
        fmt.format("skip variables with zero variance check = %s%n", skipZeroVariance);
        fmt.format("%n");

        return fmt.toString();
    }

    private static void runOptionalDataValidations(DataSet dataSet, PrintStream writer) {
        String dir = dirOut.toString();
        List<DataValidation> validations = new LinkedList<>();
        if (!skipUniqueVarName) {
            validations.add(new UniqueVariableNames(dataSet, validationOutput ? Paths.get(dir, outputPrefix + "_duplicate_var_name.txt") : null));
        }
        if (!skipZeroVariance) {
            validations.add(new NonZeroVariance(dataSet, numOfThreads, validationOutput ? Paths.get(dir, outputPrefix + "_zero_variance.txt") : null));
        }

        boolean isValid = true;
        for (DataValidation dataValidation : validations) {
            isValid = dataValidation.validate(writer, verbose) && isValid;
        }
        if (!isValid) {
            System.exit(-128);
        }
    }

    private static DataSet readInDataSet(Set<String> excludedVariables) {
        DataSet dataSet = null;

        DataReader dataReader = new TabularContinuousDataReader(dataFile, delimiter);
        try {
            System.out.printf("%s: Start reading in data.%n", DateTime.printNow());
            LOGGER.info("Start reading in data.");
            dataSet = dataReader.readInData(excludedVariables);
            System.out.printf("%s: End reading in data.%n", DateTime.printNow());
            LOGGER.info("End reading in data.");
        } catch (IOException exception) {
            String errMsg = String.format("Failed when reading data file '%s'.", dataFile.getFileName());
            System.err.println(errMsg);
            LOGGER.error(errMsg, exception);
            System.exit(-128);
        }

        return dataSet;
    }

    private static void runPreDataValidations(Set<String> excludedVariables, PrintStream stderr) {
        DataValidation dataValidation = new TabularContinuousData(excludedVariables, dataFile, delimiter);
        if (!dataValidation.validate(stderr, verbose)) {
            System.exit(-128);
        }
    }

    private static Set<String> getExcludedVariables() {
        Set<String> variables = new HashSet<>();

        try {
            System.out.printf("%s: Start reading in excluded variable file.%n", DateTime.printNow());
            LOGGER.info("Start reading in excluded variable file.");
            variables.addAll(FileIO.extractUniqueLine(excludedVariableFile));
            System.out.printf("%s: End reading in excluded variable file.%n", DateTime.printNow());
            LOGGER.info("End reading in excluded variable file.");
        } catch (IOException exception) {
            String errMsg = String.format("Failed when reading excluded variable file '%s'.", excludedVariableFile.getFileName());
            System.err.println(errMsg);
            LOGGER.error(errMsg, exception);
            System.exit(-128);
        }

        return variables;
    }

    private static String createArgsInfo() {
        Formatter fmt = new Formatter();
        if (dataFile != null) {
            fmt.format("data = %s%n", dataFile.getFileName());
        }
        if (excludedVariableFile != null) {
            fmt.format("exclude-variables = %s%n", excludedVariableFile.getFileName());
        }
        if (knowledgeFile != null) {
            fmt.format("knowledge = %s%n", knowledgeFile.getFileName());
        }
        fmt.format("delimiter = %s%n", Args.getDelimiterName(delimiter));
        fmt.format("verbose = %s%n", verbose);
        fmt.format("thread = %s%n", numOfThreads);
        fmt.format("penalty-discount = %f%n", penaltyDiscount);
        fmt.format("ignore-linear-dependence = %s%n", ignoreLinearDependence);
        fmt.format("depth = %d%n", depth);
        fmt.format("heuristic-speedup = %s%n", heuristicSpeedup);
        fmt.format("graphml = %s%n", graphML);

        fmt.format("skip-unique-var-name = %s%n", skipUniqueVarName);
        fmt.format("skip-non-zero-variance = %s%n", skipZeroVariance);

        fmt.format("out = %s%n", dirOut.getFileName().toString());
        fmt.format("output-prefix = %s%n", outputPrefix);
        fmt.format("no-validation-output = %s%n", !validationOutput);

        return fmt.toString();
    }

    private static void parseArgs(String[] args) {
        try {
            CommandLineParser cmdParser = new DefaultParser();
            CommandLine cmd = cmdParser.parse(MAIN_OPTIONS, args);
            dataFile = Args.getPathFile(cmd.getOptionValue("data"), true);
            knowledgeFile = Args.getPathFile(cmd.getOptionValue("knowledge", null), false);
            excludedVariableFile = Args.getPathFile(cmd.getOptionValue("exclude-variables", null), false);
            delimiter = Args.getDelimiterForName(cmd.getOptionValue("delimiter", dataFile.getFileName().toString().endsWith(".csv") ? "comma" : "tab"));
            penaltyDiscount = Args.getDouble(cmd.getOptionValue("penalty-discount", "4.0"));
            depth = Args.getIntegerMin(cmd.getOptionValue("depth", "-1"), -1);
            heuristicSpeedup = cmd.hasOption("heuristic-speedup");
            ignoreLinearDependence = cmd.hasOption("ignore-linear-dependence");
            graphML = cmd.hasOption("graphml");
            verbose = cmd.hasOption("verbose");
            numOfThreads = Args.getInteger(cmd.getOptionValue("thread", Integer.toString(Runtime.getRuntime().availableProcessors())));
            dirOut = Args.getPathDir(cmd.getOptionValue("out", "."), false);
            outputPrefix = cmd.getOptionValue("output-prefix", String.format("fgs_%s_%d", dataFile.getFileName(), System.currentTimeMillis()));
            validationOutput = !cmd.hasOption("no-validation-output");

            skipUniqueVarName = cmd.hasOption("skip-unique-var-name");
            skipZeroVariance = cmd.hasOption("skip-non-zero-variance");
        } catch (ParseException | FileNotFoundException exception) {
            System.err.println(exception.getLocalizedMessage());
            Args.showHelp("fgs", MAIN_OPTIONS);
            System.exit(-127);
        }
    }

}
