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
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.rmi.MarshalledObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author Joseph Ramsey
 */
public class SemPmWrapper implements SessionModel, GraphSource, KnowledgeBoxInput {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * The wrapped SemPm.
     *
     * @serial Cannot be null.
     */
    private final SemPm semPm;

    //==============================CONSTRUCTORS==========================//

    private SemPmWrapper(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.semPm = new SemPm(graph);
        log(semPm);
    }

    /**
     * Creates a new SemPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public SemPmWrapper(GraphWrapper graphWrapper) {
        this(new EdgeListGraph(graphWrapper.getGraph()));
    }

    /**
     * Creates a new SemPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public SemPmWrapper(DagWrapper dagWrapper) {
        this(new EdgeListGraph(dagWrapper.getDag()));
    }

    /**
     * Creates a new SemPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public SemPmWrapper(SemGraphWrapper semGraphWrapper) {
        this(semGraphWrapper.getSemGraph());
    }

    /**
     * Creates a new SemPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public SemPmWrapper(TimeLagGraphWrapper wrapper) {
        this(wrapper.getGraph());
    }

    public SemPmWrapper(SemEstimatorWrapper wrapper) {
        try {
            SemPm oldSemPm = wrapper.getSemEstimator().getEstimatedSem()
                    .getSemPm();
            this.semPm = (SemPm) new MarshalledObject(oldSemPm).get();
        }
        catch (Exception e) {
            throw new RuntimeException("SemPm could not be deep cloned.", e);
        }
        log(semPm);
    }

    public SemPmWrapper(SemImWrapper wrapper) {
        SemPm oldSemPm = wrapper.getSemIm().getSemPm();
        this.semPm = new SemPm(oldSemPm);
        log(semPm);
    }

    public SemPmWrapper(MimBuildRunner wrapper) {
        SemPm oldSemPm = wrapper.getSemPm();
        this.semPm = new SemPm(oldSemPm);
        log(semPm);
    }

    public SemPmWrapper(BuildPureClustersRunner wrapper) {
        Graph graph = wrapper.getResultGraph();
        if (graph == null) throw new IllegalArgumentException("No graph to display.");
        SemPm oldSemPm = new SemPm(graph);
        this.semPm = new SemPm(oldSemPm);
        log(semPm);
    }

    public SemPmWrapper(AlgorithmRunner wrapper) {
        this(new EdgeListGraph(wrapper.getResultGraph()));
    }

    public SemPmWrapper(DagInPatternWrapper wrapper) {
        this(new EdgeListGraph(wrapper.getGraph()));
    }

    public SemPmWrapper(ScoredGraphsWrapper wrapper) {
        this(new EdgeListGraph(wrapper.getGraph()));
    }

    public SemPmWrapper(PValueImproverWrapper wrapper) {
        SemPm oldSemPm = wrapper.getNewSemIm().getSemPm();
        this.semPm = new SemPm(oldSemPm);
        log(semPm);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static SemPmWrapper serializableInstance() {
        return new SemPmWrapper(Dag.serializableInstance());
    }

    //============================PUBLIC METHODS=========================//

    public SemPm getSemPm() {
        return this.semPm;
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
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (semPm == null) {
            throw new NullPointerException();
        }
    }

    public Graph getGraph() {
        return semPm.getGraph();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    //======================= Private methods ====================//

    private void log(SemPm pm){
        TetradLogger.getInstance().log("info", "Linear Structural Equation Parametric Model (SEM PM)");
        TetradLogger.getInstance().log("pm", pm.toString());
    }

	public Graph getSourceGraph() {
		return getGraph();
	}

    public Graph getResultGraph() {
        return getResultGraph();
    }

    public List<String> getVariableNames() {
		return getGraph().getNodeNames();
	}

	public List<Node> getVariables() {
		return getGraph().getNodes();
	}
}








