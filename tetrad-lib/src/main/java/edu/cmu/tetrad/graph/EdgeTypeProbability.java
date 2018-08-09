package edu.cmu.tetrad.graph;

import java.util.ArrayList;
import java.util.List;

import edu.cmu.tetrad.graph.Edge.Property;
import edu.cmu.tetrad.util.TetradSerializable;

/**
 * 
 * Apr 13, 2017 3:56:46 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
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

	public EdgeTypeProbability(EdgeType edgeType, List<Edge.Property> properties, double probability) {
		this.edgeType = edgeType;
		this.properties = properties;
		this.probability = probability;
	}

	public EdgeTypeProbability(EdgeType edgeType, double probability) {
		this.edgeType = edgeType;
		this.probability = probability;
	}

	public EdgeType getEdgeType() {
		return edgeType;
	}

	public void setEdgeType(EdgeType edgeType) {
		this.edgeType = edgeType;
	}

	public void addProperty(Property property) {
		if (!properties.contains(property)) {
			this.properties.add(property);
		}
	}

	public void removeProperty(Property property) {
		this.properties.remove(property);
	}

	public ArrayList<Property> getProperties() {
		return new ArrayList<>(this.properties);
	}

	public double getProbability() {
		return probability;
	}

	public void setProbability(double probability) {
		this.probability = probability;
	}

}
