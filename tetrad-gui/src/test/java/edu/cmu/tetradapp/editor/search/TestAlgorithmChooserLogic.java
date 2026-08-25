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

package edu.cmu.tetradapp.editor.search;

import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.TimeSeries;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetradapp.editor.search.AlgorithmChooserLogic.Answers;
import edu.cmu.tetradapp.editor.search.AlgorithmChooserLogic.LatentChoice;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins the filtering rules behind the guided algorithm chooser (added 2026-8-24) against the live registry.
 */
public class TestAlgorithmChooserLogic {

    private static final Answers ANY = new Answers(LatentChoice.ANY, false, false, false, null);

    private static List<AlgorithmModel> all() {
        return AlgorithmChooserLogic.allModels();
    }

    private static boolean has(List<AlgorithmModel> list, String name) {
        return list.stream().anyMatch(m -> name.equals(m.getAlgorithm().annotation().name()));
    }

    @Test
    public void registryIsNonTrivialAndSorted() {
        List<AlgorithmModel> models = all();
        assertTrue(models.size() > 30);
        for (int i = 1; i < models.size(); i++) {
            assertTrue(models.get(i - 1).compareTo(models.get(i)) <= 0);
        }
    }

    @Test
    public void experimentalHiddenByDefaultAndShownOnRequest() {
        List<AlgorithmModel> hidden = AlgorithmChooserLogic.filter(all(), DataType.Continuous, false, ANY);
        for (AlgorithmModel m : hidden) {
            assertFalse(m.getName(), m.getAlgorithm().clazz().isAnnotationPresent(Experimental.class));
        }
        List<AlgorithmModel> shown = AlgorithmChooserLogic.filter(all(), DataType.Continuous, false,
                new Answers(LatentChoice.ANY, false, false, true, null));
        assertTrue(shown.size() > hidden.size());
    }

    @Test
    public void latentAnswerSelectsTheFamily() {
        List<AlgorithmModel> no = AlgorithmChooserLogic.filter(all(), DataType.Continuous, false,
                ANY.withLatent(LatentChoice.NO));
        List<AlgorithmModel> yes = AlgorithmChooserLogic.filter(all(), DataType.Continuous, false,
                ANY.withLatent(LatentChoice.YES));
        assertFalse(no.isEmpty());
        assertFalse(yes.isEmpty());
        for (AlgorithmModel m : no) {
            assertEquals(m.getName(), AlgType.forbid_latent_common_causes, m.getAlgorithm().annotation().algoType());
        }
        for (AlgorithmModel m : yes) {
            assertEquals(m.getName(), AlgType.allow_latent_common_causes, m.getAlgorithm().annotation().algoType());
        }
        assertTrue(has(no, "BOSS"));
        assertTrue(has(no, "FGES"));
        assertTrue(has(no, "PC"));
        assertTrue(has(yes, "FCI"));
        assertTrue(has(yes, "GFCI"));
        assertFalse(has(yes, "BOSS"));
        assertFalse(has(no, "FCI"));
    }

    @Test
    public void anyShowsEveryFamilyIncludingMarkovBlanketAndPairwise() {
        List<AlgorithmModel> any = AlgorithmChooserLogic.filter(all(), DataType.Continuous, false, ANY);
        assertTrue(any.stream().anyMatch(m -> m.getAlgorithm().annotation().algoType() == AlgType.search_for_Markov_blankets));
        assertTrue(any.stream().anyMatch(m -> m.getAlgorithm().annotation().algoType() == AlgType.orient_pairwise));
        List<AlgorithmModel> no = AlgorithmChooserLogic.filter(all(), DataType.Continuous, false, ANY.withLatent(LatentChoice.NO));
        List<AlgorithmModel> yes = AlgorithmChooserLogic.filter(all(), DataType.Continuous, false, ANY.withLatent(LatentChoice.YES));
        assertTrue(any.size() >= no.size() + yes.size());
    }

    @Test
    public void timeSeriesFacetKeepsOnlyTimeSeriesAlgorithms() {
        List<AlgorithmModel> ts = AlgorithmChooserLogic.filter(all(), DataType.Continuous, false, ANY.withTimeSeries(true));
        for (AlgorithmModel m : ts) {
            assertTrue(m.getName(), m.getAlgorithm().clazz().isAnnotationPresent(TimeSeries.class));
        }
    }

    @Test
    public void knowledgeFacetNarrowsButKeepsKnowledgeTakers() {
        List<AlgorithmModel> any = AlgorithmChooserLogic.filter(all(), DataType.Continuous, false, ANY);
        List<AlgorithmModel> know = AlgorithmChooserLogic.filter(all(), DataType.Continuous, false, ANY.withKnowledge(true));
        assertTrue(know.size() <= any.size());
        assertTrue(has(know, "PC"));
        assertTrue(has(know, "FGES"));
    }

