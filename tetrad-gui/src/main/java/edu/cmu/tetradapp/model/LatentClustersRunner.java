/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.*;
import edu.cmu.tetrad.search.ntad_test.BollenTing;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.search.ntad_test.NtadTest;
import edu.cmu.tetrad.search.ntad_test.Wishart;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Unmarshallable;
import edu.cmu.tetradapp.session.ParamsResettable;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.List;
import java.util.Objects;

/**
 * Stores a clustering calculated by the ClusterEditor.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LatentClustersRunner implements ParamsResettable, SessionModel,
        Unmarshallable/*IndTestProducer,*/ {

    @Serial
    private static final long serialVersionUID = 23L;
    private static final String TEST_CCA = "CCA";
    private static final String TEST_BT = "Bollen-Ting";
    private static final String TEST_WIS = "Wishart";
    /**
     * The data model.
     */
    private final DataWrapper dataWrapper;
    private final DataSet dataSet;
    /**
     * The name of the model.
     */
    private String name;
    /**
     * The params object, so the GUI can remember stuff for logging.
     */
    private Parameters parameters;
    private BlockSpec blockSpec = null;
    private String alg = "FOFC";
    private String test = "CCA";
    private String blockText = "";

    //===========================CONSTRUCTORS===========================//

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param dataWrapper a {@link DataWrapper} object
     * @param parameters  a {@link Parameters} object
     */
    public LatentClustersRunner(DataWrapper dataWrapper, Parameters parameters) {
        this.dataWrapper = dataWrapper;
        this.parameters = parameters;
        this.dataSet = (DataSet) dataWrapper.getSelectedDataModel();

        BlockDiscoverer discoverer = buildDiscoverer(alg, test);
        BlockSpec spec = discoverer.discover();

        int _singletonPolicy = parameters.getInt(Params.TSC_SINGLETON_POLICY);
        SingleClusterPolicy policy = SingleClusterPolicy.values()[_singletonPolicy - 1];

        if (policy == SingleClusterPolicy.NOISE_VAR) {
            spec = BlocksUtil.renameLastVarAsNoise(spec);
        }

        this.blockText = BlockSpecTextCodec.format(spec);

        setBlockSpec(spec);
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
    private void transferVarNamesToParams(List<String> names) {
        getParameters().set("varNames", names);
    }

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
     * {@inheritDoc}
     */
    public List<Node> getVariables() {
        return this.dataWrapper.getVariables();
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getVariableNames() {
        return this.dataWrapper.getVariableNames();
    }

    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    public void setBlockSpec(BlockSpec blockSpec) {
        this.blockSpec = Objects.requireNonNull(blockSpec, "spec");
    }

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        Objects.requireNonNull(alg, "alg");
        this.alg = alg;
    }

    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public String getBlockText() {
        return blockText;
    }

    public void setBlockText(String blockText) {
        Objects.requireNonNull(blockText, "blockText");
        this.blockText = blockText;
    }

    private BlockDiscoverer buildDiscoverer(String alg, String testName) {
        NtadTest test = null;

        if (testName != null) {
            switch (testName) {
                case TEST_BT -> test = new BollenTing(dataSet.getDoubleData().getSimpleMatrix(), false);
                case TEST_WIS -> test = new Wishart(dataSet.getDoubleData().getSimpleMatrix(), false);
                // case TEST_ARK -> test = new Ark(...); // still commented out
                case TEST_CCA -> test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
                default -> test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
            }
        }

        int _singletonPolicy = parameters.getInt(Params.TSC_SINGLETON_POLICY);
        SingleClusterPolicy policy = SingleClusterPolicy.values()[_singletonPolicy - 1];

        return switch (alg) {
            case "TSC Test" -> {
                yield BlockDiscoverers.tscTest(dataSet, parameters.getDouble(Params.ALPHA), policy,
                        parameters.getInt(Params.EXPECTED_SAMPLE_SIZE));
            }
            case "TSC Score" -> {
                yield BlockDiscoverers.tscScore(dataSet, parameters.getDouble(Params.ALPHA),
                        parameters.getDouble(Params.EBIC_GAMMA), parameters.getDouble(Params.REGULARIZATION_LAMBDA),
                        parameters.getDouble(Params.PENALTY_DISCOUNT),
                        parameters.getInt(Params.EXPECTED_SAMPLE_SIZE), policy);
            }
            case "FOFC" -> {
                if (test == null) {
                    test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false); // sensible default
                }
                yield BlockDiscoverers.fofc(dataSet, test, parameters.getDouble(Params.ALPHA), policy);
            }
            case "BPC" -> {
                if (test == null) {
                    test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
                }
                yield BlockDiscoverers.bpc(dataSet, test, parameters.getDouble(Params.ALPHA), policy);
            }
            case "FTFC" -> {
                if (test == null || TEST_WIS.equals(testName)) {
                    // enforce: FTFC cannot use Wishart
                    test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
                }
                yield BlockDiscoverers.ftfc(dataSet, test, parameters.getDouble(Params.ALPHA), policy);
            }
            default -> throw new IllegalArgumentException("Unknown algorithm: " + alg);
        };
    }
}
