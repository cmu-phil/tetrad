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

package edu.cmu.tetradapp.ui.model;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetradapp.Tetrad;
import edu.cmu.tetradapp.model.GridSearchModel;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Pins the experimental-visibility contract of the three model registries (changed 2026-8-24).
 * <ul>
 * <li>{@code getInstance(true)} lists everything {@code getInstance(false)} lists, and the difference is exactly the
 * classes annotated {@code @Experimental}.</li>
 * <li>{@code getInstance()} follows {@code Tetrad.enableExperimental} at call time. Before the change,
 * {@code IndependenceTestModels} and {@code ScoreModels} read the flag once at class load, so the settings checkbox
 * had no effect on tests and scores until Tetrad was restarted; those two assertions fail on the unpatched code.</li>
 * <li>{@code GridSearchModel} accepts an experimental test or score name regardless of the flag, since an editor's
 * local switch can select one.</li>
 * </ul>
 */
public class TestExperimentalRegistries {

    private static <T> Set<String> names(List<T> list) {
        Set<String> s = new HashSet<>();
        for (T t : list) s.add(t.toString());
        return s;
    }

    @Test
    public void withIsSupersetOfWithoutAndDifferenceIsExactlyExperimental() {
        List<IndependenceTestModel> with = IndependenceTestModels.getInstance(true).getModels();
        List<IndependenceTestModel> without = IndependenceTestModels.getInstance(false).getModels();
        assertTrue(names(with).containsAll(names(without)));
        assertTrue("registry has experimental tests", with.size() > without.size());
        for (IndependenceTestModel m : with) {
            boolean exp = m.getIndependenceTest().clazz().isAnnotationPresent(Experimental.class);
            assertEquals(m.getName(), !exp, names(without).contains(m.toString()));
        }

        List<ScoreModel> withS = ScoreModels.getInstance(true).getModels();
        List<ScoreModel> withoutS = ScoreModels.getInstance(false).getModels();
        assertTrue(names(withS).containsAll(names(withoutS)));
        assertTrue(withS.size() > withoutS.size());
        for (ScoreModel m : withS) {
            boolean exp = m.getScore().clazz().isAnnotationPresent(Experimental.class);
            assertEquals(m.getName(), !exp, names(withoutS).contains(m.toString()));
        }

        List<AlgorithmModel> withA = AlgorithmModels.getInstance(true).getModels(edu.cmu.tetrad.data.DataType.Continuous, false);
        List<AlgorithmModel> withoutA = AlgorithmModels.getInstance(false).getModels(edu.cmu.tetrad.data.DataType.Continuous, false);
        assertTrue(names(withA).containsAll(names(withoutA)));
        assertTrue(withA.size() > withoutA.size());
    }

    @Test
    public void defaultInstanceFollowsTheGlobalFlagAtCallTime() {
        boolean saved = Tetrad.enableExperimental;
        try {
            Tetrad.enableExperimental = false;
            int tOff = IndependenceTestModels.getInstance().getModels().size();
            int sOff = ScoreModels.getInstance().getModels().size();
            int aOff = AlgorithmModels.getInstance().getModels(edu.cmu.tetrad.data.DataType.Continuous, false).size();
            Tetrad.enableExperimental = true;
            int tOn = IndependenceTestModels.getInstance().getModels().size();
            int sOn = ScoreModels.getInstance().getModels().size();
            int aOn = AlgorithmModels.getInstance().getModels(edu.cmu.tetrad.data.DataType.Continuous, false).size();
            assertTrue("tests: flag change must take effect without restart", tOn > tOff);
            assertTrue("scores: flag change must take effect without restart", sOn > sOff);
            assertTrue(aOn > aOff);
        } finally {
            Tetrad.enableExperimental = saved;
        }
    }

    @Test
    public void gridSearchModelAcceptsExperimentalNamesWhenFlagIsOff() {
        boolean saved = Tetrad.enableExperimental;
        try {
            Tetrad.enableExperimental = false;
            IndependenceTestModel expTest = IndependenceTestModels.getInstance(true).getModels().stream()
                    .filter(m -> m.getIndependenceTest().clazz().isAnnotationPresent(Experimental.class))
                    .findFirst().orElseThrow();
            ScoreModel expScore = ScoreModels.getInstance(true).getModels().stream()
                    .filter(m -> m.getScore().clazz().isAnnotationPresent(Experimental.class))
                    .findFirst().orElseThrow();
            GridSearchModel model = new GridSearchModel(new edu.cmu.tetrad.util.Parameters());
            String prevT = model.getLastIndependenceTest();
            String prevS = model.getLastScore();
            try {
                model.setLastIndependenceTest(expTest.getName()); // threw on the unpatched code
                model.setLastScore(expScore.getName());
            } finally {
                if (prevT != null && !prevT.isBlank()) {
                    try { model.setLastIndependenceTest(prevT); } catch (IllegalArgumentException ignored) { }
                }
                if (prevS != null && !prevS.isBlank()) {
                    try { model.setLastScore(prevS); } catch (IllegalArgumentException ignored) { }
                }
            }
        } finally {
            Tetrad.enableExperimental = saved;
        }
    }
}
