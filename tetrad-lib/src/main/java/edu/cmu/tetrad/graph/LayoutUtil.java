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

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.PointXy;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TMath;

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

    // ---- Circle layout constants ----
    private static final int CIRCLE_BASE_OFFSET = 120;
    private static final int CIRCLE_NODE_FACTOR = 7;
    private static final int CIRCLE_MARGIN = 50;

    /**
     * Constructor.
     */
    public LayoutUtil() {
    }

    // ========================================================================
    // Progress listener interface (decouples layout from Swing)
    // ========================================================================

    /**
     * Callback interface for reporting layout progress. Implementations can
     * tie into Swing, a logger, or simply do nothing (headless use).
     */
    public interface LayoutProgressListener {
        /**
         * Reports the progress of a layout operation.
         *
         * @param percent the completion percentage of the layout process, ranging from 0 to 100
         * @param message a descriptive message providing additional information about the current state of progress
         */
        void onProgress(int percent, String message);

        /**
         * Determines if the layout operation has been canceled.
         *
         * @return {@code true} if the layout operation has been canceled, {@code false} otherwise.
         */
        boolean isCanceled();
    }

    /**
     * No-op listener for headless / non-GUI use.
     */
    public static class NoOpProgressListener implements LayoutProgressListener {

        /**
         * A no-operation implementation of the LayoutProgressListener interface.
         * This listener performs no actions and is intended for use in non-interactive
         * or headless environments where progress updates are unnecessary.
         */
        public NoOpProgressListener() {}

        @Override
        public void onProgress(int percent, String message) {
            // do nothing
        }

        @Override
        public boolean isCanceled() {
            return false;
        }
    }

    /**
     * Swing adapter that wraps a {@link ProgressMonitor}.
     */
    public static class SwingProgressListener implements LayoutProgressListener {
        private final ProgressMonitor monitor;

        /**
         * Constructs a SwingProgressListener instance. This listener acts as a Swing adapter
         * that wraps a {@link ProgressMonitor}, providing a visual progress indication for
         * tasks that report progress.
         *
         * The constructed {@link ProgressMonitor} is initialized with the following settings:
         * - No parent component (null).
         * - Title message: "Energy settling...".
         * - Initial note: "Energy = ?".
         * - Minimum progress value: 0.
         * - Maximum progress value: 100.
         * - Time to decide to pop up: 10 milliseconds.
         * - Time to pop up: 0 milliseconds.
         * - Initial progress: 0%.
         */
        public SwingProgressListener() {
            this.monitor = new ProgressMonitor(null, "Energy settling...",
                    "Energy = ?", 0, 100);
            monitor.setMillisToDecideToPopup(10);
            monitor.setMillisToPopup(0);
            monitor.setProgress(0);
        }

        @Override
        public void onProgress(int percent, String message) {
            monitor.setProgress(percent);
            monitor.setNote(message);
        }

        @Override
        public boolean isCanceled() {
            return monitor.isCanceled();
        }
    }

    // ========================================================================
    // Public static layout entry points
    // ========================================================================

    /**
     * <p>kamadaKawaiLayout.</p>
     *
     * @param graph               a {@link edu.cmu.tetrad.graph.Graph} object
     * @param randomlyInitialized a boolean
     * @param naturalEdgeLength   a double
     * @param springConstant      a double
     * @param stopEnergy          a double
     */
    public static void kamadaKawaiLayout(Graph graph, boolean randomlyInitialized,
                                         double naturalEdgeLength, double springConstant,
                                         double stopEnergy) {
        kamadaKawaiLayout(graph, randomlyInitialized, naturalEdgeLength,
                springConstant, stopEnergy, null);
    }

    /**
     * Overload that accepts an optional progress listener.
     *
     * @param graph               a {@link edu.cmu.tetrad.graph.Graph} object
     * @param randomlyInitialized a boolean
     * @param naturalEdgeLength   a double
     * @param springConstant      a double
     * @param stopEnergy          a double
     * @param listener            a {@link LayoutProgressListener}, or null for Swing default
     */
    public static void kamadaKawaiLayout(Graph graph, boolean randomlyInitialized,
                                         double naturalEdgeLength, double springConstant,
                                         double stopEnergy,
                                         LayoutProgressListener listener) {
        KamadaKawaiLayout layout = new KamadaKawaiLayout(graph);
        layout.setRandomlyInitialized(randomlyInitialized);
        layout.setNaturalEdgeLength(naturalEdgeLength);
        layout.setSpringConstant(springConstant);
        layout.setStopEnergy(stopEnergy);
        if (listener != null) {
            layout.setProgressListener(listener);
        }
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
     * Arranges the nodes in the graph in a circle if there are 20 or fewer
     * nodes, otherwise arranges them in a square.
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

        int centerx = CIRCLE_BASE_OFFSET + CIRCLE_NODE_FACTOR * graph.getNumNodes();
        int centery = CIRCLE_BASE_OFFSET + CIRCLE_NODE_FACTOR * graph.getNumNodes();
        int radius = centerx - CIRCLE_MARGIN;

        List<Node> nodes = graph.getNodes();
        Collections.sort(nodes);

        double rad = 2.0 * Math.PI / nodes.size();
        double phi = 1.5 * Math.PI; // start from 12 o'clock

        for (Node node : nodes) {
            int centerX = centerx + (int) (radius * TMath.cos(phi));
            int centerY = centery + (int) (radius * TMath.sin(phi));

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
     * Finds the set of nodes which have no children, followed by the set of
     * their parents, then the set of the parents' parents, and so on.  The
     * result is returned as a List of Lists.
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
     * Arranges the nodes in the result graph according to their positions in
     * the source graph.
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
     * Repositions latent nodes in the given graph based on their non-latent
     * neighbors.
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
     * Positions a latent node based on the average position of its measured
     * neighbors.
     *
     * @param latent    the latent node to be positioned
     * @param neighbors the set of neighboring nodes
     */
    public static void positionLatentNode(Node latent, Set<Node> neighbors) {
        if (neighbors.isEmpty()) return;

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

    // ========================================================================
    // Kamada-Kawai layout
    // ========================================================================

    /**
     * Lays out a graph by placing springs between the nodes and letting the
     * system settle (one node at a time).
     *
     * @author josephramsey
     */
    public static final class KamadaKawaiLayout {

        /**
         * Step size for central-difference first partial derivative.
         */
        private static final double FIRST_PARTIAL_H = 1.e-4;

        /**
         * Step size for second partial derivative (finite-difference).
         */
        private static final double SECOND_PARTIAL_H = 1.e-2;

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
         * l[i][j] is the natural length of the spring between node i and
         * node j defined by L * d[i][j].
         */
        private double[][] l;

        /**
         * k[i][j] is the strength of the spring between node i and node j,
         * defined by K / (d[i][j] * d[i][j]).
         */
        private double[][] k;

        /**
         * Leftmost x coord minus 100.0 to lay out the next component.
         */
        private double leftmostX = -50.;

        /**
         * Progress listener (replaces Swing ProgressMonitor).
         */
        private LayoutProgressListener progressListener;

        /**
         * True if nodes should be initialized in random locations, false if
         * they should be initialized in their getModel locations.
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

            // Default to SwingProgressListener for backward compatibility.
            if (this.progressListener == null) {
                this.progressListener = new SwingProgressListener();
            }

            this.progressListener.onProgress(0, "Energy = ?");

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

            this.progressListener.onProgress(100, "Done");
        }

        private boolean isRandomlyInitialized() {
            return this.randomlyInitialized;
        }

        /**
         * Sets whether the spring layout should start from a randomly
         * initialized position or from the getModel positions of the nodes.
         *
         * @param randomlyInitialized true for random start, false for current
         *                            positions.
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
         * @param stopEnergy the max delta at which the algorithm will stop.
         */
        public void setStopEnergy(double stopEnergy) {
            if (stopEnergy <= 0.0) {
                throw new IllegalArgumentException(
                        "Stop energy must be greater than zero.");
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
                        "Natural edge length should be greater than zero.");
            }

            this.naturalEdgeLength = naturalEdgeLength;
        }

        private double getSpringConstant() {
            return this.springConstant;
        }

        /**
         * Sets the spring constant; higher for more elasticity.
         *
         * @param springConstant the spring constant.
         */
        public void setSpringConstant(double springConstant) {
            if (springConstant < 0.0) {
                throw new IllegalArgumentException(
                        "Spring constant should be greater than zero.");
            }

            this.springConstant = springConstant;
        }

        /**
         * Sets the progress listener. Pass a {@link NoOpProgressListener} for
         * headless use.
         *
         * @param listener the listener to use.
         */
        public void setProgressListener(LayoutProgressListener listener) {
            this.progressListener = listener;
        }

        //============================PRIVATE METHODS=========================//

        /**
         * Initializes the layout for the given nodes.
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
                if (this.progressListener.isCanceled()) {
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
                this.progressListener.onProgress(progress,
                        "Energy = " + nf.format(maxDelta));

                if (m[0] == -1) {
                    throw new IllegalStateException();
                }

                double oldDelta = Double.NaN;
                double delta;

                while ((delta = delta(m[0])) > deltaCutoff) {
                    Thread.yield();
                    if (this.progressListener.isCanceled()) {
                        return;
                    }

                    if (TMath.abs(delta - oldDelta) < 0.001) {
                        this.p[m[0]][0] += RandomUtil.getInstance().nextInt(
                                2 * jump) - jump;
                        this.p[m[0]][1] += RandomUtil.getInstance().nextInt(
                                2 * jump) - jump;
                        continue;
                    }

                    double partialXX = secondPartial(m[0], 0, 0);
                    double partialXY = secondPartial(m[0], 0, 1);
                    double partialX = firstPartial(m[0], 0);
                    double partialYY = secondPartial(m[0], 1, 1);
                    double partialY = firstPartial(m[0], 1);

                    a.set(0, 0, partialXX);
                    a.set(0, 1, partialXY);
                    a.set(1, 0, partialXY);
                    a.set(1, 1, partialYY);

                    b.set(0, 0, -partialX);
                    b.set(1, 0, -partialY);

                    Matrix c;

                    try {
                        c = new Matrix(a.getSimpleMatrix().solve(
                                b.getSimpleMatrix()));
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
                    sum += 0.5 * this.k[i][j]
                            * TMath.pow(distance(i, j) - this.l[i][j], 2.0);
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

            index[0] = m;
            return maxDelta;
        }

        private double delta(int i) {
            double partialX = firstPartial(i, 0);
            double partialY = firstPartial(i, 1);
            return TMath.sqrt(partialX * partialX + partialY * partialY);
        }

        /**
         * Central-difference first partial derivative of energy w.r.t.
         * p[i][var].
         */
        private double firstPartial(int i, int var) {
            double storedCoord = this.p[i][var];

            this.p[i][var] -= FIRST_PARTIAL_H;
            double energy1 = energy();

            this.p[i][var] += 2.0 * FIRST_PARTIAL_H;
            double energy2 = energy();

            this.p[i][var] = storedCoord;
            return (energy2 - energy1) / (2.0 * FIRST_PARTIAL_H);
        }

        /**
         * Finite-difference second partial derivative of energy.
         */
        private double secondPartial(int m, int i, int j) {
            double storedX = this.p[m][0];
            double storedY = this.p[m][1];

            this.p[m][i] += SECOND_PARTIAL_H;
            this.p[m][j] += SECOND_PARTIAL_H;
            double ff1 = energy();

            this.p[m][j] -= 2 * SECOND_PARTIAL_H;
            double ff2 = energy();

            this.p[m][i] -= 2 * SECOND_PARTIAL_H;
            this.p[m][j] += 2 * SECOND_PARTIAL_H;
            double ff3 = energy();

            this.p[m][j] -= 2 * SECOND_PARTIAL_H;
            double ff4 = energy();

            this.p[m][0] = storedX;
            this.p[m][1] = storedY;

            return (ff1 - ff2 - ff3 + ff4)
                    / (4.0 * SECOND_PARTIAL_H * SECOND_PARTIAL_H);
        }

        private double distance(int i, int j) {
            double x1 = this.p[i][0];
            double y1 = this.p[i][1];
            double x2 = this.p[j][0];
            double y2 = this.p[j][1];

            return TMath.sqrt(
                    (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
        }

        /**
         * BFS-based all-pairs shortest path. O(n*(n+e)), better than
         * Floyd-Warshall O(n^3) for sparse graphs.
         */
        private int[][] allPairsShortestPath() {
            int n = getComponentNodes().size();
            int[][] dist = new int[n][n];
            int infinity = n * n;

            for (int[] row : dist) {
                Arrays.fill(row, infinity);
            }

            for (int source = 0; source < n; source++) {
                dist[source][source] = 0;
                Queue<Integer> queue = new LinkedList<>();
                queue.add(source);

                while (!queue.isEmpty()) {
                    int current = queue.poll();
                    Node currentNode = getComponentNodes().get(current);

                    for (int neighbor = 0; neighbor < n; neighbor++) {
                        if (neighbor == current) continue;
                        Node neighborNode = getComponentNodes().get(neighbor);

                        if (this.graph.getEdge(currentNode, neighborNode) != null
                                && dist[source][neighbor]
                                > dist[source][current] + 1) {
                            dist[source][neighbor] =
                                    dist[source][current] + 1;
                            queue.add(neighbor);
                        }
                    }
                }
            }

            return dist;
        }

        private List<Node> getComponentNodes() {
            return this.componentNodes;
        }

        private void setComponentNodes(List<Node> componentNodes) {
            this.componentNodes = componentNodes;
        }
    }

    // ========================================================================
    // Fruchterman-Reingold layout
    // ========================================================================

    /**
     * Lays out a graph by linearly summing repulsive force between all nodes
     * and attractive force between adjacent nodes.  Includes a linear cooling
     * schedule and early termination when the layout stabilizes.
     *
     * @author josephramsey
     */
    public static final class FruchtermanReingoldLayout {

        /**
         * Average displacement per node below which the algorithm stops early.
         */
        private static final double CONVERGENCE_THRESHOLD = 0.01;

        /**
         * The graph being laid out.
         */
        private final Graph graph;

        /**
         * Array of edges for the graph.
         * The ith edge is edges[i][0] --> edges[i][1].
         */
        private int[][] edges;

        /**
         * The position of each node.
         * The position of the ith node is (pos[i][0], pos[i][1]).
         */
        private double[][] nodePosition;

        /**
         * The disposition of each node.
         * The disposition of the ith node is (disp[i][0], disp[i][1]).
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
         * Initial temperature, stored so cooling can be computed relative to
         * it.
         */
        private double initialTemperature;

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

            List<List<Node>> components =
                    this.graph.paths().connectedComponents();

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

            List<Edge> edgeList = new ArrayList<>(
                    GraphUtils.undirectedGraph(graph()).getEdges());

            edgeList.removeIf(edge -> !nodes.contains(edge.getNode1())
                    || !nodes.contains(edge.getNode2()));

            this.edges = new int[edgeList.size()][2];

            for (int i = 0; i < edgeList.size(); i++) {
                Edge edge = edgeList.get(i);
                int v = nodes.indexOf(edge.getNode1());
                int u = nodes.indexOf(edge.getNode2());
                this.edges()[i][0] = v;
                this.edges()[i][1] = u;
            }

            double avgDegree = 2 * this.graph.getNumEdges()
                    / (double) this.graph.getNumNodes();

            setOptimalDistance(20.0 + 20.0 * avgDegree);
            initTemperature();

            for (int iter = 0; iter < numIterations(); iter++) {

                // Calculate repulsive forces.
                for (int v = 0; v < numNodes; v++) {
                    nodeDisposition()[v][0] = 0.1;
                    nodeDisposition()[v][1] = 0.1;

                    for (int u = 0; u < numNodes; u++) {
                        double deltaX =
                                nodePosition()[u][0] - nodePosition()[v][0];
                        double deltaY =
                                nodePosition()[u][1] - nodePosition()[v][1];

                        double norm = norm(deltaX, deltaY);

                        if (norm == 0.0) {
                            norm = 0.1;
                        }

                        double repulsiveForce = fr(norm);

                        nodeDisposition()[v][0] +=
                                (deltaX / norm) * repulsiveForce;
                        nodeDisposition()[v][1] +=
                                (deltaY / norm) * repulsiveForce;
                    }
                }

                // Calculate attractive forces.
                for (int j = 0; j < edgeList.size(); j++) {
                    int u = this.edges()[j][0];
                    int v = this.edges()[j][1];

                    double deltaX =
                            nodePosition()[v][0] - nodePosition()[u][0];
                    double deltaY =
                            nodePosition()[v][1] - nodePosition()[u][1];

                    double norm = norm(deltaX, deltaY);

                    if (norm == 0.0) {
                        norm = 0.1;
                    }

                    double attractiveForce = fa(norm);
                    double attractX = (deltaX / norm) * attractiveForce;
                    double attractY = (deltaY / norm) * attractiveForce;

                    nodeDisposition()[v][0] -= attractX;
                    nodeDisposition()[v][1] -= attractY;

                    if (Double.isNaN(nodeDisposition()[v][0])
                            || Double.isNaN(nodeDisposition()[v][1])) {
                        throw new IllegalStateException(
                                "Undefined disposition.");
                    }

                    nodeDisposition()[u][0] += attractX;
                    nodeDisposition()[u][1] += attractY;

                    if (Double.isNaN(nodeDisposition()[u][0])
                            || Double.isNaN(nodeDisposition()[u][1])) {
                        throw new IllegalStateException(
                                "Undefined disposition.");
                    }
                }

                // Apply displacement clamped by temperature;
                // track total movement for early termination.
                double totalDisplacement = 0.0;

                for (int v = 0; v < numNodes; v++) {
                    double norm = norm(nodeDisposition()[v][0],
                            nodeDisposition()[v][1]);

                    double clampedX = (nodeDisposition()[v][0] / norm)
                            * TMath.min(norm, getTemperature());
                    double clampedY = (nodeDisposition()[v][1] / norm)
                            * TMath.min(norm, getTemperature());

                    nodePosition()[v][0] += clampedX;
                    nodePosition()[v][1] += clampedY;

                    totalDisplacement += norm(clampedX, clampedY);

                    if (Double.isNaN(nodePosition()[v][0])
                            || Double.isNaN(nodePosition()[v][1])) {
                        throw new IllegalStateException(
                                "Undefined position.");
                    }
                }

                cool(iter);

                // Early termination if the layout has stabilized.
                if (totalDisplacement / numNodes < CONVERGENCE_THRESHOLD) {
                    break;
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

        //============================PRIVATE METHODS=========================//

        private double fa(double d) {
            return (d * d) / getOptimalDistance();
        }

        private double fr(double d) {
            return -(getOptimalDistance() * getOptimalDistance()) / d;
        }

        private double norm(double x, double y) {
            return TMath.sqrt(x * x + y * y);
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

        /**
         * Initializes temperature proportional to graph size.
         */
        private void initTemperature() {
            this.initialTemperature =
                    10.0 * Math.sqrt(graph().getNumNodes());
            this.temperature = this.initialTemperature;
        }

        /**
         * Linear cooling schedule: temperature decreases toward zero over
         * the total number of iterations.
         */
        private void cool(int iteration) {
            this.temperature = this.initialTemperature
                    * (1.0 - (double) iteration / numIterations());
        }
    }
}
