/*
 * Copyright (C) 2019 University of Pittsburgh.
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
package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetFileReader;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetReader;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Jan 16, 2020 4:59:57 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class JunctionTreeAlgorithmTest {

    private static final int[][] THREE_NODE_VALUES = {
            {0, 0, 0},
            {0, 0, 1},
            {0, 1, 0},
            {0, 1, 1},
            {1, 0, 0},
            {1, 0, 1},
            {1, 1, 0},
            {1, 1, 1}
    };

    private static void printExampleProof(JunctionTreeAlgorithm jta, int[] values) {
        final int v1 = 0;
        final int v2 = 1;
        final int v3 = 2;

        double v1GivenV2 = jta.getConditionalProbability(v1, values[v1], new int[]{v2}, new int[]{values[v2]});
        double v2Parent = jta.getMarginalProbability(v2, values[v2]);
        double v3Givenv2 = jta.getConditionalProbability(v3, values[v3], new int[]{v2}, new int[]{values[v2]});

        System.out.println(
                Arrays.stream(values)
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining(",")));
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("P(v1=0|v2=0) = %f%n", v1GivenV2);
        System.out.printf("P(v2=0) = %f%n", v2Parent);
        System.out.printf("P(v3=0|v2=0) = %f%n", v3Givenv2);
        System.out.println("-------------------------------------------------");
        System.out.printf("P(v1=0|v2=0)P(v2=0)P(v3=0|v2=0) = %f%n", v1GivenV2 * v2Parent * v3Givenv2);
    }

//    @Ignore
    @Test
    public void testJointProbGivenParents() throws IOException {
        String graphFile = Objects.requireNonNull(this.getClass().getResource("/jta/graph2.txt")).getFile();
        String dataFile = Objects.requireNonNull(this.getClass().getResource("/jta/data2.txt")).getFile();

        Graph graph = readInGraph(Paths.get(graphFile));
        DataModel dataModel = readInDiscreteData(Paths.get(dataFile));

        BayesPm bayesPm = createBayesPm(dataModel, graph);
        BayesIm bayesIm = createEmBayesEstimator(dataModel, bayesPm);

        int x = bayesIm.getNodeIndex(bayesIm.getNode("x"));
        int y = bayesIm.getNodeIndex(bayesIm.getNode("y"));
        int z = bayesIm.getNodeIndex(bayesIm.getNode("z"));
        int w = bayesIm.getNodeIndex(bayesIm.getNode("w"));

        int[] nodes = {x, y};
        int[] values = {0, 0};
        int[] parents = {z, w};
        int[] parentValues = {0, 0};

        JunctionTreeAlgorithm jta = new JunctionTreeAlgorithm(bayesIm);

        double probXYGivenZW = jta.getConditionalProbabilities(nodes, values, parents, parentValues);  // 0.24614443432733896
    }

//    @Ignore
    @Test
    public void testJointProbability() {
        String graphFile = this.getClass().getResource("/jta/graph.txt").getFile();
        String dataFile = this.getClass().getResource("/jta/data.txt").getFile();
        try {
            JunctionTreeAlgorithm jta = getJunctionTreeAlgorithm(graphFile, dataFile);
            for (int[] values : JunctionTreeAlgorithmTest.THREE_NODE_VALUES) {
                JunctionTreeAlgorithmTest.printExampleProof(jta, values);
                System.out.printf("JTA: %f%n", jta.getJointProbabilityAll(values));
                System.out.println();
            }
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

//    @Ignore
    @Test
    public void testJunctionTree() {
        String graphFile = Objects.requireNonNull(this.getClass().getResource("/jta/graph.txt")).getFile();
        String dataFile = Objects.requireNonNull(this.getClass().getResource("/jta/data.txt")).getFile();
        try {
            JunctionTreeAlgorithm jta = getJunctionTreeAlgorithm(graphFile, dataFile);

            DecimalFormat df = new DecimalFormat("#.######");
            df.setRoundingMode(RoundingMode.CEILING);
            final int multiplier = 1000000;

            // P(v1=0|v2=0)
            int iNode = 0;
            int value = 0;
            int[] parents = {1};
            int[] parentValues = {0};
            double conProb = jta.getConditionalProbability(iNode, value, parents, parentValues);
            long actual = (long) (Double.parseDouble(df.format(conProb)) * multiplier);
            long expected = 433334;
            Assert.assertEquals(expected, actual);

            // P(v1=0|v2=1)
            parents[0] = 1;
            parentValues[0] = 1;
            conProb = jta.getConditionalProbability(iNode, value, parents, parentValues);
            actual = (long) (Double.parseDouble(df.format(conProb)) * multiplier);
            expected = 803233;
            Assert.assertEquals(expected, actual);

            // P(v1=1|v2=0)
            value = 1;
            parents[0] = 1;
            parentValues[0] = 0;
            conProb = jta.getConditionalProbability(iNode, value, parents, parentValues);
            actual = (long) (Double.parseDouble(df.format(conProb)) * multiplier);
            expected = 566667;
            Assert.assertEquals(expected, actual);

            // P(v1=1|v2=1)
            parents[0] = 1;
            parentValues[0] = 1;
            conProb = jta.getConditionalProbability(iNode, value, parents, parentValues);
            actual = (long) (Double.parseDouble(df.format(conProb)) * multiplier);
            expected = 196768;
            Assert.assertEquals(expected, actual);

            Node[] nodes = jta.getNodes().toArray(new Node[jta.getNumberOfNodes()]);
            System.out.printf("P(%s=%d%s) = %f%n",
                    nodes[iNode].getName(),
                    value,
                    printString(parents, parentValues, nodes),
                    jta.getConditionalProbability(iNode, value, parents, parentValues));
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

    private JunctionTreeAlgorithm getJunctionTreeAlgorithm(String graphFile, String dataFile) throws IOException {
        Graph graph = readInGraph(Paths.get(graphFile));
        DataModel dataModel = readInDiscreteData(Paths.get(dataFile));

        return new JunctionTreeAlgorithm(graph, dataModel);
    }

    private String printString(int[] parents, int[] parentValues, Node[] nodes) {
        StringBuilder sb = new StringBuilder();

        if (parentValues != null && parentValues.length > 0) {
            sb.append("|");
            int len = parents.length - 1;
            for (int i = 0; i < len; i++) {
                sb.append(String.format("%s=%d,", nodes[parents[i]].getName(), parentValues[i]));
            }
            sb.append(String.format("%s=%d", nodes[parents[len]].getName(), parentValues[len]));
        }

        return sb.toString().trim();
    }

    private BayesPm createBayesPm(DataModel dataModel, Graph graph) {
        Dag dag = new Dag(dataModel.getVariables());
        (new Dag(graph)).getEdges().forEach(edge -> {
            Node node1 = dag.getNode(edge.getNode1().getName());
            Node node2 = dag.getNode(edge.getNode2().getName());
            Endpoint endpoint1 = edge.getEndpoint1();
            Endpoint endpoint2 = edge.getEndpoint2();

            dag.addEdge(new Edge(node1, node2, endpoint1, endpoint2));
        });

        return new BayesPm(dag);
    }

    private BayesIm createEmBayesEstimator(DataModel dataModel, BayesPm bayesPm) {
        return (new EmBayesEstimator(bayesPm, (DataSet) dataModel)).getEstimatedIm();
    }

    private DataModel readInDiscreteData(Path file) throws IOException {
        // specify data properties
        final Delimiter delimiter = Delimiter.TAB;
        final char quoteCharacter = '"';
        final String commentMarker = "//";
        final String missingValueMarker = "*";
        final boolean hasHeader = true;

        // create a data reader specifically for the data
        VerticalDiscreteTabularDatasetReader dataReader = new VerticalDiscreteTabularDatasetFileReader(file, delimiter);
        dataReader.setCommentMarker(commentMarker);
        dataReader.setQuoteCharacter(quoteCharacter);
        dataReader.setMissingDataMarker(missingValueMarker);
        dataReader.setHasHeader(hasHeader);

        // read in the data
        Data data = dataReader.readInData();

        // convert the data read in to Tetrad data model
        return DataConvertUtils.toDataModel(data);
    }

    private Graph readInGraph(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return GraphSaveLoadUtils.readerToGraphTxt(reader);
        }
    }

}
