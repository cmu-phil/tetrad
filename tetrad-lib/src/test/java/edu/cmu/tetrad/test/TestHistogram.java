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
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.StatUtils;
import org.junit.Test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the Knowledge class.
 *
 * @author josephramsey
 */
public final class TestHistogram {

    @Test
    public void testHistogram() {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag trueGraph = new Dag(RandomGraph.randomGraph(nodes, 0, 5, 30,
                15, 15, false, 4829384L));
        final int sampleSize = 1000;

        // Continuous
        SemPm semPm = new SemPm(trueGraph);
        SemIm semIm = new SemIm(semPm);
        DataSet data = null;
        try {
            data = semIm.simulateData(sampleSize, false);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        Histogram histogram = new Histogram(data, "X1", false);
        histogram.setNumBins(20);

        // Use actual values from a single run; just verify structural properties
        double actualMax = histogram.getMax();
        double actualMin = histogram.getMin();

        assertTrue("Max should be finite", Double.isFinite(actualMax));
        assertTrue("Min should be finite", Double.isFinite(actualMin));
        assertTrue("Max should be > Min", actualMax > actualMin);
        assertEquals(1000, histogram.getN());

        histogram.setNumBins(10);
        histogram.addConditioningVariable("X3", 0, 1);
        histogram.addConditioningVariable("X4", 0, 1);
        histogram.removeConditioningVariable("X3");

        // getMax/getMin are unconditional — should still match
        assertEquals(actualMax, histogram.getMax(), 0.01);
        assertEquals(actualMin, histogram.getMin(), 0.01);

        double[] arr = histogram.getContinuousData("X2");
        histogram.addConditioningVariable("X2", StatUtils.min(arr), StatUtils.mean(arr));

        // Discrete
        BayesPm bayesPm = new BayesPm(trueGraph);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.InitializationMethod.RANDOM);
        DataSet data2 = bayesIm.simulateData(sampleSize, false);

        Histogram histogram2 = new Histogram(data2, "X1", false);
        histogram2.getFrequencies();

        histogram2.addConditioningVariable("X2", 0);
        histogram2.addConditioningVariable("X3", 1);
        histogram2.getFrequencies();
    }
}






