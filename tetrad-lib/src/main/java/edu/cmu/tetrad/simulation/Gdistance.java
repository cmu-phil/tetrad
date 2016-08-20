package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Erich on 7/3/2016.
 *
 * This class is used to compare the distance between two graphs learned from fmri data
 * the distance is calculated as the mean of the distance of the edges between the graphs
 * the distance between two edges is calculated as the distance between their endpoints
 * the distance between edges calculated this way is a true distance
 * the distance between two graphs is not a true distance because it is not symmetric
 */
public class Gdistance {

    public static List<Double> distances(Graph graph1, Graph graph2, DataSet locationMap) {
        //first, just impliment the brute force approach:
        // compare every edge in graph1 to all in graph2
        // for each edge in graph1, record the shortest distance to any edge in graph2
        // return the list of shortest distances

        double thisDistance = -1.0;
        double leastDistance = -1.0;
        int count = 1;
        List<Double> leastList = new ArrayList<>();
        //System.out.println(locationMap);
        // Make *SURE* that the graph nodes are the same as the location nodes
        graph1 = GraphUtils.replaceNodes(graph1,locationMap.getVariables());
        graph2 = GraphUtils.replaceNodes(graph2,locationMap.getVariables());

        //This first for loop should be parallelized.
        for (Edge edge1 : graph1.getEdges()) {
            //the variable "count" is used to initialize leastDistance to the first thisDistance
            count = 1;
            for (Edge edge2 : graph2.getEdges()) {
                thisDistance = edgesDistance(edge1, edge2, locationMap);
                //remember only the shortest distance seen
                if (count ==1) {
                    leastDistance = thisDistance;
                } else {
                    if (thisDistance < leastDistance) {
                        leastDistance = thisDistance;
                    }
                }
                count++;
            }
            //add it to a list of the leastDistances
            leastList.add(leastDistance);
        }
        return leastList;
    }


    //////======***PRIVATE METHODS BELOW *****=====/////

    private static double nodesDistance(Node node1, Node node2, DataSet locationMap) {
        //calculate distance between two nodes based on their locations
        //simple starter is simply the taxicab distance:
        //calc differences in X, Y, and Z axis, then sum them together.
        int column1 = locationMap.getColumn(node1);
        int column2 = locationMap.getColumn(node2);

        //System.out.println(column1);

        double value11 = locationMap.getDouble(0,column1);
        double value12 = locationMap.getDouble(1,column1);
        double value13 = locationMap.getDouble(2,column1);

        double value21 = locationMap.getDouble(0,column2);
        double value22 = locationMap.getDouble(1,column2);
        double value23 = locationMap.getDouble(2,column2);

        //taxicab distance
        //double taxicab = Math.abs(value11 - value21) + Math.abs(value12 - value22) + Math.abs(value13 - value23);
        //euclidian distance instead of taxicab
        double euclid = Math.sqrt((value11 - value21)*(value11 - value21)+(value12 - value22)*(value12 - value22)+(value13 - value23)*(value13 - value23) );

        return euclid;
    }

    private static double edgesDistance(Edge edge1, Edge edge2, DataSet locationMap) {
        //calculate distance between two edges based on distances of their endpoints
        //if both edges are directed, then:
        //compare edge1 head to edge2 head, tail to tail.
        //sum head distance and tail ditance
        if (edge1.isDirected() && edge2.isDirected()) {
            //find head and tail of edge1
            Node edge1h = Edges.getDirectedEdgeHead(edge1);
            Node edge1t = Edges.getDirectedEdgeTail(edge1);
            //find head and tail of edge2
            Node edge2h = Edges.getDirectedEdgeHead(edge2);
            Node edge2t = Edges.getDirectedEdgeTail(edge2);
            //compare tail to tail
            double tDistance = nodesDistance(edge1t, edge2t, locationMap);
            double hDistance = nodesDistance(edge1h, edge2h, locationMap);
            return tDistance + hDistance;
        }
        else {
            //otherwise if either edge is not directed:
            //for each of edge1's two endpoints, calc distance to both edge2 endpoints
            //store the shorter distances, and sum them.
            Node node11 = edge1.getNode1();
            Node node12 = edge1.getNode2();
            Node node21 = edge2.getNode1();
            Node node22 = edge2.getNode2();

            //first compare node1 to node1 and node2 to node2
            double dist11 = nodesDistance(node11, node21, locationMap);
            double dist22 = nodesDistance(node12, node22, locationMap);

            //then compare node1 to node2 and node2 to node1
            double dist12 = nodesDistance(node11, node22, locationMap);
            double dist21 = nodesDistance(node12, node21, locationMap);

            //then return the minimum of the two ways of pairing nodes from each edge
            return Math.min(dist11 + dist22, dist12 + dist21);
        }

    }
}
