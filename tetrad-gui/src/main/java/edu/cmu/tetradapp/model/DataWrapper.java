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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.DoNotAddOldModel;
import edu.cmu.tetradapp.session.SimulationParamsSource;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;

/**
 * Wraps a DataModel as a model class for a Session, providing constructors for the parents of Tetrad that are specified
 * by Tetrad.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DataWrapper implements KnowledgeEditable, KnowledgeBoxInput,
        DoNotAddOldModel, SimulationParamsSource, MultipleDataSource {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Maps columns to discretization specs so that user's work is not forgotten from one editing of the same data set
     * to the next.
     */
    private final Map discretizationSpecs = new HashMap();

    /**
     * The name of the data wrapper.
     */
    private String name;

    /**
     * The data model list.
     */
    private DataModelList dataModelList;

    /**
     * Stores a reference to the source workbench, if there is one.
     */
    private Graph sourceGraph;

    /**
     * The parameters being edited.
     */
    private Parameters parameters;

    /**
     * The parameter setting map.
     */
    private Map<String, String> allParamSettings;

    //==============================CONSTRUCTORS===========================//

    /**
     * <p>Constructor for DataWrapper.</p>
     */
    protected DataWrapper() {
        setDataModel(new BoxDataSet(new VerticalDoubleDataBox(0, 0), new LinkedList<>()));
        this.parameters = new Parameters();
    }

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     *
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public DataWrapper(Parameters parameters) {
        setDataModel(new BoxDataSet(new VerticalDoubleDataBox(0, 0), new LinkedList<>()));
        this.parameters = parameters;
    }

    /**
     * <p>Constructor for DataWrapper.</p>
     *
     * @param wrapper    a {@link edu.cmu.tetradapp.model.Simulation} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public DataWrapper(Simulation wrapper, Parameters parameters) {
        this.name = wrapper.getName();
        this.dataModelList = new DataModelList();

        for (DataModel model : wrapper.getDataModels()) {
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
     *
     * @param wrapper    the data wrapper to copy.
     * @param parameters the parameters to use.
     */
    public DataWrapper(DataWrapper wrapper, Parameters parameters) {
        this.name = wrapper.name;
        this.parameters = new Parameters(parameters);
        DataModelList dataModelList = new DataModelList();
        int selected = -1;

        for (int i = 0; i < wrapper.getDataModelList().size(); i++) {
            DataModel model = wrapper.getDataModelList().get(i);

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

        this.dataModelList = dataModelList;

        LogDataUtils.logDataModelList("Standalone data set.", getDataModelList());
    }

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     *
     * @param dataSet the data set to use.
     */
    public DataWrapper(DataSet dataSet) {
        setDataModel(dataSet);
    }

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     *
     * @param graph      the graph to use.
     * @param parameters the parameters to use.
     */
    public DataWrapper(Graph graph, Parameters parameters) {
        if (graph == null) {
            throw new NullPointerException();
        }

        this.parameters = new Parameters(parameters);

        List<Node> nodes = graph.getNodes();
        List<Node> variables = new LinkedList<>();

        for (Object node1 : nodes) {
            Node node = (Node) node1;
            String name = node.getName();
            NodeType nodetype = node.getNodeType();
            if (nodetype == NodeType.MEASURED) {
                ContinuousVariable var = new ContinuousVariable(name);
                variables.add(var);
            }
        }

        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(0, variables.size()), variables);
        DataModelList dataModelList = new DataModelList();
        dataModelList.add(dataSet);
        this.dataModelList = dataModelList;
    }

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     *
     * @param dagWrapper the DAG to use.
     * @param parameters the parameters to use.
     */
    public DataWrapper(DagWrapper dagWrapper, Parameters parameters) {
        this(dagWrapper.getDag(), parameters);
    }

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     *
     * @param wrapper    the SEM graph to use.
     * @param parameters the parameters to use.
     */
    public DataWrapper(SemGraphWrapper wrapper, Parameters parameters) {
        this(wrapper.getGraph(), parameters);
    }

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     *
     * @param wrapper    the SEM graph to use.
     * @param parameters the parameters to use.
     */
    public DataWrapper(GraphWrapper wrapper, Parameters parameters) {
        this(wrapper.getGraph(), parameters);
    }

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     *
     * @param regression the regression to use.
     * @param wrapper    the data model to use.
     * @param parameters the parameters to use.
     */
    public DataWrapper(RegressionRunner regression, DataWrapper wrapper, Parameters parameters) {
        this(regression.getResult(), (DataSet) Objects.requireNonNull(wrapper.getDataModelList().getSelectedModel()),
                parameters);
    }

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     *
     * @param regression the regression to use.
     * @param wrapper    the data model to use.
     * @param parameters the parameters to use.
     */
    public DataWrapper(RegressionRunner regression, Simulation wrapper, Parameters parameters) {
        this(regression.getResult(), (DataSet) Objects.requireNonNull(wrapper.getDataModelList().getSelectedModel()),
                parameters);
    }

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     *
     * @param result     the regression result to use.
     * @param data       the data to use.
     * @param parameters the parameters to use.
     */
    public DataWrapper(RegressionResult result, DataSet data, Parameters parameters) {
        this.parameters = new Parameters(parameters);

        DataSet data2 = data.copy();
        String predictedVariable = nextVariableName("Pred", data);
        data2.addVariable(new ContinuousVariable(predictedVariable));

        String[] regressorNames = result.getRegressorNames();

        for (int i = 0; i < data.getNumRows(); i++) {
            double[] x = new double[regressorNames.length];

            for (int j = 0; j < regressorNames.length; j++) {
                Node variable = data.getVariable(regressorNames[j]);

                if (variable == null) {
                    throw new NullPointerException("Variable " + variable + " doesn't "
                                                   + "exist in the input data.");
                }

                if (!(variable instanceof ContinuousVariable)) {
                    throw new IllegalArgumentException("Expecting a continuous variable: " + variable);
                }

                x[j] = data.getDouble(i, data.getColumn(variable));
            }

            double yHat = result.getPredictedValue(x);
            data2.setDouble(i, data2.getColumn(data2.getVariable(predictedVariable)), yHat);
        }

        DataModelList dataModelList = new DataModelList();
        dataModelList.add(data2);
        this.dataModelList = dataModelList;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    /**
     * Given base b (a String), returns the first node in the sequence "b1", "b2", "b3", etc., which is not already the
     * name of a node in the workbench.
     *
     * @param base the base string.
     * @return the first string in the sequence not already being used.
     */
    private String nextVariableName(String base, DataSet data) {

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

            for (Node node1 : data.getVariables()) {
                if (node1.getName().equals(name)) {
                    continue loop;
                }
            }

            break;
        }

        return name;
    }

    /**
     * Stores a reference to the data model being wrapped.
     *
     * @return the list of models.
     */
    public DataModelList getDataModelList() {
        return this.dataModelList;
    }

    /**
     * Set the data model list.
     *
     * @param dataModelList the data model list to set.
     */
    public void setDataModelList(DataModelList dataModelList) {
        if (dataModelList == null) {
            throw new NullPointerException("Data model list not provided.");
        }
        this.dataModelList = dataModelList;
    }

    /**
     * <p>getDataModels.</p>
     *
     * @return the data model for this wrapper.
     */
    public List<DataModel> getDataModels() {
        return new ArrayList<>(this.dataModelList);
    }

    /**
     * <p>getSelectedDataModel.</p>
     *
     * @return the selected data model for this wrapper.
     */
    public DataModel getSelectedDataModel() {
        DataModelList modelList = getDataModelList();
        return modelList.getSelectedModel();
    }

    /**
     * Sets the data model.
     *
     * @param dataModel the data model to set.
     */
    public void setDataModel(DataModel dataModel) {
        if (dataModel == null) {
            dataModel = new BoxDataSet(new VerticalDoubleDataBox(0, 0), new LinkedList<>());
        }

        if (dataModel instanceof DataModelList) {
            this.dataModelList = (DataModelList) dataModel;
        } else {
            DataModelList dataModelList = new DataModelList();
            dataModelList.add(dataModel);
            this.dataModelList = dataModelList;
        }
    }

    /**
     * <p>getKnowledge.</p>
     *
     * @return the knowledge for this wrapper.
     */
    public Knowledge getKnowledge() {
        return getSelectedDataModel().getKnowledge().copy();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets knowledge to a copy of the given object.
     */
    public void setKnowledge(Knowledge knowledge) {
        getSelectedDataModel().setKnowledge(knowledge.copy());
    }

    /**
     * <p>getVarNames.</p>
     *
     * @return the variable names of the selected data model.
     */
    public List<String> getVarNames() {
        return getSelectedDataModel().getVariableNames();
    }

    /**
     * <p>Getter for the field <code>sourceGraph</code>.</p>
     *
     * @return the source graph.
     */
    public Graph getSourceGraph() {
        return this.sourceGraph;
    }

    /**
     * Sets the source graph.
     *
     * @param sourceGraph the source graph to set.
     */
    protected void setSourceGraph(Graph sourceGraph) {
        this.sourceGraph = sourceGraph;
    }

    /**
     * <p>getResultGraph.</p>
     *
     * @return the result graph.
     */
    public Graph getResultGraph() {
        return getSourceGraph();
    }

    /**
     * <p>getVariables.</p>
     *
     * @return the variables, in order.
     */
    public List<Node> getVariables() {
        return this.getSelectedDataModel().getVariables();
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
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
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
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return the name of the data wrapper.
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of the data wrapper.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the parameters being edited.
     *
     * @return the parameters being edited.
     */
    public Parameters getParams() {
        return this.parameters;
    }

    /**
     * Sets the parameters being edited.
     *
     * @param parameters the parameters to set.
     */
    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    /**
     * Returns the variable names.
     *
     * @return the variable names.
     */
    public List<String> getVariableNames() {
        List<String> variableNames = new ArrayList<>();
        for (Node n : getVariables()) {
            variableNames.add(n.getName());
        }
        return variableNames;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameter setting map.
     */
    @Override
    public Map<String, String> getParamSettings() {
        Map<String, String> paramSettings = new HashMap<>();

        if (this.dataModelList.size() > 1) {
            paramSettings.put("# Datasets", Integer.toString(this.dataModelList.size()));
        } else {
            DataModel dataModel = this.dataModelList.getFirst();

            if (dataModel instanceof CovarianceMatrix) {
                paramSettings.put("# Vars", Integer.toString(((CovarianceMatrix) dataModel).getDimension()));
                paramSettings.put("N", Integer.toString(((CovarianceMatrix) dataModel).getSampleSize()));
            } else {
                paramSettings.put("# Vars", Integer.toString(((DataSet) dataModel).getNumColumns()));
                paramSettings.put("N", Integer.toString(((DataSet) dataModel).getNumRows()));
            }
        }

        return paramSettings;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameter setting map.
     */
    @Override
    public Map<String, String> getAllParamSettings() {
        return this.allParamSettings;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the parameter setting map.
     */
    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {
        this.allParamSettings = paramSettings;
    }
}

