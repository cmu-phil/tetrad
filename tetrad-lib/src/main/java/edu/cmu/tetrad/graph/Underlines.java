package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradSerializable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// This used ot be a field in the graph classes but that led to a circular dependency
// between the graph and the graph reader/writer. So now it's a separate class.
/**
 * <p>Underlines class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Underlines implements TripleClassifier, TetradSerializable {
    private static final long serialVersionUID = 23L;

    private final Graph graph;
    private Set<Triple> underLineTriples;
    private Set<Triple> dottedUnderLineTriples;
    private Set<Triple> ambiguousTriples;

    /**
     * <p>Constructor for Underlines.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Underlines(Graph graph) {
        this.graph = graph;
        this.underLineTriples = new HashSet<>();
        this.dottedUnderLineTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
    }

    /**
     * <p>Constructor for Underlines.</p>
     *
     * @param underlineModel a {@link edu.cmu.tetrad.graph.Underlines} object
     */
    public Underlines(Underlines underlineModel) {
        this(underlineModel.graph);
        this.underLineTriples = underlineModel.getUnderLines();
        this.dottedUnderLineTriples = underlineModel.getDottedUnderlines();
        this.ambiguousTriples = underlineModel.getAmbiguousTriples();
    }

    /**
     * <p>Getter for the field <code>ambiguousTriples</code>.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * <p>Setter for the field <code>ambiguousTriples</code>.</p>
     *
     * @param triples a {@link java.util.Set} object
     */
    public void setAmbiguousTriples(Set<Triple> triples) {
        this.ambiguousTriples.clear();

        for (Triple triple : triples) {
            addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * <p>getUnderLines.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Triple> getUnderLines() {
        return new HashSet<>(this.underLineTriples);
    }

    /**
     * <p>getDottedUnderlines.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Triple> getDottedUnderlines() {
        return new HashSet<>(this.dottedUnderLineTriples);
    }

    /**
     * States whether r-s-r is an underline triple or not.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.ambiguousTriples.contains(new Triple(x, y, z));
    }

    /**
     * States whether r-s-r is an underline triple or not.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    /**
     * <p>addAmbiguousTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    /**
     * <p>addUnderlineTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(graph)) {
            return;
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    /**
     * <p>addDottedUnderlineTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(graph)) {
            return;
        }

        this.dottedUnderLineTriples.add(triple);
    }

    /**
     * <p>removeAmbiguousTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    /**
     * <p>removeUnderlineTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * <p>removeDottedUnderlineTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * <p>Setter for the field <code>underLineTriples</code>.</p>
     *
     * @param triples a {@link java.util.Set} object
     */
    public void setUnderLineTriples(Set<Triple> triples) {
        this.underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * <p>Setter for the field <code>dottedUnderLineTriples</code>.</p>
     *
     * @param triples a {@link java.util.Set} object
     */
    public void setDottedUnderLineTriples(Set<Triple> triples) {
        this.dottedUnderLineTriples.clear();

        for (Triple triple : triples) {
            addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * <p>removeTriplesNotInGraph.</p>
     */
    public void removeTriplesNotInGraph() {
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
     * <p>getTriplesClassificationTypes.</p>
     *
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


    /** {@inheritDoc} */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        triplesList.add(GraphUtils.getUnderlinedTriplesFromGraph(node, graph));
        triplesList.add(GraphUtils.getDottedUnderlinedTriplesFromGraph(node, graph));
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        return triplesList;
    }
}
