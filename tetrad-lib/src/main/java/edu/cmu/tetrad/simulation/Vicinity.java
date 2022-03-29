package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeEqualityMode;
import edu.cmu.tetrad.graph.NodeEqualityMode.Type;

import java.util.*;

/**
 * This version of Vicinity finds nearby nodes by searching with an expanding cube
 * Prior to Vicinity4, versions of Vicinity looked at the 3 axis independently instead of collectively.
 * <p>
 * Vicinity5 improves on Vicinity4 by allowing for the nodes to not be distributed evenly throughout
 * the location space. This is needed for fMRI data when the voxels are not perfect cubes.
 *
 * @author jdramsey
 * @author Erich Kummerfeld
 */
public class Vicinity {
    //these are value ranges, used to constrain searches at the edges
    private final int xLow;
    private final int xHigh;
    private final int yLow;
    private final int yHigh;
    private final int zLow;
    private final int zHigh;

    //these are the dimensions of the voxels
    private final double xDist;
    private final double yDist;
    private final double zDist;


    private final DataSet locationMap;

    //Vicinity4 just uses two maps, each from array to a set of edges
    private final Map<List<Integer>, Set<Edge>> Coords1 = new HashMap<>();
    private final Map<List<Integer>, Set<Edge>> Coords2 = new HashMap<>();

    public Vicinity(List<Edge> edges, DataSet locationMap, int xLow, int xHigh, int yLow, int yHigh, int zLow, int zHigh,
                    double xDist, double yDist, double zDist) {
        //EK: the xLow etc. ints are the bounds on the coordinates in the location space, I think
        this.xLow = xLow;
        this.xHigh = xHigh;
        this.yLow = yLow;
        this.yHigh = yHigh;
        this.zLow = zLow;
        this.zHigh = zHigh;

        this.xDist = xDist;
        this.yDist = yDist;
        this.zDist = zDist;

        this.locationMap = locationMap;

        NodeEqualityMode.setEqualityMode(Type.OBJECT);

        //make the edge accessible via the map from either of its endpoints
        for (Edge edge : edges) {
            this.add(Coords1, edge, Arrays.asList(this.getX(edge.getNode1(), locationMap), this.getY(edge.getNode1(), locationMap),
                    this.getZ(edge.getNode1(), locationMap)));

            this.add(Coords2, edge, Arrays.asList(this.getX(edge.getNode2(), locationMap), this.getY(edge.getNode2(), locationMap),
                    this.getZ(edge.getNode2(), locationMap)));
        }
    }

    //chunk basically establishes how quickly the search grows for a nearest edge. It should be small for
    //graphs that are dense in the location space, and large for graphs that are sparse in the location space
    public List<Edge> getVicinity(Edge edge, int chunk) {
        //the strategy employed here is to start from the input edge nodes, and expand the search from there
        //the rate of expansion is based on the chunk parameter
        //we start the range at 0, and increase it by chunk until another edge is found
        //we're looking for any edge that has one node close to node1 and the other node close to node2
        int baserange;
        if (edge.isDirected()) {
            baserange = this.findRangeD(edge, chunk);
        } else {
            baserange = this.findRangeU(edge, chunk);
        }

        //System.out.println("baserange: " +baserange);
        //since I'm searching in a cube but distance is usually measured euclidian, I increase range by sqrt(3)
        int range = (int) Math.ceil(Math.sqrt(3) * (double) baserange);
        //System.out.println(findEdges(edge,range));
        return this.findEdges(edge, range);
    }

