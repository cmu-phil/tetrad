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
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.search.AlgorithmChooserLogic.LatentChoice;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Headless smoke tests for the guided algorithm chooser (added 2026-8-24): construction, filtering by answer,
 * default test/score derivation, persistence round trip, and compatibility with sessions saved by the classic card.
 */
public class TestGuidedAlgorithmCard {

    private static DataSet mixedData() {
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < 5; i++) vars.add(new ContinuousVariable("C" + i));
        for (int i = 0; i < 3; i++) vars.add(new DiscreteVariable("D" + i, 3));
        DataSet ds = new BoxDataSet(new MixedDataBox(vars, 100), vars);
        Random r = new Random(7);
        for (int row = 0; row < 100; row++) {
            for (int j = 0; j < 5; j++) ds.setDouble(row, j, r.nextGaussian());
            for (int j = 5; j < 8; j++) ds.setInt(row, j, r.nextInt(3));
        }
        return ds;
    }

    private static GeneralAlgorithmRunner runner() {
        try {
            return new GeneralAlgorithmRunner(new DataWrapper(mixedData()), new Parameters());
        } catch (java.text.ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean has(List<AlgorithmModel> list, String name) {
        return list.stream().anyMatch(m -> name.equals(m.getName()));
    }

    @Test
    public void constructsHeadlessAndListsSomething() {
        GuidedAlgorithmCard card = new GuidedAlgorithmCard(runner(), null);
        assertFalse(card.getListedAlgorithms().isEmpty());
        assertNotNull(card.getSelectedAlgorithm());
    }

    @Test
    public void latentAnswerFiltersAndKeepsSelectionCoherent() {
        GuidedAlgorithmCard card = new GuidedAlgorithmCard(runner(), null);
        card.setLatentChoice(LatentChoice.NO);
        List<AlgorithmModel> no = card.getListedAlgorithms();
        assertTrue(has(no, "BOSS"));
        assertFalse(has(no, "FCI"));
        assertTrue(no.contains(card.getSelectedAlgorithm()));
        for (AlgorithmModel m : no) {
            assertEquals(AlgType.forbid_latent_common_causes, m.getAlgorithm().annotation().algoType());
        }

        card.setLatentChoice(LatentChoice.YES);
        List<AlgorithmModel> yes = card.getListedAlgorithms();
        assertTrue(has(yes, "FCI"));
        assertFalse(has(yes, "BOSS"));
        assertTrue(yes.contains(card.getSelectedAlgorithm()));
    }

    @Test
    public void defaultFamilyForMixedDataIsMixedAndTestScoreAreDerived() {
        GeneralAlgorithmRunner runner = runner();
        GuidedAlgorithmCard card = new GuidedAlgorithmCard(runner, null);
        card.setLatentChoice(LatentChoice.YES);
        AlgorithmModel sel = card.getSelectedAlgorithm();
        assertNotNull(sel);
        if (sel.isRequiredTest()) assertNotNull("test should be derived", card.getSelectedTest());
        if (sel.isRequiredScore()) assertNotNull("score should be derived", card.getSelectedScore());
        assertEquals("mixed", runner.getUserAlgoSelections().get("dataset_filter"));
    }

    @Test
    public void isAllValidInstallsTheAlgorithmOnTheRunner() {
        GeneralAlgorithmRunner runner = runner();
        GuidedAlgorithmCard card = new GuidedAlgorithmCard(runner, null);
        card.setLatentChoice(LatentChoice.NO);
        assertTrue(card.isAllValid());
        assertNotNull(runner.getAlgorithm());
    }

    @Test
    public void selectionsRoundTripThroughTheRunner() {
        GeneralAlgorithmRunner runner = runner();
        GuidedAlgorithmCard card = new GuidedAlgorithmCard(runner, null);
        card.setLatentChoice(LatentChoice.YES);
        AlgorithmModel sel = card.getSelectedAlgorithm();
        Map<String, Object> saved = runner.getUserAlgoSelections();
        assertEquals("YES", saved.get("guided.latent"));
        assertEquals(sel.toString(), saved.get("algo"));
        // Classic key kept coherent.
        assertEquals(AlgType.allow_latent_common_causes.name(), saved.get("algo_type"));

        // AlgorithmModel has no equals(), and each card builds its own model list, so compare by name.
        GuidedAlgorithmCard again = new GuidedAlgorithmCard(runner, null);
        assertEquals(sel.getName(), again.getSelectedAlgorithm().getName());
        assertEquals(String.valueOf(card.getSelectedTest()), String.valueOf(again.getSelectedTest()));
        assertEquals(String.valueOf(card.getSelectedScore()), String.valueOf(again.getSelectedScore()));
    }

    @Test
    public void restoresASessionSavedByTheClassicCard() {
        GeneralAlgorithmRunner runner = runner();
        Map<String, Object> saved = runner.getUserAlgoSelections();
        saved.put("algo_type", AlgType.forbid_latent_common_causes.name());
        saved.put("algo", "PC");
        saved.put("dataset_filter", "all");
        saved.put("knowledge", Boolean.FALSE);

        GuidedAlgorithmCard card = new GuidedAlgorithmCard(runner, null);
        assertEquals("PC", card.getSelectedAlgorithm().getName());
        for (AlgorithmModel m : card.getListedAlgorithms()) {
            assertEquals(AlgType.forbid_latent_common_causes, m.getAlgorithm().annotation().algoType());
        }
        assertEquals("NO", runner.getUserAlgoSelections().get("guided.latent"));
        assertEquals("all", runner.getUserAlgoSelections().get("dataset_filter"));
    }
}
