///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
 * Tests {@link LongToWide}: a long table of (student, assessment, score, passed) rows reshaped to one row per
 * student, with absent combinations missing, duplicates resolved by the chosen aggregation, discrete value
 * variables keeping their categories, and names sanitized.
 *
 * @author josephramsey
 */
public class TestLongToWide {

    /**
     * Constructs a new test.
     */
    public TestLongToWide() {
    }

    private static DataSet longData() {
        List<Node> vars = new ArrayList<>();
        vars.add(new DiscreteVariable("student", List.of("s1", "s2", "s3")));
        vars.add(new DiscreteVariable("assessment", List.of("Unit 1 (A)", "Unit 2", "Final")));
        vars.add(new ContinuousVariable("score"));
        vars.add(new DiscreteVariable("passed", List.of("no", "yes")));

        // student, assessment, score, passed
        Object[][] rows = {
                {"s1", "Unit 1 (A)", 0.8, "yes"},
                {"s1", "Unit 2", 0.6, "no"},
                {"s1", "Final", 0.7, "yes"},
                {"s2", "Unit 1 (A)", 0.4, "no"},
                {"s2", "Unit 1 (A)", 0.9, "yes"},   // duplicate: retake
                {"s2", "Final", 0.5, "no"},
                {"s3", "Unit 2", 0.3, "no"},
        };

        DataSet ds = new BoxDataSet(new MixedDataBox(vars, rows.length), vars);
        for (int i = 0; i < rows.length; i++) {
            ds.setInt(i, 0, ((DiscreteVariable) vars.get(0)).getIndex((String) rows[i][0]));
            ds.setInt(i, 1, ((DiscreteVariable) vars.get(1)).getIndex((String) rows[i][1]));
            ds.setDouble(i, 2, (Double) rows[i][2]);
            ds.setInt(i, 3, ((DiscreteVariable) vars.get(3)).getIndex((String) rows[i][3]));
        }
        return ds;
    }

