///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.bayes.StoredCellProbs;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphConverter;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Joseph Ramsey
 */
public final class TestCellProbabilities {

    @Test
    public void testCreateRandom() {
        RandomUtil.getInstance().setSeed(4828385834L);

        DiscreteVariable x = new DiscreteVariable("X", 3);
        DiscreteVariable y = new DiscreteVariable("Y", 3);
        DiscreteVariable z = new DiscreteVariable("Z", 3);
        DiscreteVariable w = new DiscreteVariable("W", 2);

        List<Node> variables = new LinkedList<Node>();
        variables.add(x);
        variables.add(y);
        variables.add(z);
        variables.add(w);

        StoredCellProbs cellProbabilities =
                StoredCellProbs.createRandomCellTable(variables);

        double prob = cellProbabilities.getCellProb(new int[]{0, 0, 0, 0});

        assertEquals(0.002, prob, 0.0001);
    }

    private void assertEquals(double v, double prob, double v1) {
    }

    @Test
    public void testCreateUsingBayesIm() {
        RandomUtil.getInstance().setSeed(4828385834L);

        Graph graph = GraphConverter.convert("X1-->X2,X1-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(graph);
        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        StoredCellProbs cellProbs = StoredCellProbs.createCellTable(bayesIm);

        double prob = cellProbs.getCellProb(new int[]{0, 0, 0, 0});

        assertEquals(0.0058, prob, 0.0001);
    }
}




