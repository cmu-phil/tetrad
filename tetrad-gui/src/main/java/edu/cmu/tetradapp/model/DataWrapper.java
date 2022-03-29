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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.session.SimulationParamsSource;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Wraps a DataModel as a model class for a Session, providing constructors for
 * the parents of Tetrad that are specified by Tetrad.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class DataWrapper implements SessionModel, KnowledgeEditable, KnowledgeBoxInput,
        DoNotAddOldModel, SimulationParamsSource, MultipleDataSource {

    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    private DataModelList dataModelList;

    /**
     * Maps columns to discretization specs so that user's work is not forgotten
     * from one editing of the same data set to the next.
     *
     * @serial Cannot be null.
     */
    private final Map discretizationSpecs = new HashMap();

    /**
     * Stores a reference to the source workbench, if there is one.
     *
     * @serial Can be null.
     */
    private Graph sourceGraph;

    /**
//     * A list of known variables. Variables can be looked up in this list and
//     * reused where appropriate.
//     *
//     * @serial Can be null.
//     */
//    private List<Node> knownVariables;

    /**
     * The parameters being edited.
     */
    private Parameters parameters = null;
    private Map<String, String> allParamSettings;

    //==============================CONSTRUCTORS===========================//
    protected DataWrapper() {
        setDataModel(new BoxDataSet(new VerticalDoubleDataBox(0, 0), new LinkedList<Node>()));
        this.parameters = new Parameters();
    }

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     */
    public DataWrapper(final Parameters parameters) {
        setDataModel(new BoxDataSet(new VerticalDoubleDataBox(0, 0), new LinkedList<Node>()));
        this.parameters = parameters;
    }

    public DataWrapper(final Simulation wrapper, final Parameters parameters) {
        this.name = wrapper.getName();
        this.dataModelList = new DataModelList();

        for (final DataModel model : wrapper.getDataModels()) {
            if (model instanceof DataSet) {
                this.dataModelList.add(((DataSet) model).copy());
            } else if (model instanceof CorrelationMatrix) {
                this.dataModelList.add(new CorrelationMatrix((CorrelationMatrix) model));
            } else if (model instanceof CovarianceMatrix) {
                this.dataModelList.add(new CovarianceMatrix((CovarianceMatrix) model));
            } else {
                throw new IllegalArgumentException();
            }
        }

        this.dataModelList = wrapper.getDataModelList();
        this.parameters = parameters;
    }

    /**
     * Copy constructor.
     */
    public DataWrapper(final DataWrapper wrapper, final Parameters parameters) {
        this.name = wrapper.name;
        final DataModelList dataModelList = new DataModelList();
        int selected = -1;

        for (int i = 0; i < wrapper.getDataModelList().size(); i++) {
            final DataModel model = wrapper.getDataModelList().get(i);

            if (model instanceof DataSet) {
                dataModelList.add(((DataSet) model).copy());
            } else if (model instanceof CorrelationMatrix) {
                dataModelList.add(new CorrelationMatrix((CorrelationMatrix) model));
            } else if (model instanceof CovarianceMatrix) {
                dataModelList.add(new CovarianceMatrix((CovarianceMatrix) model));
            } else {
                throw new IllegalArgumentException();
            }

            if (model.equals(wrapper.getDataModelList().getSelectedModel())) {
                selected = i;
            }
        }

        if (selected > -1) {
            dataModelList.setSelectedModel(dataModelList.get(selected));
        }

        if (wrapper.sourceGraph != null) {
            this.sourceGraph = new EdgeListGraph(wrapper.sourceGraph);
        }

//        if (wrapper.knownVariables != null) {
//            this.knownVariables = new ArrayList<>(wrapper.knownVariables);
//        }

        this.dataModelList = dataModelList;

        LogDataUtils.logDataModelList("Standalone data set.", getDataModelList());
    }

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     */
    public DataWrapper(final DataSet dataSet) {
        setDataModel(dataSet);
    }

    public DataWrapper(final Graph graph, final Parameters parameters) {
        if (graph == null) {
            throw new NullPointerException();
        }

        final List<Node> nodes = graph.getNodes();
        final List<Node> variables = new LinkedList<>();

        for (final Object node1 : nodes) {
            final Node node = (Node) node1;
            final String name = node.getName();
            final NodeType nodetype = node.getNodeType();
            if (nodetype == NodeType.MEASURED) {
                final ContinuousVariable var = new ContinuousVariable(name);
                variables.add(var);
            }
        }

        final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(0, variables.size()), variables);
        final DataModelList dataModelList = new DataModelList();
        dataModelList.add(dataSet);
        this.dataModelList = dataModelList;
    }

    public DataWrapper(final DagWrapper dagWrapper, final Parameters parameters) {
        this(dagWrapper.getDag(), parameters);
    }

    public DataWrapper(final SemGraphWrapper wrapper, final Parameters parameters) {
        this(wrapper.getGraph(), parameters);
    }

    public DataWrapper(final GraphWrapper wrapper, final Parameters parameters) {
        this(wrapper.getGraph(), parameters);
    }

    public DataWrapper(final RegressionRunner regression, final DataWrapper wrapper, final Parameters parameters) {
        this(regression.getResult(), (DataSet) wrapper.getDataModelList().getSelectedModel(), parameters);
    }

    public DataWrapper(final RegressionRunner regression, final Simulation wrapper, final Parameters parameters) {
        this(regression.getResult(), (DataSet) wrapper.getDataModelList().getSelectedModel(), parameters);
    }

    // Computes regression predictions.
    public DataWrapper(final RegressionResult result, final DataSet data, final Parameters parameters) {
//        if (!data.isContinuous()) {
//            throw new IllegalArgumentException("Must provide a continuous data set.");
//        }

        final DataSet data2 = data.copy();
        final String predictedVariable = nextVariableName("Pred", data);
        data2.addVariable(new ContinuousVariable(predictedVariable));

        final String[] regressorNames = result.getRegressorNames();

        for (int i = 0; i < data.getNumRows(); i++) {
            final double[] x = new double[regressorNames.length];

            for (int j = 0; j < regressorNames.length; j++) {
                final Node variable = data.getVariable(regressorNames[j]);

                if (variable == null) {
                    throw new NullPointerException("Variable " + variable + " doesn't "
                            + "exist in the input data.");
                }

                if (!(variable instanceof ContinuousVariable)) {
                    throw new IllegalArgumentException("Expecting a continuous variable: " + variable);
                }

                x[j] = data.getDouble(i, data.getColumn(variable));
            }

            final double yHat = result.getPredictedValue(x);
            data2.setDouble(i, data2.getColumn(data2.getVariable(predictedVariable)), yHat);
        }

        final DataModelList dataModelList = new DataModelList();
        dataModelList.add(data2);
        this.dataModelList = dataModelList;
    }

    public DataWrapper(final MimBuildRunner mimBuild, final Parameters parameters) {
        final ICovarianceMatrix cov = mimBuild.getCovMatrix();

        final DataModelList dataModelList = new DataModelList();
        dataModelList.add(cov);
        this.dataModelList = dataModelList;
    }

    /**
     * Given base b (a String), returns the first node in the sequence "b1",
     * "b2", "b3", etc., which is not already the name of a node in the
     * workbench.
     *
     * @param base the base string.
     * @return the first string in the sequence not already being used.
     */
    private String nextVariableName(final String base, final DataSet data) {

        // Variable names should start with "1."
        int i = -1;
        String name = "?";

        loop:
        while (true) {
            ++i;

            if (i == 0) {
                name = base;
            } else {
                name = base + i;
            }

            for (final Node node1 : data.getVariables()) {
                if (node1.getName().equals(name)) {
                    continue loop;
                }
            }

            break;
        }

        return name;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     * <p>
     * // * @see edu.cmu.TestSerialization
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //==============================PUBLIC METHODS========================//

    /**
     * Stores a reference to the data model being wrapped.
     *
     * @return the list of models.
     */
    public DataModelList getDataModelList() {
        return this.dataModelList;
    }

    public List<DataModel> getDataModels() {
        final List<DataModel> dataModels = new ArrayList<>();
        for (final DataModel model : this.dataModelList) {
            dataModels.add(model);
        }
        return dataModels;
    }

    public void setDataModelList(final DataModelList dataModelList) {
        if (dataModelList == null) {
            throw new NullPointerException("Data model list not provided.");
        }
        this.dataModelList = dataModelList;
    }

    /**
     * @return the data model for this wrapper.
     */
    public DataModel getSelectedDataModel() {
        final DataModelList modelList = getDataModelList();
        return modelList.getSelectedModel();
    }

    /**
     * Sets the data model.
     */
    public void setDataModel(DataModel dataModel) {
        if (dataModel == null) {
            dataModel = new BoxDataSet(new VerticalDoubleDataBox(0, 0), new LinkedList<>());
        }

        if (dataModel instanceof DataModelList) {
            this.dataModelList = (DataModelList) dataModel;
        } else {
            final DataModelList dataModelList = new DataModelList();
            dataModelList.add(dataModel);
            this.dataModelList = dataModelList;
        }
    }

    public IKnowledge getKnowledge() {
        return getSelectedDataModel().getKnowledge();
    }

    public void setKnowledge(final IKnowledge knowledge) {
        getSelectedDataModel().setKnowledge(knowledge);
    }

    public List<String> getVarNames() {
        return getSelectedDataModel().getVariableNames();
    }

    /**
     * @return the source workbench, if there is one.
     */
    public Graph getSourceGraph() {
        return this.sourceGraph;
    }

    public Graph getResultGraph() {
        return getSourceGraph();
    }

    /**
     * @return the variable names, in order.
     */
    public List<Node> getVariables() {
        return this.getSelectedDataModel().getVariables();
    }

    /**
     * Sets the source graph.
     */
    protected void setSourceGraph(final Graph sourceGraph) {
        this.sourceGraph = sourceGraph;
    }

    /**
//     * Sets the source graph.
//     */
//    public void setKnownVariables(List<Node> variables) {
//        this.knownVariables = variables;
//    }

    public Map getDiscretizationSpecs() {
        return this.discretizationSpecs;
    }

//    public List<Node> getKnownVariables() {
//        return knownVariables;
//    }

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
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    /**
     * This method is overridden by classes that can identify parameters.
     *
     * @return null
     */
    public Parameters getParams() {
        return this.parameters;
    }

    public void setParameters(final Parameters parameters) {
        this.parameters = parameters;
    }

    public List<String> getVariableNames() {
        final List<String> variableNames = new ArrayList<>();
        for (final Node n : getVariables()) {
            variableNames.add(n.getName());
        }
        return variableNames;
    }

    @Override
    public Map<String, String> getParamSettings() {
        final Map<String, String> paramSettings = new HashMap<>();

        if (this.dataModelList == null) {
            System.out.println();
        }

        if (this.dataModelList.size() > 1) {
            paramSettings.put("# Datasets", Integer.toString(this.dataModelList.size()));
        } else {
            final DataModel dataModel = this.dataModelList.get(0);

            if (dataModel instanceof CovarianceMatrix) {
                if (!paramSettings.containsKey("# Nodes")) {
                    paramSettings.put("# Vars", Integer.toString(((CovarianceMatrix) dataModel).getDimension()));
                }
                paramSettings.put("N", Integer.toString(((CovarianceMatrix) dataModel).getSampleSize()));
            } else {
                if (!paramSettings.containsKey("# Nodes")) {
                    paramSettings.put("# Vars", Integer.toString(((DataSet) dataModel).getNumColumns()));
                }
                paramSettings.put("N", Integer.toString(((DataSet) dataModel).getNumRows()));
            }
        }

        return paramSettings;
    }

    @Override
    public void setAllParamSettings(final Map<String, String> paramSettings) {
        this.allParamSettings = paramSettings;
    }

    @Override
    public Map<String, String> getAllParamSettings() {
        return this.allParamSettings;
    }
}
