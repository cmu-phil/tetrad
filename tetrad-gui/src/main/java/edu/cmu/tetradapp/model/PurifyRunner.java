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
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the Purify
 * algorithm.
 *
 * @author Ricardo Silva
 */
public class PurifyRunner extends AbstractMimRunner implements GraphSource, KnowledgeBoxInput {
    static final long serialVersionUID = 23L;
//    private DeltaSextadTest sextadTest;

    //============================CONSTRUCTORS============================//

    public PurifyRunner(DataWrapper dataWrapper,
                          MeasurementModelWrapper mmWrapper,
                          Parameters params) {
        super(dataWrapper, mmWrapper.getClusters(), params);
        setClusters(mmWrapper.getClusters());
        params.set("clusters", mmWrapper.getClusters());
    }

//    public PurifyRunner(DataWrapper dataWrapper, Parameters params) {
//        super(dataWrapper, (Clusters) params.get("clusters", null), params);
//    }
//
//    public PurifyRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params) {
//        super(dataWrapper, (Clusters) params.get("clusters", null), params);
//
//        Graph mim = graphWrapper.getGraph();
//        DataModel selectedDataModel = dataWrapper.getSelectedDataModel();
//
//        List<List<Node>> partition = ClusterUtils.mimClustering(mim, selectedDataModel.getVariable());
//        Clusters clusters = ClusterUtils.partitionToClusters(partition);
//        setClusters(clusters);
//        params.set("clusters", clusters);
//    }
//
//    public PurifyRunner(BuildPureClustersRunner bpc, Parameters params) {
//        super(bpc, params);
//    }
    
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
    public void execute() {
        Object source = getData();
//        Purify purify;
//
//        if (source instanceof CovarianceMatrix) {
//            CovarianceMatrix covMatrix = (CovarianceMatrix) source;
//            CorrelationMatrix corrMatrix = new CorrelationMatrix(covMatrix);
//            purify = new Purify(corrMatrix, getParameters().getAlternativePenalty(),
//                    getParameters().getTetradTestType(), getParameters().getClusters());
//        }
//        else if (source instanceof DataSet) {
//            purify = new Purify((DataSet) source,
//                    getParameters().getAlternativePenalty(), getParameters().getTetradTestType(),
//                    getParameters().getClusters());
//        }
//        else {
//            throw new RuntimeException(
//                    "Data source for Purify of invalid type!");
//        }

        TetradTest test;


        System.out.println("Clusters " + getParams().get("clusters", null));

        if (source instanceof ICovarianceMatrix) {
            ICovarianceMatrix covMatrix = (ICovarianceMatrix) source;
            CorrelationMatrix corrMatrix = new CorrelationMatrix(covMatrix);
            double alpha = getParams().getDouble("alpha", 0.001);
            TestType sigTestType = (TestType) getParams().get("tetradTestType", TestType.TETRAD_WISHART);
            test = new ContinuousTetradTest(covMatrix, sigTestType, alpha);
//            sextadTest = new DeltaSextadTest(covMatrix);
        }
        else if (source instanceof DataSet) {
            DataSet data = (DataSet) source;
            double alpha = getParams().getDouble("alpha", 0.001);
            TestType sigTestType = (TestType) getParams().get("tetradTestType", TestType.TETRAD_WISHART);
            test = new ContinuousTetradTest(data, sigTestType, alpha);
//            sextadTest = new DeltaSextadTest(data);
        }
        else {
            throw new RuntimeException(
                    "Data source for Purify of invalid type!");
        }

        List<List<Node>> inputPartition = ClusterUtils.clustersToPartition((Clusters) getParams().get("clusters", null), test.getVariables());

        IPurify purify = new PurifyTetradBased2(test);
//        IPurify purify = new PurifySextadBased(sextadTest, test.getSignificance());
//        IPurify purify = new PurifyTetradBasedH(test, 15);


        List<List<Node>> partition = purify.purify(inputPartition);

        Clusters outputClusters = ClusterUtils.partitionToClusters(partition);

        List<int[]> partitionAsInts = ClusterUtils.convertListToInt(partition, test.getVariables());
        setResultGraph(ClusterUtils.convertSearchGraph(partitionAsInts, test.getVarNames()));
        GraphUtils.fruchtermanReingoldLayout(getResultGraph());

        setClusters(outputClusters);
    }

    public Clusters getClusters() {
        return super.getClusters();
    }

    protected void setClusters(Clusters clusters) {
        super.setClusters(clusters);
    }

    public Graph getGraph() {
        return getResultGraph();
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




