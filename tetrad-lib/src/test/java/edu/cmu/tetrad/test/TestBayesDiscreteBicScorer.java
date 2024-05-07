///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.bayes.BayesProperties;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.MathUtils;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;
import org.junit.Test;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import static org.junit.Assert.assertEquals;

/**
 * @author josephramsey
 */
public final class TestBayesDiscreteBicScorer {

    @Test
    public void testPValue() {
        RandomUtil.getInstance().setSeed(492834924L);

//        Graph graph = GraphConverter.convert("X1,X2,X4,X4,X5,X6,X7,X8");
        Graph graph = GraphUtils.convert("X1-->X2,X2-->X3,X3-->X6,X6-->X7");
//        Graph graph2 = GraphConverter.convert("X1,X2,X3,X7-->X6,X9,X10,X11,X12");

        final int numCategories = 8;
        BayesPm pm = new BayesPm(graph, numCategories, numCategories);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.InitializationMethod.RANDOM);

        DataSet data = im.simulateData(1000, false);

        BayesProperties scorer = new BayesProperties(data);

        BayesProperties properties = new BayesProperties(data);
        StringBuilder buf = new StringBuilder();
        BayesProperties.LikelihoodRet ret = properties.getLikelihoodRatioP(graph);
        NumberFormat nf = new DecimalFormat("0.00");
        buf.append("\nP-value = ").append(nf.format(ret.p));
//        buf.append("\nP-value = ").append(properties.getVuongP());
        buf.append("\nDf = ").append(nf.format(ret.dof));
        buf.append("\nChi square = ").append(nf.format(ret.chiSq));
        buf.append("\nBIC score = ").append(nf.format(ret.bic));

        System.out.println(buf);
        double lik = ret.bic;

        assertEquals(0, ret.p, 0.001);
    }

    public void testGregsBdeuStructurePrior() {
        for (int i = 100; i >= 1; i--) {
            double e = .0001 / i;
            System.out.println("e = " + e + "\t" + prior(e));
        }
    }

    private double prior(double e) {
        double choose = FastMath.exp(MathUtils.choose(10 - 1, 1));
        return choose * FastMath.pow(e / (10 - 1), 1) * FastMath.pow(1.0 - e / (10 - 1), (10 - 1 - 1));
    }

    // Greg's structure prior
    private double prior2(double e, int k, int v) {
        double choose = FastMath.exp(MathUtils.choose(v - 1, k));
        return 1.0 / choose;//k * FastMath.log(e / (v - 1)) + (v - k - 1) * FastMath.log(1.0 - (e / (v - 1)));
    }
}




