/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.pitt.dbmi.data.reader.Delimiter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The three tabular loaders of SimpleDataLoader read the column names the same way: comment lines are skipped and
 * quotes around a name are removed. The discrete and mixed loaders used to read the names before the comment marker
 * was set, and never set the quote character for them, so that a comment line at the top of the file was taken for
 * the header (leaving one column named after the comment, or an error about the number of values on a line) and
 * quoted names kept their quotes.
 *
 * @author Claude
 */
public class TestSimpleDataLoaderHeader {

    private static final String DISCRETE_ROWS = "a,x,1\nb,y,0\na,y,1\nb,x,0\na,x,1\nb,y,1\n";
    private static final String NUMERIC_ROWS = "1.5,0,2.25\n2.5,1,3.5\n0.5,0,1.75\n3.25,1,4.5\n1.75,0,2.0\n2.0,1,3.0\n";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testCommentLinesBeforeTheHeaderAreSkipped() throws IOException {
        for (String comments : new String[]{
                "// collected in 2024\n",
                "// collected in 2024\n// second note\n",
                "// site, year, wave, batch, notes\n"}) {
            for (String loader : new String[]{"continuous", "discrete", "mixed"}) {
                String rows = loader.equals("discrete") ? DISCRETE_ROWS : NUMERIC_ROWS;
                String header = loader.equals("discrete") ? "color,shape,flag\n" : "height,flag,weight\n";

                DataSet plain = load(loader, header + rows);
                DataSet commented = load(loader, comments + header + rows);

                String label = loader + " after " + comments.trim();
                assertEquals(label, names(plain), names(commented));
                assertEquals(label, 6, commented.getNumRows());
                assertSameValues(label, plain, commented);
            }
        }
    }

    @Test
    public void testACommentLineAmongTheRowsIsSkipped() throws IOException {
        DataSet discrete = load("discrete", "color,shape,flag\na,x,1\n// a note\nb,y,0\na,y,1\nb,x,0\na,x,1\nb,y,1\n");
        assertEquals(List.of("color", "shape", "flag"), names(discrete));
        assertEquals(6, discrete.getNumRows());

        DataSet mixed = load("mixed", "height,flag,weight\n1.5,0,2.25\n// a note\n2.5,1,3.5\n0.5,0,1.75\n3.25,1,4.5\n"
                                      + "1.75,0,2.0\n2.0,1,3.0\n");
        assertEquals(List.of("height", "flag", "weight"), names(mixed));
        assertEquals(6, mixed.getNumRows());
    }

    @Test
    public void testQuotesAroundColumnNamesAreRemoved() throws IOException {
        assertEquals(List.of("height", "flag", "weight"),
                names(load("continuous", "\"height\",\"flag\",\"weight\"\n" + NUMERIC_ROWS)));
        assertEquals(List.of("color", "shape", "flag"),
                names(load("discrete", "\"color\",\"shape\",\"flag\"\n" + DISCRETE_ROWS)));
        assertEquals(List.of("height", "flag", "weight"),
                names(load("mixed", "\"height\",\"flag\",\"weight\"\n" + NUMERIC_ROWS)));
    }

    @Test
    public void testAQuotedColumnNameMayContainTheDelimiter() throws IOException {
        assertEquals(List.of("height, cm", "flag", "weight"),
                names(load("continuous", "\"height, cm\",\"flag\",\"weight\"\n" + NUMERIC_ROWS)));
        assertEquals(List.of("color, main", "shape", "flag"),
                names(load("discrete", "\"color, main\",\"shape\",\"flag\"\n" + DISCRETE_ROWS)));
        assertEquals(List.of("height, cm", "flag", "weight"),
                names(load("mixed", "\"height, cm\",\"flag\",\"weight\"\n" + NUMERIC_ROWS)));
    }

    @Test
    public void testTheKindsOfColumnAreUnchanged() throws IOException {
        DataSet discrete = load("discrete", "// a note\ncolor,shape,flag\n" + DISCRETE_ROWS);
        for (Node node : discrete.getVariables()) assertTrue(node.getName(), node instanceof DiscreteVariable);
        assertEquals(List.of("a", "b"), ((DiscreteVariable) discrete.getVariable("color")).getCategories());

        // With at most three categories counted as discrete, only the 0/1 column is discrete.
        DataSet mixed = load("mixed", "// a note\nheight,flag,weight\n" + NUMERIC_ROWS);
        assertTrue(mixed.getVariable("height") instanceof ContinuousVariable);
        assertTrue(mixed.getVariable("flag") instanceof DiscreteVariable);
        assertTrue(mixed.getVariable("weight") instanceof ContinuousVariable);
        assertEquals(3.25, mixed.getDouble(3, 0), 0.0);
    }

    @Test
    public void testTheFirstColumnCanStillBeLeftOutAfterAComment() throws IOException {
        File file = write("// a note\nid,color,shape\n1,a,x\n2,b,y\n3,a,y\n4,b,x\n");
        DataSet discrete = SimpleDataLoader.loadDiscreteData(file, "//", '"', "*", true, Delimiter.COMMA, true);
        assertEquals(List.of("color", "shape"), names(discrete));
        assertEquals(4, discrete.getNumRows());

        file = write("// a note\nid,height,flag\n1,1.5,0\n2,2.5,1\n3,0.5,0\n4,3.25,1\n");
        DataSet mixed = SimpleDataLoader.loadMixedData(file, "//", '"', "*", true, 3, Delimiter.COMMA, true);
        assertEquals(List.of("height", "flag"), names(mixed));
        assertEquals(4, mixed.getNumRows());
    }

    // ---- helpers ----

    private DataSet load(String loader, String contents) throws IOException {
        File file = write(contents);

        return switch (loader) {
            case "continuous" ->
                    SimpleDataLoader.loadContinuousData(file, "//", '"', "*", true, Delimiter.COMMA, false);
            case "discrete" -> SimpleDataLoader.loadDiscreteData(file, "//", '"', "*", true, Delimiter.COMMA, false);
            default -> SimpleDataLoader.loadMixedData(file, "//", '"', "*", true, 3, Delimiter.COMMA, false);
        };
    }

    private File write(String contents) throws IOException {
        File file = this.folder.newFile();
        Files.writeString(file.toPath(), contents);
        return file;
    }

    private static List<String> names(DataSet dataSet) {
        List<String> names = new ArrayList<>();
        for (Node node : dataSet.getVariables()) names.add(node.getName());
        return names;
    }

    private static void assertSameValues(String label, DataSet expected, DataSet actual) {
        assertEquals(label, expected.getNumRows(), actual.getNumRows());
        assertEquals(label, expected.getNumColumns(), actual.getNumColumns());

        for (int r = 0; r < expected.getNumRows(); r++) {
            for (int c = 0; c < expected.getNumColumns(); c++) {
                assertEquals(label + " at row " + r + ", column " + c, expected.getObject(r, c),
                        actual.getObject(r, c));
            }
        }
    }
}
