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
package edu.cmu.tetrad.cli.search;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 * Jan 5, 2016 1:38:11 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class PcStableCliTest extends AbstractAlgorithmTest {

    public PcStableCliTest() {
    }

    /**
     * Reset static variables for each test case.
     *
     * @throws NoSuchFieldException
     * @throws SecurityException
     * @throws IllegalAccessException
     */
    @Before
    public void setUp() throws NoSuchFieldException, SecurityException, IllegalAccessException {
        // clean up static variables
        Field[] fields = {
            PcStableCli.class.getDeclaredField("dataFile"),
            PcStableCli.class.getDeclaredField("covarianceFile"),
            PcStableCli.class.getDeclaredField("knowledgeFile"),
            PcStableCli.class.getDeclaredField("dirOut"),
            PcStableCli.class.getDeclaredField("outputFileName")
        };
        for (Field field : fields) {
            field.setAccessible(true);
            field.set(null, null);
        }

        fields = new Field[]{FgsCli.class.getDeclaredField("verbose")};
        for (Field field : fields) {
            field.setAccessible(true);
            field.setBoolean(null, false);
        }
    }

    /**
     * Test of main method of class PcStableCli.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Ignore
    @Test
    public void testMain() throws IOException {
        System.out.println("main");

        // create dataset file
        String dataDir = tempFolder.newFolder("data").toString();
        Path dataFile = Paths.get(dataDir, "sim_data_20vars_100cases.txt");
        Files.write(dataFile, Arrays.asList(dataset20var100case), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        // create folder for results
        String outDir = tempFolder.newFolder("results").toString();
        String outputFileName = "pcstable";

        // run without prior knowledge
        String[] args = {
            "-d", dataFile.toAbsolutePath().toString(),
            "-o", outDir,
            "-n", outputFileName
        };
        PcStableCli.main(args);

        Path outFile = Paths.get(outDir, outputFileName + ".txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));
    }

    /**
     * Test of main method of class PcStableCli with prior knowledge.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Ignore
    @Test
    public void testMainPriorKnowledge() throws IOException {
        System.out.println("main: prior knowledge");

        // create dataset file
        String dataDir = tempFolder.newFolder("data").toString();
        Path dataFile = Paths.get(dataDir, "sim_data_20vars_100cases.txt");
        Files.write(dataFile, Arrays.asList(dataset20var100case), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        // create folder for results
        String outDir = tempFolder.newFolder("results").toString();
        String outputFileName = "pcstable";

        // create prior knowledge file
        Path knowledgeFile = Paths.get(dataDir, "knowledge_sim_data_20vars_100cases.txt");
        Files.write(knowledgeFile, Arrays.asList(knowledgeDataset20var100case), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        String[] args = {
            "-d", dataFile.toAbsolutePath().toString(),
            "-k", knowledgeFile.toAbsolutePath().toString(),
            "-o", outDir,
            "-n", outputFileName
        };
        PcStableCli.main(args);

        Path outFile = Paths.get(outDir, outputFileName + ".txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));
    }

    /**
     * Test of main method of class PcStableCli. Read in covariance matrix
     * instead of dataset.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Ignore
    @Test
    public void testMainCovariance() throws IOException {
        System.out.println("main: covariance");

        // create covariance matrix file
        String dataDir = tempFolder.newFolder("data").toString();
        Path covarianceFile = Paths.get(dataDir, "sim_data_20vars_100cases.cov");
        Files.write(covarianceFile, Arrays.asList(dataset20var100caseCovariance), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        String outDir = tempFolder.newFolder("results").toString();
        String outputFileName = "pcstable";

        String[] args = {
            "-c", covarianceFile.toAbsolutePath().toString(),
            "-o", outDir,
            "-n", outputFileName
        };
        PcStableCli.main(args);

        Path outFile = Paths.get(outDir, outputFileName + ".txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));
    }

    /**
     * Test of main method, of class PcStableCli. Output GraphML.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Ignore
    @Test
    public void testMainGraphML() throws IOException {
        System.out.println("main: graphML");

        // create dataset file
        String dataDir = tempFolder.newFolder("data").toString();
        Path dataFile = Paths.get(dataDir, "sim_data_20vars_100cases.txt");
        Files.write(dataFile, Arrays.asList(dataset20var100case), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        // create folder for results
        String outDir = tempFolder.newFolder("results").toString();
        String outputFileName = "pcstable";

        String[] args = {
            "-d", dataFile.toAbsolutePath().toString(),
            "-g",
            "-o", outDir,
            "-n", outputFileName
        };
        PcStableCli.main(args);

        Path[] resultFiles = {
            Paths.get(outDir, outputFileName + ".txt"),
            Paths.get(outDir, outputFileName + ".graphml")
        };
        for (Path resultFile : resultFiles) {
            String errMsg = resultFile.getFileName().toString() + " does not exist.";
            Assert.assertTrue(errMsg, Files.exists(resultFile, LinkOption.NOFOLLOW_LINKS));
        }
    }

}
