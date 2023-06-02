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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Optimizes a DAG SEM with no hidden variables using closed formula regressions. IT SHOULD NOT BE USED WITH SEMs THAT
 * ARE NOT DAGS OR CONTAIN HIDDEN NODES. IT ALSO ASSUMES THAT ALL OBSERVED NODES APPEAR FIRST IN
 * semIm.getSemPm().getDag().getNodes(), I.E., ERROR NODES ARE INSERTED ONLY AFTER MEASURED NODES IN THIS LIST.
 *
 * @author Ricardo Silva
 */

public class SemOptimizerRegression implements SemOptimizer {
    static final long serialVersionUID = 23L;
    private int numRestarts = 1;

    //=============================CONSTRUCTORS============================//

    /**
     * Blank constructor.
     */
    public SemOptimizerRegression() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemOptimizerRegression serializableInstance() {
        return new SemOptimizerRegression();
    }

    //============================PUBLIC METHODS==========================//

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static Matrix getCov(int[] _rows, int[] cols, Matrix covarianceMatrix) {
        return covarianceMatrix.getSelection(_rows, cols);
    }

    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    @Override
    public int getNumRestarts() {
        return this.numRestarts;
    }

    public String toString() {
        return "Sem Optimizer Regression";
    }

    /**
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
        List<Node> nodes = semIm.getVariableNodes();

        TetradLogger.getInstance().log("info", "FML = " + semIm.getScore());

        for (Node node : nodes) {
            if (node.getNodeType() != NodeType.MEASURED) {
                continue;
            }

            if (!graph.isParameterizable(node)) continue;

            int idx = nodes.indexOf(node);
            List<Node> parents = new ArrayList<>(graph.getParents(node));
//            Node errorParent = node;

            for (int i = 0; i < parents.size(); i++) {
                Node nextParent = parents.get(i);
                if (nextParent.getNodeType() == NodeType.ERROR) {
//                    errorParent = nextParent;
                    parents.remove(nextParent);
                    break;
                }
            }

            double variance = covar.get(idx, idx);

            if (parents.size() > 0) {
                Vector nodeParentsCov = new Vector(parents.size());
                Matrix parentsCov = new Matrix(parents.size(), parents.size());

                for (int i = 0; i < parents.size(); i++) {
                    int idx2 = nodes.indexOf(parents.get(i));
                    nodeParentsCov.set(i, covar.get(idx, idx2));

                    for (int j = i; j < parents.size(); j++) {
                        int idx3 = nodes.indexOf(parents.get(j));
                        parentsCov.set(i, j, covar.get(idx2, idx3));
                        parentsCov.set(j, i, covar.get(idx2, idx3));
                    }
                }

                Vector b = parentsCov.inverse().times(nodeParentsCov);
                variance -= nodeParentsCov.dotProduct(b);

                for (int i = 0; i < b.size(); i++) {
                    int idx2 = nodes.indexOf(parents.get(i));
                    semIm.setParamValue(nodes.get(idx2), node, b.get(i));
                }
            }

            semIm.setParamValue(node, node, variance);
        }

        for (Node n : nodes) {
            int i = nodes.indexOf(n);
            List<Node> parents = new ArrayList<>(graph.getParents(n));

//            parents.sort(Comparator.comparingInt(nodes::indexOf));

            for (int j = 0; j < parents.size(); j++) {
                Node nextParent = parents.get(j);
                if (nextParent.getNodeType() == NodeType.ERROR) {
//                    errorParent = nextParent;
                    parents.remove(nextParent);
                    break;
                }
            }

            int[] _parents = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                int idx2 = nodes.indexOf(parents.get(j));
                _parents[j] = idx2;
            }

            int[] all = concat(i, _parents);
            Matrix cov = getCov(all, all, covar);
            int[] pp = indexedParents(_parents);
            Matrix covxx = cov.getSelection(pp, pp);
            Matrix covxy = cov.getSelection(pp, new int[]{0});
            Matrix b = (covxx.inverse().times(covxy));

            for (int j = 0; j < b.getNumRows(); j++) {
                int idx2 = nodes.indexOf(parents.get(j));
                semIm.setParamValue(nodes.get(idx2), n, b.get(j, 0));
            }

            Matrix bStar = SemBicScore.bStar(b);
            double varry = (bStar.transpose().times(cov).times(bStar).get(0, 0));

            semIm.setParamValue(n, n, varry);

        }

        TetradLogger.getInstance().log("optimization", "FML = " + semIm.getScore());
    }

    private static int[] indexedParents(int[] parents) {
        int[] pp = new int[parents.length];
        for (int j = 0; j < pp.length; j++) pp[j] = j + 1;
        return pp;
    }

    @NotNull
    public static Matrix bStar(Matrix b) {
        Matrix byx = new Matrix(b.getNumRows() + 1, 1);
        byx.set(0, 0, 1);
        for (int j = 0; j < b.getNumRows(); j++) byx.set(j + 1, 0, -b.get(j, 0));
        return byx;
    }
}










