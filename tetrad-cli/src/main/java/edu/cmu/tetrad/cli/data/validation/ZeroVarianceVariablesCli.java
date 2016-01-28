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
package edu.cmu.tetrad.cli.data.validation;

import edu.cmu.tetrad.cli.ExtendedCommandLineParser;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.correlation.RealCovariance;
import edu.cmu.tetrad.correlation.RealCovarianceMatrixForkJoinOnTheFly;
import edu.cmu.tetrad.correlation.RealCovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.BigDataSetUtility;
import edu.cmu.tetrad.data.DataSet;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 *
 * Jan 27, 2016 4:59:26 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ZeroVarianceVariablesCli {

    private static final String USAGE = "java -cp tetrad-cli.jar edu.cmu.tetrad.cli.data.validation.ZeroVarianceVariablesCli";

    private static final Options MAIN_OPTIONS = new Options();
    private static final Option HELP_OPTION = new Option("h", "help", false, "Show help.");

    static {
        // added required option
        Option requiredOption = new Option(null, "data", true, "Data file.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        MAIN_OPTIONS.addOption(HELP_OPTION);
        MAIN_OPTIONS.addOption(null, "delimiter", true, "Data delimiter.  Default is tab.");
        MAIN_OPTIONS.addOption(null, "thread", true, "Number of threads.");
        MAIN_OPTIONS.addOption(null, "dir-out", true, "Output directory.");
    }

    private static Path dataFile;
    private static char delimiter;
    private static int numOfThreads;
    private static Path dirOut;

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
            dataFile = Args.getPathFile(cmd.getOptionValue("data"), true);
            delimiter = Args.getCharacter(cmd.getOptionValue("delimiter", "\t"));
            numOfThreads = Args.parseInteger(cmd.getOptionValue("thread", "1"));
            dirOut = Args.getPathDir(cmd.getOptionValue("dir-out", "."), false);
        } catch (ParseException | FileNotFoundException exception) {
            System.err.println(exception.getLocalizedMessage());
            System.exit(127);
        }

        try {
            DataSet dataSet = BigDataSetUtility.readContinuous(dataFile.toFile(), delimiter);

            RealCovariance covariance;
            if (numOfThreads == 1) {
                covariance = new RealCovarianceMatrixOnTheFly(dataSet.getDoubleData().toArray());
            } else {
                covariance = new RealCovarianceMatrixForkJoinOnTheFly(dataSet.getDoubleData().toArray(), numOfThreads);
            }

            double[] lowerTraingleCovariance = covariance.computeLowerTriangle(true);

            Path outputFile = Paths.get(dirOut.toString(), dataFile.getFileName() + "_vars.txt");
            try (PrintWriter writer = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
                writeZeroVarianceVariables(lowerTraingleCovariance, dataSet.getVariableNames(), writer);
            }
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

    private static void writeZeroVarianceVariables(double[] lowerTraingleCovariance, List<String> variables, PrintWriter writer) {
        int col = 0;
        for (String variable : variables) {
            int row = (col * (col + 1)) / 2;
            if (lowerTraingleCovariance[row + col] == 0) {
                writer.println(variable);
            }
            col++;
        }
    }

}
