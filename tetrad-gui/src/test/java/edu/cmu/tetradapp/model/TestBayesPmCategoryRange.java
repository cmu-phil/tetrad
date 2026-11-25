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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;

/**
 * The Bayes parametric model box gives each variable the number of categories its parameter editor asks for,
 * whatever kind of box the graph comes from.
 * <p>
 * The editor stores its choice under "bayesPmInitializationMode" ("trinary" or "range"), and for a range the
 * least and greatest number of categories under "lowerBoundNumVals" and "upperBoundNumVals". Some of the
 * constructors used to read other names, so that a range asked for with a DAG box as parent came out as 2 to 4,
 * and with a search box as parent the choice of a range was not seen at all.
 *
 * @author Claude (Anthropic)
 */
public class TestBayesPmCategoryRange {

    private static Graph dag() {
        return new Dag(RandomGraph.randomGraph(8, 0, 8, 100, 100, 100, false));
    }

    /**
     * The parameters as the editor leaves them when a range is asked for.
     */
    private static Parameters range(int least, int greatest) {
        Parameters parameters = new Parameters();
        parameters.set("bayesPmInitializationMode", "range");
        parameters.set("lowerBoundNumVals", least);
        parameters.set("upperBoundNumVals", greatest);
        return parameters;
    }

    /**
     * The parameters as the editor leaves them when three categories each is asked for. The range fields keep
     * whatever they last held.
     */
    private static Parameters trinary() {
        Parameters parameters = range(6, 6);
        parameters.set("bayesPmInitializationMode", "trinary");
        return parameters;
    }

    private static Set<Integer> categoryCounts(BayesPmWrapper wrapper) {
        BayesPm pm = wrapper.getBayesPm();
        Set<Integer> counts = new TreeSet<>();

        for (Node node : pm.getDag().getNodes()) {
            counts.add(pm.getNumCategories(node));
        }

        return counts;
    }

    @Test
    public void testRangeWithAGraphBoxAsParent() {
        assertEquals(Set.of(6), categoryCounts(new BayesPmWrapper(new GraphWrapper(dag()), range(6, 6))));
    }

    @Test
    public void testRangeWithADagBoxAsParent() {
        assertEquals(Set.of(6), categoryCounts(new BayesPmWrapper(new DagWrapper(new Dag(dag())), range(6, 6))));
    }

    /**
     * A search box as parent goes through the constructor that takes a graph.
     */
    @Test
    public void testRangeWithASearchBoxAsParent() {
        assertEquals(Set.of(6), categoryCounts(new BayesPmWrapper(dag(), range(6, 6))));
    }

    /**
     * When the box is made again it is handed the model it held before.
     */
    @Test
    public void testRangeWhenTheBoxIsMadeAgain() {
        Graph dag = dag();
        BayesPmWrapper before = new BayesPmWrapper(new DagWrapper(new Dag(dag)), trinary());

        assertEquals(Set.of(6),
                categoryCounts(new BayesPmWrapper(new DagWrapper(new Dag(dag)), before, range(6, 6))));
        assertEquals(Set.of(6), categoryCounts(new BayesPmWrapper(dag, before.getBayesPm(), range(6, 6))));
    }

    /**
     * With nothing set, every kind of parent gives three categories each. That is the registered default of the
     * choice, and what the parameter editor starts from. A graph box as parent used to give 2 to 4, since its
     * constructors alone took a range when the choice was not set.
     */
    @Test
    public void testNothingSetMeansThreeCategoriesEach() {
        assertEquals("trinary",
                ParamDescriptions.getInstance().get("bayesPmInitializationMode").getDefaultValue());

        Graph dag = dag();
        BayesPmWrapper before = new BayesPmWrapper(new DagWrapper(new Dag(dag)), new Parameters());

        assertEquals(Set.of(3), categoryCounts(new BayesPmWrapper(new GraphWrapper(dag), new Parameters())));
        assertEquals(Set.of(3), categoryCounts(new BayesPmWrapper(new DagWrapper(new Dag(dag)), new Parameters())));
        assertEquals(Set.of(3), categoryCounts(new BayesPmWrapper(dag, new Parameters())));
        assertEquals(Set.of(3), categoryCounts(new BayesPmWrapper(new GraphWrapper(dag), before, new Parameters())));
        assertEquals(Set.of(3),
                categoryCounts(new BayesPmWrapper(new DagWrapper(new Dag(dag)), before, new Parameters())));
        assertEquals(Set.of(3), categoryCounts(new BayesPmWrapper(dag, before.getBayesPm(), new Parameters())));

        // The ends of a range play no part until a range is chosen.
        Parameters ends = new Parameters();
        ends.set("lowerBoundNumVals", 6);
        ends.set("upperBoundNumVals", 6);
        assertEquals(Set.of(3), categoryCounts(new BayesPmWrapper(new GraphWrapper(dag), ends)));
    }

    @Test
    public void testThreeCategoriesEach() {
        Graph dag = dag();

        assertEquals(Set.of(3), categoryCounts(new BayesPmWrapper(new GraphWrapper(dag), trinary())));
        assertEquals(Set.of(3), categoryCounts(new BayesPmWrapper(new DagWrapper(new Dag(dag)), trinary())));
        assertEquals(Set.of(3), categoryCounts(new BayesPmWrapper(dag, trinary())));
    }

    /**
     * A range from 5 to 7 gives nothing outside it. With eight variables this does not show that every count in
     * the range turns up, only that none falls outside.
     */
    @Test
    public void testRangeIsRespectedAtBothEnds() {
        for (int i = 0; i < 20; i++) {
            Set<Integer> counts = new TreeSet<>(categoryCounts(new BayesPmWrapper(new DagWrapper(new Dag(dag())),
                    range(5, 7))));
            counts.removeAll(Set.of(5, 6, 7));
            assertEquals(Set.of(), counts);
        }
    }

    /**
     * The Bayes instantiated model box, made with no parent, builds a parametric model on an empty graph. That
     * still works.
     */
    @Test
    public void testInstantiatedModelWithNoParent() {
        BayesImWrapper wrapper = new BayesImWrapper(new Parameters());
        assertEquals(0, wrapper.getBayesIm().getNumNodes());
    }
}
