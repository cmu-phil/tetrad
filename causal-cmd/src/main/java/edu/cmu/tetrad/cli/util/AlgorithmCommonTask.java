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
package edu.cmu.tetrad.cli.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.cli.data.IKnowledgeFactory;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.io.DataReader;
import edu.cmu.tetrad.util.Parameters;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Sep 19, 2016 3:15:02 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class AlgorithmCommonTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlgorithmCommonTask.class);

    public static void writeOutTetradGraphJson(Graph graph, Path outputFile) {
        if (graph == null) {
            return;
        }

        try (PrintStream graphWriter = new PrintStream(
                new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
            String fileName = outputFile.getFileName().toString();

            String msg = String.format("Writing out Tetrad Graph Json file '%s'.", fileName);
            System.out.printf("%s: %s%n", DateTime.printNow(), msg);
            LOGGER.info(msg);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            graphWriter.print(gson.toJson(graph));

            msg = String.format("Finished writing out Tetrad Graph Json file '%s'.", fileName);
            System.out.printf("%s: %s%n", DateTime.printNow(), msg);
            LOGGER.info(msg);

        } catch (Throwable throwable) {
            String errMsg = String.format("Failed when writing out Tetrad Graph Json file '%s'.",
                    outputFile.getFileName().toString());
            System.err.println(errMsg);
            LOGGER.error(errMsg, throwable);
        }
    }

    public static void writeOutJson(String graphId, Graph graph, Path outputFile) {
        String fileName = outputFile.getFileName().toString();
        String task = "writing out Json file " + fileName;
        logStartTask(task);
        try (PrintStream graphWriter = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
            JsonSerializer.writeToStream(JsonSerializer.serialize(graph, graphId), graphWriter);
        } catch (Exception exception) {
            logFailedTask(task, exception);
        }
        logEndTask(task);
    }

    public static Graph search(DataSet dataSet, Algorithm algorithm, Parameters parameters) {
        String task = "running algorithm " + algorithm.getDescription();
        logStartTask(task);
        Graph graph = algorithm.search(dataSet, parameters);
        logEndTask(task);

        return graph;
    }

    public static IKnowledge readInPriorKnowledge(Path knowledgeFile) {
        IKnowledge knowledge = null;

        if (knowledgeFile != null) {
            String task = "reading in prior knowledge file " + knowledgeFile.getFileName();
            logStartTask(task);
            try {
                knowledge = IKnowledgeFactory.readInKnowledge(knowledgeFile);
            } catch (IOException exception) {
                logFailedTask(task, exception);
                System.exit(-127);
            }
            logEndTask(task);
        }

        return knowledge;
    }

    public static DataSet readInDataSet(Set<String> excludedVariables, Path dataFile, DataReader dataReader) {
        DataSet dataSet = null;

        String task = "reading in data file " + dataFile.getFileName();
        logStartTask(task);
        try {
            dataSet = excludedVariables.isEmpty() ? dataReader.readInData() : dataReader.readInData(excludedVariables);
        } catch (IOException exception) {
            logFailedTask(task, exception);
            System.exit(-127);
        }
        logEndTask(task);

        return dataSet;
    }

    public static Set<String> readInVariables(Path variableFile) {
        Set<String> variables = new HashSet<>();

        if (variableFile != null) {
            String task = "reading in excluded variable file " + variableFile.getFileName();
            logStartTask(task);
            try {
                variables.addAll(FileIO.extractUniqueLine(variableFile));
            } catch (IOException exception) {
                logFailedTask(task, exception);
                System.exit(-127);
            }
            logEndTask(task);
        }

        return variables;
    }

    private static void logStartTask(String task) {
        String msg = String.format("%s: Start %s.", AppTool.fmtDateNow(), task);
        System.out.println(msg);
        LOGGER.info(String.format("Start %s.", task));
    }

    private static void logEndTask(String task) {
        String msg = String.format("%s: End %s.", AppTool.fmtDateNow(), task);
        System.out.println(msg);
        LOGGER.info(String.format("End %s.", task));
    }

    private static void logFailedTask(String task, Exception exception) {
        String errMsg = String.format("Failed %s.", task);
        System.err.println(errMsg);
        LOGGER.error(errMsg, exception);
    }

}
