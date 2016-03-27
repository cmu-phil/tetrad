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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensure that all variable names are unique.
 *
 * Mar 2, 2016 6:07:39 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class UniqueVariableNames implements DataValidation {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniqueVariableNames.class);

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

        Map<String, Integer> nonuniqueNames = new HashMap<>();
        Set<String> uniqueNames = new HashSet<>();
        List<String> variableNames = dataSet.getVariableNames();
        for (String name : variableNames) {
            if (uniqueNames.contains(name)) {
                Integer count = nonuniqueNames.get(name);
                if (count == null) {
                    count = 2;
                } else {
                    count++;
                }
                nonuniqueNames.put(name, count);
            } else {
                uniqueNames.add(name);
            }
        }

        int size = nonuniqueNames.size();
        if (size > 0) {
            String errMsg = "Dataset contains variables with duplicated names.  Please make sure all variable names are unique.";
            stderr.println(errMsg);
            LOGGER.error(errMsg);
            if (outputFile != null) {
                try {
                    FileIO.writeLineByLine(nonuniqueNames.keySet(), outputFile);
                    errMsg = "Duplicated variable names have been saved to file " + outputFile.getFileName().toString() + ".";
                    stderr.println(errMsg);
                    LOGGER.error(errMsg);
                } catch (IOException exception) {
                    errMsg = String.format("Unable to write variable names to file %s.", outputFile.getFileName().toString());
                    System.err.println(errMsg);
                    LOGGER.error(errMsg, exception);
                }
            }
            if (verbose) {
                Set<String> names = nonuniqueNames.keySet();
                for (String name : names) {
                    errMsg = String.format("There are %d variables with name '%s'.%n", nonuniqueNames.get(name), name);
                    stderr.println(errMsg);
                    LOGGER.error(errMsg);
                }
            }
        }

        return size == 0;
    }

}
