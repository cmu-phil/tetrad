package edu.cmu.tetrad.simulation;
import java.util.*;

/**
 * @author jdramsey
 */
public class Vicinity {
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

    public Vicinity(List<Edge> edges, int xLow, int xHigh, int yLow, int yHigh, int zLow, int zHigh) {
        //EK: the xLow etc. ints are the bounds on the coordinates in the location space, I think
        this.xLow = xLow;
        this.xHigh = xHigh;
        this.yLow = yLow;
        this.yHigh = yHigh;
        this.zLow = zLow;
        this.zHigh = zHigh;

        for (Edge edge : edges) {
            add(xCoords, edge, edge.getP1().getX());
            add(yCoords, edge, edge.getP1().getY());
            add(zCoords, edge, edge.getP1().getZ());

            add(xCoords, edge, edge.getP2().getX());
            add(yCoords, edge, edge.getP2().getY());
            add(zCoords, edge, edge.getP2().getZ());
        }
    }

    public List<Edge> getVicinity(Edge edge) {
        Set<Edge> edges = new HashSet<>();

        //EK: I'm concerned that this won't actually remove many edges?
        //EK: since they only need one endpoint to be sorta close on ANY dimension
        //EK: this will carve out a thick 3D "+" shape and add every edge that touches it
        //EK: wouldn't it be preferable to carve out a smaller shape, like a cube?
        for (int x = edge.getP1().getX() - range; x <= edge.getP1().getX() + range; x++) {
            if (x < xLow || x > xHigh) continue;
            edges.addAll(xCoords.get(x));
        }

        for (int y = edge.getP1().getY() - range; y <= edge.getP1().getY() + range; y++) {
            if (y < yLow || y > yHigh) continue;
            edges.addAll(yCoords.get(y));
        }

        for (int z = edge.getP1().getZ() - range; z <= edge.getP1().getZ() + range; z++) {
            if (z < zLow || z > zHigh) continue;
            edges.addAll(zCoords.get(z));
        }

        for (int x = edge.getP2().getX() - range; x <= edge.getP2().getX() + range; x++) {
            if (x < xLow || x > xHigh) continue;
            edges.addAll(xCoords.get(x));
        }

        for (int y = edge.getP2().getY() - range; y <= edge.getP2().getY() + range; y++) {
            if (y < yLow || y > yHigh) continue;
            edges.addAll(yCoords.get(y));
        }

        for (int z = edge.getP2().getZ() - range; z <= edge.getP1().getZ() + range; z++) {
            if (z < zLow || z > zHigh) continue;
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

    public class Point {
        private int x;
        private int y;
        private int z;

        public Point(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }
    }

    public class Edge {
        private Point p1;
        private Point p2;

        public Edge(Point p1, Point p2) {
            this.p1 = p1;
            this.p2 = p2;
        }

        public Point getP1() {
            return p1;
        }

        public Point getP2() {
            return p2;
        }
    }
}
