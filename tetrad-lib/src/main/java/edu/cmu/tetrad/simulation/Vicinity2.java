package edu.cmu.tetrad.simulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeEqualityMode;

import java.util.*;

/**
 * @author jdramsey
 */
public class Vicinity2 {
    private int xLow;
    private int xHigh;
    private int yLow;
    private int yHigh;
    private int zLow;
    private int zHigh;
    //EK: I think range is the distance along each axis that a node can be to be "close enough" for Vicinity
    private int range = 6;
    private Map<Integer, Set<Edge>> xCoords = new HashMap<>();
    private Map<Integer, Set<Edge>> yCoords = new HashMap<>();
    private Map<Integer, Set<Edge>> zCoords = new HashMap<>();


    public Vicinity2(List<Edge> edges, DataSet locationMap, int xLow, int xHigh, int yLow, int yHigh, int zLow, int zHigh) {
        //EK: the xLow etc. ints are the bounds on the coordinates in the location space, I think
        this.xLow = xLow;
        this.xHigh = xHigh;
        this.yLow = yLow;
        this.yHigh = yHigh;
        this.zLow = zLow;
        this.zHigh = zHigh;

        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);

        for (Edge edge : edges) {
            add(xCoords, edge, getX(edge.getNode1(), locationMap));
            add(yCoords, edge, getY(edge.getNode1(), locationMap));
            add(zCoords, edge, getZ(edge.getNode1(), locationMap));

            add(xCoords, edge, getX(edge.getNode2(), locationMap));
            add(yCoords, edge, getY(edge.getNode2(), locationMap));
            add(zCoords, edge, getZ(edge.getNode2(), locationMap));
        }
    }

    public List<Edge> getVicinity(Edge edge, DataSet locationMap) {
        Set<Edge> edges = new HashSet<>();
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);
        //EK: I'm concerned that this won't actually remove many edges?
        //EK: since they only need one endpoint to be sorta close on ANY dimension
        //EK: this will carve out a thick 3D "+" shape and add every edge that touches it
        //EK: wouldn't it be preferable to carve out a smaller shape, like a cube?
        for (int x = getX(edge.getNode1(), locationMap) - range; x <= getX(edge.getNode1(), locationMap) + range; x++) {
            if (x < xLow || x > xHigh) continue;
            if (xCoords.get(x) == null) continue;
            edges.addAll(xCoords.get(x));
        }

        for (int y = getY(edge.getNode1(), locationMap) - range; y <= getY(edge.getNode1(), locationMap) + range; y++) {
            if (y < yLow || y > yHigh) continue;
            if (yCoords.get(y) == null) continue;
            edges.addAll(yCoords.get(y));
        }

        for (int z = getZ(edge.getNode1(), locationMap) - range; z <= getZ(edge.getNode1(), locationMap) + range; z++) {
            if (z < zLow || z > zHigh) continue;
            if (zCoords.get(z) == null) continue;
            edges.addAll(zCoords.get(z));
        }

        for (int x = getX(edge.getNode2(), locationMap) - range; x <= getX(edge.getNode2(), locationMap) + range; x++) {
            if (x < xLow || x > xHigh) continue;
            if (xCoords.get(x) == null) continue;
            edges.addAll(xCoords.get(x));
        }

        for (int y = getY(edge.getNode2(), locationMap) - range; y <= getY(edge.getNode2(), locationMap) + range; y++) {
            if (y < yLow || y > yHigh) continue;
            if (yCoords.get(y) == null) continue;
            edges.addAll(yCoords.get(y));
        }

        for (int z = getZ(edge.getNode2(), locationMap) - range; z <= getZ(edge.getNode2(), locationMap) + range; z++) {
            if (z < zLow || z > zHigh) continue;
            if (zCoords.get(z) == null) continue;
            edges.addAll(zCoords.get(z));
        }

        return new ArrayList<>(edges);
    }

    private void add(Map<Integer, Set<Edge>> xCoords, Edge edge, int x) {
        Set<Edge> edges = xCoords.get(x);

        if (edges == null) {
            edges = new HashSet<>();
            xCoords.put(x, edges);
        }

        xCoords.get(x).add(edge);
    }

    //=================Private methods==============================
    // want to use regular point and edge classes, so replace the below with private methods
    //this is where the loaded locationMap should be doing the work

    private int getX(Node node, DataSet locationMap) {
        //double output = locationMap.getDouble(0,locationMap.getColumn(node));
        return (int) locationMap.getDouble(0, locationMap.getColumn(node));
    }

    private int getY(Node node, DataSet locationMap) {
        //double output = locationMap.getDouble(0,locationMap.getColumn(node));
        return (int) locationMap.getDouble(1, locationMap.getColumn(node));
    }

    private int getZ(Node node, DataSet locationMap) {
        //double output = locationMap.getDouble(0,locationMap.getColumn(node));
        return (int) locationMap.getDouble(2, locationMap.getColumn(node));
    }

}