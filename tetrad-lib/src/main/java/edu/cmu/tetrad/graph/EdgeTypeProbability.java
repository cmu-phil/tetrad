package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Apr 13, 2017 3:56:46 PM
 *
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * @version $Id: $Id
 */
public class EdgeTypeProbability implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The edge type.
     */
    private EdgeType edgeType;

    /**
     * The properties.
     */
    private List<Edge.Property> properties = new ArrayList<>();

    /**
     * The probability.
     */
    private double probability;

    /**
     * <p>Constructor for EdgeTypeProbability.</p>
     */
    public EdgeTypeProbability() {

    }

    /**
     * <p>Constructor for EdgeTypeProbability.</p>
     *
     * @param edgeType    a {@link edu.cmu.tetrad.graph.EdgeTypeProbability.EdgeType} object
     * @param properties  a {@link java.util.List} object
     * @param probability a double
     */
    public EdgeTypeProbability(EdgeType edgeType, List<Edge.Property> properties, double probability) {
        this.edgeType = edgeType;
        this.properties = properties;
        this.probability = probability;
    }

    /**
     * <p>Constructor for EdgeTypeProbability.</p>
     *
     * @param edgeType    a {@link edu.cmu.tetrad.graph.EdgeTypeProbability.EdgeType} object
     * @param probability a double
     */
    public EdgeTypeProbability(EdgeType edgeType, double probability) {
        this.edgeType = edgeType;
        this.probability = probability;
    }

    /**
     * <p>Getter for the field <code>edgeType</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.EdgeTypeProbability.EdgeType} object
     */
    public EdgeType getEdgeType() {
        return this.edgeType;
    }

    /**
     * <p>Setter for the field <code>edgeType</code>.</p>
     *
     * @param edgeType a {@link edu.cmu.tetrad.graph.EdgeTypeProbability.EdgeType} object
     */
    public void setEdgeType(EdgeType edgeType) {
        this.edgeType = edgeType;
    }

    /**
     * <p>addProperty.</p>
     *
     * @param property a {@link edu.cmu.tetrad.graph.Edge.Property} object
     */
    public void addProperty(Edge.Property property) {
        if (!properties.contains(property)) {
            properties.add(property);
        }
    }

    /**
     * <p>removeProperty.</p>
     *
     * @param property a {@link edu.cmu.tetrad.graph.Edge.Property} object
     */
    public void removeProperty(Edge.Property property) {
        properties.remove(property);
    }

    /**
     * <p>Getter for the field <code>properties</code>.</p>
     *
     * @return a {@link java.util.ArrayList} object
     */
    public ArrayList<Edge.Property> getProperties() {
        return new ArrayList<>(this.properties);
    }

    /**
     * <p>Getter for the field <code>probability</code>.</p>
     *
     * @return a double
     */
    public double getProbability() {
        return this.probability;
    }

    /**
     * <p>Setter for the field <code>probability</code>.</p>
     *
     * @param probability a double
     */
    public void setProbability(double probability) {
        this.probability = probability;
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization to restore the
     * state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * An enumeration of the different types of edges.
     */
    public enum EdgeType {

        /**
         * No edge
         */
        nil,

        /**
         * Tail-to-arrow
         */
        ta,

        /**
         * Arrow-to-tail
         */
        at,

        /**
         * Circle-to-arrow
         */
        ca,

        /**
         * Arrow-to-circle
         */
        ac,

        /**
         * Circle-to-circle
         */
        cc,

        /**
         * Arrow-to-arrow
         */
        aa,

        /**
         * Tail-to-tail
         */
        tt
    }
}
