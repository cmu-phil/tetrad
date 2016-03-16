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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.ImpliedOrientation;
import edu.cmu.tetrad.search.Lingam;
import edu.cmu.tetrad.search.MeekRules;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the GES algorithm.
 *
 * @author Ricardo Silva
 */

public class LingamRunner extends AbstractAlgorithmRunner implements GraphSource,
        PropertyChangeListener {
    static final long serialVersionUID = 23L;
    private transient List<PropertyChangeListener> listeners;

    //============================CONSTRUCTORS============================//

    public LingamRunner(DataWrapper dataWrapper) {
        super(dataWrapper, new LingamParams(), null);
    }
    
    public LingamRunner(DataWrapper dataWrapper, LingamParams params) {
        super(dataWrapper, params, null);
    }

    public LingamRunner(DataWrapper dataWrapper, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, new LingamParams(), knowledgeBoxModel);
    }

    public LingamRunner(DataWrapper dataWrapper, KnowledgeBoxModel knowledgeBoxModel, LingamParams params) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public LingamRunner(GraphSource graphWrapper, LingamParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }
    
    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public LingamRunner(GraphSource graphWrapper, LingamParams params) {
        super(graphWrapper.getGraph(), params, null);
    }
    
    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static LingamRunner serializableInstance() {
        return new LingamRunner(DataWrapper.serializableInstance(), KnowledgeBoxModel.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */

    public void execute() {
        DataModel source = getDataModel();

        if (!(source instanceof DataSet)) {
            throw new IllegalArgumentException("Expecting a rectangular data set.");
        }

        DataSet data = (DataSet) source;

        if (!data.isContinuous()) {
            throw new IllegalArgumentException("Expecting a continuous data set.");
        }

//        Lingam_old lingam = new Lingam_old();
//        lingam.setParameter1(getParams().getIndTestParams().getParameter1());
//        lingam.setPruningDone(true);
//
//        double lingamPruningAlpha = Preferences.userRoot().getDouble("lingamPruningAlpha", 0.05);
//
//        lingam.setParameter1(lingamPruningAlpha);
//        Graph graph = lingam.lingam(data).getGraph();

        Lingam lingam = new Lingam();
        LingamParams params = (LingamParams) getParams();
        lingam.setPruneFactor(params.getPruneFactor());
        Graph graph = lingam.search(data);

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        }
        else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        setResultGraph(graph);
        GraphUtils.arrangeBySourceGraph(getResultGraph(), getSourceGraph());
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * @return the names of the triple classifications. Coordinates with getTriplesList.
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<String>();
        names.add("Colliders");
        names.add("Noncolliders");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>
     * for the given node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<List<Triple>>();
        Graph graph = getGraph();
        triplesList.add(GraphUtils.getCollidersFromGraph(node, graph));
        triplesList.add(GraphUtils.getNoncollidersFromGraph(node, graph));
        return triplesList;
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(getParams().getKnowledge());
        return rules;
    }

    @Override
    public String getAlgorithmName() {
        return "LiNGAM";
    }

    public void propertyChange(PropertyChangeEvent evt) {
        firePropertyChange(evt);
    }

    private void firePropertyChange(PropertyChangeEvent evt) {
        for (PropertyChangeListener l : getListeners()) {
            l.propertyChange(evt);
        }
    }

    private List<PropertyChangeListener> getListeners() {
        if (listeners == null) {
            listeners = new ArrayList<PropertyChangeListener>();
        }
        return listeners;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        if (!getListeners().contains(l)) getListeners().add(l);
    }
}


