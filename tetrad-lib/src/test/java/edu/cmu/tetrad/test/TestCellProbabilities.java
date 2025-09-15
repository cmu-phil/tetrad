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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.bayes.StoredCellProbs;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;

/**
 * @author josephramsey
 */
public final class TestCellProbabilities {

    @Test
    public void testCreateRandom() {
        RandomUtil.getInstance().setSeed(4828385834L);

        DiscreteVariable x = new DiscreteVariable("X", 3);
        DiscreteVariable y = new DiscreteVariable("Y", 3);
        DiscreteVariable z = new DiscreteVariable("Z", 3);
        DiscreteVariable w = new DiscreteVariable("W", 2);

        List<Node> variables = new LinkedList<>();
        variables.add(x);
        variables.add(y);
        variables.add(z);
        variables.add(w);

        StoredCellProbs cellProbabilities =
                StoredCellProbs.createRandomCellTable(variables);

        double prob = cellProbabilities.getCellProb(new int[]{0, 0, 0, 0});

        assertEquals(0.01, prob, 0.01);
    }

    //    @Test
    public void testCreateUsingBayesIm() {
        RandomUtil.getInstance().setSeed(4828385834L);

        Graph graph = GraphUtils.convert("X1-->X2,X1-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(graph);
        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.InitializationMethod.RANDOM);

        StoredCellProbs cellProbs = StoredCellProbs.createCellTable(bayesIm);

        double prob = cellProbs.getCellProb(new int[]{0, 0, 0, 0});

        System.out.println("prob = " + prob);

        assertEquals(0.06, prob, 0.02);
    }
}





