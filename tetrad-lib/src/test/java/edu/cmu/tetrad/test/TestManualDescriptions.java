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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.annotation.*;
import edu.cmu.tetrad.util.AlgorithmDescriptions;
import edu.cmu.tetrad.util.DeprecationUtils;
import edu.cmu.tetrad.util.IndependenceTestDescriptions;
import edu.cmu.tetrad.util.ScoreDescriptions;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Every registered, non-deprecated algorithm, independence test, and score must have a real entry in the manual
 * (docs/manual/index.html), keyed by its annotation command. The GUI shows these descriptions at the point of choice,
 * so a missing entry is a user-visible gap. Added 2026-8-24, when the last missing entries were written; this test
 * fails on the manual as it stood before that.
 * <p>
 * To exempt a command temporarily, add it to {@link #ALLOWED_MISSING}.
 */
public class TestManualDescriptions {

    private static final Set<String> ALLOWED_MISSING = Set.of();

    private static boolean isPlaceholder(String d) {
        return d == null || d.isBlank() || d.trim().toLowerCase().startsWith("please add a description");
    }

    @Test
    public void everyAlgorithmHasAManualEntry() {
        List<String> missing = new ArrayList<>();
        AlgorithmAnnotations anno = AlgorithmAnnotations.getInstance();
        for (AnnotatedClass<Algorithm> ac : anno.getAnnotatedClasses()) {
            if (DeprecationUtils.isClassDeprecated(ac.clazz())) continue;
            String cmd = ac.annotation().command();
            if (ALLOWED_MISSING.contains(cmd)) continue;
            if (isPlaceholder(AlgorithmDescriptions.getInstance().get(cmd))) {
                missing.add(ac.annotation().name() + " (" + cmd + ")");
            }
        }
        assertTrue("Algorithms without a manual entry (add a <div id=\"command\"> to docs/manual/index.html): "
                   + missing, missing.isEmpty());
    }

    @Test
    public void everyIndependenceTestHasAManualEntry() {
        List<String> missing = new ArrayList<>();
        for (AnnotatedClass<TestOfIndependence> ac : TestOfIndependenceAnnotations.getInstance().getAnnotatedClasses()) {
            if (DeprecationUtils.isClassDeprecated(ac.clazz())) continue;
            String cmd = ac.annotation().command();
            if (ALLOWED_MISSING.contains(cmd)) continue;
            if (isPlaceholder(IndependenceTestDescriptions.getInstance().get(cmd))) {
                missing.add(ac.annotation().name() + " (" + cmd + ")");
            }
        }
        assertTrue("Independence tests without a manual entry: " + missing, missing.isEmpty());
    }

    @Test
    public void everyScoreHasAManualEntry() {
        List<String> missing = new ArrayList<>();
        for (AnnotatedClass<Score> ac : ScoreAnnotations.getInstance().getAnnotatedClasses()) {
            if (DeprecationUtils.isClassDeprecated(ac.clazz())) continue;
            String cmd = ac.annotation().command();
            if (ALLOWED_MISSING.contains(cmd)) continue;
            if (isPlaceholder(ScoreDescriptions.getInstance().get(cmd))) {
                missing.add(ac.annotation().name() + " (" + cmd + ")");
            }
        }
        assertTrue("Scores without a manual entry: " + missing, missing.isEmpty());
    }
}
