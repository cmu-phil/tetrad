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
package edu.cmu.tetrad.cli;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.cli.util.AlgorithmCommonTask;
import static edu.cmu.tetrad.cli.util.AlgorithmCommonTask.search;
import static edu.cmu.tetrad.cli.util.AlgorithmCommonTask.writeOutJson;
import static edu.cmu.tetrad.cli.util.AlgorithmCommonTask.writeOutTetradGraphJson;
import edu.cmu.tetrad.cli.util.AppTool;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.cli.validation.DataValidation;
import edu.cmu.tetrad.cli.validation.TabularContinuousData;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.io.DataReader;
import edu.cmu.tetrad.latest.LatestClient;
import edu.cmu.tetrad.util.Parameters;
import java.io.BufferedOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Formatter;
import java.util.List;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Sep 9, 2016 2:01:45 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public abstract class AbstractAlgorithmCli extends AbstractApplicationCli implements AlgorithmCli {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAlgorithmCli.class);

    protected Path dataFile;
    protected Path knowledgeFile;
    protected Path excludedVariableFile;
    protected char delimiter;
    protected boolean verbose;
    protected int numOfThreads;
    protected boolean isSerializeJson;
    protected boolean tetradGraphJson;
    protected Path dirOut;
    protected static String outputPrefix;
    protected boolean validationOutput;
    protected boolean skipLatest;

    public AbstractAlgorithmCli(String[] args) {
        super(args);
        intit();
    }

    public abstract AlgorithmType getAlgorithmType();

    public abstract void printParameterInfos(Formatter fmt);

    public abstract void printValidationInfos(Formatter formatter);

    public abstract List<DataValidation> getDataValidations(DataSet dataSet, Path dirOut, String filePrefix);

    public abstract DataReader getDataReader(Path dataFile, char delimiter);

    public abstract Algorithm getAlgorithm(IKnowledge knowledge);

    private void intit() {
        setOptions();
    }

    @Override
    public void run() {
        AlgorithmType algorithmType = getAlgorithmType();
        if (needsToShowHelp()) {
            showHelp(algorithmType.getCmd());

            return;
        }

        parseOptions();

        String heading = creteHeading(algorithmType);
        String argInfo = createArgsInfo();
        System.out.printf(heading);
        System.out.println(argInfo);
        LOGGER.info(String.format("=== Starting %s: %s", algorithmType.getTitle(), Args.toString(args, ' ')));
        LOGGER.info(argInfo.trim().replaceAll("\n", ",").replaceAll(" = ", "="));

        if (!skipLatest) {
            LatestClient latestClient = LatestClient.getInstance();
            String version = AppTool.jarVersion();
            if (version == null) version = "DEVELOPMENT";
            latestClient.checkLatest("causal-cmd", version);
            System.out.println(latestClient.getLatestResult());
        }

        Set<String> excludedVariables = getExcludedVariables();
        runPreDataValidations(excludedVariables);

        DataSet dataSet = AlgorithmCommonTask.readInDataSet(excludedVariables, dataFile, getDataReader(dataFile, delimiter));
        runDataValidations(dataSet);

        IKnowledge knowledge = AlgorithmCommonTask.readInPriorKnowledge(knowledgeFile);

        Path outputFile = Paths.get(dirOut.toString(), outputPrefix + ".txt");
        try (PrintStream writer = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
            writer.println(heading);
            writer.println(createRunInfo(excludedVariables, dataSet));

            Algorithm algorithm = getAlgorithm(knowledge);
            Parameters parameters = getParameters();
            if (verbose) {
                parameters.set(ParamAttrs.PRINT_STREAM, writer);
            }

            Graph graph = search(dataSet, algorithm, parameters);
            writer.println();
            writer.println(graph.toString());

            if (isSerializeJson) {
                writeOutJson(outputPrefix, graph, Paths.get(dirOut.toString(), outputPrefix + "_graph.json"));
            }

            if (tetradGraphJson) {
                writeOutTetradGraphJson(graph, Paths.get(dirOut.toString(), outputPrefix + ".json"));
            }
        } catch (Exception exception) {
            LOGGER.error("Run algorithm failed.", exception);
            System.exit(-128);
        }
    }

    private String createRunInfo(Set<String> excludedVariables, DataSet dataSet) {
        Formatter fmt = new Formatter();

        fmt.format("Runtime Parameters:%n");
        fmt.format("verbose = %s%n", verbose);
        fmt.format("number of threads = %s%n", numOfThreads);
        fmt.format("%n");

        fmt.format("Dataset:%n");
        fmt.format("file = %s%n", dataFile.getFileName());
        fmt.format("delimiter = %s%n", Args.getDelimiterName(delimiter));
        fmt.format("cases read in = %s%n", dataSet.getNumRows());
        fmt.format("variables read in = %s%n", dataSet.getNumColumns());
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

        fmt.format("Algorithm Parameters:%n");
        printParameterInfos(fmt);
        fmt.format("%n");

        fmt.format("Data Validations:%n");
        printValidationInfos(fmt);

        return fmt.toString();
    }

    private void runDataValidations(DataSet dataSet) {
        boolean isValid = true;
        List<DataValidation> dataValidations = getDataValidations(dataSet, dirOut, outputPrefix);
        for (DataValidation dataValidation : dataValidations) {
            isValid = dataValidation.validate(System.err, verbose) && isValid;
        }

        if (!isValid) {
            System.exit(-128);
        }
    }

    protected void runPreDataValidations(Set<String> excludedVariables) {
        DataValidation dataValidation = new TabularContinuousData(excludedVariables, dataFile, delimiter);
        if (!dataValidation.validate(System.err, verbose)) {
            System.exit(-128);
        }
    }

    protected Set<String> getExcludedVariables() {
        return AlgorithmCommonTask.readInVariables(excludedVariableFile);
    }

    private String createArgsInfo() {
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
        printParameterInfos(fmt);

        printValidationInfos(fmt);

        fmt.format("out = %s%n", dirOut.getFileName().toString());
        fmt.format("output-prefix = %s%n", outputPrefix);
        fmt.format("no-validation-output = %s%n", !validationOutput);

        return fmt.toString();
    }

    private String creteHeading(AlgorithmType algorithmType) {
        Formatter fmt = new Formatter();
        fmt.format("================================================================================%n");
        fmt.format("%s (%s)%n", algorithmType.getTitle(), AppTool.fmtDateNow());
        fmt.format("================================================================================%n");

        return fmt.toString();
    }

    @Override
    public void setCommonRequiredOptions() {
        Option requiredOption = new Option("f", "data", true, "Data file.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);
    }

    @Override
    public void setCommonOptionalOptions() {
        MAIN_OPTIONS.addOption(null, "knowledge", true, "A file containing prior knowledge.");
        MAIN_OPTIONS.addOption(null, "exclude-variables", true, "A file containing variables to exclude.");
        MAIN_OPTIONS.addOption("d", "delimiter", true, "Data delimiter either comma, semicolon, space, colon, or tab. Default: comma for *.csv, else tab.");
        MAIN_OPTIONS.addOption(null, "verbose", false, "Print additional information.");
        MAIN_OPTIONS.addOption(null, "thread", true, "Number of threads.");
        MAIN_OPTIONS.addOption(null, "json", false, "Create JSON output.");
        MAIN_OPTIONS.addOption(null, "tetrad-graph-json", false, "Create Tetrad Graph JSON output.");
        MAIN_OPTIONS.addOption("o", "out", true, "Output directory.");
        MAIN_OPTIONS.addOption(null, "output-prefix", true, "Prefix name for output files.");
        MAIN_OPTIONS.addOption(null, "no-validation-output", false, "No validation output files created.");
        MAIN_OPTIONS.addOption(null, "help", false, "Show help.");
        MAIN_OPTIONS.addOption(null, "skip-latest", false, "Skip checking for latest software version");
    }

    @Override
    public void parseCommonRequiredOptions(CommandLine cmd) throws Exception {
        dataFile = Args.getPathFile(cmd.getOptionValue("data"), true);
    }

    @Override
    public void parseCommonOptionalOptions(CommandLine cmd) throws Exception {
        knowledgeFile = Args.getPathFile(cmd.getOptionValue("knowledge", null), false);
        excludedVariableFile = Args.getPathFile(cmd.getOptionValue("exclude-variables", null), false);
        delimiter = Args.getDelimiterForName(cmd.getOptionValue("delimiter", dataFile.getFileName().toString().endsWith(".csv") ? "comma" : "tab"));
        verbose = cmd.hasOption("verbose");
        numOfThreads = Args.getInteger(cmd.getOptionValue("thread", Integer.toString(Runtime.getRuntime().availableProcessors())));
        isSerializeJson = cmd.hasOption("json");
        tetradGraphJson = cmd.hasOption("tetrad-graph-json");

        dirOut = Args.getPathDir(cmd.getOptionValue("out", "."), false);
        outputPrefix = cmd.getOptionValue("output-prefix", String.format("%s_%s_%d", getAlgorithmType().getCmd(), dataFile.getFileName(), System.currentTimeMillis()));
        validationOutput = !cmd.hasOption("no-validation-output");
        skipLatest = cmd.hasOption("skip-latest");
    }

}
