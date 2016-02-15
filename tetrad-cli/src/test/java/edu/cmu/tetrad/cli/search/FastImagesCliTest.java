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

import edu.cmu.tetrad.cli.ImageTestData;
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
 * Jan 14, 2016 2:18:36 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FastImagesCliTest implements ImageTestData {

    @ClassRule
    public static TemporaryFolder tmpDir = new TemporaryFolder();

    private static Path[] dataFiles;

    private static Path knowledgeFile;

    public FastImagesCliTest() {
    }

    @BeforeClass
    public static void setUpClass() throws IOException {
        String dataDir = tmpDir.newFolder("data").toString();
        dataFiles = new Path[]{
            Paths.get(dataDir, "img1.txt"),
            Paths.get(dataDir, "img2.txt"),
            Paths.get(dataDir, "img3.txt"),
            Paths.get(dataDir, "img4.txt")
        };

        String[][] data = {
            IMG1_20CASES_9VARS,
            IMG2_20CASES_9VARS,
            IMG3_20CASES_9VARS,
            IMG4_20CASES_9VARS
        };
        for (int i = 0; i < dataFiles.length; i++) {
            Files.write(dataFiles[i], Arrays.asList(data[i]), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
        }

        knowledgeFile = Paths.get(dataDir, "img_knowledge.txt");
        Files.write(knowledgeFile, Arrays.asList(IMG_KNOWLEDGE), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
    }

    @AfterClass
    public static void tearDownClass() {
        tmpDir.delete();
    }

    private static String listFiles(Path[] files) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path file : files) {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                sb.append(file.toAbsolutePath().toString());
                sb.append(',');
            }
        }

        int size = sb.length();
        return (size > 0) ? sb.deleteCharAt(sb.length() - 1).toString() : "";
    }

    /**
     * Test of main method, of class FastImagesCli.
     *
     * @throws IOException
     */
    @Test
    public void testMain() throws IOException {
        System.out.println("main");

        // create folder for results
        String outDir = tmpDir.newFolder("results").toString();
        String outputFileName = "fgs_images";

        String[] args = {
            "-d", listFiles(dataFiles),
            "-o", outDir,
            "-n", outputFileName
        };
        FastImagesCli.main(args);

        Path outFile = Paths.get(outDir, outputFileName + ".txt");
        String errMsg = outFile.getFileName().toString() + " does not exist.";
        Assert.assertTrue(errMsg, Files.exists(outFile, LinkOption.NOFOLLOW_LINKS));
    }

}
