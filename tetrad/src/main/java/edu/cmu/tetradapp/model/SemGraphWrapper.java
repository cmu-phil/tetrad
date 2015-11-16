///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Holds a tetrad dag with all of the constructors necessary for it to serve as
 * a model for the tetrad application.
 * 
 * @author Joseph Ramsey
 */
public class SemGraphWrapper implements SessionModel, GraphSource,
        KnowledgeBoxInput {
	static final long serialVersionUID = 23L;

	/**
	 * @serial Can be null.
	 */
	private String name;

	/**
	 * @serial Cannot be null.
	 */
	private SemGraph semGraph;

	// =============================CONSTRUCTORS==========================//

	public SemGraphWrapper(SemGraph graph) {
		if (graph == null) {
			throw new NullPointerException("MAG must not be null.");
		}
		this.semGraph = graph;
		this.semGraph.setShowErrorTerms(false);
		log();
	}

    // Do not, repeat not, get rid of these params. -jdramsey 7/4/2010
	public SemGraphWrapper(GraphParams params) {
		if (Preferences.userRoot().getInt("newGraphInitializationMode",
				GraphParams.MANUAL) == GraphParams.MANUAL) {
			semGraph = new SemGraph();
			semGraph.setShowErrorTerms(false);
		} else if (Preferences.userRoot().getInt("newGraphInitializationMode",
				GraphParams.MANUAL) == GraphParams.RANDOM) {
			createRandomDag();
		}
		log();
	}

	public SemGraphWrapper(SemGraphWrapper graphWrapper, GraphParams params) {
		if (Preferences.userRoot().getInt("newGraphInitializationMode",
				GraphParams.MANUAL) == GraphParams.MANUAL) {
            try {
                this.semGraph = new SemGraph(graphWrapper.getSemGraph());
                this.semGraph.setShowErrorTerms(false);
            } catch (Exception e) {
                e.printStackTrace();
                this.semGraph = new SemGraph();
                this.semGraph.setShowErrorTerms(false);
            }
        } else if (Preferences.userRoot().getInt("newGraphInitializationMode",
				GraphParams.MANUAL) == GraphParams.RANDOM) {
			createRandomDag();
		}
		log();
	}

	public SemGraphWrapper(DagWrapper graphWrapper, GraphParams params) {
		if (Preferences.userRoot().getInt("newGraphInitializationMode",
				GraphParams.MANUAL) == GraphParams.MANUAL) {
			this.semGraph = new SemGraph(graphWrapper.getDag());
			this.semGraph.setShowErrorTerms(false);
		} else if (Preferences.userRoot().getInt("newGraphInitializationMode",
				GraphParams.MANUAL) == GraphParams.RANDOM) {
			createRandomDag();
		}
		log();
	}

	public SemGraphWrapper(GraphWrapper graphWrapper, GraphParams params) {
		if (Preferences.userRoot().getInt("newGraphInitializationMode",
				GraphParams.MANUAL) == GraphParams.MANUAL) {
			this.semGraph = new SemGraph(graphWrapper.getGraph());
			this.semGraph.setShowErrorTerms(false);
		} else if (Preferences.userRoot().getInt("newGraphInitializationMode",
				GraphParams.MANUAL) == GraphParams.RANDOM) {
			createRandomDag();
		}
		log();
	}

	public SemGraphWrapper(AbstractAlgorithmRunner wrapper) {
		this(new SemGraph(wrapper.getResultGraph()));
	}

	public SemGraphWrapper(DataWrapper wrapper) {
		this(new SemGraph(new EdgeListGraph(wrapper.getVariables())));
		GraphUtils.circleLayout(semGraph, 200, 200, 150);
	}

	public SemGraphWrapper(BayesPmWrapper wrapper) {
		this(new SemGraph(wrapper.getBayesPm().getDag()));
	}

	public SemGraphWrapper(BayesImWrapper wrapper) {
		this(new SemGraph(wrapper.getBayesIm().getBayesPm().getDag()));
	}

	public SemGraphWrapper(BayesEstimatorWrapper wrapper) {
		this(new SemGraph(wrapper.getEstimatedBayesIm().getBayesPm().getDag()));
	}

	public SemGraphWrapper(CptInvariantUpdaterWrapper wrapper) {
		this(new SemGraph(wrapper.getBayesUpdater().getManipulatedGraph()));
	}

	public SemGraphWrapper(SemPmWrapper wrapper) {
		this(new SemGraph(wrapper.getSemPm().getGraph()));
	}

	public SemGraphWrapper(SemImWrapper wrapper) {
		this(new SemGraph(wrapper.getSemIm().getSemPm().getGraph()));
	}

	public SemGraphWrapper(SemEstimatorWrapper wrapper) {
		this(new SemGraph(wrapper.getSemEstimator().getEstimatedSem()
				.getSemPm().getGraph()));
	}

	public SemGraphWrapper(RegressionRunner wrapper) {
		this(new SemGraph(wrapper.getResultGraph()));
	}

	public SemGraphWrapper(BuildPureClustersRunner wrapper) {
		this(new SemGraph(wrapper.getResultGraph()));
	}

	public SemGraphWrapper(MimBuildRunner wrapper) {
		this(new SemGraph(wrapper.getResultGraph()));
	}

	/**
	 * Generates a simple exemplar of this class to test serialization.
	 * 
	 * @see edu.cmu.TestSerialization
	 * @see TetradSerializableUtils
	 */
	public static SemGraphWrapper serializableInstance() {
		return new SemGraphWrapper(SemGraph.serializableInstance());
	}

	// ================================PUBLIC METHODS=======================//

	public SemGraph getSemGraph() {
		return semGraph;
	}

	public void setSemGraph(SemGraph graph) {
		this.semGraph = graph;
		this.semGraph.setShowErrorTerms(false);
        log();
	}

	// ============================PRIVATE METHODS========================//

	private void log() {
        TetradLogger.getInstance().log("info", "Structural Equation Model (SEM) Graph");
        TetradLogger.getInstance().log("graph", "" + semGraph);
	}

	private void createRandomDag() {
		Graph graph = null;

		while (graph == null) {
			Graph dag;

			boolean uniformlySelected = Preferences.userRoot().getBoolean(
					"graphUniformlySelected", true);
			int numMeasuredNodes = Preferences.userRoot().getInt(
					"newGraphNumMeasuredNodes", 5);
			int numLatents = Preferences.userRoot().getInt(
					"newGraphNumLatents", 0);
			int newGraphNumEdges = Preferences.userRoot().getInt(
					"newGraphNumEdges", 3);
			boolean connected = Preferences.userRoot().getBoolean(
					"randomGraphConnected", false);

			if (uniformlySelected) {
				int maxDegree = Preferences.userRoot().getInt(
						"randomGraphMaxDegree", 6);
				int maxIndegree = Preferences.userRoot().getInt(
						"randomGraphMaxIndegree", 3);
				int maxOutdegree = Preferences.userRoot().getInt(
						"randomGraphMaxOutdegree", 3);

				dag = GraphUtils.randomGraph(numMeasuredNodes + numLatents,
						numLatents, newGraphNumEdges, maxDegree, maxIndegree,
						maxOutdegree, connected);
			} else {
				do {
					dag = GraphUtils.randomGraph(numMeasuredNodes + numLatents,
							numLatents, newGraphNumEdges, 30, 15, 15,
							connected);
				} while (dag.getNumEdges() < newGraphNumEdges);
			}

			boolean addCycles = Preferences.userRoot().getBoolean(
					"randomGraphAddCycles", false);

			if (addCycles) {
				int minCycleLength = Preferences.userRoot().getInt(
						"randomGraphMinCycleLength", 2);

//				graph = DataGraphUtils
//						.addCycles2(dag, minNumCycles, minCycleLength);
//
                graph = GraphUtils.cyclicGraph4(numMeasuredNodes + numLatents, newGraphNumEdges);
			} else {
				graph = new EdgeListGraph(dag);
			}

            int minNumCycles = Preferences.userRoot().getInt(
                    "randomGraphMinNumCycles", 0);
            GraphUtils.addTwoCycles(graph, minNumCycles);
        }

		semGraph = new SemGraph(graph);
		semGraph.setShowErrorTerms(false);
	}

	/**
	 * Adds semantic checks to the default deserialization method. This method
	 * must have the standard signature for a readObject method, and the body of
	 * the method must begin with "s.defaultReadObject();". Other than that, any
	 * semantic checks can be specified and do not need to stay the same from
	 * version to version. A readObject method of this form may be added to any
	 * class, even if Tetrad sessions were previously saved out using a version
	 * of the class that didn't include it. (That's what the
	 * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
	 * 
	 * @throws java.io.IOException
	 * @throws ClassNotFoundException
	 */
	private void readObject(ObjectInputStream s) throws IOException,
			ClassNotFoundException {
		s.defaultReadObject();

		if (semGraph == null) {
			throw new NullPointerException();
		}
	}

	public Graph getGraph() {
		return semGraph;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Graph getSourceGraph() {
		return getGraph();
	}

    public Graph getResultGraph() {
        return getGraph();
    }

    public List<String> getVariableNames() {
		return getGraph().getNodeNames();
	}

	public List<Node> getVariables() {
		return getGraph().getNodes();
	}
}



