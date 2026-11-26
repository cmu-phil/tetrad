/// ////////////////////////////////////////////////////////////////////////////
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
package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts an order of variables right with respect to required edges, for the permutation searches (BOSS, BOSS Top-K,
 * GRaSP, SP), which start from an order and expect every variable to come after the variables knowledge requires to
 * be its parents.
 * <p>
 * Each of those searches first repairs its starting order in one pass: a variable found after one it is required to
 * precede is moved to just before it. One pass is not always enough. Moving a variable forward can carry it past
 * another that is required to precede it. With C required before A and A required before B, and the order B, C, A,
 * the pass moves A to the front, giving A, B, C, and stops, leaving A ahead of C. The search then starts from an
 * order that breaks a required edge. Its moves take the order as sound and do not put it right, and the edge can be
 * missing from the result.
 * <p>
 * This finishes the job. An order the one pass left sound is not touched, so searches that were right before give
 * the same result as before.
 *
 * @author Claude
 */
public final class RequiredEdgeOrder {

    private RequiredEdgeOrder() {
    }

    /**
     * Whether some variable in the order comes before one that knowledge requires to precede it.
     *
     * @param order     the order
     * @param knowledge the knowledge
     * @return true if the order breaks a required edge
     */
    public static boolean breaksRequiredEdge(List<Node> order, Knowledge knowledge) {
        if (knowledge == null || knowledge.isEmpty()) return false;

        for (int i = 0; i < order.size(); i++) {
            String earlier = order.get(i).getName();

            for (int j = i + 1; j < order.size(); j++) {
                if (knowledge.isRequired(order.get(j).getName(), earlier)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Reorders the variables, in place, so that none comes before one that knowledge requires to precede it, if the
     * order does not already have that property. Otherwise it is left exactly as it is.
     * <p>
     * The reordering keeps the variables as near their present places as it can: it takes, again and again, the first
     * variable in the present order that is not waiting for one required to precede it. If the required edges go
     * round in a circle no order can honor them all; the variables in the circle are then taken in their present
     * order.
     *
     * @param order     the order, changed in place
     * @param knowledge the knowledge
     */
    public static void repair(List<Node> order, Knowledge knowledge) {
        if (!breaksRequiredEdge(order, knowledge)) return;

        int n = order.size();
        List<List<Integer>> followers = new ArrayList<>(n);
        int[] waitingFor = new int[n];

        for (int i = 0; i < n; i++) followers.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && knowledge.isRequired(order.get(i).getName(), order.get(j).getName())) {
                    followers.get(i).add(j);
                    waitingFor[j]++;
                }
            }
        }

        boolean[] taken = new boolean[n];
        List<Node> repaired = new ArrayList<>(n);

        for (int count = 0; count < n; count++) {
            int next = -1;

            for (int i = 0; i < n; i++) {
                if (!taken[i] && waitingFor[i] == 0) {
                    next = i;
                    break;
                }
            }

            // Only if the required edges go round in a circle.
            if (next == -1) {
                for (int i = 0; i < n; i++) {
                    if (!taken[i]) {
                        next = i;
                        break;
                    }
                }
            }

            taken[next] = true;
            repaired.add(order.get(next));

            for (int j : followers.get(next)) {
                waitingFor[j]--;
            }
        }

        order.clear();
        order.addAll(repaired);
    }
}
