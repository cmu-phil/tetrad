/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.pitt.dbmi.algo.bayesian.constraint.inference;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Test;

/**
 *
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
        Path casFile = Paths.get(getClass().getResource("/cooper.data/small_data.cas").getFile());
        int[] nodeDimension = readInNodeDimension(casFile);
        int[][] dataset = readInDataset(casFile);

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

    private static int[][] readInDataset(Path casFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(casFile)) {
            int numOfNodes = Integer.parseInt(reader.readLine().trim());

            // skip node dimesion
            reader.readLine();

            int numOfCases = Integer.parseInt(reader.readLine().trim());
            int[][] dataset = new int[numOfCases + 1][numOfNodes + 2];
            Pattern spaceDelim = Pattern.compile("\\s+");
            for (int i = 1; i <= numOfCases; i++) {
                String[] data = spaceDelim.split(reader.readLine().trim());
                int j = 0;
                for (String d : data) {
                    dataset[i][++j] = Integer.parseInt(d);
                }
            }

            return dataset;
        }
    }

    private static int[] readInNodeDimension(Path casFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(casFile)) {
            int numOfNodes = Integer.parseInt(reader.readLine().trim());
            String[] data = reader.readLine().trim().split("\\s+");

            int[] nodeDimension = new int[numOfNodes + 2];
            for (int i = 0; i < data.length; i++) {
                nodeDimension[i + 1] = Integer.parseInt(data[i].trim());
            }

            return nodeDimension;
        }
    }

}
