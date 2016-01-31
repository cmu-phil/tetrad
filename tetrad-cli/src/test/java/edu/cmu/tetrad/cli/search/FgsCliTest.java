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

import edu.cmu.tetrad.cli.SimulatedDatasets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * Nov 30, 2015 9:20:03 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FgsCliTest implements SimulatedDatasets {

    @ClassRule
    public static TemporaryFolder tmpDir = new TemporaryFolder();

    private static final String dataFileName = "sim_20vars100cases.txt";
    private static final String knowledgeFileName = "sim_20vars100cases_knowledge.txt";
    private static final String variableFileName = "sim_20vars100cases_vars.txt";

    private static Path dataFile;
    private static Path knowledgeFile;
    private static Path variableFile;

    public FgsCliTest() {
    }

    @BeforeClass
    public static void setUpClass() throws IOException {
        String dataDir = tmpDir.newFolder("data").toString();

        dataFile = Paths.get(dataDir, dataFileName);
        Files.write(dataFile, Arrays.asList(SIM_20VARS_100CASES), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        knowledgeFile = Paths.get(dataDir, knowledgeFileName);
        Files.write(knowledgeFile, Arrays.asList(SIM_20VARS_100CASES_KNOWLEDGE), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        variableFile = Paths.get(dataDir, variableFileName);
        Files.write(variableFile, Arrays.asList(SIM_20VARS_100CASES_VARIABLES), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
    }

    @AfterClass
    public static void tearDownClass() {
        tmpDir.delete();
    }

    /**
     * Test of main method, of class FgsCli.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Test
    public void testMain() throws IOException {
        System.out.println("main");

        String dataFilePath = dataFile.toAbsolutePath().toString();
        String penaltyDiscount = "4.0";
        String depth = "-1";
        String prefixOutput = "fgs";
        String outDir = tmpDir.newFolder("fgs").toString();

        String[] args = {
            "--data", dataFilePath,
            "--penalty-discount", penaltyDiscount,
            "--depth", depth,
            "--prefix-out", prefixOutput,
            "--faithful",
            "--verbose",
            "--ignore-linear-dependence",
            "--dir-out", outDir
        };
        FgsCli.main(args);

        Path outFile = Paths.get(outDir, prefixOutput + "_output.txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));
    }

    /**
     * Test of main method, of class FgsCli, with Graph ML.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Test
    public void testMainGraphML() throws IOException {
        System.out.println("main: Graph ML");

        String dataFilePath = dataFile.toAbsolutePath().toString();
        String penaltyDiscount = "2.0";
        String depth = "3";
        String prefixOutput = "fgs";
        String outDir = tmpDir.newFolder("fgs_graphml").toString();

        String[] args = {
            "--data", dataFilePath,
            "--penalty-discount", penaltyDiscount,
            "--depth", depth,
            "--prefix-out", prefixOutput,
            "--graphml",
            "--dir-out", outDir
        };
        FgsCli.main(args);

        Path outFile = Paths.get(outDir, prefixOutput + "_output.txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));

        Path graphOutputFile = Paths.get(outDir, prefixOutput + "_graph.txt");
        errMsg = graphOutputFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(graphOutputFile, LinkOption.NOFOLLOW_LINKS));
    }

    /**
     * Test of main method, of class FgsCli, with knowledge.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Test
    public void testMainKnowledge() throws IOException {
        System.out.println("main: knowledge");

        String dataFilePath = dataFile.toAbsolutePath().toString();
        String knowledgePath = knowledgeFile.toAbsolutePath().toString();
        String penaltyDiscount = "2.0";
        String depth = "3";
        String prefixOutput = "fgs";
        String outDir = tmpDir.newFolder("fgs_knowledge").toString();

        String[] args = {
            "--data", dataFilePath,
            "--knowledge", knowledgePath,
            "--penalty-discount", penaltyDiscount,
            "--depth", depth,
            "--prefix-out", prefixOutput,
            "--dir-out", outDir
        };
        FgsCli.main(args);

        Path outFile = Paths.get(outDir, prefixOutput + "_output.txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));
    }

    /**
     * Test of main method, of class FgsCli, with variable exclusion.
     *
     * @throws IOException whenever unable to read or right to file
     */
    @Test
    public void testMainExcludeVariables() throws IOException {
        System.out.println("main: exclude variables");

        String dataFilePath = dataFile.toAbsolutePath().toString();
        String variableFilePath = variableFile.toAbsolutePath().toString();
        String prefixOutput = "fgs";
        String outDir = tmpDir.newFolder("fgs_var").toString();

        String[] args = {
            "--data", dataFilePath,
            "--prefix-out", prefixOutput,
            "--exclude-variables", variableFilePath,
            "--dir-out", outDir
        };
        FgsCli.main(args);

        Path outFile = Paths.get(outDir, prefixOutput + "_output.txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));
    }

}
