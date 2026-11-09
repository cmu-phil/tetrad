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

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Pins the rule, added 2026-10-11, that neither chooser card offers a dataset-properties filter choice that would
 * list nothing the selected algorithm requires: with supplied clusters the block tests and scores are listed
 * unconditionally and the filter is not offered, and without clusters an empty filter option is disabled and the
 * selection moves off it.
 */
public class TestChooserTestScoreFilters {

    private static DataSet continuousData() {
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < 6; i++) vars.add(new ContinuousVariable("X" + i));
        DataSet ds = new BoxDataSet(new MixedDataBox(vars, 100), vars);
        Random r = new Random(11);
        for (int row = 0; row < 100; row++) {
            for (int j = 0; j < 6; j++) ds.setDouble(row, j, r.nextGaussian());
        }
        return ds;
    }

    private static DataSet discreteData() {
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < 5; i++) vars.add(new DiscreteVariable("D" + i, 3));
        DataSet ds = new BoxDataSet(new MixedDataBox(vars, 100), vars);
        Random r = new Random(12);
        for (int row = 0; row < 100; row++) {
            for (int j = 0; j < 5; j++) ds.setInt(row, j, r.nextInt(3));
        }
        return ds;
    }

    private static GeneralAlgorithmRunner runner(DataSet data) {
        try {
            return new GeneralAlgorithmRunner(new DataWrapper(data), new Parameters());
        } catch (java.text.ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static BlockSpec spec(DataSet data) {
        return BlocksUtil.toSpec(List.of(List.of(0, 1, 2), List.of(3, 4, 5)), data);
    }

    // ---------------- With supplied clusters: block tests and scores regardless of the filter ----------------

    @Test
    public void classicCardWithClustersListsBlockTestsAndScoresUnderAnySavedFilter() {
        for (String savedFilter : List.of("linear-gaussian", "general", "all", "mixed")) {
            DataSet data = continuousData();
            GeneralAlgorithmRunner r = runner(data);
            r.getUserAlgoSelections().put("dataset_filter", savedFilter);

            AlgorithmCard card = new AlgorithmCard(r, spec(data));

            // PC needs a test; the block tests must be listed whatever filter was saved.
            r.getUserAlgoSelections().put("algo", "PC");
            card.refresh();
            assertFalse("saved filter " + savedFilter, card.listedTestNames().isEmpty());

            // BOSS needs a score; same requirement.
            r.getUserAlgoSelections().put("algo", "BOSS");
            card.refresh();
            assertFalse("saved filter " + savedFilter, card.listedScoreNames().isEmpty());
        }
    }

    @Test
    public void guidedCardWithClustersListsBlockTestsUnderAnySavedFamily() {
        for (String savedFamily : List.of("linear-gaussian", "general", "all", "mixed")) {
            DataSet data = continuousData();
            GeneralAlgorithmRunner r = runner(data);
            r.getUserAlgoSelections().put("dataset_filter", savedFamily);
            r.getUserAlgoSelections().put("guided.latent", "ANY");
            r.getUserAlgoSelections().put("algo", "PC");

            GuidedAlgorithmCard card = new GuidedAlgorithmCard(r, spec(data));
            assertEquals("PC", card.getSelectedAlgorithm().getName());
            assertFalse("saved family " + savedFamily, card.listedTestNames().isEmpty());
            assertNotNull("saved family " + savedFamily, card.getSelectedTest());
        }
    }

    // ---------------- Without clusters: an empty filter option is disabled and the selection moves ----------------

    @Test
    public void classicCardDisablesEmptyFiltersAndMovesOffThem() {
        DataSet data = discreteData();
        GeneralAlgorithmRunner r = runner(data);
        r.getUserAlgoSelections().put("dataset_filter", "linear-gaussian");
        r.getUserAlgoSelections().put("algo", "PC");

        AlgorithmCard card = new AlgorithmCard(r, null);

        // Whatever filter the card lands on lists something for PC's required test.
        assertFalse(card.listedTestNames().isEmpty());

        // The enabled state of the linear-Gaussian option matches the registry: it is enabled exactly if some
        // registered test for discrete data carries the LinearGaussian annotation.
        boolean lgHasDiscreteTests = false;
        for (IndependenceTestModel m : IndependenceTestModels.getInstance().getModels(DataType.Discrete)) {
            if (m.getIndependenceTest().clazz().isAnnotationPresent(LinearGaussian.class)) {
                lgHasDiscreteTests = true;
                break;
            }
        }
        assertEquals(lgHasDiscreteTests, card.datasetFilterOptionEnabled("linear-gaussian"));
        if (!lgHasDiscreteTests) {
            assertNotEquals("linear-gaussian", card.selectedDatasetFilterKey());
        }
        assertTrue(card.datasetFilterOptionEnabled(card.selectedDatasetFilterKey()));
    }

    @Test
    public void guidedCardMovesOffAnEmptyFamily() {
        DataSet data = discreteData();
        GeneralAlgorithmRunner r = runner(data);
        r.getUserAlgoSelections().put("dataset_filter", "linear-gaussian");
        r.getUserAlgoSelections().put("guided.latent", "ANY");
        r.getUserAlgoSelections().put("algo", "PC");

        GuidedAlgorithmCard card = new GuidedAlgorithmCard(r, null);
        assertEquals("PC", card.getSelectedAlgorithm().getName());
        assertFalse(card.listedTestNames().isEmpty());
        assertNotNull(card.getSelectedTest());
    }
}