    //======%====%=======Private methods===========%==========%=========
    //******************* This finds the range when edge is Undirected **********
    private int findRangeU(Edge edge, int chunksize) {
        Set<Edge> edges = new HashSet<>();
        //System.out.println("edges is empty?"+edges.isEmpty());
        //System.out.println("edges is null?"+edges == null);
        NodeEqualityMode.setEqualityMode(Type.OBJECT);
        int range = 0 - chunksize;
        while (edges.isEmpty()) {
            //increment range by chunk
            range += chunksize;
            //initialize the edge sets
            Set<Edge> node1edges1 = new HashSet<>();
            Set<Edge> node1edges2 = new HashSet<>();

            /*for Vicinity5 the first and second arguments of the for loop need to be modified so that
             * they respect that a single increment of the x/y/z grid scales with that dimension of the voxel
             */
            //create separate range values for x y and z, scaled by xdist ydist zdist
            int xrange = (int) Math.ceil(range / xDist);
            int yrange = (int) Math.ceil(range / yDist);
            int zrange = (int) Math.ceil(range / zDist);

            //list edges with either endpoint near node1
            for (int x = this.getX(edge.getNode1(), locationMap) - xrange; x <= this.getX(edge.getNode1(), locationMap) + xrange; x++) {
                for (int y = this.getY(edge.getNode1(), locationMap) - yrange; y <= this.getY(edge.getNode1(), locationMap) + yrange; y++) {
                    for (int z = this.getZ(edge.getNode1(), locationMap) - zrange; z <= this.getZ(edge.getNode1(), locationMap) + zrange; z++) {
                        if (x < xLow || x > xHigh || y < yLow || y > yHigh || z < zLow || z > zHigh) continue;
                        if (Coords1.get(Arrays.asList(x, y, z)) != null)
                            node1edges1.addAll(Coords1.get(Arrays.asList(x, y, z)));
                        if (Coords2.get(Arrays.asList(x, y, z)) != null)
                            node1edges2.addAll(Coords2.get(Arrays.asList(x, y, z)));
                    }
                }
            }
            //for bugchecking
            //System.out.println("node1edges1 is empty? "+node1edges1.isEmpty());
            //System.out.println("node1edges2 is empty? "+node1edges2.isEmpty());

            int x2 = this.getX(edge.getNode2(), locationMap);
            int y2 = this.getY(edge.getNode2(), locationMap);
            int z2 = this.getZ(edge.getNode2(), locationMap);
            //if one or both of the above lists is nonempty, find edges where the other endpoint is near node2!
            if (!node1edges1.isEmpty()) {
                for (Edge edge11 : node1edges1) {
                    int x = this.getX(edge11.getNode2(), locationMap);
                    int y = this.getY(edge11.getNode2(), locationMap);
                    int z = this.getZ(edge11.getNode2(), locationMap);
                    /*for Vicinity5 the first and second arguments of the for loop need to be modified so that
                     * they respect that a single increment of the x/y/z grid scales with that dimension of the voxel
                     */
                    if (x >= x2 - xrange && x <= x2 + xrange && y >= y2 - yrange && y <= y2 + yrange && z >= z2 - zrange && z <= z2 + zrange) {
                        edges.add(edge11);
                    }
                }
            }
            if (!node1edges2.isEmpty()) {
                for (Edge edge12 : node1edges2) {
                    int x = this.getX(edge12.getNode1(), locationMap);
                    int y = this.getY(edge12.getNode1(), locationMap);
                    int z = this.getZ(edge12.getNode1(), locationMap);
                    /*for Vicinity5 the first and second arguments of the for loop need to be modified so that
                     *  * they respect that a single increment of the x/y/z grid scales with that dimension of the voxel
                     *  Any time that xyz indexes are compared using range, then some rescaling needs to be done to
                     *  account for how much distance is covered by one increment of that index dimension
                     *  */
                    if (x >= x2 - xrange && x <= x2 + xrange && y >= y2 - yrange && y <= y2 + yrange && z >= z2 - zrange && z <= z2 + zrange) {
                        edges.add(edge12);
                    }
                }
            }
            //System.out.println("edges is empty?"+edges.isEmpty()+" at range "+range);
            //System.out.println(edges);
        }

        return range;
    }

    //**********====== This finds the range when Edge is Directed ============*********************
    private int findRangeD(Edge edge, int chunksize) {
        Set<Edge> edges = new HashSet<>();
        //It matters whether Node1 is the tail or the head of the arrow
        //Because of how the Edge class works, it looks like Node1 is ALWAYS the TAIL
        NodeEqualityMode.setEqualityMode(Type.OBJECT);
        int range = 0 - chunksize;
        while (edges.isEmpty()) {
            //increment range by chunk
            range += chunksize;

            //create separate range values for x y and z, scaled by xdist ydist zdist
            int xrange = (int) Math.ceil(range / xDist);
            int yrange = (int) Math.ceil(range / yDist);
            int zrange = (int) Math.ceil(range / zDist);

            //initialize the edge sets
            Set<Edge> node1edges1 = new HashSet<>();
            Set<Edge> node1edges2 = new HashSet<>();
            //list edges with either endpoint near node1
            for (int x = this.getX(edge.getNode1(), locationMap) - xrange; x <= this.getX(edge.getNode1(), locationMap) + xrange; x++) {
                for (int y = this.getY(edge.getNode1(), locationMap) - yrange; y <= this.getY(edge.getNode1(), locationMap) + yrange; y++) {
                    for (int z = this.getZ(edge.getNode1(), locationMap) - zrange; z <= this.getZ(edge.getNode1(), locationMap) + zrange; z++) {
                        if (x < xLow || x > xHigh || y < yLow || y > yHigh || z < zLow || z > zHigh) continue;
                        if (Coords1.get(Arrays.asList(x, y, z)) != null)
                            node1edges1.addAll(Coords1.get(Arrays.asList(x, y, z)));
                        if (Coords2.get(Arrays.asList(x, y, z)) != null)
                            node1edges2.addAll(Coords2.get(Arrays.asList(x, y, z)));
                    }
                }
            }

            //** Since edge is directed, node1edges2 is NOT allowed to contain directed edges
            //it's okay if the edges in node1edges2 are unidrected, though
            if (!node1edges2.isEmpty()) {
                List<Edge> edges12 = new ArrayList<>(node1edges2);
                for (Edge thisedge : edges12) {
                    if (thisedge.isDirected()) node1edges2.remove(thisedge);
                }

            }

            int x2 = this.getX(edge.getNode2(), locationMap);
            int y2 = this.getY(edge.getNode2(), locationMap);
            int z2 = this.getZ(edge.getNode2(), locationMap);
            //if one or both of the above lists is nonempty, find edges where the other endpoint is near node2!
            if (!node1edges1.isEmpty()) {
                for (Edge edge11 : node1edges1) {
                    int x = this.getX(edge11.getNode2(), locationMap);
                    int y = this.getY(edge11.getNode2(), locationMap);
                    int z = this.getZ(edge11.getNode2(), locationMap);
                    if (x >= x2 - xrange && x <= x2 + xrange && y >= y2 - yrange && y <= y2 + yrange && z >= z2 - zrange && z <= z2 + zrange) {
                        edges.add(edge11);
                    }
                }
            }
            if (!node1edges2.isEmpty()) {
                for (Edge edge12 : node1edges2) {
                    int x = this.getX(edge12.getNode1(), locationMap);
                    int y = this.getY(edge12.getNode1(), locationMap);
                    int z = this.getZ(edge12.getNode1(), locationMap);
                    if (x >= x2 - xrange && x <= x2 + xrange && y >= y2 - yrange && y <= y2 + yrange && z >= z2 - zrange && z <= z2 + zrange) {
                        edges.add(edge12);
                    }
                }
            }
            //System.out.println("edges is empty?"+edges.isEmpty()+" at range "+range);
            //System.out.println(edges);
        }

        return range;
    }

