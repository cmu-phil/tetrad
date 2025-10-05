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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IMbSearch;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MbUtils;

import java.util.*;

/**
 * Created by IntelliJ IDEA. User: jdramsey Date: Jan 26, 2006 Time: 10:29:07 PM To change this template use File |
 * Settings | File Templates.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IambnPc implements IMbSearch {

    /**
     * The independence test used to perform the search.
     */
    private final IndependenceTest independenceTest;

    /**
     * The list of variables being searched over. Must contain the target.
     */
    private final List<Node> variables;

    /**
     * Constructs a new search.
     *
     * @param test The source of conditional independence information for the search.
     */
    public IambnPc(IndependenceTest test) {
        if (test == null) {
            throw new NullPointerException();
        }

        this.independenceTest = test;
        this.variables = test.getVariables();
    }

    /**
     * {@inheritDoc}
     */
    public Set<Node> findMb(Node target) throws InterruptedException {
        Set<Node> cmb = new HashSet<>();
        Pc pc = new Pc(this.independenceTest);
        boolean cont = true;

        // Forward phase.
        while (cont) {
            cont = false;

            List<Node> remaining = new LinkedList<>(this.variables);
            remaining.removeAll(cmb);
            remaining.remove(target);

            double strength = Double.NEGATIVE_INFINITY;
            Node f = null;

            for (Node v : remaining) {
                if (v == target) {
                    continue;
                }

                double _strength = associationStrength(v, target, cmb);

                if (_strength > strength) {
                    strength = _strength;
                    f = v;
                }
            }

            if (f == null) {
                break;
            }

            if (!this.independenceTest.checkIndependence(f, target, cmb).isIndependent()) {
                cmb.add(f);
                cont = true;
            }
        }

        // Backward phase.
        cmb.add(target);
        Graph graph = pc.search(new ArrayList<>(cmb));

        MbUtils.trimToMbNodes(graph, target, false);
//        cmb = DataGraphUtils.markovBlanketDag(target, graph).getNodes();
        cmb = new HashSet<>(graph.getNodes());
        cmb.remove(target);

        return cmb;
    }

    private double associationStrength(Node v, Node target, Set<Node> cmb) throws InterruptedException {
        IndependenceResult result = this.independenceTest.checkIndependence(v, target, cmb);
        return 1.0 - result.getPValue();
    }

    /**
     * <p>getAlgorithmName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getAlgorithmName() {
        return "IAMBnPC";
    }

    /**
     * <p>getNumIndependenceTests.</p>
     *
     * @return a int
     */
    public int getNumIndependenceTests() {
        return 0;
    }

    private Node getVariableForName(String targetName) {
        Node target = null;

        for (Node V : this.variables) {
            if (V.getName().equals(targetName)) {
                target = V;
                break;
            }
        }

        if (target == null) {
            throw new IllegalArgumentException(
                    "Target variable not in dataset: " + targetName);
        }

        return target;
    }
}




