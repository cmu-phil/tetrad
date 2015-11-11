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

package edu.cmu.tetrad.graph;


import java.util.*;

/**
 * Lays out a graph by linearly summing repulsive force between all nodes and
 * attractive force between adjacent nodes.
 *
 * @author Joseph Ramsey
 */
public final class FruchtermanReingoldLayout {

    /**
     * The graph being laid out.
     */
    private Graph graph;

    /**
     * Array of e for the graph. The ith edge is e[i][0]-->e[i][[1].
     */
    private int[][] edges;

    /**
     * The position of each node. The position of the ith node is (pos[i][0],
     * pos[i][1]).
     */
    private double[][] nodePosition;

    /**
     * The disposition of each node. The disposition of the ith node is
     * (disp[i][0], disp[i][1]).
     */
    private double[][] nodeDisposition;

    /**
     * Optimal distance between vertices.
     */
    private double optimalDistance;

    /**
     * The number of iterations.
     */
    private final int numIterations = 6000;

    /**
     * Temperature.
     */
    private double temperature;

    /**
     * Leftmost x position to help layout components left to right.
     */
    private double leftmostX = -50.;

    //==============================CONSTRUCTORS===========================//

    public FruchtermanReingoldLayout(Graph graph) {
        if (graph == null) {
            throw new NullPointerException();
        }

        this.graph = graph;
    }

    //============================PUBLIC METHODS==========================//

    public void doLayout() {
        List<List<Node>> components =
                GraphUtils.connectedComponents(this.graph());

        Collections.sort(components, new Comparator<List<Node>>() {
            public int compare(List<Node> o1, List<Node> o2) {
                int i1 = o1.size();
                int i2 = o2.size();
                return i2 < i1 ? -1 : i2 == i1 ? 0 : 1;
            }
        });

        for (List<Node> component1 : components) {
            layoutComponent(component1);
        }
    }

    private void layoutComponent(List<Node> nodes) {
        int numNodes = nodes.size();
        nodePosition = new double[numNodes][2];
        nodeDisposition = new double[numNodes][2];

        for (int i = 0; i < numNodes; i++) {
            Node node = nodes.get(i);
            nodePosition()[i][0] = node.getCenterX();
            nodePosition()[i][1] = node.getCenterY();

            //pos[i][0] = RandomUtil.nextInt(600);
            //pos[i][1] = RandomUtil.nextInt(600);
        }

        List<Edge> edges = new ArrayList<Edge>(graph().getEdges());

        for (Iterator<Edge> i = edges.iterator(); i.hasNext();) {
            Edge edge = i.next();
            if (!nodes.contains(edge.getNode1()) ||
                    !nodes.contains(edge.getNode2())) {
                i.remove();
            }
        }

        this.edges = new int[edges.size()][2];

        for (int i = 0; i < edges.size(); i++) {
            Edge edge = edges.get(i);
            int v = nodes.indexOf(edge.getNode1());
            int u = nodes.indexOf(edge.getNode2());
            this.edges()[i][0] = v;
            this.edges()[i][1] = u;
        }

        setOptimalDistance(60.0);
        setTemperature(5.0);

        for (int i = 0; i < numIterations(); i++) {

            // Calculate repulsive forces.
            for (int v = 0; v < numNodes; v++) {
                nodeDisposition()[v][0] = 0.;
                nodeDisposition()[v][1] = 0.;

                for (int u = 0; u < numNodes; u++) {
                    double deltaX = nodePosition()[u][0] - nodePosition()[v][0];
                    double deltaY = nodePosition()[u][1] - nodePosition()[v][1];

                    double norm = norm(deltaX, deltaY);

                    if (norm == 0.0) {
                        continue;
                    }

                    if (norm > 4.0 * optimalDistance()) {
                        continue;
                    }

                    double repulsiveForce = fr(norm);

                    nodeDisposition()[v][0] += (deltaX / norm) * repulsiveForce;
                    nodeDisposition()[v][1] += (deltaY / norm) * repulsiveForce;
                }
            }

            // Calculate attractive forces.
            for (int j = 0; j < edges.size(); j++) {
                int u = this.edges()[j][0];
                int v = this.edges()[j][1];

                double deltaX = nodePosition()[v][0] - nodePosition()[u][0];
                double deltaY = nodePosition()[v][1] - nodePosition()[u][1];

                double norm = norm(deltaX, deltaY);

                if (norm == 0.0) {
                    continue;
                }

                if (norm < 1.5 * optimalDistance()) {
                    continue;
                }

                double attractiveForce = fa(norm);
                double attractX = (deltaX / norm) * attractiveForce;
                double attractY = (deltaY / norm) * attractiveForce;

                nodeDisposition()[v][0] -= attractX;
                nodeDisposition()[v][1] -= attractY;

                if (Double.isNaN(nodeDisposition()[v][0]) ||
                        Double.isNaN(nodeDisposition()[v][1])) {
                    throw new IllegalStateException("Undefined disposition.");
                }

                nodeDisposition()[u][0] += attractX;
                nodeDisposition()[u][1] += attractY;

                if (Double.isNaN(nodeDisposition()[u][0]) ||
                        Double.isNaN(nodeDisposition()[u][1])) {
                    throw new IllegalStateException("Undefined disposition.");
                }
            }

            for (int v = 0; v < numNodes; v++) {
                double norm =
                        norm(nodeDisposition()[v][0], nodeDisposition()[v][1]);

                if (norm == 0.0) {
                    continue;
                }

                nodePosition()[v][0] += (nodeDisposition()[v][0] / norm) *
                        Math.min(norm, temperature());
                nodePosition()[v][1] += (nodeDisposition()[v][1] / norm) *
                        Math.min(norm, temperature());

                if (Double.isNaN(nodePosition()[v][0]) ||
                        Double.isNaN(nodePosition()[v][1])) {
                    throw new IllegalStateException("Undefined position.");
                }
            }
        }

        shiftComponentToRight(nodes);
    }

