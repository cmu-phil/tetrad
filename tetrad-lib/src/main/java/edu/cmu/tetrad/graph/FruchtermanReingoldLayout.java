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
    private final Graph graph;

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
    private double optimalDistance = 100;

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
        GraphUtils.circleLayout(graph, 300, 300, 200);

        List<List<Node>> components =
                GraphUtils.connectedComponents(graph());

        Collections.sort(components, new Comparator<List<Node>>() {
            public int compare(List<Node> o1, List<Node> o2) {
                int i1 = o1.size();
                int i2 = o2.size();
                return i2 < i1 ? -1 : i2 == i1 ? 0 : 1;
            }
        });

        for (List<Node> component1 : components) {
            this.layoutComponent(component1);
        }
    }

    private void layoutComponent(List<Node> nodes) {
        int numNodes = nodes.size();
        nodePosition = new double[numNodes][2];
        nodeDisposition = new double[numNodes][2];

        for (int i = 0; i < numNodes; i++) {
            Node node = nodes.get(i);
            this.nodePosition()[i][0] = node.getCenterX();
            this.nodePosition()[i][1] = node.getCenterY();

            //pos[i][0] = RandomUtil.nextInt(600);
            //pos[i][1] = RandomUtil.nextInt(600);
        }

        List<Edge> edges = new ArrayList<>(GraphUtils.undirectedGraph(this.graph()).getEdges());

        for (Iterator<Edge> i = edges.iterator(); i.hasNext(); ) {
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
            edges()[i][0] = v;
            edges()[i][1] = u;
        }

        double avgDegree = 2 * graph.getNumEdges() / graph.getNumNodes();

        this.setOptimalDistance(20.0 + 20.0 * avgDegree);
        this.setTemperature(5.0);

        for (int i = 0; i < this.numIterations(); i++) {

            // Calculate repulsive forces.
            for (int v = 0; v < numNodes; v++) {
                this.nodeDisposition()[v][0] = 0.1;
                this.nodeDisposition()[v][1] = 0.1;

                for (int u = 0; u < numNodes; u++) {
                    double deltaX = this.nodePosition()[u][0] - this.nodePosition()[v][0];
                    double deltaY = this.nodePosition()[u][1] - this.nodePosition()[v][1];

                    double norm = this.norm(deltaX, deltaY);

                    if (norm == 0.0) {
                        norm = 0.1;
//                        continue;
                    }
//
//                    if (norm > 4.0 * getOptimalDistance()) {
//                        continue;
//                    }

                    double repulsiveForce = this.fr(norm);

                    this.nodeDisposition()[v][0] += (deltaX / norm) * repulsiveForce;
                    this.nodeDisposition()[v][1] += (deltaY / norm) * repulsiveForce;
                }
            }

            // Calculate attractive forces.
            for (int j = 0; j < edges.size(); j++) {
                int u = edges()[j][0];
                int v = edges()[j][1];

                double deltaX = this.nodePosition()[v][0] - this.nodePosition()[u][0];
                double deltaY = this.nodePosition()[v][1] - this.nodePosition()[u][1];

                double norm = this.norm(deltaX, deltaY);

                if (norm == 0.0) {
                    norm = 0.1;
//                    continue;
                }

//                if (norm < 1.5 * getOptimalDistance()) {
//                    continue;
//                }

                double attractiveForce = this.fa(norm);
                double attractX = (deltaX / norm) * attractiveForce;
                double attractY = (deltaY / norm) * attractiveForce;

                this.nodeDisposition()[v][0] -= attractX;
                this.nodeDisposition()[v][1] -= attractY;

                if (Double.isNaN(this.nodeDisposition()[v][0]) ||
                        Double.isNaN(this.nodeDisposition()[v][1])) {
                    throw new IllegalStateException("Undefined disposition.");
                }

                this.nodeDisposition()[u][0] += attractX;
                this.nodeDisposition()[u][1] += attractY;

                if (Double.isNaN(this.nodeDisposition()[u][0]) ||
                        Double.isNaN(this.nodeDisposition()[u][1])) {
                    throw new IllegalStateException("Undefined disposition.");
                }
            }

            for (int v = 0; v < numNodes; v++) {
                double norm = this.norm(this.nodeDisposition()[v][0], this.nodeDisposition()[v][1]);

//                if (norm == 0.0) {
//                    continue;
//                }

                this.nodePosition()[v][0] += (this.nodeDisposition()[v][0] / norm) *
                        Math.min(norm, this.getTemperature());
                this.nodePosition()[v][1] += (this.nodeDisposition()[v][1] / norm) *
                        Math.min(norm, this.getTemperature());

                if (Double.isNaN(this.nodePosition()[v][0]) ||
                        Double.isNaN(this.nodePosition()[v][1])) {
                    throw new IllegalStateException("Undefined position.");
                }
            }
        }

        this.shiftComponentToRight(nodes);
    }

    private void shiftComponentToRight(List<Node> componentNodes) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;

        for (int i = 0; i < componentNodes.size(); i++) {
            if (this.nodePosition()[i][0] < minX) {
                minX = this.nodePosition()[i][0];
            }
            if (this.nodePosition()[i][1] < minY) {
                minY = this.nodePosition()[i][1];
            }
        }

        leftmostX = this.leftmostX() + 100.;

        for (int i = 0; i < componentNodes.size(); i++) {
            this.nodePosition()[i][0] += this.leftmostX() - minX;
            this.nodePosition()[i][1] += 40.0 - minY;
        }

        for (int i = 0; i < componentNodes.size(); i++) {
            if (this.nodePosition()[i][0] > this.leftmostX()) {
                leftmostX = this.nodePosition()[i][0];
            }
        }

        for (int i = 0; i < componentNodes.size(); i++) {
            Node node = componentNodes.get(i);
            node.setCenterX((int) this.nodePosition()[i][0]);
            node.setCenterY((int) this.nodePosition()[i][1]);
        }
    }

    //============================PRIVATE METHODS=========================//  \

    private double fa(double d) {
        return (d * d) / this.getOptimalDistance();
    }

    private double fr(double d) {
        return -(this.getOptimalDistance() * this.getOptimalDistance()) / d;
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

    private int numIterations() {
        return 500;
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





