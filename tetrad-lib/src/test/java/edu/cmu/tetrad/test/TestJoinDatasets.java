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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests {@link JoinDatasets}: a wide table of students joined to a background table on an anonymized student key,
 * under left, inner, and full outer joins; keys matched across variable types; repeated right keys rejected unless
 * one-to-many is allowed; colliding column names suffixed; composite keys; missing keys never matching.
 *
 * @author josephramsey
 */
public class TestJoinDatasets {

    /**
     * Constructs a new test.
     */
    public TestJoinDatasets() {
    }

    /**
     * The wide table: student (discrete key), score (continuous), gender (discrete; collides with the right).
     * Student s5 has a missing key.
     */
    private static DataSet wide() {
        List<Node> vars = new ArrayList<>();
        vars.add(new DiscreteVariable("student", List.of("s1", "s2", "s3", "s4", "s5")));
        vars.add(new ContinuousVariable("score"));
        vars.add(new DiscreteVariable("gender", List.of("F", "M")));

        Object[][] rows = {
                {"s1", 0.8, "F"},
                {"s2", 0.6, "M"},
                {"s3", 0.7, "F"},
                {"s4", 0.4, "M"},
                {null, 0.9, "F"},   // missing key
        };

        return build(vars, rows);
    }

    /**
     * The background table: student (discrete key; s3 absent, s9 extra), age (continuous), gender (discrete).
     */
    private static DataSet background() {
        List<Node> vars = new ArrayList<>();
        vars.add(new DiscreteVariable("student", List.of("s1", "s2", "s4", "s9")));
        vars.add(new ContinuousVariable("age"));
        vars.add(new DiscreteVariable("gender", List.of("F", "M")));

        Object[][] rows = {
                {"s4", 17.0, "M"},
                {"s1", 16.0, "F"},
                {"s9", 15.0, "F"},
                {"s2", 18.0, "M"},
        };

        return build(vars, rows);
    }

