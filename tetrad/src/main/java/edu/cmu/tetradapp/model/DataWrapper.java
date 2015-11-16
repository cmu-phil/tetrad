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
import edu.cmu.tetrad.util.Params;
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
public class DataWrapper implements SessionModel, KnowledgeEditable, KnowledgeBoxInput, DoNotAddOldModel {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * Stores a reference to the data model being wrapped.
     *
     * @serial Cannot be null.
     */
    private DataModelList dataModelList;

    /**
     * Maps columns to discretization specs so that user's work is not forgotten
     * from one editing of the same data set to the next.
     *
     * @serial Cannot be null.
     */
    private Map discretizationSpecs = new HashMap();

    /**
     * Stores a reference to the source workbench, if there is one.
     *
     * @serial Can be null.
     */
    private Graph sourceGraph;

    /**
     * A list of known variables. Variables can be looked up in this list and
     * reused where appropriate.
     *
     * @serial Can be null.
     */
    private List<Node> knownVariables;

    /**
     * The params being edited.
     */
    private Params params = null;

    //==============================CONSTRUCTORS===========================//

    /**
     * Constructs a data wrapper using a new DataSet as data model.
     */
    public DataWrapper() {
        setDataModel(new ColtDataSet(0, new LinkedList<Node>()));
    }


    /**
     * Copy constructor.
     *
     * @param wrapper
     */
    public DataWrapper(DataWrapper wrapper) {
        this.name = wrapper.name;
        this.dataModelList = new DataModelList();
        int selected = -1;

        for (int i = 0; i < wrapper.dataModelList.size(); i++) {
            if (wrapper.dataModelList.get(i) instanceof DataSet) {
                DataSet data = (DataSet) wrapper.dataModelList.get(i);

                if (data.equals(wrapper.dataModelList.getSelectedModel())) {
                    selected = i;
                }

                this.dataModelList.add(copyData(data));
            }

        }

        if (selected > -1) {
            this.dataModelList.setSelectedModel(this.dataModelList.get(selected));
        }

        if(wrapper.sourceGraph != null){
            this.sourceGraph = new EdgeListGraph(wrapper.sourceGraph);
        }

        if(wrapper.knownVariables != null){
            this.knownVariables = new ArrayList<Node>(wrapper.knownVariables);
        }

        LogDataUtils.logDataModelList("Standalone data set.", getDataModelList());
    }


    /**
     * Constructs a data wrapper using a new DataSet as data model.
     */
    public DataWrapper(DataSet dataSet) {
        setDataModel(dataSet);
    }

    public DataWrapper(Graph graph) {
        if (graph == null) {
            throw new NullPointerException();
        }

        List<Node> nodes = graph.getNodes();
        List<Node> variables = new LinkedList<Node>();

        for (Object node1 : nodes) {
            Node node = (Node) node1;
            String name = node.getName();
            NodeType nodetype = node.getNodeType();
            if (nodetype == NodeType.MEASURED) {
                ContinuousVariable var = new ContinuousVariable(name);
                variables.add(var);
            }
        }

        DataSet dataSet = new ColtDataSet(0, variables);
        this.dataModelList = new DataModelList();
        this.dataModelList.add(dataSet);
    }

    public DataWrapper(DagWrapper dagWrapper) {
        this(dagWrapper.getDag());
    }

    public DataWrapper(SemGraphWrapper wrapper) {
        this(wrapper.getGraph());
    }

    public DataWrapper(GraphWrapper wrapper) {
        this(wrapper.getGraph());
    }

    public DataWrapper(RegressionRunner regression, DataWrapper wrapper) {
        this(regression.getResult(), (DataSet) wrapper.getDataModelList().getSelectedModel());
    }

    public DataWrapper(RegressionRunner regression, SemDataWrapper wrapper) {
        this(regression.getResult(), (DataSet) wrapper.getDataModelList().getSelectedModel());
    }

