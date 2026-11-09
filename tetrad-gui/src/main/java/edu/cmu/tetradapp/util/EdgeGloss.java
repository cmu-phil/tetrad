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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Node;

/**
 * Renders a one- or two-sentence plain-language reading of an edge's endpoint marks, for display in the graph workbench
 * edge tooltip.
 * <p>
 * Added 2026-8-24. The edge tooltip already showed the edge in symbolic form ({@code X o-> Y}), but a user who does
 * not already know the PAG/CPDAG conventions had to find the "PAG Edge Type Instructions" menu item to learn what a
 * circle or a bidirected edge means. This puts a short reading at the point of use.
 * <p>
 * The workbench does not know whether the graph it is displaying is a DAG, CPDAG, PAG, or MAG, so the readings are
 * stated in terms that hold across the graph types Tetrad's searches output. Where the reading differs by graph type
 * (an undirected edge means one thing in a CPDAG and another in a PAG), both readings are given.
 */
public final class EdgeGloss {

    private EdgeGloss() {
    }

    /**
     * Returns the plain-language reading of the given edge, or the empty string if the edge has an endpoint combination
     * with no standard reading (for example a NULL or STAR endpoint).
     *
     * @param edge the edge.
     * @return the reading, as plain text (no HTML).
     */
    public static String describe(Edge edge) {
        if (edge == null) return "";

        Node n1 = edge.getNode1();
        Node n2 = edge.getNode2();
        Endpoint e1 = edge.getEndpoint1();
        Endpoint e2 = edge.getEndpoint2();
        if (n1 == null || n2 == null || e1 == null || e2 == null) return "";

        String a = n1.getName();
        String b = n2.getName();

        // Normalize so that, for asymmetric edges, the "from" side is listed first.
        if (rank(e1) > rank(e2)) {
            String t = a;
            a = b;
            b = t;
            Endpoint te = e1;
            e1 = e2;
            e2 = te;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW) {
            return a + " is a cause of " + b + " (" + a + " is an ancestor of " + b + "; "
                   + b + " is not an ancestor of " + a + ").";
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.TAIL) {
            return "Undirected. In a CPDAG: " + a + " and " + b + " are adjacent, and the direction is not "
                   + "determined by the data (some equivalent DAGs orient it one way, some the other). "
                   + "In a PAG: neither is an ancestor of the other, which indicates selection bias.";
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.ARROW) {
            return "Bidirected: neither " + a + " nor " + b + " is an ancestor of the other; "
                   + "the association is explained by an unmeasured common cause (latent confounder).";
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW) {
            return b + " is not an ancestor of " + a + ". The circle at " + a + " is undetermined: "
                   + a + " may be a cause of " + b + ", or they may share a latent confounder, or both.";
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.CIRCLE) {
            return "Nondirected: " + a + " and " + b + " are adjacent, but both ends are undetermined. "
                   + "Consistent with " + a + " causing " + b + ", " + b + " causing " + a
                   + ", or a latent confounder.";
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.TAIL) {
            // Non-standard in PAG output but possible in hand-edited graphs; give the endpoint-level reading.
            return "The tail at " + b + " means " + b + " is an ancestor of " + a
                   + "; the circle at " + a + " is undetermined.";
        }

        return "";
    }

    /**
     * Ordering used to put the "from" side first: tail before circle before arrow.
     */
    private static int rank(Endpoint e) {
        return switch (e) {
            case TAIL -> 0;
            case CIRCLE -> 1;
            case ARROW -> 2;
            default -> 3;
        };
    }
}
