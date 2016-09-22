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
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.ClusterUtils;
import edu.cmu.tetrad.search.MimUtils;
import edu.cmu.tetrad.search.Mimbuild2;
import edu.cmu.tetrad.sem.ReidentifyVariables;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the MIMBuild
 * algorithm.
 *
 * @author Ricardo Silva
 */
public class MimBuildRunner extends AbstractMimRunner implements GraphSource {
    static final long serialVersionUID = 23L;
    private final DataSet dataSet;
    private Graph fullGraph;
    private ICovarianceMatrix covMatrix;

    //============================CONSTRUCTORS===========================//

    public MimBuildRunner(DataWrapper dataWrapper,
                          MeasurementModelWrapper mmWrapper,
                          Parameters params) {
        super(dataWrapper, mmWrapper.getClusters(), params);
        this.dataSet = (DataSet) getData();
        setClusters(mmWrapper.getClusters());
        params.set("clusters", mmWrapper.getClusters());
    }

    public MimBuildRunner(DataWrapper dataWrapper,
                          PurifyRunner mmWrapper,
                          Parameters params) {
        super(dataWrapper, mmWrapper.getClusters(), params);
        this.dataSet = (DataSet) getData();
        setClusters(mmWrapper.getClusters());
        params.set("clusters", mmWrapper.getClusters());
    }

    public MimBuildRunner(DataWrapper dataWrapper,
                          GraphSource graphSource,
                          Parameters params) {
        super(dataWrapper, ClusterUtils.mimClusters(graphSource.getGraph()), params);
        this.dataSet = (DataSet) getData();
        params.set("clusters", getClusters());
    }

//    public MimBuildRunner(DataWrapper dataWrapper,
//                          FofcRunner mmWrapper,
//                          Parameters params) {
//        super(dataWrapper, mmWrapper.getClusters(), params);
//        this.dataSet = (DataSet) getData();
//        setClusters(mmWrapper.getClusters());
//        params.set("clusters", mmWrapper.getClusters());
//    }

//    public MimBuildRunner(DataWrapper dataWrapper,
//                          MeasurementModelWrapper mmWrapper,
//                          Parameters params,
//                          KnowledgeBoxModel knowledgeBoxModel) {
//        super(dataWrapper, mmWrapper.getClusters(), params);
//        this.dataSet = (DataSet) getData();
//        setClusters(mmWrapper.getClusters());
//        params.set("clusters", mmWrapper.getClusters());
//        params.set("knowledge", knowledgeBoxModel.getKnowledge());
//    }

//    public MimBuildRunner(MeasurementModelWrapper mmWrapper,
//                          DataWrapper dataWrapper,
//                          Parameters params) {
//        super(dataWrapper, mmWrapper.getClusters(), params);
//        this.dataSet = (DataSet) dataWrapper.getDataModelList().get(0);
//        setClusters(mmWrapper.getClusters());
//        params.set("clusters", mmWrapper.getClusters());
//    }

//    public MimBuildRunner(MeasurementModelWrapper mmWrapper,
//                          Parameters params,
//                          KnowledgeBoxModel knowledgeBoxModel) {
//        super(mmWrapper, mmWrapper.getClusters(), params);
//        this.dataSet = (DataSet) getData();
//        setClusters(mmWrapper.getClusters());
//        getParams().set("clusters", mmWrapper.getClusters());
//        params.set("knowledge", knowledgeBoxModel.getKnowledge());
//    }

//    public MimBuildRunner(DataWrapper dataWrapper, Parameters params) {
//        super(dataWrapper, params.getClusters(), params);
//        this.dataSet = (DataSet) getContinuousData();
//        setClusters(params.getClusters());
//    }

//    public MimBuildRunner(DataWrapper dataWrapper, Parameters params, KnowledgeBoxModel knowledgeBox) {
//        super(dataWrapper, (Clusters) params.get("clusters", null), params);
//        this.dataSet = (DataSet) getData();
//        setClusters((Clusters) params.get("clusters", null));
//        params.set("knowledge", knowledgeBox.getKnowledge());
//    }

//    public MimBuildRunner(BuildPureClustersRunner pureClustersRunner,
//                          Parameters params) {
//        super(pureClustersRunner, params);
//
//        if (getData() instanceof CovarianceMatrix) {
//            CovarianceMatrix cov = (CovarianceMatrix) getData();
//            this.dataSet = DataUtils.choleskySimulation(cov);
//        }
//        else {
//            this.dataSet = (DataSet) getData();
//        }
//
//        setClusters((Clusters) params.get("clusters", null));
//    }
//
//    public MimBuildRunner(BuildPureClustersRunner bpcRunner, KnowledgeBoxModel knowledgeBox, Parameters params) {
//        super(bpcRunner, params);
//        this.dataSet = (DataSet) getData();
//        setClusters((Clusters) params.get("clusters", null));
//        params.set("knowledge", knowledgeBox.getKnowledge());
//    }
//
//    public MimBuildRunner(PurifyRunner runner, Parameters params) {
//        super(runner, params);
//        this.dataSet = (DataSet) getData();
//        setClusters((Clusters) params.get("clusters", null));
//    }
//
//    public MimBuildRunner(PurifyRunner runner, KnowledgeBoxModel knowledgeBox, Parameters params) {
//        super(runner, params);
//        this.dataSet = (DataSet) getData();
//        setClusters((Clusters) params.get("clusters", null));
//        params.set("knowledge", knowledgeBox.getKnowledge());
//    }
//
//    public MimBuildRunner(PurifyRunner runner, DataWrapper dataWrapper, Parameters params) {
//        super(runner, params);
//        this.dataSet = (DataSet) dataWrapper.getSelectedDataModel();
//        setClusters((Clusters) params.get("clusters", null));
//    }
//
//    public MimBuildRunner(PurifyRunner runner, DataWrapper dataWrapper, KnowledgeBoxModel knowledgeBox, Parameters params) {
//        super(runner, params);
//        this.dataSet = (DataSet) dataWrapper.getSelectedDataModel();
//        setClusters((Clusters) params.get("clusters", null));
//        params.set("knowledge", knowledgeBox.getKnowledge());
//    }
//
//
//    public MimBuildRunner(MimBuildRunner runner, Parameters params) {
//        super(runner, params);
//        this.dataSet = (DataSet) getData();
//        setClusters((Clusters) params.get("clusters", null));
//    }
//
//    public MimBuildRunner(MimBuildRunner runner, KnowledgeBoxModel knowledgeBox, Parameters params) {
//        super(runner, params);
//        this.dataSet = (DataSet) getData();
//        setClusters((Clusters) params.get("clusters", null));
//        params.set("knowledge", knowledgeBox.getKnowledge());
//    }

