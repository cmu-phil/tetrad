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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Optimizes a DAG SEM by regressing each varaible onto its parents using a linear regression.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SemOptimizerRegression implements SemOptimizer {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The number of restarts.
     */
    private int numRestarts = 1;

    /**
     * Blank constructor.
     */
    public SemOptimizerRegression() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemOptimizerRegression} object
     */
    public static SemOptimizerRegression serializableInstance() {
        return new SemOptimizerRegression();
    }


    private static int[] indexedParents(int[] parents) {
        int[] pp = new int[parents.length];
        for (int j = 0; j < pp.length; j++) pp[j] = j + 1;
        return pp;
    }

    @NotNull
    private static Matrix bStar(Matrix b) {
        Matrix byx = new Matrix(b.getNumRows() + 1, 1);
        byx.set(0, 0, 1);
        for (int j = 0; j < b.getNumRows(); j++) byx.set(j + 1, 0, -b.get(j, 0));
        return byx;
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static Matrix getCov(int[] _rows, int[] cols, Matrix covarianceMatrix) {
        return covarianceMatrix.view(_rows, cols).mat();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Fit the freeParameters by doing local regressions.
     */
    public void optimize(SemIm semIm) {
        if (this.numRestarts != 1) {
            throw new IllegalArgumentException("Number of restarts must be 1 for this method.");
        }

        Matrix covar = semIm.getSampleCovar();

        if (covar == null) {
            throw new NullPointerException("Sample covar has not been set.");
        }

        SemGraph graph = semIm.getSemPm().getGraph();
        graph.setShowErrorTerms(false);
        List<Node> nodes = new ArrayList<>(semIm.getVariableNodes());
        nodes.removeIf(node -> node.getNodeType() == NodeType.ERROR);

        TetradLogger.getInstance().log("FML = " + semIm.getScore());

        for (Node n : nodes) {
            int i = nodes.indexOf(n);
            List<Node> parents = new ArrayList<>(graph.getParents(n));

            parents.removeIf(parent -> parent.getNodeType() == NodeType.ERROR);
            parents.sort(Comparator.comparingInt(nodes::indexOf));

            int[] _parents = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                _parents[j] = nodes.indexOf(parents.get(j));
            }

            int[] all = concat(i, _parents);
            Matrix cov = getCov(all, all, covar);
            int[] pp = indexedParents(_parents);
            Matrix covxx = cov.view(pp, pp).mat();
            Matrix covxy = cov.view(pp, new int[]{0}).mat();
            Matrix b = (covxx.inverse().times(covxy));

            for (int j = 0; j < b.getNumRows(); j++) {
                semIm.setParamValue(parents.get(j), n, b.get(j, 0));
            }

            Matrix bStar = bStar(b);
            double varry = (bStar.transpose().times(cov).times(bStar).get(0, 0));

            semIm.setParamValue(n, n, varry);
        }

        String message = "FML = " + semIm.getScore();
        TetradLogger.getInstance().log(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumRestarts() {
        return this.numRestarts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "Sem Optimizer Regression";
    }
}