    /**
     * Duplicates fail by default, with a message naming the count.
     */
    @Test
    public void testDuplicatesFailByDefault() {
        try {
            new LongToWide("assessment", "student").apply(longData());
            fail("Expected a failure on duplicated (unit, level) combinations.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("1 (unit, assessment) combinations"));
        }
    }

    /**
     * Shape, names, types, values, missing cells, and aggregation.
     */
    @Test
    public void testReshape() {
        DataSet longDs = longData();
        LongToWide t = new LongToWide("assessment", "student")
                .setDefaultAggregation(LongToWide.Aggregation.MEAN)
                .setAggregation("passed", LongToWide.Aggregation.LAST);
        LongToWide.Result res = t.apply(longDs);
        DataSet w = res.wide();

        assertEquals(3, w.getNumRows());
        assertEquals(6, w.getNumColumns()); // 2 value variables x 3 levels
        assertEquals(List.of(List.of("s1"), List.of("s2"), List.of("s3")), res.unitKeys());

        // Names: sanitized, value.level, in category order of the column key.
        assertEquals("score.Unit_1_A_", w.getVariable(0).getName());
        assertEquals("score.Unit_2", w.getVariable(1).getName());
        assertEquals("score.Final", w.getVariable(2).getName());
        assertEquals("passed.Unit_1_A_", w.getVariable(3).getName());
        assertTrue(w.getVariable(0) instanceof ContinuousVariable);
        assertTrue(w.getVariable(3) instanceof DiscreteVariable);
        assertEquals(List.of("no", "yes"), ((DiscreteVariable) w.getVariable(3)).getCategories());

        // s1: all three present.
        assertEquals(0.8, w.getDouble(0, 0), 0.0);
        assertEquals(0.6, w.getDouble(0, 1), 0.0);
        assertEquals(0.7, w.getDouble(0, 2), 0.0);
        assertEquals(1, w.getInt(0, 3)); // yes

        // s2: Unit 1 duplicated -> mean of 0.4 and 0.9; passed -> last ("yes"); Unit 2 absent -> missing.
        assertEquals(0.65, w.getDouble(1, 0), 1e-12);
        assertTrue(Double.isNaN(w.getDouble(1, 1)));
        assertEquals(DiscreteVariable.MISSING_VALUE, w.getInt(1, 4));
        assertEquals(1, w.getInt(1, 3));

        // s3: only Unit 2.
        assertTrue(Double.isNaN(w.getDouble(2, 0)));
        assertEquals(0.3, w.getDouble(2, 1), 0.0);
        assertTrue(Double.isNaN(w.getDouble(2, 2)));

        assertTrue(res.report(), res.report().contains("Duplicated (unit, level) combinations: 1"));
        assertTrue(res.report(), res.report().contains("score.Unit_2: 66.7% (2/3)"));
    }

    /**
     * COUNT turns the number of long rows into a continuous column with zeros where a unit has no rows; a value
     * subset restricts the output; kept row keys lead.
     */
    @Test
    public void testCountSubsetAndKeys() {
        LongToWide.Result res = new LongToWide("assessment", "student")
                .setValueVariables(List.of("score"))
                .setAggregation("score", LongToWide.Aggregation.COUNT)
                .setKeepRowKeys(true)
                .apply(longData());
        DataSet w = res.wide();

        assertEquals(4, w.getNumColumns());
        assertEquals("student", w.getVariable(0).getName());
        assertEquals("s2", ((DiscreteVariable) w.getVariable(0)).getCategory(w.getInt(1, 0)));
        assertEquals(2.0, w.getDouble(1, 1), 0.0); // s2 took Unit 1 twice
        assertEquals(0.0, w.getDouble(1, 2), 0.0); // s2 never took Unit 2
        assertEquals(1.0, w.getDouble(2, 2), 0.0);
    }

    /**
     * Level renaming and the level-only naming option, plus the short-name suggestion heuristic: common tokens
     * dropped, distinguishing leading and trailing tokens kept, uniqueness enforced.
     */
    @Test
    public void testLevelNamesAndSuggestions() {
        java.util.Map<String, String> names = new java.util.LinkedHashMap<>();
        names.put("Unit 1 (A)", "U1");
        names.put("Final", "F");

        DataSet w = new LongToWide("assessment", "student")
                .setValueVariables(List.of("score"))
                .setDefaultAggregation(LongToWide.Aggregation.MEAN)
                .setLevelNames(names)
                .setIncludeValueName(false)
                .apply(longData()).wide();
        assertEquals(List.of("U1", "Unit_2", "F"),
                List.of(w.getVariable(0).getName(), w.getVariable(1).getName(), w.getVariable(2).getName()));

        // With two value variables the value name is kept regardless of the option.
        DataSet w2 = new LongToWide("assessment", "student")
                .setDefaultAggregation(LongToWide.Aggregation.MEAN)
                .setAggregation("passed", LongToWide.Aggregation.LAST)
                .setLevelNames(names)
                .setIncludeValueName(false)
                .apply(longData()).wide();
        assertEquals("score.U1", w2.getVariable(0).getName());
        assertEquals("passed.F", w2.getVariable(5).getName());

        java.util.Map<String, String> s = LongToWide.suggestShortLevelNames(List.of(
                "Unit 01 Mastery Assessment_ Gases Report ver 1",
                "Unit 01 Mastery Assessment_ Gases Report ver 2",
                "Unit 02 Mastery Assessment_ Thermochemistry ver A",
                "Unit 06 Mastery Assessments_ Kinetics ver B",
                "Chem 1b Final Exam"), 20);
        assertEquals("01_Gases_Report_1", s.get("Unit 01 Mastery Assessment_ Gases Report ver 1"));
        assertEquals("01_Gases_Report_2", s.get("Unit 01 Mastery Assessment_ Gases Report ver 2"));
        assertEquals("02_Thermochemistry_A", s.get("Unit 02 Mastery Assessment_ Thermochemistry ver A"));
        assertEquals("06_Kinetics_B", s.get("Unit 06 Mastery Assessments_ Kinetics ver B"));
        assertEquals("Chem_1b_Final_Exam", s.get("Chem 1b Final Exam"));
        assertEquals(5, new java.util.HashSet<>(s.values()).size());
    }

    /**
     * A discrete value variable rejects a numeric aggregation, and an unknown variable is reported by name.
     */
    @Test
    public void testValidation() {
        try {
            new LongToWide("assessment", "student").setDefaultAggregation(LongToWide.Aggregation.MEAN).apply(longData());
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not defined for the discrete value variable 'passed'"));
        }
        try {
            new LongToWide("assessment", "nobody").apply(longData());
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("'nobody'"));
        }
        try {
            new LongToWide("score", "student").apply(longData());
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("must be a discrete variable"));
        }
    }
}
