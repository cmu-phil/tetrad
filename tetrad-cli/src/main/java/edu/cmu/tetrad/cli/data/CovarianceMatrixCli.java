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
package edu.cmu.tetrad.cli.data;

import edu.cmu.tetrad.cli.ExtendedCommandLineParser;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.data.BigDataSetUtility;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
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
 * A command-line interface to compute covariance and save it to a file.
 *
 * Jan 8, 2016 12:19:26 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class CovarianceMatrixCli {

    private static final String USAGE = "java -cp tetrad-cli.jar edu.cmu.tetrad.cli.data.CovarianceMatrixCli";

    private static final Options MAIN_OPTIONS = new Options();
    private static final Option HELP_OPTION = new Option("h", "help", false, "Show help.");

    static {
        // added required option
        Option requiredOption = new Option("d", "data", true, "Data file.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        MAIN_OPTIONS.addOption(HELP_OPTION);
        MAIN_OPTIONS.addOption("l", "delimiter", true, "Data file delimiter.");
        MAIN_OPTIONS.addOption("f", "on-the-fly", true, "Compute covariance on the fly.");
        MAIN_OPTIONS.addOption("o", "dir-out", true, "Result directory.");
        MAIN_OPTIONS.addOption("n", "name", true, "Output file name.");
    }

    private static Path dataFile;
    private static boolean onTheFly;
    private static Path dirOut;
    private static char delimiter;
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
            delimiter = Args.getCharacter(cmd.getOptionValue("l", "\t"));
            onTheFly = cmd.hasOption("f");
            dirOut = Args.getPathDir(cmd.getOptionValue("o", "./"), false);
            fileOut = cmd.getOptionValue("n", String.format("%s.cov", dataFile.getFileName().toString()));
        } catch (ParseException | FileNotFoundException | IllegalArgumentException exception) {
            System.err.println(exception.getLocalizedMessage());
            System.exit(127);
        }

        try {
            DataSet dataSet = BigDataSetUtility.readContinuous(dataFile.toFile(), delimiter);

            ICovarianceMatrix covarianceMatrix = (onTheFly)
                    ? new CovarianceMatrixOnTheFly(dataSet)
                    : new CovarianceMatrix(dataSet);
            Path outputFile = Paths.get(dirOut.toString(), fileOut);

            try (PrintWriter stream = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
                DataWriter.writeCovMatrix(covarianceMatrix, stream, NumberFormatUtil.getInstance().getNumberFormat());
            }
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
            System.exit(127);
        }
    }

}
