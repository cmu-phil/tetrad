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
package edu.cmu.tetrad.sem;

import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.Functions;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradVector;
import java.util.List;

/**
 * Calculates updated structural equation models given evidence of the form
 * X1=x1',...,The main task of such and algorithm is to calculate P(X = x' |
 * evidence), where evidence takes the form of a Proposition over the variables
 * in the Bayes net, possibly with additional information about which variables
 * in the Bayes net have been manipulated.
 *
 * @author Joseph Ramsey
 * @see edu.cmu.tetrad.bayes.Evidence
 * @see edu.cmu.tetrad.bayes.Proposition
 * @see edu.cmu.tetrad.bayes.Manipulation
 */
public class SemUpdater implements TetradSerializable {

    static final long serialVersionUID = 23L;
    private SemEvidence evidence;
    private SemIm semIm;

    public SemUpdater(SemIm semIm) {
        if (semIm == null) {
            throw new NullPointerException();
        }

        setEvidence(new SemEvidence(semIm));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemUpdater serializableInstance() {
        return new SemUpdater(SemIm.serializableInstance());
    }

    public SemEvidence getEvidence() {
        return this.evidence;
    }

    /**
     * Sets new evidence for the updater. Once this is called, old updating
     * results should not longer be available.
     */
    public void setEvidence(SemEvidence evidence) {
        if (evidence == null) {
            throw new NullPointerException();
        }

        this.evidence = evidence;
        this.semIm = evidence.getSemIm();
    }

    /**
     * @return the Bayes instantiated model that is being updated.
     */
    public SemIm getSemIm() {
        return this.semIm;
    }

    /**
     * See http://en.wikipedia.org/wiki/Multivariate_normal_distribution.
     */
    public SemIm  getUpdatedSemIm() {

        // First manipulate the old semIm.
        SemIm manipulatedSemIm = getManipulatedSemIm();

        double[] means = new double[manipulatedSemIm.getVariableNodes().size()];

        for (int i = 0; i < means.length; i++) {
            means[i] = manipulatedSemIm.getMean(manipulatedSemIm.getVariableNodes().get(i));
        }

        TetradVector mu = new TetradVector(means);
        TetradMatrix B = manipulatedSemIm.getEdgeCoef();

        TetradMatrix sigma = new TetradMatrix(manipulatedSemIm.getImplCovar(true).toArray());

        // Updating on x2 = a.
        SemEvidence evidence = getEvidence();
        List nodesInEvidence = evidence.getNodesInEvidence();

        for (int i = 0; i < nodesInEvidence.size(); i++) {
            int j = evidence.getNodeIndex((Node) nodesInEvidence.get(i));
            mu.set(j, evidence.getProposition().getValue(j));
        }

        // x1 is all the variables.
        int[] x1 = new int[sigma.rows()];

        for (int i = 0; i < sigma.rows(); i++) {
            x1[i] = i;
        }

        // Calculate muBar.
        TetradVector muBar = B.transpose().times(mu);

        for (int i = 0; i < nodesInEvidence.size(); i++) {
            int j = evidence.getNodeIndex((Node) nodesInEvidence.get(i));
            muBar.set(j, evidence.getProposition().getValue(j));
        }

        DoubleMatrix2D sigma2 = new DenseDoubleMatrix2D(manipulatedSemIm.getErrCovar().toArray());

        return manipulatedSemIm.updatedIm(new TetradMatrix(sigma2.toArray()), new TetradVector(muBar.toArray()));
    }

    public Graph getManipulatedGraph() {
        return createManipulatedGraph(getSemIm().getSemPm().getGraph());
    }

    public SemIm getManipulatedSemIm() {
        SemGraph graph = getSemIm().getSemPm().getGraph();
        SemGraph manipulatedGraph = createManipulatedGraph(graph);
        return SemIm.retainValues(getSemIm(), manipulatedGraph);
    }

    /**
     * Alters the graph by removing edges from parents to manipulated variables.
     */
    private SemGraph createManipulatedGraph(Graph graph) {
        SemGraph updatedGraph = new SemGraph(graph);

        for (int i = 0; i < evidence.getNumNodes(); ++i) {
            if (evidence.isManipulated(i)) {
                Node node = evidence.getNode(i);
                List<Node> parents = updatedGraph.getParents(node);

                for (Node parent : parents) {
                    if (parent.getNodeType() == NodeType.ERROR) {
                        continue;
                    }

                    updatedGraph.removeEdge(node, parent);
                }
            }
        }

        return updatedGraph;
    }
}
