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

import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.DeterminismRemovalSuggester;
import edu.cmu.tetrad.data.audit.DeterminismRemovalSuggester.Suggestion;
import edu.cmu.tetrad.data.audit.FindingCode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests {@link DeterminismRemovalSuggester}: the convention for which member of a relationship is suggested, the
 * greedy resolution of findings by earlier removals, the ordering of exact before near findings, and the exclusion
 * of findings that name no removable variable.
 *
 * @author josephramsey
 */
public class TestDeterminismRemovalSuggester {

    /**
     * Constructs a new test.
     */
    public TestDeterminismRemovalSuggester() {
    }

    private static AuditFinding finding(FindingCode code, List<String> vars, Map<String, Double> values) {
        return new AuditFinding(code, AuditFinding.Severity.WARNING, vars, values, code.name());
    }

    private static Suggestion find(List<Suggestion> suggestions, String variable) {
        for (Suggestion s : suggestions) if (s.variable().equals(variable)) return s;
        return null;
    }

    /**
     * The second of a duplicate pair, the determined (first-listed) variable of a determinism finding, and the
     * discrete member of a discrete-continuous finding are the suggested removals.
     */
    @Test
    public void testConventions() {
        List<AuditFinding> findings = List.of(
                finding(FindingCode.DUPLICATE_COLUMNS, List.of("gender", "woman"), Map.of("correlation", 1.0)),
                finding(FindingCode.DETERMINISTIC_RELATION, List.of("urm", "ethnicity"), Map.of()),
                finding(FindingCode.NEAR_DETERMINISM_NONLINEAR, List.of("fwi", "temp", "wind"),
                        Map.of("looRSquared", 0.97)),
                finding(FindingCode.NEAR_DETERMINISM_DISCRETE_CONTINUOUS, List.of("level", "score"),
                        Map.of("etaSquared", 0.99)));

        List<Suggestion> suggestions = DeterminismRemovalSuggester.suggest(findings);
        List<String> recommended = new ArrayList<>();
        for (Suggestion s : suggestions) if (s.recommended()) recommended.add(s.variable());

        assertEquals(List.of("woman", "urm", "fwi", "level"), recommended);
        assertTrue(find(suggestions, "fwi").reason().contains("temp, wind"));
        assertTrue(find(suggestions, "level").reason().contains("coarsening of score"));
    }

    /**
     * A finding any of whose variables has already been chosen for removal is treated as resolved: its own
     * candidate is offered but not recommended. Here X and Y determine each other; only the first-processed one is
     * recommended, and a near-collinear cluster (A on {B}, B on {A}) yields one removal, not two.
     */
    @Test
    public void testGreedyResolution() {
        List<AuditFinding> findings = List.of(
                finding(FindingCode.DETERMINISTIC_RELATION, List.of("X", "Y"), Map.of()),
                finding(FindingCode.DETERMINISTIC_RELATION, List.of("Y", "X"), Map.of()),
                finding(FindingCode.NEAR_DETERMINISM_LINEAR, List.of("A", "B"), Map.of("rSquared", 0.98)),
                finding(FindingCode.NEAR_DETERMINISM_LINEAR, List.of("B", "A"), Map.of("rSquared", 0.97)));

        List<Suggestion> suggestions = DeterminismRemovalSuggester.suggest(findings);

        assertTrue(find(suggestions, "X").recommended());
        assertFalse(find(suggestions, "Y").recommended());
        assertTrue(find(suggestions, "Y").reason().contains("resolved by removing X"));
        assertTrue(find(suggestions, "A").recommended());
        assertFalse(find(suggestions, "B").recommended());

        int recommended = 0;
        for (Suggestion s : suggestions) if (s.recommended()) recommended++;
        assertEquals(2, recommended);
    }

    /**
     * Exact findings are processed before near ones regardless of input order, stronger near findings before
     * weaker, and each variable appears at most once.
     */
    @Test
    public void testOrderingAndUniqueness() {
        List<AuditFinding> findings = List.of(
                finding(FindingCode.NEAR_DETERMINISM_LINEAR, List.of("P"), Map.of("rSquared", 0.91)),
                finding(FindingCode.NEAR_DETERMINISM_LINEAR, List.of("Q"), Map.of("rSquared", 0.99)),
                finding(FindingCode.DUPLICATE_COLUMNS, List.of("R", "S"), Map.of("correlation", -1.0)),
                finding(FindingCode.NEAR_DETERMINISM_NONLINEAR, List.of("Q", "P"), Map.of("looRSquared", 0.95)));

        List<Suggestion> suggestions = DeterminismRemovalSuggester.suggest(findings);
        List<String> order = new ArrayList<>();
        for (Suggestion s : suggestions) if (s.recommended()) order.add(s.variable());

        assertEquals(List.of("S", "Q", "P"), order);
        assertEquals(3, suggestions.size());
    }

    /**
     * Findings outside the determinism set, and determinism findings with no variables, produce nothing.
     */
    @Test
    public void testIgnoresOtherFindings() {
        List<AuditFinding> findings = List.of(
                finding(FindingCode.CONSTANT_COLUMN, List.of("c"), Map.of()),
                finding(FindingCode.HIGH_CORRELATION, List.of("a", "b"), Map.of("correlation", 0.9)),
                finding(FindingCode.EXACT_LINEAR_DEPENDENCE, List.of(), Map.of()));

        assertTrue(DeterminismRemovalSuggester.suggest(findings).isEmpty());
    }
}
