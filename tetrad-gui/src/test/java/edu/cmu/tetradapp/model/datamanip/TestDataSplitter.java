///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.datamanip.DataSplitter.BinMode;
import edu.cmu.tetradapp.model.datamanip.DataSplitter.Preview;
import edu.cmu.tetradapp.model.datamanip.DataSplitter.Spec;
import edu.cmu.tetradapp.model.datamanip.DataSplitter.SplitDef;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link DataSplitter}, the engine of the Split Data tool.
 */
public class TestDataSplitter {

    /**
     * A small mixed data set: Region (2 categories, with one missing value), Sex (with a category containing a
     * space), X continuous, Y continuous with one missing value.
     */
    private static DataSet mixed() {
        DiscreteVariable region = new DiscreteVariable("Region", Arrays.asList("Bejaia", "Sidi-Bel Abbes"));
        DiscreteVariable sex = new DiscreteVariable("Sex", Arrays.asList("F", "not stated"));
        ContinuousVariable x = new ContinuousVariable("X");
        ContinuousVariable y = new ContinuousVariable("Y");
        List<Node> vars = Arrays.asList(region, sex, x, y);

        int n = 10;
        DataSet data = new BoxDataSet(new MixedDataBox(vars, n), vars);

        int[] regionVals = {0, 0, 0, 0, 0, 1, 1, 1, 1, DiscreteVariable.MISSING_VALUE};
        int[] sexVals = {0, 1, 0, 1, 0, 1, 0, 1, 0, 1};
        double[] xVals = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        double[] yVals = {0.5, 1.5, Double.NaN, 3.5, 4.5, 5.5, 6.5, 7.5, 8.5, 9.5};

        for (int i = 0; i < n; i++) {
            data.setInt(i, 0, regionVals[i]);
            data.setInt(i, 1, sexVals[i]);
            data.setDouble(i, 2, xVals[i]);
            data.setDouble(i, 3, yVals[i]);
        }

        return data;
    }

    @Test
    public void testParseLines() {
        List<SplitDef> defs = DataSplitter.parseLines(Arrays.asList(
                "Region = Bejaia", "", "# comment", "  young | X < 5  ", "\"a|b\" = c"));

        assertEquals(3, defs.size());
        assertEquals("Region = Bejaia", defs.get(0).name());
        assertEquals("Region = Bejaia", defs.get(0).condition());
        assertEquals(1, defs.get(0).lineNumber());
        assertEquals("young", defs.get(1).name());
        assertEquals("X < 5", defs.get(1).condition());
        assertEquals(4, defs.get(1).lineNumber());
        assertEquals("\"a|b\" = c", defs.get(2).condition());   // bar inside quotes is not a separator

        assertThrows(IllegalArgumentException.class,
                () -> DataSplitter.parseLines(List.of("name |")));
        assertThrows(IllegalArgumentException.class,
                () -> DataSplitter.parseLines(List.of("| X < 5")));
    }

    @Test
    public void testSplitByDiscreteDropsSplitterAndSendsMissingToRemainder() {
        DataSet data = mixed();
        Spec spec = new Spec(Arrays.asList("Region = Bejaia", "Region = \"Sidi-Bel Abbes\""),
                List.of("Region"), true, null);

        DataModelList out = DataSplitter.split(data, spec);
        assertEquals(3, out.size());

        DataSet a = (DataSet) out.get(0);
        DataSet b = (DataSet) out.get(1);
        DataSet rest = (DataSet) out.get(2);

        assertEquals("Region = Bejaia", a.getName());
        assertEquals("Region = \"Sidi-Bel Abbes\"", b.getName());
        assertEquals(DataSplitter.DEFAULT_REMAINDER_NAME, rest.getName());

        assertEquals(5, a.getNumRows());
        assertEquals(4, b.getNumRows());
        assertEquals(1, rest.getNumRows());              // the row with Region missing

        // Same variables in every split, Region gone, order preserved.
        for (DataModel m : out) {
            List<Node> vars = ((DataSet) m).getVariables();
            assertEquals(Arrays.asList("Sex", "X", "Y"),
                    vars.stream().map(Node::getName).toList());
        }

        // Rows are the right ones: X identifies them.
        assertEquals(1.0, a.getDouble(0, 1), 0);
        assertEquals(6.0, b.getDouble(0, 1), 0);
        assertEquals(10.0, rest.getDouble(0, 1), 0);
    }

