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
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ensure that all variable names are unique.
 *
 * Mar 2, 2016 6:07:39 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class UniqueVariableNames implements DataValidation {

    private final DataSet dataSet;

    private final Path outputFile;

    /**
     * Constructor.
     *
     * @param dataSet dataset to validate
     * @param outputFile file to write out non-unique variables
     */
    public UniqueVariableNames(DataSet dataSet, Path outputFile) {
        this.dataSet = dataSet;
        this.outputFile = outputFile;
    }

    @Override
    public boolean validate(PrintStream stderr, boolean verbose) {
        if (stderr == null) {
            stderr = System.err;
        }

        Set<String> unique = new HashSet<>();
        Set<String> nonUnique = new HashSet<>();
        List<String> variableNames = dataSet.getVariableNames();
        for (String variableName : variableNames) {
            if (unique.contains(variableName)) {
                nonUnique.add(variableName);
            } else {
                unique.add(variableName);
            }
        }

        int size = nonUnique.size();
        if (size > 0) {
            if (size == 1) {
                stderr.printf("Dataset contains %d non-unique variable name.", size);
            } else {
                stderr.printf("Dataset contains %d non-unique variable names.", size);
            }
            if (outputFile != null) {
                try {
                    FileIO.writeLineByLine(nonUnique, outputFile);
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
                    stderr.println("Non-unique variable name:");
                } else {
                    stderr.println("Non-unique variable names:");
                }
                for (String s : nonUnique) {
                    stderr.println(s);
                }
            }
        }

        return size == 0;
    }

}
