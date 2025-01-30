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
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Mimbuild;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.search.utils.MimUtils;
import edu.cmu.tetrad.sem.ReidentifyVariables;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the MIMBuild algorithm.
 *
 * @author Ricardo Silva
 * @version $Id: $Id
 */
public class MimBuildRunner extends AbstractMimRunner implements GraphSource {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private final DataSet dataSet;

    /**
     * The full graph.
     */
    private Graph fullGraph;

    /**
     * The covariance matrix.
     */
    private ICovarianceMatrix covMatrix;

    //============================CONSTRUCTORS===========================//

    /**
     * <p>Constructor for MimBuildRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param mmWrapper   a {@link edu.cmu.tetradapp.model.MeasurementModelWrapper} object
     * @param params      a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public MimBuildRunner(DataWrapper dataWrapper,
                          MeasurementModelWrapper mmWrapper,
                          Parameters params) {
        super(dataWrapper, mmWrapper.getClusters(), params);
        this.dataSet = (DataSet) getData();
        setClusters(mmWrapper.getClusters());
        params.set("clusters", mmWrapper.getClusters());
    }

    /**
     * <p>Constructor for MimBuildRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param mmWrapper   a {@link edu.cmu.tetradapp.model.PurifyRunner} object
     * @param params      a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public MimBuildRunner(DataWrapper dataWrapper,
                          PurifyRunner mmWrapper,
                          Parameters params) {
        super(dataWrapper, mmWrapper.getClusters(), params);
        this.dataSet = (DataSet) getData();
        setClusters(mmWrapper.getClusters());
        params.set("clusters", mmWrapper.getClusters());
    }

    /**
     * <p>Constructor for MimBuildRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param params      a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public MimBuildRunner(DataWrapper dataWrapper,
                          GraphSource graphSource,
                          Parameters params) {
        super(dataWrapper, ClusterUtils.mimClusters(graphSource.getGraph()), params);
        this.dataSet = (DataSet) getData();
        params.set("clusters", getClusters());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    /**
     * <p>Getter for the field <code>covMatrix</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public ICovarianceMatrix getCovMatrix() {
        return this.covMatrix;
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be implemented in the extending class.
     *
     * @throws java.lang.Exception if any.
     */
    public void execute() throws Exception {
        DataSet data = this.dataSet;

        Mimbuild mimbuild = new Mimbuild();
        mimbuild.setPenaltyDiscount(getParams().getDouble(Params.PENALTY_DISCOUNT));
        mimbuild.setKnowledge((Knowledge) getParams().get("knowledge", new Knowledge()));

        if (getParams().getBoolean("includeThreeClusters", true)) {
            mimbuild.setMinClusterSize(3);
        } else {
            mimbuild.setMinClusterSize(4);
        }

        Clusters clusters = (Clusters) getParams().get("clusters", null);

        List<List<Node>> partition = ClusterUtils.clustersToPartition(clusters, data.getVariables());
        List<String> latentNames = new ArrayList<>();

        for (int i = 0; i < clusters.getNumClusters(); i++) {
            latentNames.add(clusters.getClusterName(i));
        }

        CovarianceMatrix cov = new CovarianceMatrix(data);

        Graph structureGraph = mimbuild.search(partition, latentNames, cov);
        LayoutUtil.defaultLayout(structureGraph);
        LayoutUtil.fruchtermanReingoldLayout(structureGraph);

        ICovarianceMatrix latentsCov = mimbuild.getLatentsCov();

        TetradLogger.getInstance().log("Latent covs = \n" + latentsCov);

        Graph fullGraph = mimbuild.getFullGraph(data.getVariables());
        LayoutUtil.defaultLayout(fullGraph);
        LayoutUtil.fruchtermanReingoldLayout(fullGraph);

        setResultGraph(fullGraph);
        setFullGraph(fullGraph);
        setClusters(MimUtils.convertToClusters(structureGraph));

        setClusters(ClusterUtils.partitionToClusters(mimbuild.getClustering()));

        setStructureGraph(structureGraph);

        getParams().set("latentVariableNames", new ArrayList<>(latentNames));

        this.covMatrix = latentsCov;

        double p = mimbuild.getpValue();

        TetradLogger.getInstance().log("\nStructure graph = " + structureGraph);
        TetradLogger.getInstance().log(getLatentClustersString(fullGraph).toString());
        TetradLogger.getInstance().log("P = " + p);

        if (getParams().getBoolean("showMaxP", false)) {
            if (p > getParams().getDouble("maxP", 1.0)) {
                getParams().set("maxP", p);
                getParams().set("maxStructureGraph", structureGraph);
                getParams().set("maxClusters", getClusters());
                getParams().set("maxFullGraph", fullGraph);
                getParams().set("maxAlpha", getParams().getDouble("alpha", 0.001));
            }

            setStructureGraph((Graph) getParams().get("maxStructureGraph", null));
            setFullGraph((Graph) getParams().get("maxFullGraph", null));
            if (getParams().get("maxClusters", null) != null) {
                setClusters((Clusters) getParams().get("maxClusters", null));
            }
            setResultGraph((Graph) getParams().get("maxFullGraph", null));

            String message1 = "\nMAX Graph = " + getParams().get("maxStructureGraph", null);
            TetradLogger.getInstance().log(message1);
            TetradLogger.getInstance().log(getLatentClustersString((Graph) getParams().get("maxFullGraph", null)).toString());
            String message = "MAX P = " + getParams().getDouble("maxP", 1.0);
            TetradLogger.getInstance().log(message);
        }
    }

    private StringBuilder getLatentClustersString(Graph graph) {
        StringBuilder builder = new StringBuilder();
        builder.append("Latent Clusters:\n");

        List<Node> latents = ReidentifyVariables.getLatents(graph);
        Collections.sort(latents);

        for (Node latent : latents) {
            List<Node> children = graph.getChildren(latent);
            latents.forEach(children::remove);
//            Collections.sort(children);

            builder.append(latent.getName()).append(": ");

            for (Node child : children) {
                builder.append(child).append(" ");
            }

            builder.append("\n");
        }

        return builder;
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return getResultGraph();
    }

    //===========================PRIVATE METHODS==========================//

    /**
     * <p>getSemPm.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemPm} object
     */
    public SemPm getSemPm() {
        Graph graph = getResultGraph();
        return new SemPm(graph);
    }

    /**
     * <p>Getter for the field <code>fullGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getFullGraph() {
        return this.fullGraph;
    }

    private void setFullGraph(Graph fullGraph) {
        this.fullGraph = fullGraph;
    }
}