    @Test
    public void queryMatchesNameOrCommandCaseInsensitively() {
        List<AlgorithmModel> boss = AlgorithmChooserLogic.filter(all(), DataType.Continuous, false,
                new Answers(LatentChoice.ANY, false, false, false, "BoSs"));
        assertFalse(boss.isEmpty());
        for (AlgorithmModel m : boss) {
            String n = (m.getAlgorithm().annotation().name() + " " + m.getAlgorithm().annotation().command()).toLowerCase();
            assertTrue(n, n.contains("boss"));
        }
        assertEquals(boss.toString(), AlgorithmChooserLogic.filter(all(), DataType.Continuous, false,
                new Answers(LatentChoice.ANY, false, false, false, "  boss ")).toString());
    }

    @Test
    public void withoutQueryDropsOnlyTheQuery() {
        Answers a = new Answers(LatentChoice.NO, true, true, true, "x");
        Answers b = a.withoutQuery();
        assertEquals(LatentChoice.NO, b.latent());
        assertTrue(b.timeSeries());
        assertTrue(b.knowledge());
        assertTrue(b.experimental());
        assertNull(b.query());
    }

    @Test
    public void dataTypeCompatibility() {
        assertTrue(AlgorithmChooserLogic.fitsData(new DataType[]{DataType.All}, DataType.Discrete));
        assertTrue(AlgorithmChooserLogic.fitsData(new DataType[]{DataType.Continuous}, DataType.Continuous));
        assertFalse(AlgorithmChooserLogic.fitsData(new DataType[]{DataType.Continuous}, DataType.Discrete));
        assertFalse(AlgorithmChooserLogic.fitsData(new DataType[]{DataType.Continuous}, null));
        assertTrue(AlgorithmChooserLogic.fitsData(new DataType[]{DataType.All}, null));
        assertTrue(AlgorithmChooserLogic.fitsData(new DataType[]{DataType.Discrete}, DataType.All));
        // No data connected: only "All" algorithms, and there are some.
        assertFalse(AlgorithmChooserLogic.filter(all(), null, false, ANY).isEmpty());
    }

    @Test
    public void everyAlgTypeHasARole() {
        for (AlgType t : AlgType.values()) {
            String r = AlgorithmChooserLogic.role(t);
            assertNotNull(r);
            assertFalse(t.name(), r.isBlank());
        }
        assertEquals("", AlgorithmChooserLogic.role(null));
    }

    @Test
    public void latentChoiceParsesLeniently() {
        assertEquals(LatentChoice.NO, LatentChoice.parse("no"));
        assertEquals(LatentChoice.YES, LatentChoice.parse(" YES "));
        assertEquals(LatentChoice.ANY, LatentChoice.parse(null));
        assertEquals(LatentChoice.ANY, LatentChoice.parse("bogus"));
    }

    @Test
    public void firstSentenceAndPlaceholders() {
        assertEquals("BOSS is fast. ", AlgorithmChooserLogic.firstSentence("BOSS is fast. It scales well.") + " ");
        assertEquals("Uses e.g. BIC.", AlgorithmChooserLogic.firstSentence("Uses e.g. BIC. Then stops."));
        assertEquals("One line no period", AlgorithmChooserLogic.firstSentence("  One line   no period "));
        assertEquals("No description in the manual yet.",
                AlgorithmChooserLogic.firstSentence("Please add a description for bfci."));
        assertEquals("No description in the manual yet.", AlgorithmChooserLogic.firstSentence(""));
        assertTrue(AlgorithmChooserLogic.isPlaceholderDescription("Please add a description for x."));
        assertFalse(AlgorithmChooserLogic.isPlaceholderDescription("Real text."));
    }

    @Test
    public void needsLabels() {
        List<AlgorithmModel> models = all();
        AlgorithmModel boss = models.stream().filter(m -> "BOSS".equals(m.getName())).findFirst().orElseThrow();
        AlgorithmModel pc = models.stream().filter(m -> "PC".equals(m.getName())).findFirst().orElseThrow();
        AlgorithmModel gfci = models.stream().filter(m -> "GFCI".equals(m.getName())).findFirst().orElseThrow();
        assertEquals("score", AlgorithmChooserLogic.needs(boss));
        assertEquals("test", AlgorithmChooserLogic.needs(pc));
        assertEquals("test + score", AlgorithmChooserLogic.needs(gfci));
    }
}
