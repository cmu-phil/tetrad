package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.StatUtils;

import java.io.PrintStream;
import java.util.*;

import static java.util.Comparator.comparingDouble;

public class OrientColliders {
    public void setConflictRule(ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }

    public enum ColliderMethod {SEPSETS, CPC, MPC, PC_MAX, FIRST_EMPTY}

    public enum IndependenceDetectionMethod {ALPHA, FDR}

    public enum ConflictRule {PRIORITY, BIDIRECTED, OVERWRITE}

    private final IndependenceTest test;
    private int depth = 1000;
    private double orientationQ = 0.2;
    private PrintStream out = System.out;
    private boolean verbose = false;
    private IKnowledge knowledge = new Knowledge2();
    private ColliderMethod colliderMethod;
    private IndependenceDetectionMethod independenceDetectionMethod = IndependenceDetectionMethod.ALPHA;
    private ConflictRule conflictRule = ConflictRule.PRIORITY;
    private SepsetMap sepsets = null;

    public OrientColliders(IndependenceTest test, ColliderMethod colliderMethod) {
        this.test = test;
        if (colliderMethod == ColliderMethod.SEPSETS)
            throw new IllegalArgumentException("To search from sepsets, please use the constructor where you provide the sepsets.");
        this.colliderMethod = colliderMethod;
    }

    public OrientColliders(IndependenceTest test, SepsetMap sepsetMap) {
        this.test = test;
        this.sepsets = sepsetMap;
        this.colliderMethod = ColliderMethod.SEPSETS;
    }