    public ICovarianceMatrix getCovMatrix() {
        return this.covMatrix;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() throws Exception {
        DataSet data = this.dataSet;

        Mimbuild2 mimbuild = new Mimbuild2();
        mimbuild.setAlpha(getParams().getDouble("alpha", 0.001));
        mimbuild.setKnowledge((IKnowledge) getParams().get("knowledge", new Knowledge2()));

        if (getParams().getBoolean("includeThreeClusters", true)) {
            mimbuild.setMinClusterSize(3);
        }
        else {
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
        GraphUtils.circleLayout(structureGraph, 200, 200, 150);
        GraphUtils.fruchtermanReingoldLayout(structureGraph);

        ICovarianceMatrix latentsCov = mimbuild.getLatentsCov();

        TetradLogger.getInstance().log("details", "Latent covs = \n" + latentsCov);

        Graph fullGraph = mimbuild.getFullGraph();
        GraphUtils.circleLayout(fullGraph, 200, 200, 150);
        GraphUtils.fruchtermanReingoldLayout(fullGraph);

        setResultGraph(fullGraph);
        setFullGraph(fullGraph);
        setClusters(MimUtils.convertToClusters(structureGraph));

        setClusters(ClusterUtils.partitionToClusters(mimbuild.getClustering()));

        setStructureGraph(structureGraph);

        getParams().set("latentVariableNames", new ArrayList<>(latentNames));

        this.covMatrix = latentsCov;

        double p = mimbuild.getpValue();

        TetradLogger.getInstance().log("details", "\nStructure graph = " + structureGraph);
        TetradLogger.getInstance().log("details", getLatentClustersString(fullGraph).toString());
        TetradLogger.getInstance().log("details", "P = " + p);

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

            TetradLogger.getInstance().log("maxmodel", "\nMAX Graph = " + getParams().get("maxStructureGraph", null));
            TetradLogger.getInstance().log("maxmodel", getLatentClustersString((Graph) getParams().get("maxFullGraph", null)).toString());
            TetradLogger.getInstance().log("maxmodel", "MAX P = " + getParams().getDouble("maxP", 1.0));
        }
    }

    private StringBuilder getLatentClustersString(Graph graph) {
        StringBuilder builder = new StringBuilder();
        builder.append("Latent Clusters:\n");

        List<Node> latents = ReidentifyVariables.getLatents(graph);
        Collections.sort(latents);

        for (Node latent : latents) {
            List<Node> children = graph.getChildren(latent);
            children.removeAll(latents);
            Collections.sort(children);

            builder.append(latent.getName() + ": ");

            for (int j = 0; j < children.size(); j++) {
                builder.append(children.get(j) + " ");
            }

            builder.append("\n");
        }

        return builder;
    }

    private void setFullGraph(Graph fullGraph) {
        this.fullGraph = fullGraph;
    }

    //===========================PRIVATE METHODS==========================//

    public Graph getGraph() {
        return getResultGraph();
    }

    public SemPm getSemPm() {
        Graph graph = getResultGraph();
        return new SemPm(graph);
    }

    public Graph getFullGraph() {
        return fullGraph;
    }
}





