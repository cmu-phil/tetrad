///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.blocks.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Unmarshallable;
import edu.cmu.tetradapp.session.Executable;
import edu.cmu.tetradapp.session.ParamsResettable;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;

/**
 * Stores a clustering calculated by the ClusterEditor.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LatentClustersRunner implements ParamsResettable, SessionModel, Executable,
        KnowledgeBoxInput, Unmarshallable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data model.
     */
    private final DataWrapper dataWrapper;
    private final DataSet dataSet;
    private final Map<String, List<String>> trueNamedClusters;
    private final int ess;
    /**
     * The name of the model.
     */
    private String name;
    /**
     * The params object, so the GUI can remember stuff for logging.
     */
    private Parameters parameters;
    private BlockSpec blockSpec = null;
    private String blockText = "";

    //===========================CONSTRUCTORS===========================//

    /**
     * <p>Constructor for LatentClustersRunner.</p>
     *
     * @param dataWrapper a {@link DataWrapper} object
     * @param parameters  a {@link Parameters} object
     */
    public LatentClustersRunner(DataWrapper dataWrapper, Parameters parameters) {
        this.dataWrapper = dataWrapper;
        this.parameters = parameters;
        this.dataSet = (DataSet) dataWrapper.getSelectedDataModel();
        int sampleSize = dataSet.getNumRows();
        int ess = parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE);
        this.ess = ess == -1 ? sampleSize : ess;

        // If we're in simulation mode, grab the true clusters and their latent names so we can use these to
        // give good names to the estimated clusters. This helps avoid people having so much trouble figuring
        // out which output clusters correspond to which true clusters.
        trueNamedClusters = new HashMap<>();

        if (dataWrapper instanceof Simulation simulation) {
            Graph trueGraph = simulation.getGraph();

            for (Node node : trueGraph.getNodes()) {
                if (node.getNodeType() == NodeType.LATENT) {
                    List<Node> clusterNodes = trueGraph.getChildren(node);

                    clusterNodes.removeIf(_node -> _node.getNodeType() == NodeType.LATENT);

                    List<String> clusterNames = new ArrayList<>();
                    for (Node clusterNode : clusterNodes) {
                        clusterNames.add(clusterNode.getName());
                    }

                    trueNamedClusters.put(node.getName(), clusterNames);
                }
            }
        }

        System.out.println("true named clusters: " + trueNamedClusters);
    }

    //============================PUBLIC METHODS==========================//

    /**
     * <p>getDataModelList.</p>
     *
     * @return a {@link DataModelList} object
     */
    public final DataModelList getDataModelList() {
        if (this.dataWrapper == null) {
            return new DataModelList();
        }
        return this.dataWrapper.getDataModelList();
    }

    /**
     * <p>Getter for the field <code>parameters</code>.</p>
     *
     * @return a {@link Parameters} object
     */
    public final Parameters getParameters() {
        return this.parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getResettableParams() {
        return this.getParameters();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetParams(Object params) {
        this.parameters = (Parameters) params;
    }

    //===========================PRIVATE METHODS==========================//

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization to restore the
     * state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * {@inheritDoc}
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
     * <p>Getter for the field <code>dataWrapper</code>.</p>
     *
     * @return a {@link DataWrapper} object
     */
    public DataWrapper getDataWrapper() {
        return this.dataWrapper;
    }

    /**
     * Retrieves the list of variables from the associated data wrapper.
     *
     * @return a list of {@link Node} objects representing the variables.
     */
    public List<Node> getVariables() {
        return this.blockSpec.blockVariables();
    }

    /**
     * Retrieves a list of variable names from the associated data wrapper.
     *
     * @return a list of variable names as {@link String} objects.
     */
    public List<String> getVariableNames() {
        List<String> names = new ArrayList<>();

        for (Node node : this.blockSpec.blockVariables()) {
            names.add(node.getName());
        }

        return names;
    }

    /**
     * Retrieves the block specification associated with this instance.
     *
     * @return a {@link BlockSpec} object representing the block specification.
     */
    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    /**
     * Sets the block specification for the instance.
     *
     * @param blockSpec the {@link BlockSpec} object representing the block specification to set for this instance. Must
     *                  not be null.
     * @throws NullPointerException if the provided blockSpec is null
     */
    public void setBlockSpec(BlockSpec blockSpec) {
        this.blockSpec = Objects.requireNonNull(blockSpec, "spec");
    }

    /**
     * Retrieves the algorithm name associated with this instance.
     *
     * @return a string representing the name of the algorithm.
     */
    public String getAlg() {
        return parameters.getString("latentClusterRunnerAlgorithm", "TSC");
    }

    /**
     * Sets the algorithm name for the instance.
     *
     * @param alg the name of the algorithm to set. Must not be null.
     * @throws NullPointerException if the provided alg is null.
     */
    public void setAlg(String alg) {
        Objects.requireNonNull(alg, "alg");
//        this.alg = alg;
        parameters.set("latentClusterRunnerAlgorithm", alg);
    }

    /**
     * Retrieves the test value.
     *
     * @return a string representing the test value.
     */
    public String getTest() {
        return parameters.getString("latentClusterRunnerTest", "CCA");
    }

    /**
     * Sets the test value for the instance.
     *
     * @param test the test value to set. Must not be null.
     * @throws NullPointerException if the provided test is null.
     */
    public void setTest(String test) {
//        this.test = test;
        parameters.set("latentClusterRunnerTest", test);
    }

    /**
     * Retrieves the block text.
     *
     * @return a string representing the block text.
     */
    public String getBlockText() {
        return blockText;
    }

    /**
     * Sets the block text for the instance.
     *
     * @param blockText the block text to set. Must not be null.
     * @throws NullPointerException if the provided blockText is null.
     */
    public void setBlockText(String blockText) {
        Objects.requireNonNull(blockText, "blockText");
        this.blockText = blockText;
    }

    private BlockDiscoverer buildDiscoverer(String alg) {
        int _singletonPolicy = parameters.getInt(Params.TSC_SINGLETON_POLICY);
        SingleClusterPolicy policy = SingleClusterPolicy.values()[_singletonPolicy - 1];

        return switch (alg) {
            case "TSC" -> BlockDiscoverers.tsc(dataSet, parameters.getDouble(Params.ALPHA),
                    parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE),
                    parameters.getDouble(Params.REGULARIZATION_LAMBDA),
                    parameters.getInt(Params.MAX_RANK),
                    policy,
                    parameters.getInt(Params.TSC_MIN_REDUNDANCY),
                    parameters.getBoolean(Params.VERBOSE)
            );
            case "FOFC" -> BlockDiscoverers.fofc(dataSet, parameters.getDouble(Params.ALPHA), ess, policy,
                    parameters.getBoolean(Params.VERBOSE));
            case "BPC" -> BlockDiscoverers.bpc(dataSet, parameters.getDouble(Params.ALPHA), ess, policy,
                    parameters.getBoolean(Params.VERBOSE));
            case "FTFC" -> BlockDiscoverers.ftfc(dataSet, parameters.getDouble(Params.ALPHA), ess, policy,
                    parameters.getBoolean(Params.VERBOSE));
            case "GFFC" -> BlockDiscoverers.gffc(dataSet, parameters.getDouble(Params.ALPHA), ess,
                    parameters.getInt(Params.MAX_RANK), policy,
                    parameters.getBoolean(Params.VERBOSE));
            default -> throw new IllegalArgumentException("Unknown algorithm: " + alg);
        };
    }

    /**
     * Executes the algorithm and discovers the block specification.
     *
     * @throws Exception if an error occurs during execution.
     */
    @Override
    public void execute() throws Exception {

        // This can't be put into a watch thread because downstream session nodes are counting on
        // a block spec being set when propagating downstream. jdramsey 2025-8-23
        String alg = parameters.getString("latentClusterRunnerAlgorithm", "TSC");

        BlockDiscoverer discoverer = buildDiscoverer(alg);
        BlockSpec spec = discoverer.discover();
        spec = BlocksUtil.giveGoodLatentNames(spec, trueNamedClusters, BlocksUtil.NamingMode.LEARNED_SINGLE);

        setBlockText(BlockSpecTextCodec.format(spec));

        setBlockSpec(spec);
    }

    /**
     * Retrieves a map of true named clusters, if available, where each key represents the name of a cluster, and the
     * corresponding value is a list of {@link Node} objects that belong to that cluster. The method returns a deep copy
     * of the clusters to ensure the original data remains unaffected. The purpose of this is for all clusters to be
     * given names that correspond to the names of the true latents in simulation mode. If the true clusters are not
     * available, an empty map is returned.
     *
     * @return a map containing cluster names as keys and their associated lists of {@link Node} objects as values
     */
    public Map<String, List<String>> getTrueNamedClusters() {
        Map<String, List<String>> map = new HashMap<>();
        for (String s : trueNamedClusters.keySet()) {
            map.put(s, new ArrayList<>(trueNamedClusters.get(s)));
        }
        return map;
    }

    @Override
    public Graph getSourceGraph() {
        return null;
    }

    @Override
    public Graph getResultGraph() {
        return null;
    }
}

