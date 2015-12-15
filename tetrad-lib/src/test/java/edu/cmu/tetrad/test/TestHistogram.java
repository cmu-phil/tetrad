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
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests the Knowledge class.
 *
 * @author Joseph Ramsey
 */
public final class TestHistogram{
    private IKnowledge knowledge;

    @Test
    public void testHistogram() {
        RandomUtil.getInstance().setSeed(4829384L);

        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag trueGraph = new Dag(GraphUtils.randomGraph(nodes, 0, 5, 30, 15, 15, false));
        int sampleSize = 1000;

        // Continuous
        SemPm semPm = new SemPm(trueGraph);
        SemIm semIm = new SemIm(semPm);
        DataSet data = semIm.simulateData(sampleSize, false);

        Histogram histogram = new Histogram(data);
        histogram.setTarget("X1");
        histogram.setNumBins(20);

        assertEquals(3.76, histogram.getMax(), 0.01);
        assertEquals(-3.83, histogram.getMin(), 0.01);
        assertEquals(1000, histogram.getN());

        histogram.setTarget("X1");
        histogram.setNumBins(10);
        histogram.addConditioningVariable("X3", 0, 1);
        histogram.addConditioningVariable("X4", 0, 1);

        histogram.removeConditioningVariable("X3");

        assertEquals(3.76, histogram.getMax(), 0.01);
        assertEquals(-3.83, histogram.getMin(), 0.01);
        assertEquals(188, histogram.getN());

        double[] arr = histogram.getContinuousData("X2");
        histogram.addConditioningVariable("X2", StatUtils.min(arr), StatUtils.mean(arr));

        // Discrete
        BayesPm bayesPm = new BayesPm(trueGraph);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        DataSet data2 = bayesIm.simulateData(sampleSize, false);

        // For some reason these are giving different
        // values when all of the unit tests are run are
        // once. TODO They produce stable values when
        // this particular test is run repeatedly.
        Histogram histogram2 = new Histogram(data2);
        histogram2.setTarget("X1");
        int[] frequencies1 = histogram2.getFrequencies();
//        assertEquals(928, frequencies1[0]);
//        assertEquals(72, frequencies1[1]);

        histogram2.setTarget("X1");
        histogram2.addConditioningVariable("X2", 0);
        histogram2.addConditioningVariable("X3", 1);
        int[] frequencies = histogram2.getFrequencies();
//        assertEquals(377, frequencies[0]);
//        assertEquals(28, frequencies[1]);
    }
}





