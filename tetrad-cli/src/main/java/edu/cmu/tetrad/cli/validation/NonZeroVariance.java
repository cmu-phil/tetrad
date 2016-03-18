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
package edu.cmu.tetrad.cli.validation;

import edu.cmu.tetrad.cli.util.FileIO;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.stat.RealVariance;
import edu.cmu.tetrad.stat.RealVarianceVectorForkJoin;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

/**
 * Ensure all the variances are non-zero value.
 *
 * Mar 17, 2016 3:12:02 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class NonZeroVariance implements DataValidation {

    private final DataSet dataSet;

    private final int numOfThreads;

    private final Path outputFile;

    /**
     * Constructor.
     *
     * @param dataSet dataset to validate
     * @param numOfThreads
     * @param outputFile file to write out zero-variance variables
     */
    public NonZeroVariance(DataSet dataSet, int numOfThreads, Path outputFile) {
        this.dataSet = dataSet;
        this.numOfThreads = numOfThreads;
        this.outputFile = outputFile;
    }

    @Override
    public boolean validate(PrintStream stderr, boolean verbose) {
        if (stderr == null) {
            stderr = System.err;
        }

        RealVariance variance = new RealVarianceVectorForkJoin(dataSet.getDoubleData().toArray(), numOfThreads);
        double[] varianceVector = variance.compute(true);

        List<String> list = new LinkedList<>();
        List<String> variables = dataSet.getVariableNames();
        int index = 0;
        for (String variable : variables) {
            if (varianceVector[index++] == 0) {
                list.add(variable);
            }
        }

        int size = list.size();
        if (size > 0) {
            if (size == 1) {
                stderr.println("Dataset contains " + size + " variable with zero variance.  Please remove the variable from the dataset or use the '--exclude-variables' option to exclude it.");
            } else {
                stderr.println("Dataset contains " + size + " variables with zero variance.  Please remove the variables from the dataset or use the '--exclude-variables' option to exclude them.");
            }

            if (outputFile != null) {
                try {
                    FileIO.writeLineByLine(list, outputFile);
                    if (size == 1) {
                        stderr.println("The name of the variable with zero variance has been saved to file " + outputFile.getFileName().toString() + ".");
                    } else {
                        stderr.println("The names of the variables with zero variance have been saved to file " + outputFile.getFileName().toString() + ".");
                    }
                } catch (IOException exception) {
                    exception.printStackTrace(System.err);
                }
            }

            if (verbose) {
                if (size == 1) {
                    stderr.println("Variable with zero variance:");
                } else {
                    stderr.println("Variables with zero variance:");
                }
                for (String s : list) {
                    stderr.println(s);
                }
            }
        }

        return size == 0;
    }

}