    // Computes regression predictions.
    public DataWrapper(RegressionResult result, DataSet data) {
//        if (!data.isContinuous()) {
//            throw new IllegalArgumentException("Must provide a continuous data set.");
//        }

        DataSet data2 = new ColtDataSet((ColtDataSet) data);
        String predictedVariable = nextVariableName("Pred", data);
        data2.addVariable(new ContinuousVariable(predictedVariable));

        String[] regressorNames = result.getRegressorNames();

        for (int i = 0; i < data.getNumRows(); i++) {
            double[] x = new double[regressorNames.length];

            for (int j = 0; j < regressorNames.length; j++) {
                Node variable = data.getVariable(regressorNames[j]);

                if (variable == null) throw new NullPointerException("Variable " + variable + " doesn't " +
                        "exist in the input data.");

                if (!(variable instanceof ContinuousVariable)) {
                    throw new IllegalArgumentException("Expecting a continuous variable: " + variable);
                }

                x[j] = data.getDouble(i, data.getColumn(variable));
            }

            double yHat = result.getPredictedValue(x);
            data2.setDouble(i, data2.getColumn(data2.getVariable(predictedVariable)), yHat);
        }


        this.dataModelList = new DataModelList();
        this.dataModelList.add(data2);
    }

    public DataWrapper(MimBuildRunner mimBuild) {
        ICovarianceMatrix cov = mimBuild.getCovMatrix();

        this.dataModelList = new DataModelList();
        this.dataModelList.add(cov);
    }

    /**
     * Given base b (a String), returns the first node in the sequence "b1",
     * "b2", "b3", etc., which is not already the name of a node in the
     * workbench.
     *
     * @param base the base string.
     * @return the first string in the sequence not already being used.
     */
    public String nextVariableName(String base, DataSet data) {

        // Variable names should start with "1."
        int i = -1;
        String name = "?";

        loop:
        while (true) {
            ++i;

            if (i == 0) {
                name = base;
            }
            else {
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
     * Generates a simple exemplar of this class to test serialization.
     *
//     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new DataWrapper(DataUtils.discreteSerializableInstance());
    }

    //==============================PUBLIC METHODS========================//

    /**
     * @return the list of models.
     */
    public DataModelList getDataModelList() {
        return dataModelList;
    }

    public void setDataModelList(DataModelList dataModelList) {
        this.dataModelList = dataModelList;
    }

    /**
     * @return the data model for this wrapper.
     */
    public DataModel getSelectedDataModel() {
        DataModelList modelList = this.dataModelList;
        return modelList.getSelectedModel();
    }

    /**
     * Sets the data model.
     */
    public void setDataModel(DataModel dataModel) {
        if (dataModel == null) {
            dataModel = new ColtDataSet(0, new LinkedList<Node>());
        }

        if (dataModel instanceof DataModelList) {
            this.dataModelList = (DataModelList) dataModel;
        } else {
            this.dataModelList = new DataModelList();
            this.dataModelList.add(dataModel);
        }
    }

    public IKnowledge getKnowledge() {
        return getSelectedDataModel().getKnowledge();
    }

    public void setKnowledge(IKnowledge knowledge) {
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
    public void setSourceGraph(Graph sourceGraph) {
        this.sourceGraph = sourceGraph;
    }

    /**
     * Sets the source graph.
     */
    public void setKnownVariables(List<Node> variables) {
        this.knownVariables = variables;
    }

    public Map getDiscretizationSpecs() {
        return discretizationSpecs;
    }

    public List<Node> getKnownVariables() {
        return knownVariables;
    }

    //=============================== Private Methods ==========================//

    private static DataModel copyData(DataSet data) {
        ColtDataSet newData = new ColtDataSet(data.getNumRows(), data.getVariables());
        for (int col = 0; col < data.getNumColumns(); col++) {
            for (int row = 0; row < data.getNumRows(); row++) {
                newData.setObject(row, col, data.getObject(row, col));
            }
        }
        newData.setKnowledge(data.getKnowledge().copy());
        if (data.getName() != null) {
            newData.setName(data.getName());
        }
        return newData;
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

        if (dataModelList == null) {
            throw new NullPointerException();
        }

        if (discretizationSpecs == null) {
            throw new NullPointerException();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * This method is overridden by classes that can identify parameters.
     * @return null
     */
    public Params getParams() {
        return this.params;
    }

    public void setParams(Params params) {
        this.params = params;
    }


	public List<String> getVariableNames() {
		List<String> variableNames = new ArrayList<String>();
		for (Node n: getVariables()) {
			variableNames.add(n.getName());
		}
		return variableNames;
	}
}