    @Test
    public void testNoRemainderByDefaultAndEmptySplitsOmitted() {
        DataSet data = mixed();
        Spec spec = new Spec(Arrays.asList("Region = Bejaia", "X > 100", "Region = \"Sidi-Bel Abbes\""),
                null, false, null);

        DataModelList out = DataSplitter.split(data, spec);
        assertEquals(2, out.size());
        assertEquals(4, ((DataSet) out.get(0)).getNumColumns());   // nothing dropped
    }

    @Test
    public void testPreviewCountsUnmatchedAndOverlap() {
        DataSet data = mixed();
        Spec spec = new Spec(Arrays.asList("X < 4", "X in [3, 8)", "Y > 100"), null, false, null);

        Preview p = DataSplitter.preview(data, spec);
        assertArrayEquals(new int[]{3, 5, 0}, p.rowCounts());
        assertEquals(2, p.numNonEmpty());
        assertEquals(3, p.unmatched());          // X = 8, 9, 10
        assertEquals(1, p.multiplyMatched());    // X = 3
        assertEquals(10, p.numRows());
    }

    @Test
    public void testErrorsCarryLineNumbers() {
        DataSet data = mixed();
        Spec spec = new Spec(Arrays.asList("Region = Bejaia", "", "Nope = 1"), null, false, null);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DataSplitter.preview(data, spec));
        assertTrue(e.getMessage(), e.getMessage().startsWith("Line 3:"));

