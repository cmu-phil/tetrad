package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import org.apache.commons.lang3.RandomUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.apache.commons.math3.util.FastMath.max;
import static org.apache.commons.math3.util.FastMath.min;


/**
 * Implements a scorer extending Teyssier, M., and Koller, D. (2012). Ordering-based search: A simple and effective
 * algorithm for learning Bayesian networks. arXiv preprint arXiv:1207.1429. You give it a score function
 * and a variable ordering, and it computes the score. You can move any variable left or right, and it will
 * keep track of the score using the Teyssier and Kohler method. You can move a variable to a new position,
 * and you can bookmark a state and come back to it.
 *
 * @author josephramsey
 * @author bryanandrews
 */
public class TeyssierScorerExperimental {
    private final Score score;
    private final List<Node> variables;
    private final int n;
    private final int[] order;
//    private final ConcurrentMap<Integer, Set<Integer>> parents;
    private final Map<Integer, Set<Integer>> parents;
    private final double[] scores;


    public TeyssierScorerExperimental(Score score) {
        this.score = score;
        this.variables = score.getVariables();
        this.n = this.variables.size();

        this.order = new int[n];
//        this.parents = new ConcurrentHashMap<>();
        this.parents = new HashMap<>();
        this.scores = new double[n];

        for (int i = 0; i < n; i++) this.order[i] = i;
        reset();
    }


    private void reset() {
        for (int i = 0; i < n; i++) {
            int x = this.order[i];
            Set<Integer> xParents = new HashSet<>();
            this.parents.put(x, xParents);
            this.scores[x] = growShrink(i, i, Collections.emptySet(), xParents);
        }
    }


    public void shuffleOrder() {
        for (int i = 0; i < this.n - 2; i++) {
            int j = RandomUtils.nextInt(i, this.n);
            int x = this.order[j];
            this.order[j] = this.order[i];
            this.order[i] = x;
        }

        reset();
    }


    public void setOrder(Graph graph) {
        List<Integer> order = new LinkedList<>();
        for (int i : this.order) order.add(i);

        int i, j;
        i = this.n;
        while (i-- > 0) {
            ListIterator<Integer> itr = order.listIterator(order.size());
            do j = itr.previous();
            while (!validSink(this.variables.get(j), graph));
            graph.removeNode(this.variables.get(j));
            this.order[i] = j;
            itr.remove();
        }

        reset();
    }


    private boolean validSink(Node x, Graph graph) {
        LinkedList<Node> neighbors = new LinkedList<>();

        for (Edge edge : graph.getEdges(x)) {
            if (edge.getDistalEndpoint(x) == Endpoint.ARROW) return false;
            if (edge.getProximalEndpoint(x) == Endpoint.TAIL) neighbors.add(edge.getDistalNode(x));
        }

        while (!neighbors.isEmpty()) {
            Node y = neighbors.pop();
            for (Node z : neighbors) if (!graph.isAdjacentTo(y, z)) return false;
        }

        return true;
    }


    public double getScore() {
        double score = 0;
        for (int i = 0; i < n; i++) score += this.scores[i];

        return score;
    }


    public boolean hasParent(int i, int j) {
        return this.parents.get(this.order[i]).contains(this.order[j]);
    }


    private Set<Integer> getAncestors(int i, int j) {
        Set<Integer> ancestors = new HashSet<>();
        ancestors.add(this.order[i]);
        for (int k = i; k > j; k--) {
            if (!ancestors.contains(this.order[k])) continue;
            ancestors.addAll(this.parents.get(k));
        }
        ancestors.remove(this.order[j]);

        return ancestors;
    }


    public boolean tuck(int i, int j) {
        Set<Integer> ancestors = getAncestors(i, j);
        Map<Integer, Set<Integer>> parents = new HashMap<>();

        int n = i - j + 1;
        int[] order = new int[n];
        double[] scores = new double[n];
        double diff = 0;

        int ii = 0;
        int jj = n;

        while (i >= j) {
            int x = this.order[i];
            Set<Integer> xParents = new HashSet<>();
            parents.put(x, xParents);
            if (ancestors.contains(x)) {
                order[--jj] = x;
                scores[jj] = growShrink(i--, j, ancestors, xParents);
                diff += scores[jj] - this.scores[x];
            } else {
                order[ii] = x;
                scores[ii] = growShrink(i--, j + n - 1, ancestors, xParents);
                diff += scores[ii++] - this.scores[x];
            }
        }

        if (diff <= 0) return false;

        while (ii < n) {
            int x = order[ii];
            this.order[j++] = x;
            this.parents.replace(x, parents.get(x));
            this.scores[x] = scores[ii++];
        }
        while (jj > 0) {
            int x = order[--jj];
            this.order[j++] = x;
            this.parents.replace(x, parents.get(x));
            this.scores[x] = scores[jj];
        }
        return true;
    }


    private int[] toArray(Set<Integer> set) {
        int i = 0;
        int[] array = new int[set.size()];
        for (Integer j : set) array[i++] = j;

        return array;
    }


    private int[] toArray(Set<Integer> set, int remove) {
        int i = 0;
        int[] array = new int[set.size() - 1];
        for (Integer j : set) if (j != remove) array[i++] = j;

        return array;
    }


    private double growShrink(int i, int j, Set<Integer> allowWithin, Set<Integer> parents) {
        int x = this.order[i];
        int y, z;

        double sMax = this.score.localScore(x, toArray(parents));

        int lb = min(i, j);
        int ub = max(i, j);

        do {
            z = -1;
            for (int k = 0; k <= ub; k++) {
                if (k == i) continue;
                y = this.order[k];

                if (parents.contains(y)) continue;
                if (k >= lb && !allowWithin.contains(y)) continue;

                parents.add(y);
                double s = this.score.localScore(x, toArray(parents));
                parents.remove(y);

                if (s <= sMax) continue;

                sMax = s;
                z = y;
            }
            if (z != -1) parents.add(z);
        } while(z != -1);

        do {
            z = -1;
            for (int remove : parents) {
                double s = this.score.localScore(x, toArray(parents, remove));

                if (s <= sMax) continue;

                sMax = s;
                z = remove;
            }
            if (z != -1) parents.remove(z);
        } while(z != -1);

        return sMax;
    }


    public Graph getGraph(boolean cpDag) {
        Graph graph = new EdgeListGraph(this.variables);

        for (int a : this.order) {
            for (int b : this.parents.get(a)) {
                graph.addDirectedEdge(this.variables.get(b), this.variables.get(a));
            }
        }

        if (cpDag) {
            MeekRules rules = new MeekRules();
            rules.orientImplied(graph);
        }

        return graph;
    }


    public int size() {
        return this.n;
    }
}
