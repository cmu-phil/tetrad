package edu.cmu.tetrad.cli.util;

import edu.cmu.tetrad.cli.search.FgsContinuous;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Author : Jeremy Espino MD
 * Created  8/1/16 3:28 PM
 */
public class JsonSerializerTest {

    @ClassRule
    public static TemporaryFolder tmpDir = new TemporaryFolder();

    public JsonSerializerTest() {
    }

    @AfterClass
    public static void tearDownClass() {
        // tmpDir.delete();
    }

    @Test
    public void testSerialize() throws Exception {

        System.out.println(tmpDir.getRoot().getAbsolutePath());

        Path dataFile = Paths.get("causal-cmd","test", "data", "diff_delim", "sim_data_20vars_100cases.txt");

                String data = dataFile.toAbsolutePath().toString();
                String delimiter = "\t";
                String dirOut = tmpDir.newFolder("fgs").toString();
                String outputPrefix = "fgs";
                String[] args = {
                    "--data", data,
                    "--delimiter", delimiter,
                    "--out", dirOut,
                    "--verbose",
                    "--json",
                    "--output-prefix", outputPrefix
                };
                FgsContinuous.main(args);


                Path jsonOutFile = Paths.get(dirOut, outputPrefix + "_graph.json");
                String errMsg = jsonOutFile.getFileName().toString() + " does not exist.";
                Assert.assertTrue(errMsg, Files.exists(jsonOutFile, LinkOption.NOFOLLOW_LINKS));

    }
}