    private static DataSet build(List<Node> vars, Object[][] rows) {
        DataSet ds = new BoxDataSet(new MixedDataBox(vars, rows.length), vars);

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < vars.size(); j++) {
                Object cell = rows[i][j];

                if (vars.get(j) instanceof DiscreteVariable dv) {
                    ds.setInt(i, j, cell == null ? DiscreteVariable.MISSING_VALUE : dv.getIndex((String) cell));
                } else {
                    ds.setDouble(i, j, cell == null ? Double.NaN : (Double) cell);
                }
            }
        }

        return ds;
    }

    private static String cat(DataSet ds, int row, String var) {
        int col = ds.getColumnIndex(ds.getVariable(var));
        int idx = ds.getInt(row, col);
        return idx == DiscreteVariable.MISSING_VALUE ? null : ((DiscreteVariable) ds.getVariable(col)).getCategory(idx);
    }

    private static double num(DataSet ds, int row, String var) {
        return ds.getDouble(row, ds.getColumnIndex(ds.getVariable(var)));
    }

    /**
     * Left join: every left row kept in order; s3 and the missing-key row get missing right values; s9 dropped;
     * gender suffixed on both sides; report counts.
     */
    @Test
    public void testLeftJoin() {
        JoinDatasets.Result result = new JoinDatasets("student").apply(wide(), background());
        DataSet joined = result.joined();

        assertEquals(5, joined.getNumRows());
        assertEquals(List.of("student", "score", "gender_left", "age", "gender_right"), joined.getVariableNames());

        assertEquals("s1", cat(joined, 0, "student"));
        assertEquals(16.0, num(joined, 0, "age"), 0.0);
        assertEquals("s2", cat(joined, 1, "student"));
        assertEquals(18.0, num(joined, 1, "age"), 0.0);
        assertEquals("s3", cat(joined, 2, "student"));
        assertTrue(Double.isNaN(num(joined, 2, "age")));
        assertNull(cat(joined, 2, "gender_right"));
        assertEquals("F", cat(joined, 2, "gender_left"));
        assertEquals("s4", cat(joined, 3, "student"));
        assertEquals(17.0, num(joined, 3, "age"), 0.0);
        assertNull(cat(joined, 4, "student"));
        assertTrue(Double.isNaN(num(joined, 4, "age")));
        assertEquals(0.9, num(joined, 4, "score"), 0.0);

        // Categories of the key are the left's, unchanged.
        assertEquals(List.of("s1", "s2", "s3", "s4", "s5"),
                ((DiscreteVariable) joined.getVariable("student")).getCategories());

        assertTrue(result.report(), result.report().contains("Left rows matched: 3 of 5"));
        assertTrue(result.report(), result.report().contains("unmatched right rows dropped: 1"));
        assertTrue(result.report(), result.report().contains("gender -> gender_left, gender_right"));
    }

    /**
     * Inner join keeps only the three matched rows.
     */
    @Test
    public void testInnerJoin() {
        DataSet joined = new JoinDatasets("student").setJoinType(JoinDatasets.JoinType.INNER)
                .apply(wide(), background()).joined();

        assertEquals(3, joined.getNumRows());
        assertEquals("s1", cat(joined, 0, "student"));
        assertEquals("s2", cat(joined, 1, "student"));
        assertEquals("s4", cat(joined, 2, "student"));
    }

    /**
     * Full outer join appends s9 with missing left values, extending the key's categories.
     */
    @Test
    public void testFullOuterJoin() {
        JoinDatasets.Result result = new JoinDatasets("student").setJoinType(JoinDatasets.JoinType.FULL_OUTER)
                .apply(wide(), background());
        DataSet joined = result.joined();

        assertEquals(6, joined.getNumRows());
        assertEquals("s9", cat(joined, 5, "student"));
        assertEquals(15.0, num(joined, 5, "age"), 0.0);
        assertTrue(Double.isNaN(num(joined, 5, "score")));
        assertNull(cat(joined, 5, "gender_left"));
        assertEquals("F", cat(joined, 5, "gender_right"));

        assertEquals(List.of("s1", "s2", "s3", "s4", "s5", "s9"),
                ((DiscreteVariable) joined.getVariable("student")).getCategories());
        assertTrue(result.report(), result.report().contains("1 category added for right-only rows"));
    }

    /**
     * A key stored as an integer-valued continuous variable on the left and as a discrete variable of numeric
     * strings on the right still matches.
     */
    @Test
    public void testKeyMatchesAcrossTypes() {
        List<Node> lv = new ArrayList<>();
        lv.add(new ContinuousVariable("id"));
        lv.add(new ContinuousVariable("x"));
        DataSet left = build(lv, new Object[][]{{1001.0, 1.0}, {1002.0, 2.0}, {1003.0, 3.0}});

        List<Node> rv = new ArrayList<>();
        rv.add(new DiscreteVariable("key", List.of("1002", "1003.0", "1004")));
        rv.add(new ContinuousVariable("y"));
        DataSet right = build(rv, new Object[][]{{"1002", 20.0}, {"1003.0", 30.0}, {"1004", 40.0}});

        DataSet joined = new JoinDatasets(List.of("id"), List.of("key")).apply(left, right).joined();

        assertEquals(3, joined.getNumRows());
        assertEquals(List.of("id", "x", "y"), joined.getVariableNames());
        assertTrue(Double.isNaN(num(joined, 0, "y")));
        assertEquals(20.0, num(joined, 1, "y"), 0.0);
        assertEquals(30.0, num(joined, 2, "y"), 0.0);
        assertTrue(joined.getVariable("id") instanceof ContinuousVariable);
    }

    /**
     * A repeated right key is an error by default and a row multiplication when allowed.
     */
    @Test
    public void testRepeatedRightKey() {
        List<Node> rv = new ArrayList<>();
        rv.add(new DiscreteVariable("student", List.of("s1", "s2")));
        rv.add(new ContinuousVariable("age"));
        DataSet right = build(rv, new Object[][]{{"s1", 16.0}, {"s1", 17.0}, {"s2", 18.0}});

        try {
            new JoinDatasets("student").apply(wide(), right);
            fail("Expected a repeated-key error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("1 key value occurring in more than one row"));
        }

        JoinDatasets.Result result = new JoinDatasets("student").setAllowOneToMany(true).apply(wide(), right);
        DataSet joined = result.joined();

        assertEquals(6, joined.getNumRows());
        assertEquals("s1", cat(joined, 0, "student"));
        assertEquals(16.0, num(joined, 0, "age"), 0.0);
        assertEquals("s1", cat(joined, 1, "student"));
        assertEquals(17.0, num(joined, 1, "age"), 0.0);
        assertEquals("s2", cat(joined, 2, "student"));
        assertTrue(result.report(), result.report().contains("1 key repeated (1 extra row; one-to-many allowed)"));
    }

    /**
     * Composite key: (student, term) on the left matches (sid, term) on the right; only the exact pair matches,
     * and the right's key columns are not duplicated in the output.
     */
    @Test
    public void testCompositeKey() {
        List<Node> lv = new ArrayList<>();
        lv.add(new DiscreteVariable("student", List.of("s1", "s2")));
        lv.add(new DiscreteVariable("term", List.of("fall", "spring")));
        lv.add(new ContinuousVariable("score"));
        DataSet left = build(lv, new Object[][]{
                {"s1", "fall", 0.5}, {"s1", "spring", 0.6}, {"s2", "fall", 0.7}});

        List<Node> rv = new ArrayList<>();
        rv.add(new DiscreteVariable("term", List.of("fall", "spring")));
        rv.add(new DiscreteVariable("sid", List.of("s1", "s2")));
        rv.add(new ContinuousVariable("absences"));
        DataSet right = build(rv, new Object[][]{
                {"spring", "s1", 3.0}, {"fall", "s2", 1.0}});

        DataSet joined = new JoinDatasets(List.of("student", "term"), List.of("sid", "term"))
                .apply(left, right).joined();

        assertEquals(3, joined.getNumRows());
        assertEquals(List.of("student", "term", "score", "absences"), joined.getVariableNames());
        assertTrue(Double.isNaN(num(joined, 0, "absences")));
        assertEquals(3.0, num(joined, 1, "absences"), 0.0);
        assertEquals(1.0, num(joined, 2, "absences"), 0.0);
    }

    /**
     * Missing keys never match, even to each other; a right row with a missing key is unmatched.
     */
    @Test
    public void testMissingKeysNeverMatch() {
        List<Node> rv = new ArrayList<>();
        rv.add(new DiscreteVariable("student", List.of("s1")));
        rv.add(new ContinuousVariable("age"));
        DataSet right = build(rv, new Object[][]{{null, 99.0}, {"s1", 16.0}});

        JoinDatasets.Result result = new JoinDatasets("student").setJoinType(JoinDatasets.JoinType.FULL_OUTER)
                .apply(wide(), right);
        DataSet joined = result.joined();

        // 5 left rows (the missing-key left row unmatched) + 1 right-only row with a missing key.
        assertEquals(6, joined.getNumRows());
        assertTrue(Double.isNaN(num(joined, 4, "age")));
        assertNull(cat(joined, 5, "student"));
        assertEquals(99.0, num(joined, 5, "age"), 0.0);
        assertTrue(result.report(), result.report().contains("1 row with missing key"));
    }
}
