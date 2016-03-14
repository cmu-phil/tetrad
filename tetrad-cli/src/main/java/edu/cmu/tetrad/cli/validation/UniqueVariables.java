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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 *
 * Mar 3, 2016 2:21:19 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class UniqueVariables implements DataValidation {

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
    public UniqueVariables(DataSet dataSet, int numOfThreads, Path outputFile) {
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

        Set<Integer> set = new HashSet<>();
        int size = varianceVector.length;
        int lastIndex = size - 1;
        for (int i = 0; i < lastIndex; i++) {
            for (int j = i + 1; j < size; j++) {
                if (varianceVector[i] == varianceVector[j]) {
                    set.add(i);
                    set.add(j);
                }
            }
        }

        size = set.size();
        if (size > 0) {
            int[] indices = new int[set.size()];
            int index = 0;
            for (Integer i : set) {
                indices[index++] = i;
            }
            Arrays.sort(indices);

            List<String> list = new LinkedList<>();
            List<String> variables = dataSet.getVariableNames();
            index = 0;
            int count = 0;
            for (String variable : variables) {
                if (count == indices[index]) {
                    list.add(variable);
                    index++;
                    if (index >= indices.length) {
                        break;
                    }
                }
                count++;
            }

            if (size == 1) {
                stderr.printf("Dataset contains %d non-unique variable.", size);
            } else {
                stderr.printf("Dataset contains %d non-unique variables.", size);
            }
            if (outputFile != null) {
                try {
                    FileIO.writeLineByLine(list, outputFile);
                    if (size == 1) {
                        stderr.printf("  Variable name has been saved to file %s.", outputFile.getFileName().toString());
                    } else {
                        stderr.printf("  Variable names have been saved to file %s.", outputFile.getFileName().toString());
                    }
                } catch (IOException exception) {
                    exception.printStackTrace(System.err);
                }
            }
            stderr.println();

            if (verbose) {
                if (size == 1) {
                    stderr.println("Non-unique variable:");
                } else {
                    stderr.println("Non-unique variables:");
                }
                for (String s : list) {
                    stderr.println(s);
                }
            }
        }

        return size == 0;
    }

}
