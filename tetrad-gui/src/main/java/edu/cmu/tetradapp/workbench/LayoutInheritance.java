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

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.awt.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The rule by which a graph inherits the layout of a reference graph while keeping the changes made to it locally.
 * It has no Swing or session dependencies, so that it can be tested on its own; {@link TieLayoutMenu} supplies the
 * reference and stores the record.
 * <p>
 * The record of a graph is the position each of its nodes was given the last time it was synchronized with its
 * reference. A node is <i>local</i> if it has a record and no longer sits where the record says, that is, if it has
 * been moved since (dragged, laid out, pasted, or put back by an undo). A local node keeps its own position. Every
 * other node the reference also has, matched by name, takes the reference's position; in particular a graph with no
 * record at all takes the whole layout of its reference. A node the reference lacks, or has unpositioned, keeps its
 * own position.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class LayoutInheritance {

    private LayoutInheritance() {
    }

    /**
     * @param graph a graph.
     * @return the centers of the nodes of the graph, by node name, in the order of the graph's nodes.
     */
    public static Map<String, Point> positionsOf(Graph graph) {
        Map<String, Point> positions = new LinkedHashMap<>();

        for (Node node : graph.getNodes()) {
            positions.put(node.getName(), new Point(node.getCenterX(), node.getCenterY()));
        }

        return positions;
    }

    /**
     * @param name   the name of a node.
     * @param own    the node positions of the graph, by node name.
     * @param record the record of the graph, by node name, or null if it has none.
     * @return True if the node has been moved since the graph was last synchronized with its reference.
     */
    public static boolean isLocal(String name, Map<String, Point> own, Map<String, int[]> record) {
        if (record == null) {
            return false;
        }

        int[] last = record.get(name);
        Point point = own.get(name);

        return last != null && point != null && (last[0] != point.x || last[1] != point.y);
    }

    /**
     * @param own       the node positions of the graph, by node name.
     * @param reference the node positions of its reference, by node name.
     * @param record    the record of the graph, by node name, or null if it has none.
     * @return the positions the nodes of the graph should have, by node name: the reference's for inherited nodes,
     * their own for the rest.
     */
    public static Map<String, Point> resolve(Map<String, Point> own, Map<String, Point> reference,
                                             Map<String, int[]> record) {
        Map<String, Point> resolved = new LinkedHashMap<>();

        for (Map.Entry<String, Point> entry : own.entrySet()) {
            String name = entry.getKey();
            Point inherited = reference.get(name);

            if (inherited == null || inherited.x == -1 || inherited.y == -1 || isLocal(name, own, record)) {
                resolved.put(name, entry.getValue());
            } else {
                resolved.put(name, inherited);
            }
        }

        return resolved;
    }

    /**
     * Brings a record up to date after a synchronization: each inherited node is recorded at the position it ended
     * up with, and each local node keeps the record it had, so that it stays local.
     *
     * @param before    the node positions of the graph before the synchronization, by node name.
     * @param after     the node positions of the graph after it, by node name.
     * @param reference the node positions of its reference, by node name.
     * @param record    the record of the graph before the synchronization, or null if it had none.
     * @return the new record.
     */
    public static Map<String, int[]> updatedRecord(Map<String, Point> before, Map<String, Point> after,
                                                   Map<String, Point> reference, Map<String, int[]> record) {
        Map<String, int[]> updated = new HashMap<>();

        for (Map.Entry<String, Point> entry : after.entrySet()) {
            String name = entry.getKey();

            if (isLocal(name, before, record)) {
                updated.put(name, record.get(name));
            } else if (reference.containsKey(name)) {
                updated.put(name, new int[]{entry.getValue().x, entry.getValue().y});
            }
        }

        return updated;
    }

    /**
     * The record that leaves a graph exactly as it is: each node is recorded at its reference's position, so that
     * the nodes sitting where the reference has them are inherited and the rest are local. Used for the graphs of a
     * session saved before layouts were inherited.
     *
     * @param own       the node positions of the graph, by node name.
     * @param reference the node positions of its reference, by node name.
     * @return the record.
     */
    public static Map<String, int[]> recordAsIs(Map<String, Point> own, Map<String, Point> reference) {
        Map<String, int[]> record = new HashMap<>();

        for (String name : own.keySet()) {
            Point point = reference.get(name);

            if (point != null) {
                record.put(name, new int[]{point.x, point.y});
            }
        }

        return record;
    }
}
