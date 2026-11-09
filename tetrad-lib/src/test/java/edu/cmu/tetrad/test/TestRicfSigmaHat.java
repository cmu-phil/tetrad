///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import cern.colt.matrix.DoubleMatrix2D;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScorer;
import edu.cmu.tetrad.sem.Ricf;
import edu.cmu.tetrad.sem.RicfEjml;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Regression tests for the colt {@link Ricf}'s returned fitted covariance (sigmahat). Before the fix,
 * (a) the final assembly inverted the non-symmetric B matrix through invSPD, which symmetrizes its
 * argument first, so sigmahat was wrong for any graph with a directed edge; and (b) the SemGraph entry
 * point {@code ricf} stored a precision where the Graph entry point {@code ricf2} stored a covariance
 * into Omega's block for arrowhead-free nodes, corrupting both sigmahat and, through the sweep's oInv,
 * the fit of spouse-bearing nodes. These tests fail on the pre-fix code and pass after it.
 *
 * @author josephramsey
 */
public class TestRicfSigmaHat {

    /**
     * On a DAG with a root node (which exercises the arrowhead-free branch), both entry points must
     * return sigmahat with the root's sample variance in its slot, and the Gaussian log-likelihood at
     * sigmahat must agree with SemBicScorer's DAG likelihood.
     */
    @Test
    public void dagSigmaHatMatchesSampleAndScorer() throws Exception {
        RandomUtil.getInstance().setSeed(29573914L);

        Node x1 = new GraphNode("X1"), x2 = new GraphNode("X2"), x3 = new GraphNode("X3");
        Graph dag = new EdgeListGraph(List.of(x1, x2, x3));
        dag.addEdge(Edges.directedEdge(x1, x2));
        dag.addEdge(Edges.directedEdge(x2, x3));

        DataSet data = new SemIm(new SemPm(dag)).simulateData(5000, false);
        ICovarianceMatrix cov = new CovarianceMatrix(data);
        int n = cov.getSampleSize();

        double bic = SemBicScorer.scoreDag(dag, cov, 1.0, false);
        double llScorer = (bic + dag.getNumEdges() * Math.log(n)) / 2.0;

        Ricf.RicfResult r2 = new Ricf().ricf2(dag, cov, 1e-10);
        Ricf.RicfResult r1 = new Ricf().ricf(new SemGraph(dag), cov, 1e-10);

        assertEquals("ricf2: root variance", cov.getValue(0, 0), r2.getShat().get(0, 0), 1e-8);
        assertEquals("ricf: root variance", cov.getValue(0, 0), r1.getShat().get(0, 0), 1e-8);

        assertEquals("ricf2: log-likelihood", llScorer, gaussLL(r2.getShat(), cov), 1e-6);
        assertEquals("ricf: log-likelihood", llScorer, gaussLL(r1.getShat(), cov), 1e-6);
    }

    /**
     * On a MAG with arrowhead-free roots feeding a bidirected district (X1 -> X2 <-> X3 <- X4), the two
     * entry points must agree with each other and with RicfEjml, entrywise on sigmahat and on the
     * log-likelihood. Before the fix they disagreed with each other and were both wrong.
     */
    @Test
    public void entryPointsAgreeWithEachOtherAndWithRicfEjml() throws Exception {
        RandomUtil.getInstance().setSeed(40918273L);

        Node x1 = new GraphNode("X1"), x2 = new GraphNode("X2"), x3 = new GraphNode("X3"), x4 = new GraphNode("X4");
        Graph mag = new EdgeListGraph(List.of(x1, x2, x3, x4));
        mag.addEdge(Edges.directedEdge(x1, x2));
        mag.addEdge(Edges.bidirectedEdge(x2, x3));
        mag.addEdge(Edges.directedEdge(x4, x3));

        // Data simulated from a latent DAG whose projection is exactly this MAG.
        Node l = new GraphNode("L");
        l.setNodeType(NodeType.LATENT);
        Graph dagL = new EdgeListGraph(List.of(x1, x2, x3, x4, l));
        dagL.addEdge(Edges.directedEdge(x1, x2));
        dagL.addEdge(Edges.directedEdge(x4, x3));
        dagL.addEdge(Edges.directedEdge(l, x2));
        dagL.addEdge(Edges.directedEdge(l, x3));

        DataSet data = new SemIm(new SemPm(dagL)).simulateData(5000, false);
        List<Node> keep = List.of(data.getVariable("X1"), data.getVariable("X2"),
                data.getVariable("X3"), data.getVariable("X4"));
        ICovarianceMatrix cov = new CovarianceMatrix(data.subsetColumns(keep));

        Ricf.RicfResult r2 = new Ricf().ricf2(mag, cov, 1e-10);
        Ricf.RicfResult r1 = new Ricf().ricf(new SemGraph(mag), cov, 1e-10);
        RicfEjml.RicfResult re = new RicfEjml().ricf(mag, cov, 1e-10, 1000);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals("shat[" + i + "," + j + "]: ricf vs ricf2",
                        r2.getShat().get(i, j), r1.getShat().get(i, j), 1e-8);
                assertEquals("shat[" + i + "," + j + "]: ricf2 vs RicfEjml",
                        re.getSigmaHat().get(i, j), r2.getShat().get(i, j), 1e-6);
            }
        }

        assertEquals("log-likelihood: ricf2 vs RicfEjml", re.getLogLik(), gaussLL(r2.getShat(), cov), 1e-6);
    }

    private static double gaussLL(DoubleMatrix2D sigmaHat, ICovarianceMatrix cov) {
        int p = cov.getDimension();
        int n = cov.getSampleSize();

        RealMatrix sigma = new Array2DRowRealMatrix(sigmaHat.toArray());
        RealMatrix s = new Array2DRowRealMatrix(cov.getMatrix().toArray());

        LUDecomposition lu = new LUDecomposition(sigma);
        double logDet = Math.log(Math.abs(lu.getDeterminant()));
        double trace = lu.getSolver().getInverse().multiply(s).getTrace();

        return -0.5 * n * (logDet + trace + p * Math.log(2.0 * Math.PI));
    }
}
