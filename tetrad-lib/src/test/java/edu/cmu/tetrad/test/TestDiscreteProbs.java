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

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.LinkedList;
import java.util.List;

/**
 * Tests the BayesIm.
 *
 * @author josephramsey
 */
public final class TestDiscreteProbs extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestDiscreteProbs(String name) {
        super(name);
    }

    public static void testCreateRandom() {
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
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestDiscreteProbs.class);
    }

    public void testCreateUsingBayesIm() {
        Graph graph = GraphUtils.convert("X1-->X2,X1-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(graph);
        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.InitializationMethod.RANDOM);

        StoredCellProbs cellProbs = StoredCellProbs.createCellTable(bayesIm);

        Proposition assertion = Proposition.tautology(bayesIm);
        assertion.setCategory(0, 1);
        assertion.removeCategory(2, 0);

        Proposition condition = Proposition.tautology(bayesIm);
        condition.setCategory(0, 1);

        cellProbs.getConditionalProb(assertion, condition);
    }
}





