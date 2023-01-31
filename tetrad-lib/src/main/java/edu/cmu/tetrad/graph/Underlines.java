package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradSerializable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Underlines implements TripleClassifier, TetradSerializable {
    static final long serialVersionUID = 23L;

    private final Graph graph;

    /**
     * @serial
     */
    Set<Triple> underLineTriples;
    /**
     * @serial
     */
    Set<Triple> dottedUnderLineTriples;

    /**
     * Set of ambiguous triples.
     */
    protected Set<Triple> ambiguousTriples;

    public Underlines(Graph graph) {
        this.graph = graph;
        this.underLineTriples = new HashSet<>();
        this.dottedUnderLineTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
    }

    public Underlines(Underlines underlineModel) {
        this(underlineModel.graph);
        this.underLineTriples = underlineModel.getUnderLines();
        this.dottedUnderLineTriples = underlineModel.getDottedUnderlines();
        this.ambiguousTriples = underlineModel.getAmbiguousTriples();
    }

    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    public void setAmbiguousTriples(Set<Triple> triples) {
        this.ambiguousTriples.clear();

        for (Triple triple : triples) {
            addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }


    public Set<Triple> getUnderLines() {
        return new HashSet<>(this.underLineTriples);
    }

    public Set<Triple> getDottedUnderlines() {
        return new HashSet<>(this.dottedUnderLineTriples);
    }

    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.ambiguousTriples.contains(new Triple(x, y, z));
    }


    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(graph)) {
            return;
//            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> must lie along a path in the graph.");
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(graph)) {
            return;
//            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> must lie along a path in the graph.");
        }

        this.dottedUnderLineTriples.add(triple);
    }

    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }

    public void setUnderLineTriples(Set<Triple> triples) {
        this.underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    public void setDottedUnderLineTriples(Set<Triple> triples) {
        this.dottedUnderLineTriples.clear();

        for (Triple triple : triples) {
            addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    public void removeTriplesNotInGraph() {
//        if (!stuffRemovedSinceLastTripleAccess) return;

        for (Triple triple : new HashSet<>(this.ambiguousTriples)) {
            if (!graph.containsNode(triple.getX()) || !graph.containsNode(triple.getY())
                    || !graph.containsNode(triple.getZ())) {
                this.ambiguousTriples.remove(triple);
                continue;
            }

            if (!graph.isAdjacentTo(triple.getX(), triple.getY())
                    || !graph.isAdjacentTo(triple.getY(), triple.getZ())) {
                this.ambiguousTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<>(this.underLineTriples)) {
            if (!graph.containsNode(triple.getX()) || !graph.containsNode(triple.getY())
                    || !graph.containsNode(triple.getZ())) {
                this.underLineTriples.remove(triple);
                continue;
            }

            if (!graph.isAdjacentTo(triple.getX(), triple.getY()) || !graph.isAdjacentTo(triple.getY(), triple.getZ())) {
                this.underLineTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<>(this.dottedUnderLineTriples)) {
            if (!graph.containsNode(triple.getX()) || !graph.containsNode(triple.getY()) || !graph.containsNode(triple.getZ())) {
                this.dottedUnderLineTriples.remove(triple);
                continue;
            }

            if (!graph.isAdjacentTo(triple.getX(), triple.getY()) || graph.isAdjacentTo(triple.getY(), triple.getZ())) {
                this.dottedUnderLineTriples.remove(triple);
            }
        }
    }


    /**
     * @return the names of the triple classifications. Coordinates with
     * <code>getTriplesList</code>
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
        names.add("Underlines");
        names.add("Dotted Underlines");
        names.add("Ambiguous Triples");
        return names;
    }


    /**
     * @return the list of triples corresponding to
     * <code>getTripleClassificationNames</code> for the given node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        triplesList.add(GraphUtils.getUnderlinedTriplesFromGraph(node, graph));
        triplesList.add(GraphUtils.getDottedUnderlinedTriplesFromGraph(node, graph));
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        return triplesList;
    }
}
