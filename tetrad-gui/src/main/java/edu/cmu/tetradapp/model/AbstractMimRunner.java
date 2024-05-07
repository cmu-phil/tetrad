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
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.ParamsResettable;

import java.io.Serial;
import java.util.List;

/**
 * Implements a stub that basic algorithm wrappers can extend if they take either a dataModel model or a workbench model
 * as parent. Contains basic methods for executing algorithm and returning results.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public abstract class AbstractMimRunner implements MimRunner, ParamsResettable {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Keeps a reference to the dataModel source that has been provided (hopefully either a dataModel model or a
     * graph).
     */
    private final transient DataModel dataModel;

    /**
     * The name of the algorithm.
     */
    private String name;

    /**
     * The parameters guiding this search (when executed).
     */
    private Parameters params;

    /**
     * Clusters resulting from the last run of the algorithm.
     */
    private Clusters clusters = new Clusters();

    /**
     * Keeps a reference to the source graph, if there is one.
     */
    private Graph sourceGraph;

    /**
     * Keeps a reference to the result graph for the algorithm.
     */
    private Graph resultGraph;

    /**
     * The result structure graph, if there is one. Otherwise, null.
     */
    private Graph structureGraph;

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     */
    private DataWrapper dataWrapper;

    //===========================CONSTRUCTORS===========================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     */
    AbstractMimRunner(DataWrapper dataWrapper, Clusters clusters, Parameters params) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (params == null) {
            throw new NullPointerException();
        }

        this.dataWrapper = dataWrapper;

        this.params = params;
        setClusters(clusters);
        this.sourceGraph = dataWrapper.getSourceGraph();

        DataModel data = getDataModel(dataWrapper);
        getParams().set("knowledge", dataWrapper.getKnowledge());
        List<String> names = data.getVariableNames();
        transferVarNamesToParams(names);
        this.dataModel = data;
    }

    AbstractMimRunner(MeasurementModelWrapper wrapper, Clusters clusters, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
        setClusters(clusters);
//        this.sourceGraph = wrapper.getSourceGraph();

        DataModel data = wrapper.getData();
        List<String> names = data.getVariableNames();
        transferVarNamesToParams(names);
        this.dataModel = data;
    }

    AbstractMimRunner(MimRunner runner, Parameters params) {
        if (runner == null) {
            throw new NullPointerException();
        }

        this.params = params;
        this.params.set("clusters", runner.getClusters());
        this.sourceGraph = runner.getSourceGraph();

        DataModel dataSource = runner.getData();
        List<String> names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
        this.dataModel = dataSource;
    }

    //============================PUBLIC METHODS==========================//

    /**
     * <p>Getter for the field <code>resultGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public final Graph getResultGraph() {
        return this.resultGraph;
    }

    void setResultGraph(Graph graph) {
        this.resultGraph = graph;
    }

    /**
     * <p>Getter for the field <code>clusters</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Clusters} object
     */
    public Clusters getClusters() {
        return this.clusters;
    }

    void setClusters(Clusters clusters) {
        if (clusters == null) {
            throw new NullPointerException();
        }

        this.clusters = clusters;
    }

    /**
     * <p>Getter for the field <code>structureGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getStructureGraph() {
        return this.structureGraph;
    }

    void setStructureGraph(Graph graph) {
        this.structureGraph = graph;
    }

    /**
     * <p>getFullGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getFullGraph() {
        return null;
    }

    /**
     * <p>Getter for the field <code>sourceGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public final Graph getSourceGraph() {
        return this.sourceGraph;
    }

    /**
     * <p>getData.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public final DataModel getData() {
        if (this.dataWrapper != null) {
            DataModelList dataModelList = this.dataWrapper.getDataModelList();

            if (dataModelList.size() == 1) {
                return dataModelList.get(0);
            } else {
                return dataModelList;
            }
        } else if (this.dataModel != null) {
            return this.dataModel;
        } else {
            throw new IllegalArgumentException();
        }
    }

    //===========================PROTECTED METHODS========================//

    /**
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public final Parameters getParams() {
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

    //===========================PRIVATE METHODS==========================//

    /**
     * Find the dataModel model. (If it's a list, take the one that's selected.)
     */
    private DataModel getDataModel(DataWrapper dataWrapper) {
        DataModel dataModel = dataWrapper.getSelectedDataModel();

        if (dataModel instanceof DataModelList dataModelList) {
            dataModel = dataModelList.getSelectedModel();

        }

        if (dataModel instanceof DataSet dataSet) {

            if (dataSet.isDiscrete()) {
                return dataSet;
            } else if (dataSet.isContinuous()) {
                return dataSet;
            }

            throw new IllegalArgumentException("<html>" +
                                               "This dataModel set contains a mixture of discrete and continuous " +
                                               "<br>columns; there are no algorithm in Tetrad currently to " +
                                               "<br>search over such data sets." + "</html>");
        } else if (dataModel instanceof ICovarianceMatrix) {
            return dataModel;
        } else if (dataModel instanceof TimeSeriesData) {
            return dataModel;
        }

        throw new IllegalArgumentException(
                "Unexpected dataModel source: " + dataModel);
    }

    private void transferVarNamesToParams(List<String> names) {
        getParams().set("varNames", names);
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


}





