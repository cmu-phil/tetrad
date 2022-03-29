package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.graph.Edge.Property;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * Apr 13, 2017 3:56:46 PM
 *
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 */
public class EdgeTypeProbability implements TetradSerializable {

    private static final long serialVersionUID = 23L;

    public enum EdgeType {
        nil, ta, at, ca, ac, cc, aa, tt
    }

    private EdgeType edgeType;

    private List<Edge.Property> properties = new ArrayList<>();

    private double probability;

    public EdgeTypeProbability() {

    }

    public EdgeTypeProbability(final EdgeType edgeType, final List<Edge.Property> properties, final double probability) {
        this.edgeType = edgeType;
        this.properties = properties;
        this.probability = probability;
    }

    public EdgeTypeProbability(final EdgeType edgeType, final double probability) {
        this.edgeType = edgeType;
        this.probability = probability;
    }

    public EdgeType getEdgeType() {
        return this.edgeType;
    }

    public void setEdgeType(final EdgeType edgeType) {
        this.edgeType = edgeType;
    }

    public void addProperty(final Property property) {
        if (!this.properties.contains(property)) {
            this.properties.add(property);
        }
    }

    public void removeProperty(final Property property) {
        this.properties.remove(property);
    }

    public ArrayList<Property> getProperties() {
        return new ArrayList<>(this.properties);
    }

    public double getProbability() {
        return this.probability;
    }

    public void setProbability(final double probability) {
        this.probability = probability;
    }

}
