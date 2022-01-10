package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;

import java.io.PrintStream;
import java.util.*;

public final class PLS implements GraphSearch{

    private final Score score;
    private final List<Node> variables;
    private final Map<Node, Integer> index;

    private final Map<Node, List<Node>> mb;
    private final Set<Set<Node>> doubles;
    private final Set<Set<Node>> triples;
    private final Graph graph;

    private int depth = 1;

    private boolean verbose = false;
    private long elapsedTime;

    private PrintStream out = System.out;

    public PLS(Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
        this.variables = new LinkedList<>(score.getVariables());
        this.index = new HashMap<>();

        int i = 0;
        for(Node a : this.variables) {
            this.index.put(a, i++);
        }

        this.mb = new HashMap<>();
        this.doubles = new HashSet<>();
        this.triples = new HashSet<>();
        this.graph = new EdgeListGraph(this.variables);
    }

    private List<Node> shrink(Node a, List<Node> A) {
        int i = this.index.get(a);
        List<Node> B = new ArrayList<>(A);

        int j = 0;
        int[] C = new int[B.size()];
        for(Node b : B) {
            C[j++] = this.index.get(b);
        }
        double s = this.score.localScore(i, C);
        Node b;

        do {
            b = null;
            for (Node c : B) {
                int k = 0;
                int[] D = new int[B.size()-1];
                for(Node d : B) {
                    if(c != d) D[k++] = this.index.get(d);
                }

                double t = this.score.localScore(i, D);
                if(s < t) {
                    s = t;
                    b = c;
                }
            }
            if(b != null) B.remove(b);
        } while(b != null);

        return B;
    }

//    private List<Node> growShrink(Node a, List<Node> A) {
//        int i = this.index.get(a);
//        List<Node> B = new ArrayList<>();
//
//        double s = this.score.localScore(i);
//        Node b;
//
//        do {
//            b = null;
//            for (Node c : A) {
//                int j = 0;
//                int[] C = new int[B.size()+1];
//                for(Node d : B) {
//                    C[j++] = this.index.get(d);
//                }
//                C[j] = this.index.get(c);
//
//                double t = this.score.localScore(i, C);
//                if(s < t) {
//                    s = t;
//                    b = c;
//                }
//            }
//            if(b != null){
//                A.remove(b);
//                B.add(b);
//            }
//        } while(b != null);
//
//        A.addAll(B);
//
//        do {
//            b = null;
//            for (Node c : B) {
//
//                int j = 0;
//                int[] C = new int[B.size()-1];
//                for(Node d : B) {
//                    if(c != d) C[j++] = this.index.get(d);
//                }
//
//                double t = this.score.localScore(i, C);
//                if(s < t) {
//                    s = t;
//                    b = c;
//                }
//            }
//            if(b != null) B.remove(b);
//        } while(b != null);
//
//        return B;
//    }

    private void addSets(Node a, List<Node> A) {
        Set<Node> set = new HashSet<>();
        set.add(a);
        for (Node b : A) {
            set.add(b);
            this.doubles.add(new HashSet<>(set));
            for(Node c : A) {
                if(this.index.get(b) < this.index.get(c)) {
                    set.add(c);
                    this.triples.add(new HashSet<>(set));
                    set.remove(c);
                }
            }
            set.remove(b);
        }
    }

    private void removeSets(Node a, List<Node> A, List<Node> B) {
        Set<Node> set = new HashSet<>();
        set.add(a);
        for(Node b : A) {
            if (!B.contains(b)) {
                set.add(b);
                this.doubles.remove(set);
                for (Node c : B) {
                    set.add(c);
                    this.triples.remove(set);
                    set.remove(c);
                }
                set.remove(b);
            }
        }
    }

    private void projectDAG() {
        for (Set<Node> set : this.doubles) {
            List<Node> list = new ArrayList<>(set);
            this.graph.addUndirectedEdge(list.get(0), list.get(1));
        }

        List<Node> variables = new ArrayList<>(this.variables);
        List<Set<Node>> doubles = new ArrayList(this.doubles);
        List<Set<Node>> triples = new ArrayList(this.triples);

        Collections.reverse(variables);

        for(Node a : variables) {

            ListIterator<Set<Node>> ditr = doubles.listIterator();
            while(ditr.hasNext()) {
                Set<Node> set = ditr.next();
                if(set.contains(a)) {
                    for(Node b : set) {
                        if(a != b) this.graph.addDirectedEdge(b, a);
                    }
                    ditr.remove();
                }
            }

            ListIterator<Set<Node>> titr = triples.listIterator();
            while(titr.hasNext()) {
                Set<Node> set = titr.next();
                if(set.contains(a)) {
                    for(Node b : set) {
                        if(a != b) this.graph.addDirectedEdge(b, a);
                    }
                    titr.remove();
                }
            }

        }
    }

//    private void localScore(Node a, Node b, List<Node> A, int depth) {
//        int i = this.index.get(b);
//        List<Node> B;
//
//        if(depth == 0) {
//            B = growShrink(a, A);
//            addSets(a, B);
//        } else {
//            B = shrink(a, A);
//            if (B.size() < A.size()) removeSets(a, A, B);
//        }
//
//        if(depth < this.depth && B.size() < A.size()) {
//            ListIterator<Node> itr = B.listIterator();
//            while(itr.hasNext()) {
//                Node c = itr.next();
//                itr.remove();
//                if(depth == 0 || i < this.index.get(c)) localScore(a, c, B, depth+1);
//                itr.add(c);
//            }
//        }
//    }


    private double addAndScore(int i, List<Node> B, Node c){
        int j = 0;
        int[] C = new int[B.size()+1];
        for(Node d : B) C[j++] = index.get(d);
        C[j] = index.get(c);
        return score.localScore(i, C);
    }

    private double removeAndScore(int i, List<Node> B, Node c){
        int j = 0;
        int[] C = new int[B.size()-1];
        for(Node d : B) if(c != d) C[j++] = index.get(d);
        return score.localScore(i, C);
    }

    private List<Node> growShrink(Node a, ListIterator<Node> A) {

        int i = index.get(a);
        double s = score.localScore(i);

        List<Node> B = new LinkedList<>();
        Node b = null;

        do {

            B.add(b);
            b = null;

            while(A.hasNext()) {
                Node c = A.next();
//                if(a == c) continue;
                double t = addAndScore(i,B,c);
                if(s < t) {
                    A.remove();
                    A.add(b);
                    s = t;
                    b = c;
                }
            }

            if(b == null) break;
            B.add(b);
            b = null;

            while(A.hasPrevious()) {
                Node c = A.previous();
//                if(a == c) continue;
                double t = addAndScore(i,B,c);
                if(s < t) {
                    A.add(b);
                    A.remove();
                    s = t;
                    b = c;
                }
            }

        } while(b != null);

        return B;
    }


    public Graph search() {
        long time1 = System.currentTimeMillis();


        List<Node> A = new LinkedList<>(variables);

        ListIterator<Node> B = A.listIterator();

        Node a = variables.get(3);

        B.next();

        B.remove();
        B.add(null);

        Node b = B.next();

        long time2 = System.currentTimeMillis();
        this.elapsedTime = time2 - time1;
        this.graph.setPag(true);

        return this.graph;
    }

    public boolean isVerbose() { return verbose; }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public int getDepth() { return depth; }

    public void setDepth(int depth) { this.depth = depth; }

    public PrintStream getOut() { return out; }

    public void setOut(PrintStream out) { this.out = out; }

    public long getElapsedTime() { return this.elapsedTime; }
}
