/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.pitt.dbmi.algo.bayesian.constraint.inference;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Jan 30, 2019 5:47:01 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class BCCausalInferenceTest {

    public BCCausalInferenceTest() {
    }

    /**
     * Test of probConstraint method, of class BCCausalInference.
     *
     * @throws IOException
     */
    @Test
    public void testProbConstraint() throws IOException {
        final Path casFile = Paths.get(getClass().getResource("/cooper.data/small_data.cas").getFile());
        final int[] nodeDimension = readInNodeDimension(casFile);
        final int[][] dataset = readInDataset(casFile);

        float expected = 0.7650975f;
        float result = (float) (new BCCausalInference(nodeDimension, dataset))
                .probConstraint(BCCausalInference.OP.DEPENDENT, 3, 5, new int[]{0});  // returns P(node3 dependent node5 given {} | data)
        Assert.assertEquals(expected, result, 0);

        expected = 0.34093106f;
        result = (float) (new BCCausalInference(nodeDimension, dataset))
                .probConstraint(BCCausalInference.OP.INDEPENDENT, 1, 4, new int[]{2, 2, 3});
        Assert.assertEquals(expected, result, 0);

        expected = 0.9353456f;
        result = (float) (new BCCausalInference(nodeDimension, dataset))
                .probConstraint(BCCausalInference.OP.INDEPENDENT, 1, 5, new int[]{1, 3});
        Assert.assertEquals(expected, result, 0);
    }

    private static int[][] readInDataset(final Path casFile) throws IOException {
        try (final BufferedReader reader = Files.newBufferedReader(casFile)) {
            final int numOfNodes = Integer.parseInt(reader.readLine().trim());

            // skip node dimesion
            reader.readLine();

            final int numOfCases = Integer.parseInt(reader.readLine().trim());
            final int[][] dataset = new int[numOfCases + 1][numOfNodes + 2];
            final Pattern spaceDelim = Pattern.compile("\\s+");
            for (int i = 1; i <= numOfCases; i++) {
                final String[] data = spaceDelim.split(reader.readLine().trim());
                int j = 0;
                for (final String d : data) {
                    dataset[i][++j] = Integer.parseInt(d);
                }
            }

            return dataset;
        }
    }

    private static int[] readInNodeDimension(final Path casFile) throws IOException {
        try (final BufferedReader reader = Files.newBufferedReader(casFile)) {
            final int numOfNodes = Integer.parseInt(reader.readLine().trim());
            final String[] data = reader.readLine().trim().split("\\s+");

            final int[] nodeDimension = new int[numOfNodes + 2];
            for (int i = 0; i < data.length; i++) {
                nodeDimension[i + 1] = Integer.parseInt(data[i].trim());
            }

            return nodeDimension;
        }
    }

}
