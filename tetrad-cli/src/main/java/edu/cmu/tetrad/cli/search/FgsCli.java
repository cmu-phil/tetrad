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

import edu.cmu.tetrad.cli.data.DatasetReader;
import edu.cmu.tetrad.cli.data.IKnowledgeFactory;
import edu.cmu.tetrad.cli.data.TabularDatasetReader;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.cli.util.FileIO;
import edu.cmu.tetrad.cli.util.GraphmlSerializer;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.stat.RealVariance;
import edu.cmu.tetrad.stat.RealVarianceVectorForkJoin;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private static final Options MAIN_OPTIONS = new Options();

    static {
        // added required option
        Option requiredOption = new Option(null, "data", true, "Data file.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        MAIN_OPTIONS.addOption(null, "knowledge", true, "A file containing prior knowledge.");
        MAIN_OPTIONS.addOption(null, "exclude-variables", true, "A file containing variables to exclude.");
        MAIN_OPTIONS.addOption(null, "delimiter", true, "Data delimiter. Default is tab.");
        MAIN_OPTIONS.addOption(null, "penalty-discount", true, "Penalty discount. Default is 4.0");
        MAIN_OPTIONS.addOption(null, "depth", true, "Search depth. Must be an integer >= -1 (-1 means unlimited). Default is -1.");
        MAIN_OPTIONS.addOption(null, "faithful", false, "Assume faithfulness.");
        MAIN_OPTIONS.addOption(null, "thread", true, "Number of threads.");
        MAIN_OPTIONS.addOption(null, "ignore-linear-dependence", false, "Ignore linear dependence.");
        MAIN_OPTIONS.addOption(null, "verbose", false, "Print additional information.");
        MAIN_OPTIONS.addOption(null, "graphml", false, "Create graphML output.");
        MAIN_OPTIONS.addOption(null, "out", true, "Output directory.");
        MAIN_OPTIONS.addOption(null, "help", false, "Show help.");
        MAIN_OPTIONS.addOption(null, "no-validation-output", false, "No validation output files created.");
        MAIN_OPTIONS.addOption(null, "output-prefix", true, "Prefix name of output files.");
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
            delimiter = Args.getCharacter(cmd.getOptionValue("delimiter", "\t"));
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
        } catch (ParseException | FileNotFoundException exception) {
            System.err.println(exception.getLocalizedMessage());
            showHelp();
            System.exit(-127);
        }

        try {
            Path outputFile = Paths.get(dirOut.toString(), outputPrefix + ".txt");
            try (PrintStream writer = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
                writer.println("Runtime Parameters:");
                writer.printf("number of threads = %,d\n", numOfThreads);
                writer.printf("verbose = %s\n", verbose);
                writer.println();

                writer.println("Algorithm Parameters:");
                writer.printf("penalty discount = %f\n", penaltyDiscount);
                writer.printf("depth = %s\n", depth);
                writer.printf("faithfulness = %s\n", faithfulness);
                writer.printf("ignore linear dependence = %s\n", ignoreLinearDependence);
                writer.println();

                File datasetFile = dataFile.toFile();
                int numOfCases = DataUtility.countLine(datasetFile) - 1;
                int numOfVars = DataUtility.countColumn(datasetFile, delimiter);
                writer.println("Data File:");
                writer.printf("file = %s\n", dataFile.getFileName());
                writer.printf("cases = %,d\n", numOfCases);
                writer.printf("variables = %,d\n", numOfVars);
                writer.println();

                if (knowledgeFile != null) {
                    writer.println("Knowledge:");
                    writer.printf("file = %s\n", knowledgeFile.getFileName());
                    writer.println();
                }

                Set<String> variables = FileIO.extractUniqueLine(variableFile);
                if (variableFile != null) {
                    writer.println("Variable Exclusion:");
                    writer.printf("file = %s\n", variableFile.getFileName());
                    writer.printf("variables to exclude = %,d\n", variables.size());
                    writer.println();
                }

                DatasetReader datasetReader = new TabularDatasetReader(dataFile, delimiter);
                DataSet dataSet = datasetReader.readInContinuousData(variables);
                writer.println("Dataset Read In:");
                writer.printf("cases = %,d\n", dataSet.getNumRows());
                writer.printf("variables = %,d\n", dataSet.getNumColumns());
                writer.println();

                validate(dataSet, writer);

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

                Graph graph = fgs.search();
                writer.println();
                writer.println(graph.toString().trim());
                writer.flush();

                if (graphML) {
                    Path graphOutputFile = Paths.get(dirOut.toString(), outputPrefix + "_graph.txt");
                    try (PrintStream graphWriter = new PrintStream(new BufferedOutputStream(Files.newOutputStream(graphOutputFile, StandardOpenOption.CREATE)))) {
                        graphWriter.println(GraphmlSerializer.serialize(graph, outputPrefix));
                    }
                }
            }
        } catch (Exception exception) {
            System.err.println(exception.getMessage());
            System.exit(-126);
        }
    }

    private static void validate(DataSet dataSet, PrintStream writer) throws Exception {
        double[][] data = dataSet.getDoubleData().toArray();
        List<String> variables = dataSet.getVariableNames();

        // check for non-unique variables
        Set<String> vars = new HashSet<>();
        Set<String> unique = new HashSet<>();
        variables.forEach(var -> {
            if (unique.contains(var)) {
                vars.add(var);
            } else {
                unique.add(var);
            }
        });
        int size = vars.size();
        if (size > 0) {
            writer.println("Error:");
            writer.printf("non-unique variable = %d\n", size);
            writer.println();
            if (validationOutput) {
                writeToFile(Paths.get(dirOut.toString(), outputPrefix + "_non-unique.txt"), vars);
            }
            throw new Exception("Non-unique variable(s) found.");
        }

        // check for zero-variance
        RealVariance variance = new RealVarianceVectorForkJoin(data, numOfThreads);
        double[] varianceVector = variance.compute(true);
        int index = 0;
        vars.clear();
        for (String variable : variables) {
            if (varianceVector[index++] == 0) {
                vars.add(variable);
            }
        }
        size = vars.size();
        if (size > 0) {
            writer.println("Error:");
            writer.printf("zero-variance = %d\n", size);
            writer.println();
            if (validationOutput) {
                writeToFile(Paths.get(dirOut.toString(), outputPrefix + "_zero-variance.txt"), vars);
            }
            throw new Exception("Zero-variance found.");
        }
    }

    private static void writeToFile(Path outputFile, Set<String> set) {
        try (PrintStream writer = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
            set.forEach(s -> {
                writer.println(s);
            });
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

    private static void showHelp() {
        String cmdLineSyntax = "java -jar tetrad-cli.jar --algorithm fgs";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(cmdLineSyntax, MAIN_OPTIONS);
    }

}
