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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.ClusterUtils;
import edu.cmu.tetrad.session.ParamsResettable;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * Compares a target workbench with a reference workbench by counting errors of
 * omission and commission.  (for edge presence only, not orientation).
 *
 * @author Joseph Ramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 */
public final class MeasurementModelWrapper implements SessionModel, ParamsResettable,
        KnowledgeBoxInput {
    static final long serialVersionUID = 23L;

    /**
     * Clusters resulting from the last run of the algorithm.
     *
     * @serial Cannot be null.
     */

    private Clusters clusters;
    private List<String> varNames;
    private String name;
    private DataSet data;
    private Graph sourceGraph;
    private Parameters params;

    //=============================CONSTRUCTORS==========================//

    public MeasurementModelWrapper(Parameters params) {
        this.setVarNames(new ArrayList<String>());
        this.setClusters((Clusters) params.get("clusters", null));
        this.params = params;
    }

    public MeasurementModelWrapper(KnowledgeBoxInput knowledgeInput, Parameters params) {
        if (knowledgeInput instanceof GraphSource) {
            GraphSource graphWrapper = (GraphSource) knowledgeInput;
            Graph mim = graphWrapper.getGraph();

            Clusters clusters = ClusterUtils.mimClusters(mim);
            List<String> nodeNames = new ArrayList<>();

            for (Node node : mim.getNodes()) {
                if (node.getNodeType() != NodeType.LATENT) {
                    nodeNames.add(node.getName());
                }
            }

            this.setVarNames(nodeNames);
            setClusters(clusters);
            this.params = params;

            getParams().set("clusters", clusters);
            getParams().set("varNames", nodeNames);
        }
        else {
            this.setVarNames(knowledgeInput.getVariableNames());
            this.setClusters((Clusters) params.get("clusters", null));
            this.params = params;
        }
    }

    public MeasurementModelWrapper(DataWrapper dataWrapper, Parameters params) {
        this.setVarNames(dataWrapper.getVarNames());
        this.setClusters((Clusters) params.get("clusters", null));

        DataModel selectedDataModel = dataWrapper.getSelectedDataModel();

        if (!(selectedDataModel instanceof  DataSet)) {
            throw new IllegalArgumentException("That data box did not contain a dataset.");
        }

        this.data = (DataSet) selectedDataModel;
        this.params = params;
    }

    public static MeasurementModelWrapper serializableInstance() {
        return new MeasurementModelWrapper(DataWrapper.serializableInstance(), new Parameters());
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
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

    }

    public Clusters getClusters() {
        return clusters;
    }

    private void setClusters(Clusters clusters) {
        this.clusters = clusters;
    }

    public List<String> getVarNames() {
        return varNames;
    }

    private void setVarNames(List<String> varNames) {
        this.varNames = varNames;
    }

    public DataSet getData() {
        return data;
    }

    public Graph getSourceGraph() {
        return sourceGraph;
    }

    public Graph getResultGraph() {
        return sourceGraph;
    }

    private Parameters getParams() {
        return params;
    }

    public void resetParams(Object params) {
        this.params = (Parameters) params;
    }

    public Object getResettableParams() {
        return this.params;
    }

    public java.util.List<Node> getVariables() {
        List<Node> latents = new ArrayList<>();

        for (String name : getVariableNames()) {
            Node node = new ContinuousVariable(name);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
        }

        return latents;
    }

    public List<String> getVariableNames() {
        List<List<Node>> partition = ClusterUtils.clustersToPartition(getClusters(),
                getData().getVariables());
        return ClusterUtils.generateLatentNames(partition.size());
    }
}


