package edu.cmu.tetrad.graph;

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

	private double probability;

	public EdgeTypeProbability() {

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

	public double getProbability() {
		return probability;
	}

	public void setProbability(double probability) {
		this.probability = probability;
	}

}
