/*
 * Copyright (C) 2015 University of Pittsburgh.
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
import org.junit.Test;

/**
 *
 * Nov 30, 2015 9:20:03 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FgsCliTest extends AbstractAlgorithmTest {

    public FgsCliTest() {
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
            FgsCli.class.getDeclaredField("dataFile"),
            FgsCli.class.getDeclaredField("knowledgeFile"),
            FgsCli.class.getDeclaredField("dirOut"),
            FgsCli.class.getDeclaredField("outputFileName")
        };
        for (Field field : fields) {
            field.setAccessible(true);
            field.set(null, null);
        }

        fields = new Field[]{
            FgsCli.class.getDeclaredField("faithfulness"),
            FgsCli.class.getDeclaredField("verbose"),
            FgsCli.class.getDeclaredField("outputGraphML")
        };
        for (Field field : fields) {
            field.setAccessible(true);
            field.setBoolean(null, false);
        }
    }

    /**
     * Test of main method, of class FgsCli.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Test
    public void testMain() throws IOException {
        System.out.println("main");

        // create dataset file
        String dataDir = tempFolder.newFolder("data").toString();
        Path dataFile = Paths.get(dataDir, "sim_data_20vars_100cases.txt");
        Files.write(dataFile, Arrays.asList(dataset20var100case), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        // create folder for results
        String outDir = tempFolder.newFolder("results").toString();
        String outputFileName = "fgs";

        // run without prior knowledge
        String[] args = {
            "-d", dataFile.toAbsolutePath().toString(),
            "-f",
            "-o", outDir,
            "-n", outputFileName
        };

        FgsCli.main(args);
    }

    /**
     * Test of main method, of class FgsCli with prior knowledge.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Test
    public void testMainPriorKnowledge() throws IOException {
        System.out.println("main: prior knowledge");

        // create dataset file
        String dataDir = tempFolder.newFolder("data").toString();
        Path dataFile = Paths.get(dataDir, "sim_data_20vars_100cases.txt");
        Files.write(dataFile, Arrays.asList(dataset20var100case), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        // create folder for results
        String outDir = tempFolder.newFolder("results").toString();
        String outputFileName = "fgs";

        Path knowledgeFile = Paths.get(dataDir, "knowledge_sim_data_20vars_100cases.txt");
        Files.write(knowledgeFile, Arrays.asList(knowledgeDataset20var100case), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        // run without prior knowledge
        String[] args = {
            "-d", dataFile.toAbsolutePath().toString(),
            "-k", knowledgeFile.toAbsolutePath().toString(),
            "-f",
            "-o", outDir,
            "-n", outputFileName
        };
        FgsCli.main(args);

        Path outFile = Paths.get(outDir, outputFileName + ".txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));
    }

    /**
     * Test of main method, of class FgsCli. Output GraphML.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Test
    public void testMainGraphML() throws IOException {
        System.out.println("main: graphML");

        // create dataset file
        String dataDir = tempFolder.newFolder("data").toString();
        Path dataFile = Paths.get(dataDir, "sim_data_20vars_100cases.txt");
        Files.write(dataFile, Arrays.asList(dataset20var100case), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        // create folder for results
        String outDir = tempFolder.newFolder("results").toString();
        String outputFileName = "fgs";

        // run without prior knowledge
        String[] args = {
            "-d", dataFile.toAbsolutePath().toString(),
            "-f",
            "-g",
            "-o", outDir,
            "-n", outputFileName
        };
        FgsCli.main(args);

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