    void orientTriples(Graph graph) {
        List<Triple> colliders = new ArrayList<>();
        List<Triple> ambiguous = new ArrayList<>();
        List<Triple> noncolliders = new ArrayList<>();

        Map<NodePair, List<PValue>> notBMap = new HashMap<>();

        for (Node b : graph.getNodes()) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;


            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                if (sepsets != null) {
                    List<Node> sepset = sepsets.get(a, c);
                    if (sepset != null) {
                        PValue pValue = new PValue(sepsets.getPValue(a, c), sepset);
                        notBMap.put(new NodePair(a, c), Collections.singletonList(pValue));

                        if (sepset.contains(b)) {
                            noncolliders.add(new Triple(a, b, c));
                        } else {
                            colliders.add(new Triple(a, b, c));
                        }
                    }
                } else {

                    List<PValue> pValues = getAllPValues(a, c, depth, graph, test);

                    List<PValue> bPvals = new ArrayList<>();
                    List<PValue> notbPvals = new ArrayList<>();

                    for (PValue p : pValues) {
                        if (p.getSepset().contains(b)) {
                            bPvals.add(p);
                        } else {
                            notbPvals.add(p);
                        }
                    }

                    if (colliderMethod == ColliderMethod.SEPSETS) {
                        List<Node> sepset = sepsets.get(a, c);

                        if (sepset.contains(b)) {
                            noncolliders.add(new Triple(a, b, c));

                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": noncollider"
                                        + " bVals = " + bPvals.size() + " notbVals " + notbPvals.size());
                            }
                        } else {
                            colliders.add(new Triple(a, b, c));

                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": COLLIDER"
                                        + " bVals = " + bPvals.size() + " notbVals " + notbPvals.size());
                            }
                        }
                    } else if (colliderMethod == ColliderMethod.CPC) {
                        List<PValue> existsb = getNegatives(bPvals, orientationQ);
                        List<PValue> existsnotb = getNegatives(notbPvals, orientationQ);

                        if (!existsb.isEmpty() && existsnotb.isEmpty()) {
                            noncolliders.add(new Triple(a, b, c));
                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": noncollider"
                                        + " existsb = " + existsb.size() + " existsnotb " + existsnotb.size());
                            }
                        } else if (existsb.isEmpty() && !existsnotb.isEmpty() && knowledgeAllowsCollider(a, b, c, knowledge)) {
                            colliders.add(new Triple(a, b, c));

                            notbPvals.sort((p1, p2) -> Double.compare(p2.getP(), p1.getP()));
                            notBMap.put(new NodePair(a, c), notbPvals);

                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": COLLIDER"
                                        + " existb = " + existsb.size() + " existsnotb " + existsnotb.size());
                            }
                        } else {
                            pValues.sort((o1, o2) -> Double.compare(o2.getP(), o1.getP()));
                            ambiguous.add(new Triple(a, b, c));

                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": ...ambiguous"
                                        + " existb = " + existsb.size() + " existsnotb " + existsnotb.size());
                            }
                        }
                    } else if (colliderMethod == ColliderMethod.MPC) {
                        List<PValue> existsb = getNegatives(bPvals, orientationQ);
                        List<PValue> existsnotb = getNegatives(notbPvals, orientationQ);

                        if (existsb.size() > existsnotb.size()) {
                            noncolliders.add(new Triple(a, b, c));
                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": noncollider"
                                        + " existsb = " + existsb.size() + " existsnotb " + existsnotb.size());
                            }
                        } else if (existsb.size() < existsnotb.size() && knowledgeAllowsCollider(a, b, c, knowledge)) {
                            colliders.add(new Triple(a, b, c));

                            notbPvals.sort((p1, p2) -> Double.compare(p2.getP(), p1.getP()));
                            notBMap.put(new NodePair(a, c), notbPvals);

                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": COLLIDER"
                                        + " existb = " + existsb.size() + " existsnotb " + existsnotb.size());
                            }
                        } else {
                            ambiguous.add(new Triple(a, b, c));

                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": ...ambiguous"
                                        + " existb = " + existsb.size() + " existsnotb " + existsnotb.size());
                            }
                        }
                    } else if (colliderMethod == ColliderMethod.PC_MAX) {
                        List<PValue> above = getNegatives(pValues, orientationQ);

                        above.sort(comparingDouble(PValue::getP));

                        Set<Node> sepset = null;

                        if (!above.isEmpty()) {
                            sepset = above.get(above.size() - 1).getSepset();
                        }

                        if (sepset != null && sepset.contains(b)) {
                            noncolliders.add(new Triple(a, b, c));
                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + orientationQ + ": noncollider"
                                        + " bVals = " + bPvals.size() + " notbVals " + notbPvals.size());
                            }
                        } else if (sepset != null && !sepset.contains(b)) {
                            colliders.add(new Triple(a, b, c));

                            notbPvals.sort((p1, p2) -> Double.compare(p2.getP(), p1.getP()));
                            notBMap.put(new NodePair(a, c), notbPvals);

                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": COLLIDER"
                                        + " bVals = " + bPvals.size() + " notbVals " + notbPvals.size());
                            }
                        } else {
                            ambiguous.add(new Triple(a, b, c));

                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": ...ambiguous"
                                        + " bVals = " + bPvals.size() + " notbVals " + notbPvals.size());
                            }
                        }

                    } else if (colliderMethod == ColliderMethod.FIRST_EMPTY) {
                        List<PValue> above = getNegatives(pValues, orientationQ);

                        above.sort(comparingDouble(PValue::getP));

                        Set<Node> sepset = null;

                        if (!above.isEmpty()) {
                            sepset = above.get(above.size() - 1).getSepset();
                        }

                        if (sepset != null && sepset.contains(b)) {
                            noncolliders.add(new Triple(a, b, c));
                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + orientationQ + ": noncollider"
                                        + " bVals = " + bPvals.size() + " notbVals " + notbPvals.size());
                            }
                        } else if (sepset != null && !sepset.contains(b)) {
                            colliders.add(new Triple(a, b, c));

                            notbPvals.sort((p1, p2) -> Double.compare(p2.getP(), p1.getP()));
                            notBMap.put(new NodePair(a, c), notbPvals);

                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": COLLIDER"
                                        + " bVals = " + bPvals.size() + " notbVals " + notbPvals.size());
                            }
                        } else {
                            ambiguous.add(new Triple(a, b, c));

                            if (verbose) {
                                out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": ...ambiguous"
                                        + " bVals = " + bPvals.size() + " notbVals " + notbPvals.size());
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("Undefined collider method");
                    }
                }
            }
        }

        colliders.sort(comparingDouble(a -> -notBMap.get(new NodePair(a.getX(), a.getZ())).get(0).getP()));

        for (Triple triple : colliders) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            if (conflictRule == ConflictRule.PRIORITY) {
                if (!(graph.getEndpoint(b, a) == Endpoint.ARROW || graph.getEndpoint(b, c) == Endpoint.ARROW)) {
                    if (!graph.getEdge(a, b).pointsTowards(a) && !graph.getEdge(b, c).pointsTowards(c)) {
                        graph.removeEdge(a, b);
                        graph.removeEdge(c, b);
                        graph.addDirectedEdge(a, b);
                        graph.addDirectedEdge(c, b);
                    }
                }
            } else if (conflictRule == ConflictRule.BIDIRECTED) {
                graph.setEndpoint(a, b, Endpoint.ARROW);
                graph.setEndpoint(c, b, Endpoint.ARROW);
            } else if (conflictRule == ConflictRule.OVERWRITE) {
                graph.removeEdge(a, b);
                graph.removeEdge(c, b);
                graph.addDirectedEdge(a, b);
                graph.addDirectedEdge(c, b);
            }
        }

        for (Triple triple : noncolliders) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            graph.addUnderlineTriple(a, b, c);
        }

        for (Triple triple : ambiguous) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            graph.addAmbiguousTriple(a, b, c);
        }

    }

    private boolean knowledgeAllowsCollider(Node a, Node b, Node c, IKnowledge knowledge) {
        return !knowledge.isForbidden(a.getName(), b.getName()) && !knowledge.isForbidden(c.getName(), b.getName());
    }

    private List<PValue> getAllPValues(Node a, Node c, int depth, Graph graph, IndependenceTest test) {
        List<Node> adja = graph.getAdjacentNodes(a);
        List<Node> adjc = graph.getAdjacentNodes(c);

        adja.remove(c);
        adjc.remove(a);

        List<List<Node>> adj = new ArrayList<>();
        adj.add(adja);
        adj.add(adjc);

        List<PValue> pValues = new ArrayList<>();

        for (List<Node> _adj : adj) {
            DepthChoiceGenerator cg1 = new DepthChoiceGenerator(_adj.size(), depth);
            int[] comb2;

            while ((comb2 = cg1.next()) != null) {
                List<Node> s = GraphUtils.asList(comb2, _adj);

                test.isIndependent(a, c, s);
                PValue _p = new PValue(test.getPValue(), s);
                if (pValues.contains(_p)) continue;
                pValues.add(_p);
            }
        }

        return pValues;
    }

    private List<PValue> getNegatives(List<PValue> pValues, double alpha) {
        return extractH1(pValues, alpha);
    }

    private List<PValue> extractH0(List<PValue> pValues, double alpha) {
        List<PValue> h1 = extractH1(pValues, alpha);
        List<PValue> h0 = new ArrayList<>(pValues);
        h0.removeAll(h1);
        return h0;
    }

    private List<PValue> extractH1(List<PValue> pValues, double alpha) {
        pValues.sort(comparingDouble(PValue::getP));

        List<PValue> neg = new ArrayList<>();

        int m = pValues.size();
        double pmax = pValues.get(m - 1).getP();

        int r = m;

        for (int i = m - 1; i >= 1; i--) {
            double pi = pValues.get(i - 1).getP();
            double pr = pValues.get(r - 1).getP();

            if (pi < pr - 1. / m && pi >= alpha) {
                neg.add(pValues.get(i - 1));
            } else {
                r = i;
            }
        }

        return neg;
    }

    private double getCutoff(List<PValue> pValues, double q) {
        List<Double> _pValues = new ArrayList<>();

        for (PValue p : pValues) {
            _pValues.add(p.getP());
        }

        return StatUtils.fdrCutoff(q, _pValues, true, false);
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setOrientationQ(double orientationQ) {
        this.orientationQ = orientationQ;
    }

    private static class PValue {
        private double p;
        private Set<Node> sepset;

        PValue(double p, List<Node> sepset) {
            this.p = p;
            this.sepset = new HashSet<>(sepset);
        }

        public Set<Node> getSepset() {
            return sepset;
        }

        public double getP() {
            return p;
        }

        public boolean equals(Object o) {
            if (!(o instanceof PValue)) return false;
            PValue _o = (PValue) o;
            return _o.getP() == getP() && _o.getSepset().equals(getSepset());
        }
    }

    public void setIndependenceDetectionMethod(IndependenceDetectionMethod independenceDetectionMethod) {
        this.independenceDetectionMethod = independenceDetectionMethod;
    }


}
