package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;

import java.io.PrintStream;
import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Arrays.*;

public final class Grasp implements GraphSearch{

    private final Score score;
    private final Graph graph;
    private final List<Integer> order;
    private final List<Set<Integer>> parents;
    private final List<int[]> covered;
    private final List<int[]> exposed;

    private int numCovered = 0;
    private int numExposed = 0;
    private int depth = 1;

    private long elapsedTime;
    private boolean verbose = false;
    private PrintStream out = System.out;



    public Grasp(Score score) {
        if (score == null) throw new NullPointerException();

        this.score = score;
        graph = new EdgeListGraph();
        order = new ArrayList<>();
        parents = new ArrayList<>();
        covered = new ArrayList<>();
        exposed = new ArrayList<>();
    }

    private boolean coveredTuck(int[] nodes, int[] indices, Map<Integer, Set<Integer>> changes) {
        indices[0] = order.indexOf(nodes[0]);
        indices[1] = order.indexOf(nodes[1]);
        sort(indices);
        int i = indices[1];
        int j = indices[0];
        int a = order.get(indices[0]);
        int b = order.get(indices[1]);

//        out.printf("tuck: %d@%d %d@%d \n", a, j, b, i);
//        out.println(order);

        double s = 0;

        Set<Integer> A = parents.get(i);
        A.remove(a);

        do{
            int c = order.get(i - 1);
            Set<Integer> B = parents.get(i - 1);
//            B.add(b);
//            Set<Integer> S = new HashSet<>();
//            s += scoreShrink(c, B, S);
            order.set(i, c);
            parents.set(i, B);
//            changes.put(c,S);
            i--;
        } while(a != order.get(i));


        Set<Integer> B = parents.get(i + 1);
        B.add(b);
        Set<Integer> S0 = new HashSet<>();
        s += scoreShrink(a, B, S0);
        changes.put(a,S0);


        Set<Integer> S = new HashSet<>();
        S.add(a);
        s += scoreShrink(b, A, S);
        order.set(i, b);
        parents.set(i, A);
        changes.put(b,S);

//        out.println(order);

        return s > 0;
    }



    private boolean exposedTuckAndUntuck(int[] nodes) {
        int a = nodes[0];
        int b = nodes[1];

        int i = order.indexOf(b);
        do order.set(i, order.get(++i)); while(order.get(i) != a);

        Set<Integer> B = parents.get(i);
        Set<Integer> G = new HashSet<>();
        Set<Integer> S = new HashSet<>();

//        scoreGrowAndShrink(i, order.subList(0, i), B, G, S);

        return false;
    }



    private void untuck(int[] indices, Map<Integer, Set<Integer>> changes) {
        int i = indices[0];
        int j = indices[1];

        int a = order.get(i);
        int c = order.get(j);

//        out.printf("untuck\n");
//        out.println(order);

        Set<Integer> A = parents.get(i);
        A.addAll(changes.get(a));

        while(i < j) {
            int b = order.get(i + 1);
            Set<Integer> B = parents.get(i + 1);
            if(changes.containsKey(b)) B.addAll(changes.get(b));
            B.remove(a);
            order.set(i, b);
            parents.set(i, B);
            i++;
        }
        order.set(i, a);
        parents.set(i, A);

//        out.println(order);
    }

    /**
     * Performs DFS with max depth returning true an improvement is found and false otherwise.
     */
    private boolean graspDfs(int i, int d) {
        while(i < numCovered){
            int[] indices = new int[2];
            Map<Integer, Set<Integer>> changes = new HashMap<>();
            if(coveredTuck(covered.get(i++), indices, changes)) return true;
            if (d < depth && graspDfs(i, d + 1)) return true;
            untuck(indices, changes);
            break;
        }
        // Comment this line out for GSP.
//        for(int[] nodes : exposed) if(exposedTuckAndUntuck(nodes)) return true;

        return false;
    }

    /**
     * The variables covered, exposed, numCovered, and numExposed need to be updated once before each initial call of GraspDfs.
     */
    private void update() {
        covered.clear();
        exposed.clear();
        numCovered = 0;
        numExposed = 0;

        for(int i = 0; i < order.size() - 1; i++) {
            int a = order.get(i);
            Set<Integer> A = parents.get(i);
            for(int j = i + 1; j < order.size(); j++) {
                int b = order.get(j);
                Set<Integer> B = parents.get(b);
                if(B.contains(a)) {
                    if(B.size() - A.size() == 1 && B.containsAll(A)) {
                        covered.add(new int[] {a, b});
                        numCovered++;
                    } else {
                        exposed.add(new int[] {a, b});
                        numExposed++;
                    }
                }
            }
        }
    }