        assertThrows(IllegalArgumentException.class,
                () -> DataSplitter.split(data, new Spec(List.of(), null, false, null)));
        assertThrows(IllegalArgumentException.class,
                () -> DataSplitter.split(data, new Spec(List.of("X > 100"), null, false, null)));
    }

    @Test
    public void testDuplicateNamesMadeUnique() {
        DataSet data = mixed();
        Spec spec = new Spec(Arrays.asList("g | X < 4", "g | X >= 4"), null, false, null);

        DataModelList out = DataSplitter.split(data, spec);
        assertEquals("g", out.get(0).getName());
        assertEquals("g (2)", out.get(1).getName());
    }

    @Test
    public void testGenerateDiscreteQuotesAndSkipsUnobservedCategories() {
        DiscreteVariable a = new DiscreteVariable("A", Arrays.asList("x", "unused", "with space"));
        List<Node> vars = List.of(a);
        DataSet data = new BoxDataSet(new MixedDataBox(vars, 4), vars);
        data.setInt(0, 0, 0);
        data.setInt(1, 0, 2);
        data.setInt(2, 0, 0);
        data.setInt(3, 0, 2);

        List<String> lines = DataSplitter.generateLines(data, vars, BinMode.EQUAL_COUNT, 2, null);
        assertEquals(Arrays.asList("A = x", "A = \"with space\""), lines);
    }

    @Test
    public void testGenerateEqualCountBinsPartitionNonMissingRows() {
        DataSet data = mixed();
        Node y = data.getVariable("Y");

        List<String> lines = DataSplitter.generateLines(data, List.of(y), BinMode.EQUAL_COUNT, 3, null);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).startsWith("Y < "));
        assertTrue(lines.get(1).startsWith("Y in ["));
        assertTrue(lines.get(2).startsWith("Y >= "));

        Preview p = DataSplitter.preview(data, new Spec(lines, null, false, null));
        assertEquals(1, p.unmatched());               // only the row with Y missing
        assertEquals(0, p.multiplyMatched());
        assertEquals(9, Arrays.stream(p.rowCounts()).sum());
        assertArrayEquals(new int[]{3, 3, 3}, p.rowCounts());
    }

    @Test
    public void testGenerateCutPointsAndEqualWidth() {
        DataSet data = mixed();
        Node x = data.getVariable("X");

        List<String> cut = DataSplitter.generateLines(data, List.of(x), BinMode.CUT_POINTS, 0,
                new double[]{7.5, 2.5, 7.5});
        assertEquals(Arrays.asList("X < 2.5", "X in [2.5, 7.5)", "X >= 7.5"), cut);
        assertArrayEquals(new int[]{2, 5, 3},
                DataSplitter.preview(data, new Spec(cut, null, false, null)).rowCounts());

        List<String> width = DataSplitter.generateLines(data, List.of(x), BinMode.EQUAL_WIDTH, 3, null);
        assertEquals(Arrays.asList("X < 4", "X in [4, 7)", "X >= 7"), width);

        assertThrows(IllegalArgumentException.class,
                () -> DataSplitter.generateLines(data, List.of(x), BinMode.CUT_POINTS, 0, null));
    }

    @Test
    public void testGenerateProductOmitsEmptyCombinations() {
        DataSet data = mixed();
        List<Node> vars = Arrays.asList(data.getVariable("Region"), data.getVariable("Sex"));

        List<String> lines = DataSplitter.generateLines(data, vars, BinMode.EQUAL_COUNT, 2, null);
        assertEquals(4, lines.size());
        assertEquals("Region = Bejaia and Sex = F", lines.get(0));
        assertEquals("Region = Bejaia and Sex = \"not stated\"", lines.get(1));

        // Product with a continuous variable whose bins leave some combinations empty.
        List<Node> vars2 = Arrays.asList(data.getVariable("Region"), data.getVariable("X"));
        List<String> lines2 = DataSplitter.generateLines(data, vars2, BinMode.CUT_POINTS, 0,
                new double[]{5.5});
        // Bejaia has X in 1..5 only, Sidi-Bel Abbes has X in 6..9 only: two of four combinations are empty.
        assertEquals(Arrays.asList("Region = Bejaia and X < 5.5", "Region = \"Sidi-Bel Abbes\" and X >= 5.5"),
                lines2);
    }

    @Test
    public void testConstantVariableCannotSplit() {
        ContinuousVariable c = new ContinuousVariable("C");
        List<Node> vars = List.of(c);
        DataSet data = new BoxDataSet(new VerticalDoubleDataBox(5, 1), vars);
        for (int i = 0; i < 5; i++) data.setDouble(i, 0, 3.0);

        assertTrue(DataSplitter.generateLines(data, vars, BinMode.EQUAL_COUNT, 4, null).isEmpty());
        assertTrue(DataSplitter.generateLines(data, vars, BinMode.EQUAL_WIDTH, 4, null).isEmpty());
    }

    @Test
    public void testEqualCountWithHeavyTiesNeverMakesEmptyLowestBin() {
        ContinuousVariable c = new ContinuousVariable("C");
        List<Node> vars = List.of(c);
        DataSet data = new BoxDataSet(new VerticalDoubleDataBox(6, 1), vars);
        double[] v = {1, 1, 1, 1, 2, 3};
        for (int i = 0; i < 6; i++) data.setDouble(i, 0, v[i]);

        List<String> lines = DataSplitter.generateLines(data, vars, BinMode.EQUAL_COUNT, 3, null);
        assertEquals(Arrays.asList("C < 2", "C >= 2"), lines);
    }

    @Test
    public void testSpecRoundTripsThroughParameters() {
        Parameters params = new Parameters();
        assertNull(Spec.fromParameters(params));

        Spec spec = new Spec(Arrays.asList("Region = Bejaia", "old | X >= 7"), List.of("Region"), true, "Other");
        spec.storeIn(params);

        Spec back = Spec.fromParameters(params);
        assertNotNull(back);
        assertEquals(spec.lines(), back.lines());
        assertEquals(spec.dropVarNames(), back.dropVarNames());
        assertTrue(back.includeRemainder());
        assertEquals("Other", back.effectiveRemainderName());

        // The model applies the stored spec to the parent; the row with X = 6 matches neither condition.
        DataModelList out = DataSplitModel.computeSplits(mixed(), params);
        assertEquals(3, out.size());
        assertEquals("old", out.get(1).getName());
        assertEquals("Other", out.get(2).getName());
        assertEquals(1, ((DataSet) out.get(2)).getNumRows());
        assertEquals(6.0, ((DataSet) out.get(2)).getDouble(0, 1), 0);   // X is column 1 once Region is dropped
    }

    @Test
    public void testKnowledgeCopiedToSplits() {
        DataSet data = mixed();
        Knowledge knowledge = new Knowledge();
        knowledge.addToTier(0, "X");
        knowledge.addToTier(1, "Y");
        data.setKnowledge(knowledge);

        DataModelList out = DataSplitter.split(data, new Spec(List.of("Region = Bejaia"), null, false, null));
        Knowledge k = ((DataSet) out.get(0)).getKnowledge();
        assertNotNull(k);
        assertFalse(k.isEmpty());
        assertTrue(k.isInWhichTier(new ContinuousVariable("X")) == 0);
        assertNotSame(knowledge, k);
    }

    @Test
    public void testBareVariableLineExpandsPerValueAndDropsTheVariable() {
        DataSet data = mixed();
        Spec spec = new Spec(List.of("Region"), null, true, null);

        Preview p = DataSplitter.preview(data, spec);
        assertEquals(Arrays.asList("Bejaia", "Sidi-Bel Abbes"),
                p.splits().stream().map(SplitDef::name).toList());
        assertEquals("Region = \"Sidi-Bel Abbes\"", p.splits().get(1).condition());
        assertArrayEquals(new int[]{5, 4}, p.rowCounts());
        assertEquals(List.of("Region"), p.impliedDropVarNames());

        DataModelList out = DataSplitter.split(data, spec);
        assertEquals(3, out.size());                      // two values plus the remainder (missing Region)
        assertEquals("Bejaia", out.get(0).getName());
        assertEquals(Arrays.asList("Sex", "X", "Y"),
                ((DataSet) out.get(0)).getVariables().stream().map(Node::getName).toList());

        // Case-insensitive lookup, named line, and a two-variable product with " / " labels.
        Preview p2 = DataSplitter.preview(data, new Spec(List.of("groups | region, SEX"), null, false, null));
        assertEquals(4, p2.splits().size());
        assertEquals("groups: Bejaia / F", p2.splits().get(0).name());
        assertEquals("Region = Bejaia and Sex = F", p2.splits().get(0).condition());
        assertEquals(Arrays.asList("Region", "Sex"), p2.impliedDropVarNames());
    }

    @Test
    public void testBareVariableLineMixesWithConditionLines() {
        DataSet data = mixed();
        Spec spec = new Spec(Arrays.asList("Region", "big | X >= 9"), List.of("Y"), false, null);

        DataModelList out = DataSplitter.split(data, spec);
        assertEquals(3, out.size());
        assertEquals("big", out.get(2).getName());
        assertEquals(2, ((DataSet) out.get(2)).getNumRows());
        // Explicit and implied drops combine.
        assertEquals(Arrays.asList("Sex", "X"),
                ((DataSet) out.get(0)).getVariables().stream().map(Node::getName).toList());
    }

    @Test
    public void testUnknownBareLineGivesClearError() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DataSplitter.preview(mixed(), new Spec(List.of("anon_student_ID"), null, false, null)));
        assertTrue(e.getMessage(), e.getMessage().startsWith("Line 1:"));
        assertTrue(e.getMessage(), e.getMessage().contains("neither a condition"));
    }

    @Test
    public void testManySubjectsWithNumericIdLoadedAsContinuous() {
        int numSubjects = 250;
        int rowsPer = 7;
        ContinuousVariable id = new ContinuousVariable("anon_student_id");
        ContinuousVariable score = new ContinuousVariable("score");
        List<Node> vars = Arrays.asList(id, score);
        DataSet data = new BoxDataSet(new VerticalDoubleDataBox(numSubjects * rowsPer, 2), vars);

        int row = 0;
        for (int s = 0; s < numSubjects; s++) {
            for (int r = 0; r < rowsPer; r++) {
                data.setDouble(row, 0, 100000 + 3 * s);
                data.setDouble(row, 1, s + 0.01 * r);
                row++;
            }
        }

        Spec spec = new Spec(List.of("anon_student_id"), null, false, null);
        DataModelList out = DataSplitter.split(data, spec);

        assertEquals(numSubjects, out.size());
        for (int s = 0; s < numSubjects; s++) {
            DataSet d = (DataSet) out.get(s);
            assertEquals(Integer.toString(100000 + 3 * s), d.getName());
            assertEquals(rowsPer, d.getNumRows());
            assertEquals(1, d.getNumColumns());
            assertEquals("score", d.getVariable(0).getName());
            assertEquals(s, d.getDouble(0, 0), 1e-9);
        }
    }

    @Test
    public void testNonIntegerContinuousValuesMatchExactly() {
        ContinuousVariable c = new ContinuousVariable("C");
        List<Node> vars = List.of(c);
        DataSet data = new BoxDataSet(new VerticalDoubleDataBox(4, 1), vars);
        double[] v = {0.1 + 0.2, 0.1 + 0.2, 1.0 / 3.0, 1.0 / 3.0};   // not representable at six digits
        for (int i = 0; i < 4; i++) data.setDouble(i, 0, v[i]);

        Preview p = DataSplitter.preview(data, new Spec(List.of("C"), null, false, null));
        assertArrayEquals(new int[]{2, 2}, p.rowCounts());
        assertEquals(0, p.unmatched());
    }

    @Test
    public void testCategoriesKeptByDefaultAndTrimmedOnRequest() {
        DataSet data = mixed();
        DiscreteVariable sex = (DiscreteVariable) data.getVariable("Sex");
        DiscreteVariable region = (DiscreteVariable) data.getVariable("Region");
        // Bejaia rows (0..4) have both Sex values and one Region value. Rows 6 and 8 (X in {7, 9}) are both
        // Sex F and both Region "Sidi-Bel Abbes". Row 9 (X = 10) has Region missing.
        List<String> lines = Arrays.asList("Region = Bejaia", "top | X in {7, 9}", "miss | X = 10");

        Preview p = DataSplitter.preview(data, new Spec(lines, null, false, null));
        assertEquals(3, p.numSplitsWithUnobservedCategories());       // Region in each; Sex in "top" and "miss"
        Preview pDrop = DataSplitter.preview(data, new Spec(lines, List.of("Region"), false, null));
        assertEquals(2, pDrop.numSplitsWithUnobservedCategories());   // Sex, in "top" and "miss"

        // Default: same variable objects, full category lists everywhere.
        DataModelList kept = DataSplitter.split(data, new Spec(lines, null, false, null, true));
        DataSet top = (DataSet) kept.get(1);
        assertSame(sex, top.getVariable("Sex"));
        assertSame(region, top.getVariable("Region"));

        // Trimmed: new variables with only the observed categories, codes remapped, the rest untouched.
        DataModelList trimmed = DataSplitter.split(data, new Spec(lines, null, false, null, false));
        DataSet bejaia = (DataSet) trimmed.get(0);
        DataSet top2 = (DataSet) trimmed.get(1);
        DataSet miss = (DataSet) trimmed.get(2);

        assertSame(sex, bejaia.getVariable("Sex"));                    // both categories observed there
        assertEquals(List.of("Bejaia"), ((DiscreteVariable) bejaia.getVariable("Region")).getCategories());
        for (int i = 0; i < bejaia.getNumRows(); i++) assertEquals(0, bejaia.getInt(i, 0));

        DiscreteVariable sex2 = (DiscreteVariable) top2.getVariable("Sex");
        assertNotSame(sex, sex2);
        assertEquals("Sex", sex2.getName());
        assertEquals(List.of("F"), sex2.getCategories());
        assertEquals(List.of("Sidi-Bel Abbes"), ((DiscreteVariable) top2.getVariable("Region")).getCategories());
        assertEquals(0, top2.getInt(0, 0));                             // remapped from 1 to 0
        assertEquals(0, top2.getInt(1, 0));
        assertEquals(7.0, top2.getDouble(0, 2), 0);                     // continuous columns copied
        assertEquals(9.0, top2.getDouble(1, 2), 0);
        assertEquals("top", top2.getName());

        // Region is missing in every row of "miss": it keeps its full list, and the value stays missing.
        assertSame(region, miss.getVariable("Region"));
        assertEquals(DiscreteVariable.MISSING_VALUE, miss.getInt(0, 0));
        assertEquals(List.of("not stated"), ((DiscreteVariable) miss.getVariable("Sex")).getCategories());
        assertEquals(0, miss.getInt(0, 1));
    }

    @Test
    public void testKeepAllCategoriesRoundTripsAndDefaultsTrue() {
        Parameters params = new Parameters();
        new Spec(List.of("Region"), null, false, null, false).storeIn(params);
        assertFalse(Spec.fromParameters(params).keepAllCategories());

        Parameters old = new Parameters();
        old.set(DataSplitter.KEY_LINES, new ArrayList<>(List.of("Region")));   // saved before this option existed
        assertTrue(Spec.fromParameters(old).keepAllCategories());
    }

    @Test
    public void testFmt() {
        assertEquals("4", DataSplitter.fmt(4.0));
        assertEquals("-3", DataSplitter.fmt(-3.0));
        assertEquals("2.5", DataSplitter.fmt(2.5));
        assertEquals("22.3333", DataSplitter.fmt(22.33333333));
        assertEquals("0.001", DataSplitter.fmt(0.001));
        assertEquals(1.23457e+08, Double.parseDouble(DataSplitter.fmt(123456789.5)), 1e-3 * 1e8);

        // Boundaries generated from the formatted text re-parse to a total order.
        double[] cuts = new double[]{1.11111111, 2.22222222, 3.33333333};
        List<String> texts = new ArrayList<>();
        for (double c : cuts) texts.add(DataSplitter.fmt(c));
        for (int i = 0; i + 1 < texts.size(); i++) {
            assertTrue(Double.parseDouble(texts.get(i)) < Double.parseDouble(texts.get(i + 1)));
        }
    }
}
