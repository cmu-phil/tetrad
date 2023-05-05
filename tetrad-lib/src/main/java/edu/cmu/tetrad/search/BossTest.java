package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.shuffle;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.math.NumberUtils.max;
import static org.apache.commons.lang3.math.NumberUtils.min;

/**
 * <p>Implements the BOSS (Best Order Permutation Search) algorithm. This procedure uses
 * an optimization of the BOSS algorithm (reference to be included in a future version),
 * looking for a permutation such that when a DAG is built it has the fewest number of
 * edges (i.e., is a most 'frugal' or a 'sparsest' DAG). Returns the CPDAG of this discovered
 * frugal DAG.</p>
 *
 * <p>Knowledge can be used with this search. If tiered knowledge is used, then the procedure
 * is carried out for each tier separately, given the variable preceding that tier, which
 * allows the SP algorithm to address tiered (e.g., time series) problems with larger numbers of
 * variables.</p>
 *
 * <p>This class is meant to be used in the context of the PermutationSearch class (see).
 * the proper use is PermutationSearch search = new PermutationSearch(new Sp(score));</p>
 *
 * @author bryanandrews
 * @author josephramsey
 * @see PermutationSearch
 */
public class BossTest {
//    private final PermutationBes bes;
    private final Score score;
    private final List<Node> variables;
    private final List<Integer> order;
    private final Map<Node, GrowShrinkTree> gsts;
    private final double[][] insert;

    /**
     * This algorithm will work with an arbitrary score.
     * @param score The Score to use.
     */
    public BossTest(Score score) {
        this.score = score;
        this.variables = score.getVariables();

        this.order = new LinkedList<>();
        this.gsts = new HashMap<>();

        int i = 0;
        Map<Node, Integer> index = new HashMap<>();
        for (Node node : this.variables) {
            index.put(node, i);
            this.order.add(i++);
            this.gsts.put(node, new GrowShrinkTree(score, index, node));
        }

        this.insert = new double[i][i];
    }

    public Graph search() {

        // Maybe don't do this?
        shuffle(this.order);

        update(0, this.order.size());

        double bestScore;
        int[] bestInsert;

        double accum;
        double score;

        do {

            HashSet<Node> prefix = new HashSet<>();
            ListIterator<Integer> rows = this.order.listIterator();

            bestScore = 0;
            bestInsert = null;

            while (rows.hasNext()) {
                int i = rows.nextIndex();
                int row = rows.next();
                Node a = this.variables.get(row);

                double baseline = this.gsts.get(a).trace(new HashSet<>(prefix), new HashSet<>());

                accum = 0;
                for (int col : this.order) {
                    if (row == col) break;
                    accum += this.insert[row][col];
                }

                ListIterator<Integer> cols = this.order.listIterator();
                Set<Node> alt = new HashSet<>();

                while (cols.hasNext()) {
                    int j = cols.nextIndex();
                    int col = cols.next();
                    Node b = this.variables.get(col);

                    if (i == j) {
                        accum = 0;
                        continue;
                    }

                    if (i < j) accum += this.insert[row][col];
                    if (i > j) accum -= this.insert[row][col];

                    if (i < j) alt.add(b);

                    score = this.gsts.get(a).trace(new HashSet<>(alt), new HashSet<>());
                    score -= baseline;
                    score += accum;

                    if (i > j) alt.add(b);

                    if (score <= 1e-10) continue;
                    if ((i < j) && (score < bestScore)) continue;
                    if ((i > j) && (score <= bestScore) )continue;

                    bestScore = score;
                    bestInsert = new int[]{i, j};
                }

                prefix.add(a);
            }

            System.out.println(Arrays.toString(bestInsert));
            System.out.println(bestScore);

            if (bestInsert != null) {

                int i = bestInsert[0];
                int j = bestInsert[1];

                this.order.add(j, this.order.remove(i));

                if (i < j) update(i, j + 1);
                if (i > j) update(j, i + 1);
            }
        } while(bestInsert != null);

        Graph graph = getCpdag();

        Bes bes = new Bes(this.score);
        bes.bes(graph, this.variables);

        return graph;
    }


    private Graph getCpdag() {

        Graph graph = new EdgeListGraph(this.variables);

        HashSet<Node> prefix = new HashSet<>();
        HashSet<Node> parents = new HashSet<>();
        for (Node node : this.order.stream()
                .map(this.variables::get)
                .collect(toList())) {
            this.gsts.get(node).trace(new HashSet<>(prefix), parents);
            for (Node parent : parents) {
                graph.addDirectedEdge(parent, node);
            }
            prefix.add(node);
            parents.clear();
        }

        MeekRules rules = new MeekRules();
        rules.orientImplied(graph);

        return graph;
    }



    private void update(int i, int j) {

        Set<Node> prefix = this.order.subList(0, i).stream()
                .map(this.variables::get).collect(toSet());

        for (int col : this.order.subList(i, j)){
            Node a = this.variables.get(col);

            boolean halfway = false;

            for (int row: this.order){
                Node b = this.variables.get(row);

                double baseline = this.gsts.get(a).trace(new HashSet<>(prefix), new HashSet<>());
                if (row == col) {
                    halfway = true;
                    continue;
                }

                if (halfway) prefix.add(b);
                else prefix.remove(b);
                this.insert[row][col] = this.gsts.get(a).trace(new HashSet<>(prefix), new HashSet<>()) - baseline;
                if (halfway) prefix.remove(b);
                else prefix.add(b);
            }
            prefix.add(a);
        }
    }


    public List<Node> getVariables() {
        return variables;
    }

    public Score getScore() {
        return score;
    }
}



