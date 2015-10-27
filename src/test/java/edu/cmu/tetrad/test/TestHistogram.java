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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.StatUtils;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.Arrays;

/**
 * Tests the Knowledge class.
 *
 * @author Joseph Ramsey
 */
public final class TestHistogram extends TestCase {
    private IKnowledge knowledge;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestHistogram(String name) {
        super(name);
    }

    public void testHistogram() {
        Dag trueGraph = new Dag(GraphUtils.randomGraph(5, 0, 5, 30, 15, 15, false));
        int sampleSize = 1000;

        // Continuous
        SemPm semPm = new SemPm(trueGraph);
        SemIm semIm = new SemIm(semPm);
        DataSet data = semIm.simulateData(sampleSize, false);

        Histogram histogram = new Histogram(data);
        histogram.setTarget("X1");
//        histogram.setNumBins(10);
        System.out.println("A " + Arrays.toString(histogram.getFrequencies()));

        histogram.setNumBins(20);

        System.out.println("A2 " + Arrays.toString(histogram.getFrequencies()));

        System.out.println("Max = " + histogram.getMax());
        System.out.println( "Min = " + histogram.getMin());
        System.out.println("N = " + histogram.getN());

        histogram.setTarget("X1");
        histogram.setNumBins(10);
        histogram.addConditioningVariable("X3", 0, 1);
        histogram.addConditioningVariable("X4", 0, 1);

        System.out.println("B " + Arrays.toString(histogram.getFrequencies()));

        histogram.removeConditioningVariable("X3");
        System.out.println("C " + Arrays.toString(histogram.getFrequencies()));

        System.out.println("Max = " + histogram.getMax());
        System.out.println("Min = " + histogram.getMin());
        System.out.println("N = " + histogram.getN());

        double[] arr = histogram.getContinuousData("X2");
        histogram.addConditioningVariable("X2", StatUtils.min(arr), StatUtils.mean(arr));

        // Discrete
        BayesPm bayesPm = new BayesPm(trueGraph);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        DataSet data2 = bayesIm.simulateData(sampleSize, false);

        Histogram histogram2 = new Histogram(data2);
        histogram2.setTarget("X1");
        System.out.println("D " + Arrays.toString(histogram2.getFrequencies()));

        histogram2.setTarget("X1");
        histogram2.addConditioningVariable("X2", 0);
        histogram2.addConditioningVariable("X3", 1);
        System.out.println("E " + Arrays.toString(histogram2.getFrequencies()));
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestHistogram.class);
    }
}





