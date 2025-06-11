package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.PointXy;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import java.text.NumberFormat;
import java.util.*;

/**
 * <p>LayoutUtil class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LayoutUtil {

    /**
     * Constructor.
     */
    public LayoutUtil() {
    }

    /**
     * <p>kamadaKawaiLayout.</p>
     *
     * @param graph               a {@link edu.cmu.tetrad.graph.Graph} object
     * @param randomlyInitialized a boolean
     * @param naturalEdgeLength   a double
     * @param springConstant      a double
     * @param stopEnergy          a double
     */
    public static void kamadaKawaiLayout(Graph graph, boolean randomlyInitialized, double naturalEdgeLength, double springConstant, double stopEnergy) {
        KamadaKawaiLayout layout = new KamadaKawaiLayout(graph);
        layout.setRandomlyInitialized(randomlyInitialized);
        layout.setNaturalEdgeLength(naturalEdgeLength);
        layout.setSpringConstant(springConstant);
        layout.setStopEnergy(stopEnergy);
        layout.doLayout();
    }

    /**
     * <p>fruchtermanReingoldLayout.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static void fruchtermanReingoldLayout(Graph graph) {
        FruchtermanReingoldLayout layout = new FruchtermanReingoldLayout(graph);
        layout.doLayout();
    }

    /**
     * <p>arrangeByLayout.</p>
     *
     * @param graph  a {@link edu.cmu.tetrad.graph.Graph} object
     * @param layout a {@link java.util.HashMap} object
     */
    public static void arrangeByLayout(Graph graph, HashMap<String, PointXy> layout) {
        for (Node node : graph.getNodes()) {
            PointXy point = layout.get(node.getName());
            node.setCenter(point.getX(), point.getY());
        }
    }

    /**
     * Arranges the nodes in the graph in a circle if there are 20 or fewer nodes, otherwise arranges them in a square.
     *
     * @param graph the graph to be arranged.
     */
    public static void defaultLayout(Graph graph) {
        boolean allOriented = true;

        for (Node node : graph.getNodes()) {
            if (node.getCenterX() == -1 || node.getCenterY() == -1) {
                allOriented = false;
            }
        }

        if (allOriented) {
            return;
        }

        if (graph.getNumNodes() <= 20) {
            circleLayout(graph);
        } else {
            squareLayout(graph);
        }
    }

    /**
     * Arranges the nodes in the graph in a circle.
     *
     * @param graph the graph to be arranged.
     */
    public static void circleLayout(Graph graph) {
        if (graph == null) {
            return;
        }

        int centerx = 120 + 7 * graph.getNumNodes();
        int centery = 120 + 7 * graph.getNumNodes();
        int radius = centerx - 50;

        List<Node> nodes = graph.getNodes();
        Collections.sort(nodes);

        double rad = 6.28 / nodes.size();
        double phi = .75 * 6.28;    // start from 12 o'clock.

        for (Node node : nodes) {
            int centerX = centerx + (int) (radius * FastMath.cos(phi));
            int centerY = centery + (int) (radius * FastMath.sin(phi));

            node.setCenterX(centerX);
            node.setCenterY(centerY);

            phi += rad;
        }

        repositionLatents(graph);
    }

    /**
     * <p>squareLayout.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static void squareLayout(Graph graph) {
        List<Node> nodes = new ArrayList<>(graph.getNodes());

        Collections.sort(nodes);

        int bufferx = 70;
        int buffery = 50;
        int spacex = 70;
        int spacey = 50;

        int side = nodes.size() / 4;

        if (nodes.size() % 4 != 0) {
            side++;
        }

        for (int i = 0; i < side; i++) {
            if (i >= nodes.size()) {
                break;
            }
            Node node = nodes.get(i);
            node.setCenterX(bufferx + spacex * i);
            node.setCenterY(buffery);
        }

        for (int i = 0; i < side; i++) {
            if (i + side >= nodes.size()) {
                break;
            }
            Node node = nodes.get(i + side);
            node.setCenterX(bufferx + spacex * side);
            node.setCenterY(buffery + i * spacey);
        }

        for (int i = 0; i < side; i++) {
            if (i + 2 * side >= nodes.size()) {
                break;
            }
            Node node = nodes.get(i + 2 * side);
            node.setCenterX(bufferx + spacex * (side - i));
            node.setCenterY(buffery + spacey * side);
        }

        for (int i = 0; i < side; i++) {
            if (i + 3 * side >= nodes.size()) {
                break;
            }
            Node node = nodes.get(i + 3 * side);
            node.setCenterX(bufferx);
            node.setCenterY(buffery + spacey * (side - i));
        }

        repositionLatents(graph);
    }

    /**
     * <p>layoutByCausalOrder.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static void layoutByCausalOrder(Graph graph) {
        List<List<Node>> tiers = getTiers(graph);

        int y = 0;

        for (List<Node> tier : tiers) {
            y += 60;

            if (tier.isEmpty()) continue;

            Node node = tier.get(0);

            int width = 80;

            int x = width / 2 + 10;

            node.setCenterX(x);
            node.setCenterY(y);

            int lastHalf = width / 2;

            for (int i = 1; i < tier.size(); i++) {
                node = tier.get(i);
                int thisHalf = width / 2;
                x += lastHalf + thisHalf + 5;
                node.setCenterX(x);
                node.setCenterY(y);
                lastHalf = thisHalf;
            }
        }
    }

    /**
     * Finds the set of nodes which have no children, followed by the set of their parents, then the set of the parents'
     * parents, and so on.  The result is returned as a List of Lists.
     *
     * @return the tiers of this digraph.
     */
    /**
     * Finds the set of nodes which have no children, followed by the set of their parents, then the set of the parents'
     * parents, and so on.  The result is returned as a List of Lists.
     *
     * @return the tiers of this digraph.
     */
    private static List<List<Node>> getTiers(Graph graph) {
        Set<Node> found = new HashSet<>();
        List<List<Node>> tiers = new LinkedList<>();

        // first copy all the nodes into 'notFound'.
        Set<Node> notFound = new HashSet<>(graph.getNodes());

        // repeatedly run through the nodes left in 'notFound'.  If any node
        // has all of its parents already in 'found', then add it to the
        // getModel tier.
        while (!notFound.isEmpty()) {
            List<Node> thisTier = new LinkedList<>();

            for (Node node : notFound) {
                List<Node> nodesInTo = graph.getNodesInTo(node, Endpoint.ARROW);
                nodesInTo.removeAll(graph.getNodesOutTo(node, Endpoint.ARROW));

                if (found.containsAll(nodesInTo)) {
                    thisTier.add(node);
                }
            }

            if (thisTier.isEmpty()) {
                tiers.add(new ArrayList<>(notFound));
                break;
            }

            // shift all the nodes in this tier from 'notFound' to 'found'.
            thisTier.forEach(notFound::remove);
            found.addAll(thisTier);

            // add the getModel tier to the list of tiers.
            tiers.add(thisTier);
        }

        return tiers;
    }


    /**
     * Arranges the nodes in the result graph according to their positions in the source graph.
     *
     * @param resultGraph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param sourceGraph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return true if all the nodes were arranged, false if not.
     */
    public static boolean arrangeBySourceGraph(Graph resultGraph, Graph sourceGraph) {
        if (resultGraph == null) {
            throw new IllegalArgumentException("Graph must not be null.");
        }

        if (sourceGraph == null) {
            defaultLayout(resultGraph);
            return true;
        }

        boolean arrangedAll = true;

        // There is a source graph. Position the nodes in the
        // result graph correspondingly.
        for (Node o : resultGraph.getNodes()) {
            String name = o.getName();
            Node sourceNode = sourceGraph.getNode(name);

            if (sourceNode == null) {
                arrangedAll = false;
                continue;
            }

            o.setCenterX(sourceNode.getCenterX());
            o.setCenterY(sourceNode.getCenterY());
        }

        return arrangedAll;
    }

    /**
     * Repositions latent nodes in the given graph based on their non-latent neighbors.
     * <p>
     * This method iterates through all nodes in the graph, identifies latent nodes, and repositions them using the
     * non-latent neighbors only. The method works by filtering out latent neighbors of each latent node before
     * repositioning.
     *
     * @param graph the graph containing the nodes to be repositioned.
     */
    public static void repositionLatents(Graph graph) {
        for (Node latent : graph.getNodes()) {
            if (latent.getNodeType() == NodeType.LATENT) {
                Set<Node> neighbors = new HashSet<>(graph.getAdjacentNodes(latent));

                for (Node neighbor : new HashSet<>(neighbors)) {
                    if (neighbor.getNodeType() == NodeType.LATENT) {
                        neighbors.remove(neighbor);
                    }
                }

                positionLatentNode(latent, neighbors);
            }
        }
    }

    /**
     * Positions a latent node based on the average position of its measured neighbors. The method calculates the
     * average x and y coordinates of the measured neighbors and repositions the latent node to this calculated center.
     *
     * @param latent    the latent node to be positioned
     * @param neighbors the set of neighboring nodes; only measured neighbors are used to calculate the position
     */
    public static void positionLatentNode(Node latent, Set<Node> neighbors) {
        if (neighbors.isEmpty()) return; // safety check to prevent division by zero.

        float avgX = 0f;
        float avgY = 0f;
        int count = 0;

        for (Node neighbor : neighbors) {
            if (neighbor.getNodeType() == NodeType.MEASURED) {
                avgX += neighbor.getCenterX();
                avgY += neighbor.getCenterY();
                count++;
            }
        }

        avgX /= count;
        avgY /= count;

        latent.setCenter((int) avgX, (int) avgY);
    }

    /**
     * Lays out a graph by placing springs between the nodes and letting the system settle (one node at a time).
     *
     * @author josephramsey
     */
    public static final class KamadaKawaiLayout {

        /**
         * The graph being laid out.
         */
        private final Graph graph;

        /**
         * The list of nodes used to construct d, p, k, and l.
         */
        private List<Node> componentNodes;

        /**
         * Natural length of an edge.
         */
        private double naturalEdgeLength = 80.0;

        /**
         * Spring constant; higher for more elasticity.
         */
        private double springConstant = 0.5;

        /**
         * Node i is at (p[i][0], p[i][1]).
         */
        private double[][] p;

        /**
         * l[i][j] is the natural length of the spring between node i and node j defined by L * d[i][j].
         */
        private double[][] l;

        /**
         * k[i][j] is the strength of the spring between node i and node j, defined by K / (d[i][j] * d[i][j]).
         */
        private double[][] k;

        /**
         * Leftmost x coord minus 100.0 to lay out the next component.
         */
        private double leftmostX = -50.;

        /**
         * Monitors progress for the user.
         */
        private ProgressMonitor monitor;

        /**
         * True if nodes should be initialized in random locations, false if they should be initialized in their
         * getModel locations.
         */
        private boolean randomlyInitialized;

        /**
         * The max delta at which the algorithm will stop settling.
         */
        private double stopEnergy = 1.0;

        //==============================CONSTRUCTORS===========================//

        /**
         * Constructs a new Kamada-Kawai layout for the given graph.
         *
         * @param graph the graph to be laid out.
         */
        public KamadaKawaiLayout(Graph graph) {
            if (graph == null) {
                throw new NullPointerException();
            }

            this.graph = GraphUtils.undirectedGraph(graph);
        }

        //============================PUBLIC METHODS==========================//

        /**
         * Lays out the graph.
         */
        public void doLayout() {
            defaultLayout(this.graph);

            this.monitor = new ProgressMonitor(null, "Energy settling...",
                    "Energy = ?", 0, 100);
            getMonitor().setMillisToDecideToPopup(10);
            getMonitor().setMillisToPopup(0);
            getMonitor().setProgress(0);

            List<List<Node>> components = this.graph.paths().connectedComponents();

            components.sort((o1, o2) -> {
                int i1 = o1.size();
                int i2 = o2.size();
                return Integer.compare(i2, i1);
            });

            for (List<Node> component1 : components) {
                initialize(component1, isRandomlyInitialized());
                layoutComponent(component1);
            }

            getMonitor().setProgress(100);
        }


        private boolean isRandomlyInitialized() {
            return this.randomlyInitialized;
        }

        /**
         * Sets whether the spring layout should start from a randomlyInitialized position or from the getModel
         * positions of the nodes.
         *
         * @param randomlyInitialized true if the spring layout should start from a randomlyInitialized position, false
         *                            if the spring layout should start from the getModel positions of the nodes.
         */
        public void setRandomlyInitialized(boolean randomlyInitialized) {
            this.randomlyInitialized = randomlyInitialized;
        }

        private double getStopEnergy() {
            return this.stopEnergy;
        }

        /**
         * Sets the max delta at which the algorithm will stop settling.
         *
         * @param stopEnergy the max delta at which the algorithm will stop settling.
         */
        public void setStopEnergy(double stopEnergy) {
            if (stopEnergy <= 0.0) {
                throw new IllegalArgumentException(
                        "Stop energy must be greater than" + " zero.");
            }

            this.stopEnergy = stopEnergy;
        }


        private double getNaturalEdgeLength() {
            return this.naturalEdgeLength;
        }

        /**
         * Sets the natural length of an edge.
         *
         * @param naturalEdgeLength the natural length of an edge.
         */
        public void setNaturalEdgeLength(double naturalEdgeLength) {
            if (naturalEdgeLength < 0.0) {
                throw new IllegalArgumentException(
                        "Natural edge length should be " + "greater than zero.");
            }

            this.naturalEdgeLength = naturalEdgeLength;
        }

        private double getSpringConstant() {
            return this.springConstant;
        }

        /**
         * Sets the spring constant; higher for more elasticity.
         *
         * @param springConstant the spring constant; higher for more elasticity.
         */
        public void setSpringConstant(double springConstant) {
            if (springConstant < 0.0) {
                throw new IllegalArgumentException(
                        "Spring constant should be " + "greater than zero.");
            }

            this.springConstant = springConstant;
        }

        //============================PRIVATE METHODS=========================//

        /**
         * Initializes the layout for the given nodes.
         *
         * @param nodes               the nodes to be laid out.
         * @param randomlyInitialized true if the spring layout should start from a randomlyInitialized position, false
         *                            if the spring layout should start from the getModel positions of the nodes.
         */
        private void initialize(List<Node> nodes, boolean randomlyInitialized) {
            setComponentNodes(Collections.unmodifiableList(nodes));

            this.p = new double[nodes.size()][2];
            int[][] d;
            this.l = new double[nodes.size()][nodes.size()];
            this.k = new double[nodes.size()][nodes.size()];

            if (randomlyInitialized) {
                for (int i = 0; i < nodes.size(); i++) {
                    this.p[i][0] = RandomUtil.getInstance().nextInt(600);
                    this.p[i][1] = RandomUtil.getInstance().nextInt(600);
                }
            } else {
                for (int i = 0; i < nodes.size(); i++) {
                    Node node = nodes.get(i);
                    this.p[i][0] = node.getCenterX();
                    this.p[i][1] = node.getCenterY();
                }

            }

            d = allPairsShortestPath();

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = 0; j < nodes.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    this.l[i][j] = getNaturalEdgeLength() * d[i][j];
                }
            }

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = 0; j < nodes.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    this.k[i][j] = getSpringConstant() / (d[i][j] * d[i][j]);
                }
            }
        }

        private void layoutComponent(List<Node> componentNodes) {
            setComponentNodes(componentNodes);
            optimize(getStopEnergy());
            shiftComponentToRight(componentNodes);
        }

        private void shiftComponentToRight(List<Node> componentNodes) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;

            for (int i = 0; i < componentNodes.size(); i++) {
                if (this.p[i][0] < minX) {
                    minX = this.p[i][0];
                }
                if (this.p[i][1] < minY) {
                    minY = this.p[i][1];
                }
            }

            this.leftmostX += 100.;

            for (int i = 0; i < componentNodes.size(); i++) {
                this.p[i][0] += this.leftmostX - minX;
                this.p[i][1] += 40.0 - minY;
            }

            for (int i = 0; i < componentNodes.size(); i++) {
                if (this.p[i][0] > this.leftmostX) {
                    this.leftmostX = this.p[i][0];
                }
            }

            for (int i = 0; i < componentNodes.size(); i++) {
                Node node = componentNodes.get(i);
                node.setCenterX((int) this.p[i][0]);
                node.setCenterY((int) this.p[i][1]);
            }
        }

        private void optimize(double deltaCutoff) {
            NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

            double initialMaxDelta = -1.;
            double maxDelta;
            final int jump = 100;
            Matrix a = new Matrix(2, 2);
            Matrix b = new Matrix(2, 1);
            int oldM = -1;

            do {
                if (this.monitor.isCanceled()) {
                    return;
                }

                int[] m = new int[1];
                maxDelta = maxDelta(m);

                if (initialMaxDelta == -1) {
                    initialMaxDelta = maxDelta;
                }

                if (m[0] == oldM) {
                    this.p[m[0]][0] += RandomUtil.getInstance().nextInt(
                            2 * jump) - jump;
                    this.p[m[0]][1] += RandomUtil.getInstance().nextInt(
                            2 * jump) - jump;
                    continue;
                }

                oldM = m[0];

                int progress =
                        (int) (99.0 - 98.0 * maxDelta / (0.5 * initialMaxDelta));
                if (progress < 1) {
                    progress = 1;
                }
                if (progress > 99) {
                    progress = 99;
                }
                getMonitor().setProgress(progress);
                getMonitor().setNote("Energy = " + nf.format(maxDelta));

                if (m[0] == -1) {
                    throw new IllegalStateException();
                }

                double oldDelta = Double.NaN;
                double delta;

                while ((delta = delta(m[0])) > deltaCutoff) {
                    Thread.yield();
                    if (this.monitor.isCanceled()) {
                        return;
                    }

                    if (FastMath.abs(delta - oldDelta) < 0.001) {
                        this.p[m[0]][0] += RandomUtil.getInstance().nextInt(
                                2 * jump) - jump;
                        this.p[m[0]][1] += RandomUtil.getInstance().nextInt(
                                2 * jump) - jump;
                        continue;
                    }

                    final double h = 1.e-2;

                    double partialXX = secondPartial(m[0], 0, 0);
                    double partialXY = secondPartial(m[0], 0, 1);
                    double partialX = firstPartial(m[0], 0, h);
                    double partialYY = secondPartial(m[0], 1, 1);
                    double partialY = firstPartial(m[0], 1, h);

                    a.set(0, 0, partialXX);
                    a.set(0, 1, partialXY);
                    a.set(1, 0, partialXY);
                    a.set(1, 1, partialYY);

                    b.set(0, 0, -partialX);
                    b.set(1, 0, -partialY);

                    Matrix c;

                    try {
                        c = new Matrix(a.getDataCopy().solve(b.getDataCopy()));
                    } catch (Exception e) {
                        this.p[m[0]][0] += RandomUtil.getInstance().nextInt(
                                2 * jump) - jump;
                        this.p[m[0]][1] += RandomUtil.getInstance().nextInt(
                                2 * jump) - jump;
                        continue;
                    }

                    double dx = c.get(0, 0);
                    double dy = c.get(1, 0);

                    this.p[m[0]][0] += dx;
                    this.p[m[0]][1] += dy;

                    oldDelta = delta;
                }
            } while (maxDelta > deltaCutoff);
        }

        private double energy() {
            int n = this.p.length;
            double sum = 0.0;

            for (int i = 0; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    sum += 0.5 * this.k[i][j] * FastMath.pow(distance(i, j) - this.l[i][j], 2.0);
                }
            }

            return sum;
        }

        private double maxDelta(int[] index) {
            double maxDelta = Double.NEGATIVE_INFINITY;
            int m = -1;

            for (int i = 0; i < getComponentNodes().size(); i++) {
                double delta = delta(i);

                if (delta == Double.NEGATIVE_INFINITY) {
                    throw new IllegalStateException();
                }

                if (delta > maxDelta) {
                    maxDelta = delta;
                    m = i;
                }
            }

            //        System.out.println("maxDelta = " + maxDelta);
            index[0] = m;
            return maxDelta;
        }

        private double delta(int i) {
            double partialX = firstPartial(i, 0, 1.e-4);
            double partialY = firstPartial(i, 1, 1.e-4);
            return FastMath.sqrt(partialX * partialX + partialY * partialY);
        }

        private double firstPartial(int i, int var, double h) {
            double storedCoord = this.p[i][var];

            this.p[i][var] -= h;
            double energy1 = energy();

            this.p[i][var] += h;
            double energy2 = energy();

            this.p[i][var] = storedCoord;
            return (energy2 - energy1) / (2. * h);
        }

        private double secondPartial(int m, int i, int j) {
            double storedX = this.p[m][0];
            double storedY = this.p[m][1];

            this.p[m][i] += 0.01;
            this.p[m][j] += 0.01;
            double ff1 = energy();

            this.p[m][j] -= 2 * 0.01;
            double ff2 = energy();

            this.p[m][i] -= 2 * 0.01;
            this.p[m][j] += 2 * 0.01;
            double ff3 = energy();

            this.p[m][j] -= 2 * 0.01;
            double ff4 = energy();

            this.p[m][0] = storedX;
            this.p[m][1] = storedY;

            return (ff1 - ff2 - ff3 + ff4) / (4.0 * 0.01 * 0.01);
        }

        private double distance(int i, int j) {
            double x1 = this.p[i][0];
            double y1 = this.p[i][1];
            double x2 = this.p[j][0];
            double y2 = this.p[j][1];

            return FastMath.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
        }

        /**
         * Floyd's all-pairs shortest-path algorithm, restricted to integral lengths. Returns an int[][] matrix I, where
         * I[i][j] is the length of the shortest path from i to j.
         */
        private int[][] allPairsShortestPath() {
            int[][] I1 = new int[getComponentNodes().size()][getComponentNodes()
                    .size()];
            int[][] I2 = new int[getComponentNodes().size()][getComponentNodes()
                    .size()];
            int infinity = getComponentNodes().size() * getComponentNodes().size();

            for (int i = 0; i < getComponentNodes().size(); i++) {
                for (int j = 0; j < getComponentNodes().size(); j++) {
                    Node node1 = getComponentNodes().get(i);
                    Node node2 = getComponentNodes().get(j);
                    if (this.graph.getEdge(node1, node2) != null) {
                        I2[i][j] = 1;
                    } else {
                        I2[i][j] = infinity;
                    }
                }
            }

            for (int k = 0; k < getComponentNodes().size(); k++) {
                int[][] temp = I1;
                I1 = I2;
                I2 = temp;

                for (int i = 0; i < getComponentNodes().size(); i++) {
                    for (int j = 0; j < getComponentNodes().size(); j++) {
                        I2[i][j] = FastMath.min(I1[i][j], I1[i][k] + I1[k][j]);
                    }
                }

            }

            return I2;
        }

        private ProgressMonitor getMonitor() {
            return this.monitor;
        }

        private List<Node> getComponentNodes() {
            return this.componentNodes;
        }

        private void setComponentNodes(List<Node> componentNodes) {
            this.componentNodes = componentNodes;
        }
    }

    /**
     * Lays out a graph by linearly summing repulsive force between all nodes and attractive force between adjacent
     * nodes.
     *
     * @author josephramsey
     */
    public static final class FruchtermanReingoldLayout {

        /**
         * The graph being laid out.
         */
        private final Graph graph;

        /**
         * Array of e for the graph. The ith edge is e[i][0]-->e[i][[1].
         */
        private int[][] edges;

        /**
         * The position of each node. The position of the ith node is (pos[i][0], pos[i][1]).
         */
        private double[][] nodePosition;

        /**
         * The disposition of each node. The disposition of the ith node is (disp[i][0], disp[i][1]).
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

        /**
         * Constructs a new FruchtermanReingoldLayout for the given graph.
         *
         * @param graph the graph to be laid out.
         */
        public FruchtermanReingoldLayout(Graph graph) {
            if (graph == null) {
                throw new NullPointerException();
            }

            this.graph = graph;
        }

        //============================PUBLIC METHODS==========================//

        /**
         * Lays out the graph.
         */
        public void doLayout() {
            defaultLayout(this.graph);

            List<List<Node>> components = this.graph.paths().connectedComponents();

            components.sort((o1, o2) -> {
                int i1 = o1.size();
                int i2 = o2.size();
                return Integer.compare(i2, i1);
            });

            for (List<Node> component1 : components) {
                Collections.sort(component1);
                layoutComponent(component1);
            }
        }

        private void layoutComponent(List<Node> nodes) {
            int numNodes = nodes.size();
            this.nodePosition = new double[numNodes][2];
            this.nodeDisposition = new double[numNodes][2];

            for (int i = 0; i < numNodes; i++) {
                Node node = nodes.get(i);
                nodePosition()[i][0] = node.getCenterX();
                nodePosition()[i][1] = node.getCenterY();
            }

            List<Edge> edges = new ArrayList<>(GraphUtils.undirectedGraph(graph()).getEdges());

            edges.removeIf(edge -> !nodes.contains(edge.getNode1()) ||
                                   !nodes.contains(edge.getNode2()));

            this.edges = new int[edges.size()][2];

            for (int i = 0; i < edges.size(); i++) {
                Edge edge = edges.get(i);
                int v = nodes.indexOf(edge.getNode1());
                int u = nodes.indexOf(edge.getNode2());
                this.edges()[i][0] = v;
                this.edges()[i][1] = u;
            }

            double avgDegree = 2 * this.graph.getNumEdges() / (double) this.graph.getNumNodes();

            setOptimalDistance(20.0 + 20.0 * avgDegree);
            setTemperature();

            for (int i = 0; i < numIterations(); i++) {

                // Calculate repulsive forces.
                for (int v = 0; v < numNodes; v++) {
                    nodeDisposition()[v][0] = 0.1;
                    nodeDisposition()[v][1] = 0.1;

                    for (int u = 0; u < numNodes; u++) {
                        double deltaX = nodePosition()[u][0] - nodePosition()[v][0];
                        double deltaY = nodePosition()[u][1] - nodePosition()[v][1];

                        double norm = norm(deltaX, deltaY);

                        if (norm == 0.0) {
                            norm = 0.1;
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
                        norm = 0.1;
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
                    double norm = norm(nodeDisposition()[v][0], nodeDisposition()[v][1]);

                    nodePosition()[v][0] += (nodeDisposition()[v][0] / norm) *
                                            FastMath.min(norm, getTemperature());
                    nodePosition()[v][1] += (nodeDisposition()[v][1] / norm) *
                                            FastMath.min(norm, getTemperature());

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

            this.leftmostX = leftmostX() + 100.;

            for (int i = 0; i < componentNodes.size(); i++) {
                nodePosition()[i][0] += leftmostX() - minX;
                nodePosition()[i][1] += 40.0 - minY;
            }

            for (int i = 0; i < componentNodes.size(); i++) {
                if (nodePosition()[i][0] > leftmostX()) {
                    this.leftmostX = nodePosition()[i][0];
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
            return (d * d) / getOptimalDistance();
        }

        private double fr(double d) {
            return -(getOptimalDistance() * getOptimalDistance()) / d;
        }

        private double norm(double x, double y) {
            return FastMath.sqrt(x * x + y * y);
        }

        private Graph graph() {
            return this.graph;
        }

        private int[][] edges() {
            return this.edges;
        }

        private double[][] nodePosition() {
            return this.nodePosition;
        }

        private double[][] nodeDisposition() {
            return this.nodeDisposition;
        }

        private int numIterations() {
            return 500;
        }

        private double leftmostX() {
            return this.leftmostX;
        }

        private double getOptimalDistance() {
            return this.optimalDistance;
        }

        private void setOptimalDistance(double optimalDistance) {
            this.optimalDistance = optimalDistance;
        }

        private double getTemperature() {
            return this.temperature;
        }

        private void setTemperature() {
            this.temperature = 5.0;
        }
    }
}
