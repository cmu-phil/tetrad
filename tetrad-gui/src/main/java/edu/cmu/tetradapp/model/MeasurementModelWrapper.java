///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.session.ParamsResettable;
import edu.cmu.tetrad.util.Parameters;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;


/**
 * Compares a target workbench with a reference workbench by counting errors of omission and commission.  (for edge
 * presence only, not orientation).
 *
 * @author josephramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 * @version $Id: $Id
 */
public final class MeasurementModelWrapper implements ParamsResettable,
        KnowledgeBoxInput {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Clusters resulting from the last run of the algorithm.
     */
    private Clusters clusters;

    /**
     * The names of the variables.
     */
    private List<String> varNames;

    /**
     * The data.
     */
    private String name;

    /**
     * The source graph.
     */
    private DataSet data;

    /**
     * The source graph.
     */
    private Graph sourceGraph;

    /**
     * The parameters object, so the GUI can remember stuff for logging.
     */
    private Parameters params;

    //=============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for MeasurementModelWrapper.</p>
     *
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public MeasurementModelWrapper(Parameters params) {
        this.setVarNames(new ArrayList<>());
        Clusters clusters = (Clusters) params.get("clusters", null);
        if (clusters == null) clusters = new Clusters();
        this.setClusters(clusters);
        this.params = params;
    }

    /**
     * <p>Constructor for MeasurementModelWrapper.</p>
     *
     * @param knowledgeInput a {@link edu.cmu.tetrad.data.KnowledgeBoxInput} object
     * @param params         a {@link edu.cmu.tetrad.util.Parameters} object
     */
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
        } else {
            this.setVarNames(knowledgeInput.getVariableNames());
            this.setClusters((Clusters) params.get("clusters", null));
            this.params = params;
        }
    }

    /**
     * <p>Constructor for MeasurementModelWrapper.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params      a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public MeasurementModelWrapper(DataWrapper dataWrapper, Parameters params) {
        this.setVarNames(dataWrapper.getVarNames());
        this.setClusters(new Clusters());

        DataModel selectedDataModel = dataWrapper.getSelectedDataModel();

        if (!(selectedDataModel instanceof DataSet)) {
            throw new IllegalArgumentException("That data box did not contain a dataset.");
        }

        this.data = (DataSet) selectedDataModel;
        this.params = params;
    }

    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

    }

    /**
     * <p>Getter for the field <code>clusters</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Clusters} object
     */
    public Clusters getClusters() {
        return this.clusters;
    }

    private void setClusters(Clusters clusters) {
        this.clusters = clusters;
    }

    /**
     * <p>Getter for the field <code>varNames</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVarNames() {
        return this.varNames;
    }

    private void setVarNames(List<String> varNames) {
        this.varNames = varNames;
    }

    /**
     * <p>Getter for the field <code>data</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getData() {
        return this.data;
    }

    /**
     * <p>Getter for the field <code>sourceGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getSourceGraph() {
        return this.sourceGraph;
    }

    /**
     * <p>getResultGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getResultGraph() {
        return this.sourceGraph;
    }

    private Parameters getParams() {
        return this.params;
    }

    /**
     * {@inheritDoc}
     */
    public void resetParams(Object params) {
        this.params = (Parameters) params;
    }

    /**
     * <p>getResettableParams.</p>
     *
     * @return a {@link java.lang.Object} object
     */
    public Object getResettableParams() {
        return this.params;
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public java.util.List<Node> getVariables() {
        List<Node> latents = new ArrayList<>();

        for (String name : getVariableNames()) {
            Node node = new ContinuousVariable(name);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
        }

        return latents;
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        List<List<Node>> partition = ClusterUtils.clustersToPartition(getClusters(),
                getData().getVariables());
        return ClusterUtils.generateLatentNames(partition.size());
    }
}