    private void shiftComponentToRight(List<Node> componentNodes) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;

        for (int i = 0; i < componentNodes.size(); i++) {
            if (nodePosition()[i][0] < minX) {
                minX = nodePosition()[i][0];
            }
            if (nodePosition()[i][1] < minY) {
                minY = nodePosition()[i][1];
            }
        }

        leftmostX = leftmostX() + 100.;

        for (int i = 0; i < componentNodes.size(); i++) {
            nodePosition()[i][0] += leftmostX() - minX;
            nodePosition()[i][1] += 40.0 - minY;
        }

        for (int i = 0; i < componentNodes.size(); i++) {
            if (nodePosition()[i][0] > leftmostX()) {
                leftmostX = nodePosition()[i][0];
            }
        }

        for (int i = 0; i < componentNodes.size(); i++) {
            Node node = componentNodes.get(i);
            node.setCenterX((int) nodePosition()[i][0]);
            node.setCenterY((int) nodePosition()[i][1]);
        }
    }

    //============================PRIVATE METHODS=========================//  \

    private double fa(double d) {
        return (d * d) / optimalDistance();
    }

    private double fr(double d) {
        return -(optimalDistance() * optimalDistance()) / d;
    }

    private double norm(double x, double y) {
        return Math.sqrt(x * x + y * y);
    }

    private Graph graph() {
        return graph;
    }

    private int[][] edges() {
        return edges;
    }

    private double[][] nodePosition() {
        return nodePosition;
    }

    private double[][] nodeDisposition() {
        return nodeDisposition;
    }

    private double optimalDistance() {
        return getOptimalDistance();
    }

    private int numIterations() {
        return numIterations;
    }

    private double temperature() {
        return getTemperature();
    }

    private double leftmostX() {
        return leftmostX;
    }

    private double getOptimalDistance() {
        return optimalDistance;
    }

    private void setOptimalDistance(double optimalDistance) {
        this.optimalDistance = optimalDistance;
    }

    private double getTemperature() {
        return temperature;
    }

    private void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}