    /**
     * Returns the local score of a|A.
     */
    private double scoreInitial(int a, Set<Integer> A) {
        int i = 0;
        int[] B = new int[A.size()];
        for(int b : A) B[i++] = b;
        return score.localScore(a, B);
    }

    /**
     * Returns the local score of a|A after adding b to A.
     */
    private double scoreAddition(int a, int b, Set<Integer> A) {
        int i = 0;
        int[] B = new int[A.size()+1];
        for(int c : A) B[i++] = c;
        B[i] = b;
        return score.localScore(a, B);
    }

    /**
     * Returns the local score of a|A after removing b from A.
     */
    private double scoreRemoval(int a, int b, Set<Integer> A) {
        int i = 0;
        int[] B = new int[A.size()-1];
        for(int c : A) if(b != c) B[i++] = c;
        return score.localScore(a, B);
    }

    /**
     * Returns the local score difference of a|B before and after growing and shrinking B with respect to A.
     */
    private double scoreGrowAndShrink(int a, List<Integer> A, Set<Integer> B) {
        double s0 = scoreInitial(a, B);
        double s1 = s0;
        int b;

        do {
            b = -1;
            for(int c : A) {
                if(B.contains(c)) continue;
                double s = scoreAddition(a, c, B);
                if (s1 < s) {
                    s1 = s;
                    b = c;
                }
            }
            if(b != -1) B.add(b);
        } while(b != -1);

        do {
            b = -1;
            for(int c : B) {
                double s = scoreRemoval(a, c, B);
                if (s1 < s) {
                    s1 = s;
                    b = c;
                }
            }
            if(b != -1) B.remove(b);
        } while(b != -1);

        return s1 - s0;
    }

    /**
     * Returns the local score difference of a|B before and after shrinking B while tracking changes.
     */
    private double scoreShrink(int a, Set<Integer> B, Set<Integer> S) {
        double s0 = scoreInitial(a, B);
        double s1 = s0;
        int b;

        do {
            b = -1;
            for(int c : B) {
                double s = scoreRemoval(a, c, B);
                if (s1 < s) {
                    s1 = s;
                    b = c;
                }
            }
            if(b != -1) {
                B.remove(b);
                S.add(b);
            }
        } while(b != -1);

        return s1 - s0;
    }

    /**
     * Returns the local score difference of a|B before and after growing and shrinking B with respect to A while tracking changes.
     */
    private double scoreGrowAndShrink(int a, List<Integer> A, Set<Integer> B, Set<Integer> G, Set<Integer> S) {
        double s0 = scoreInitial(a, B);
        double s1 = s0;
        int b;

        do {
            b = -1;
            for(int c : A) {
                if(B.contains(c)) continue;
                double s = scoreAddition(a, c, B);
                if (s1 < s) {
                    s1 = s;
                    b = c;
                }
            }
            if(b != -1) {
                B.add(b);
                G.add(b);
            }
        } while(b != -1);

        do {
            b = -1;
            for(int c : B) {
                double s = scoreRemoval(a, c, B);
                if (s1 < s) {
                    s1 = s;
                    b = c;
                }
            }
            if(b != -1) {
                B.remove(b);
                S.add(b);
            }
        } while(b != -1);

        return s1 - s0;
    }

    public Graph search() {
        graph.clear();
        order.clear();
        parents.clear();

        long time1 = System.currentTimeMillis();

        List<Node> V = score.getVariables();
        for(int i = 0; i < V.size(); i++) {
            graph.addNode(V.get(i));
            order.add(i);
            Set<Integer> B = new HashSet<>();
            scoreGrowAndShrink(i, order.subList(0, i), B);
            parents.add(B);
        }

        do {
            update();
//            out.printf("\nnew dfs: %d \n", numCovered);
        } while(graspDfs(0, 1));

//        out.println("\nsearching complete");

        int i = 0;
        for(int a : order) for (int b : parents.get(i++)) graph.addDirectedEdge(V.get(b), V.get(a));

        MeekRules rules = new MeekRules();
        rules.setRevertToUnshieldedColliders(true);
        rules.orientImplied(graph);

        long time2 = System.currentTimeMillis();
        this.elapsedTime = time2 - time1;

        return graph;
    }

    public int getDepth() { return depth; }

    public void setDepth(int depth) { this.depth = depth; }

    public long getElapsedTime() { return elapsedTime; }

    public boolean isVerbose() { return verbose; }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public PrintStream getOut() { return out; }

    public void setOut(PrintStream out) { this.out = out; }

}
