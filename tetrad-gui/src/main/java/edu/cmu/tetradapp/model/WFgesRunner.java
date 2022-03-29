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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.FgesRunner.Type;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the GES algorithm.
 *
 * @author Ricardo Silva
 */

public class WFgesRunner extends AbstractAlgorithmRunner implements IFgesRunner,
        PropertyChangeListener, IGesRunner, Indexable, DoNotAddOldModel {
    static final long serialVersionUID = 23L;
    private LinkedHashMap<String, String> allParamSettings;

    private transient List<PropertyChangeListener> listeners;
    private List<ScoredGraph> topGraphs;
    private int index;
    private transient Graph externalGraph;

    //============================CONSTRUCTORS============================//

    public WFgesRunner(DataWrapper[] dataWrappers, Parameters params) {
        super(new MergeDatasetsWrapper(dataWrappers, params), params, null);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //============================PUBLIC METHODS==========================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        System.out.println("A");

        DataSet dataSet = (DataSet) this.getDataModel();

        Parameters params = this.getParams();

        double penaltyDiscount = params.getDouble("penaltyDiscount", 4);

        WFges fges = new WFges(dataSet);
        fges.setPenaltyDiscount(penaltyDiscount);
        Graph graph = fges.search();

        if (this.getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, this.getSourceGraph());
        } else if (((IKnowledge) this.getParams().get("knowledge", new Knowledge2())).isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, (IKnowledge) this.getParams().get("knowledge", new Knowledge2()));
        } else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        this.setResultGraph(graph);

        topGraphs = new ArrayList<>();
        topGraphs.add(new ScoredGraph(this.getResultGraph(), Double.NaN));
        this.setIndex(topGraphs.size() - 1);
    }

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public Type getType() {
        if (true) {
            return Type.CONTINUOUS;
        }

        Object model = this.getDataModel();

        if (model == null && this.getSourceGraph() != null) {
            model = this.getSourceGraph();
        }

        if (model == null) {
            throw new RuntimeException("Data source is unspecified. You may need to double click all your data boxes, \n" +
                    "then click Save, and then right click on them and select Propagate Downstream. \n" +
                    "The issue is that we use a seed to simulate from IM's, so your data is not saved to \n" +
                    "file when you save the session. It can, however, be recreated from the saved seed.");
        }

        Type type;

        if (model instanceof Graph) {
            type = Type.GRAPH;
        } else if (model instanceof DataSet) {
            DataSet dataSet = (DataSet) model;

            if (dataSet.isContinuous()) {
                type = Type.CONTINUOUS;
            } else if (dataSet.isDiscrete()) {
                type = Type.DISCRETE;
            } else {
                type = Type.MIXED;
//                throw new IllegalStateException("Data set must either be continuous or discrete.");
            }
        } else if (model instanceof ICovarianceMatrix) {
            type = Type.CONTINUOUS;
        } else if (model instanceof DataModelList) {
            DataModelList list = (DataModelList) model;

            if (this.allContinuous(list)) {
                type = Type.CONTINUOUS;
            } else if (this.allDiscrete(list)) {
                type = Type.DISCRETE;
            } else {
                type = Type.MIXED;
//                throw new IllegalArgumentException("Data must be either all discrete or all continuous.");
            }
        } else {
            throw new IllegalArgumentException("Unrecognized data type.");
        }

        return type;
    }

    private boolean allContinuous(List<DataModel> dataModels) {
        for (DataModel dataModel : dataModels) {
            if (dataModel instanceof DataSet) {
                if (!((DataSet) dataModel).isContinuous() || dataModel instanceof ICovarianceMatrix) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean allDiscrete(List<DataModel> dataModels) {
        for (DataModel dataModel : dataModels) {
            if (dataModel instanceof DataSet) {
                if (!((DataSet) dataModel).isDiscrete()) {
                    return false;
                }
            }
        }

        return true;
    }

    public void setIndex(int index) {
        if (index < -1) {
            throw new IllegalArgumentException("Must be in >= -1: " + index);
        }

        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public Graph getGraph() {
        if (this.getIndex() >= 0) {
            return this.getTopGraphs().get(this.getIndex()).getGraph();
        } else {
            return this.getResultGraph();
        }
    }


    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new ArrayList<>();
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new ArrayList<>();
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setKnowledge((IKnowledge) this.getParams().get("knowledge", new Knowledge2()));
        return rules;
    }

    @Override
    public Map<String, String> getParamSettings() {
        super.getParamSettings();
        Parameters params = this.getParams();
        paramSettings.put("Penalty Discount", new DecimalFormat("0.0").format(params.getDouble("penaltyDiscount", 4)));
        return paramSettings;
    }

    @Override
    public String getAlgorithmName() {
        return "FGES";
    }

    public void propertyChange(PropertyChangeEvent evt) {
        this.firePropertyChange(evt);
    }

    private void firePropertyChange(PropertyChangeEvent evt) {
        for (PropertyChangeListener l : this.getListeners()) {
            l.propertyChange(evt);
        }
    }

    private List<PropertyChangeListener> getListeners() {
        if (listeners == null) {
            listeners = new ArrayList<>();
        }
        return listeners;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        if (!this.getListeners().contains(l)) this.getListeners().add(l);
    }

    public List<ScoredGraph> getTopGraphs() {
        return topGraphs;
    }
}





