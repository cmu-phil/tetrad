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

package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.*;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.*;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.List;

import static junit.framework.TestCase.assertTrue;

public class TestKnowledge {

    private static void testKnowledge(DataSet dataSet, Knowledge knowledge, Parameters parameters, HasKnowledge algorithm) {
        algorithm.setKnowledge(knowledge);
        Graph _graph = null;
        try {
            _graph = ((Algorithm) algorithm).search(dataSet, parameters);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        _graph = GraphUtils.replaceNodes(_graph, dataSet.getVariables());
        Node x1 = _graph.getNode("X1");
        List<Node> innodes = _graph.getNodesOutTo(x1, Endpoint.ARROW);
        assertTrue(innodes.isEmpty());
    }

    // Tests to make sure knowledge gets passed into the algcomparison wrappers for
    // all methods that take knowledge.
    @Test
    public void test1() {
        RandomUtil.getInstance().setSeed(3848283L);

        Graph graph = RandomGraph.randomGraph(10, 0, 10, 100, 1090, 100, false);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(100, false);

        Knowledge knowledge = new Knowledge();
        knowledge.addToTier(1, "X1");
        for (int i = 2; i <= 10; i++) knowledge.addToTier(0, "X" + i);

        System.out.println(knowledge);

        IndependenceWrapper test = new FisherZ();
        ScoreWrapper score = new SemBicScore();
        Parameters parameters = new Parameters();

        testKnowledge(dataSet, knowledge, parameters, new Boss(score));
        testKnowledge(dataSet, knowledge, parameters, new Cpc(test));
        testKnowledge(dataSet, knowledge, parameters, new Fges(score));
        testKnowledge(dataSet, knowledge, parameters, new Grasp(test, score));
        testKnowledge(dataSet, knowledge, parameters, new Pc(test));
        testKnowledge(dataSet, knowledge, parameters, new Sp(score));

        testKnowledge(dataSet, knowledge, parameters, new BossFci(test, score));
        testKnowledge(dataSet, knowledge, parameters, new Fci(test));
        testKnowledge(dataSet, knowledge, parameters, new FciMax(test));
        testKnowledge(dataSet, knowledge, parameters, new FgesFci(test, score));
        testKnowledge(dataSet, knowledge, parameters, new GraspFci(test, score));
        testKnowledge(dataSet, knowledge, parameters, new Rfci(test));
        testKnowledge(dataSet, knowledge, parameters, new SpFci(test, score));
    }
}

