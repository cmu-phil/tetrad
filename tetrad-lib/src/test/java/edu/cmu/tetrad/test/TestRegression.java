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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionCovariance;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests the new regression classes. There is a tabular linear regression
 * model as well as a correlation linear regression model. (Space for more
 * in the future.)
 *
 * @author Joseph Ramsey
 */
public class TestRegression {
    DataSet data;

    public void setUp() {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        RandomUtil.getInstance().setSeed(342233L);
        Graph graph = new Dag(GraphUtils.randomGraphRandomForwardEdges(nodes, 0, 5, 3,
                3, 3, false));

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        data = im.simulateDataReducedForm(1000, false);
    }

    /**
     * This tests whether the answer to a rather arbitrary problem changes.
     * At one point, this was the answer being returned.
     */
    @Test
    public void testTabular() {
        setUp();

        RandomUtil.getInstance().setSeed(3848283L);

        List<Node> nodes = data.getVariables();

        Node target = nodes.get(0);
        List<Node> regressors = new ArrayList<Node>();

        for (int i = 1; i < nodes.size(); i++) {
            regressors.add(nodes.get(i));
        }

        Regression regression = new RegressionDataset(data);
        RegressionResult result = regression.regress(target, regressors);

        double[] coeffs = result.getCoef();
        assertEquals(.08, coeffs[0], 0.01);
        assertEquals(-.05, coeffs[1], 0.01);
        assertEquals(.035, coeffs[2], 0.01);
        assertEquals(0.019, coeffs[3], 0.01);
        assertEquals(-.003, coeffs[4], 0.01);
    }

    /**
     * Same problem, using the covariance matrix.
     */
    @Test
    public void testCovariance() {
        setUp();

        RandomUtil.getInstance().setSeed(3848283L);

        ICovarianceMatrix cov = new CovarianceMatrix(data);
        List<Node> nodes = cov.getVariables();

        Node target = nodes.get(0);
        List<Node> regressors = new ArrayList<Node>();

        for (int i = 1; i < nodes.size(); i++) {
            regressors.add(nodes.get(i));
        }

        Regression regression = new RegressionCovariance(cov);
        RegressionResult result = regression.regress(target, regressors);

        double[] coeffs = result.getCoef();
        assertEquals(0.00, coeffs[0], 0.01);
        assertEquals(-.053, coeffs[1], 0.01);
        assertEquals(0.036, coeffs[2], 0.01);
        assertEquals(.019, coeffs[3], 0.01);
        assertEquals(.007, coeffs[4], 0.01);
    }
}




