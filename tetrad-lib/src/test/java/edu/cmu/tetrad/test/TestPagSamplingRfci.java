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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.algo.bayesian.constraint.search.PagSamplingRfci;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * Tests the PagSamplingRfci algorithm.
 *
 * @author josephramsey
 */
public class TestPagSamplingRfci {

    @Test
    public void testPagSamplingRfci() {
        final int numModels = 5;
        final int sampleSize = 1000;
        final long seed = 42L;
        RandomUtil.getInstance().setSeed(seed);

        Graph g = GraphUtils.convert("X1-->X2,X1-->X3,X1-->X4,X1-->X5,X2-->X3,X2-->X4,X2-->X6,X3-->X4,X4-->X5,X5-->X6");
        Dag dag = new Dag(g);

        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.InitializationMethod.RANDOM);

        DataSet fullData = bayesIm.simulateData(sampleSize, true);
        DataSet dataSet = DataTransforms.restrictToMeasured(fullData);

        PagSamplingRfci search = new PagSamplingRfci(dataSet);
        search.setNumRandomizedSearchModels(numModels);
        search.setVerbose(true);

        Graph result = search.search();

        assertNotNull(result);
        System.out.println("Resulting graph edges: " + result.getEdges());
    }
}