    //***********()(*&(*%^#$%^&*^&%^%^%******
    //this is like findRange, but it returns the edges within the range in one step, without iterating chunksize
    private List<Edge> findEdges(Edge edge, int range) {
        Set<Edge> edges = new HashSet<>();
        NodeEqualityMode.setEqualityMode(Type.OBJECT);
        //create separate range values for x y and z, scaled by xdist ydist zdist
        int xrange = (int) Math.ceil(range / xDist);
        int yrange = (int) Math.ceil(range / yDist);
        int zrange = (int) Math.ceil(range / zDist);

        //initialize the edge sets
        Set<Edge> node1edges1 = new HashSet<>();
        Set<Edge> node1edges2 = new HashSet<>();
        //list edges with either endpoint near node1
        for (int x = this.getX(edge.getNode1(), locationMap) - xrange; x <= this.getX(edge.getNode1(), locationMap) + xrange; x++) {
            for (int y = this.getY(edge.getNode1(), locationMap) - yrange; y <= this.getY(edge.getNode1(), locationMap) + yrange; y++) {
                for (int z = this.getZ(edge.getNode1(), locationMap) - zrange; z <= this.getZ(edge.getNode1(), locationMap) + zrange; z++) {
                    if (x < xLow || x > xHigh || y < yLow || y > yHigh || z < zLow || z > zHigh) continue;
                    //if (Coords1.get(new Integer[] {x,y,z}) == null) continue;
                    if (Coords1.get(Arrays.asList(x, y, z)) != null)
                        node1edges1.addAll(Coords1.get(Arrays.asList(x, y, z)));
                    if (Coords2.get(Arrays.asList(x, y, z)) != null)
                        node1edges2.addAll(Coords2.get(Arrays.asList(x, y, z)));
                }
            }
        }
        int x2 = this.getX(edge.getNode2(), locationMap);
        int y2 = this.getY(edge.getNode2(), locationMap);
        int z2 = this.getZ(edge.getNode2(), locationMap);
        //if one or both of the above lists is nonempty, find edges where the other endpoint is near node2!
        if (!node1edges1.isEmpty()) {
            for (Edge edge11 : node1edges1) {
                int x = this.getX(edge11.getNode2(), locationMap);
                int y = this.getY(edge11.getNode2(), locationMap);
                int z = this.getZ(edge11.getNode2(), locationMap);
                if (x >= x2 - xrange && x <= x2 + xrange && y >= y2 - yrange && y <= y2 + yrange && z >= z2 - zrange && z <= z2 + zrange) {
                    edges.add(edge11);
                }
            }
        }
        if (!node1edges2.isEmpty()) {
            for (Edge edge12 : node1edges2) {
                int x = this.getX(edge12.getNode1(), locationMap);
                int y = this.getY(edge12.getNode1(), locationMap);
                int z = this.getZ(edge12.getNode1(), locationMap);
                if (x >= x2 - xrange && x <= x2 + xrange && y >= y2 - yrange && y <= y2 + yrange && z >= z2 - zrange && z <= z2 + zrange) {
                    edges.add(edge12);
                }
            }
        }

        return new ArrayList<>(edges);
    }

    //this is just the private method for adding entries to a map
    private void add(Map<List<Integer>, Set<Edge>> Coords, Edge edge, List<Integer> x) {
        Set<Edge> edges = Coords.get(x);
        if (edges == null) {
            edges = new HashSet<>();
            Coords.put(x, edges);
        }
        Coords.get(x).add(edge);
    }

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