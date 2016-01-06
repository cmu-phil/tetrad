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
import edu.cmu.tetrad.session.ParamsResettable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Implements a stub that basic algorithm wrappers can extend if they take
 * either a dataModel model or a workbench model as parent. Contains basic
 * methods for executing algorithms and returning results.
 *
 * @author Joseph Ramsey
 */
public abstract class AbstractMimRunner implements MimRunner, ParamsResettable {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * Keeps a reference to the dataModel source that has been provided
     * (hopefully either a dataModel model or a graph).
     *
     * @serial Cannot be null.
     */
    private transient DataModel dataModel;

    /**
     * The parameters guiding this search (when executed).
     *
     * @serial Cannot be null.
     */
    private MimParams params;

    /**
     * Clusters resulting from the last run of the algorithm.
     *
     * @serial Cannot be null.
     */
    private Clusters clusters = new Clusters();

    /**
     * Keeps a reference to the source graph, if there is one.
     *
     * @serial Can be null.
     */
    private Graph sourceGraph;

    /**
     * Keeps a reference to the result graph for the algorithm.
     *
     * @serial Can be null.
     */
    private Graph resultGraph;

    /**
     * The result structure graph, if there is one. Otherwise, null.
     *
     * @serial Can be null.
     */
    private Graph structureGraph;
    private DataWrapper dataWrapper;

    //===========================CONSTRUCTORS===========================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public AbstractMimRunner(DataWrapper dataWrapper, Clusters clusters, MimParams params) {
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
        getParams().setKnowledge(dataWrapper.getKnowledge());
        List names = data.getVariableNames();
        transferVarNamesToParams(names);
        this.dataModel = data;
    }

    public AbstractMimRunner(MeasurementModelWrapper wrapper, Clusters clusters, MimParams params) {
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
        List names = data.getVariableNames();
        transferVarNamesToParams(names);
        this.dataModel = data;
    }

    public AbstractMimRunner(MimRunner runner, MimParams params) {
        if (runner == null) {
            throw new NullPointerException();
        }

        this.params = params;
        this.params.setClusters(runner.getClusters());
        this.sourceGraph = runner.getSourceGraph();

        DataModel dataSource = runner.getData();
        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
        this.dataModel = dataSource;
    }

    //============================PUBLIC METHODS==========================//

    public final Graph getResultGraph() {
        return this.resultGraph;
    }

    public Clusters getClusters() {
        return this.clusters;
    }

    public Graph getStructureGraph() {
        return this.structureGraph;
    }

    public Graph getFullGraph() {
        return null;
    }

    public final Graph getSourceGraph() {
        return this.sourceGraph;
    }

    public final DataModel getData() {
        if (dataWrapper != null) {
            DataModelList dataModelList = dataWrapper.getDataModelList();

            if (dataModelList.size() == 1) {
                return dataModelList.get(0);
            } else {
                return dataModelList;
            }
        } else if (dataModel != null) {
            return dataModel;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public final MimParams getParams() {
        return this.params;
    }

    public void resetParams(Object params) {
        this.params = (MimParams) params;
    }

    public Object getResettableParams() {
        return this.params;
    }

    //===========================PROTECTED METHODS========================//

    protected void setResultGraph(Graph graph) {
        this.resultGraph = graph;
    }

    protected void setClusters(Clusters clusters) {
        if (clusters == null) {
            throw new NullPointerException();
        }

        this.clusters = clusters;
    }

    protected void setStructureGraph(Graph graph) {
        this.structureGraph = graph;
    }

    //===========================PRIVATE METHODS==========================//

    /**
     * Find the dataModel model. (If it's a list, take the one that's
     * selected.)
     */
    private DataModel getDataModel(DataWrapper dataWrapper) {
        DataModel dataModel = dataWrapper.getSelectedDataModel();

        if (dataModel instanceof DataModelList) {
            DataModelList dataModelList = (DataModelList) dataModel;
            dataModel = dataModelList.getSelectedModel();

        }

        if (dataModel instanceof DataSet) {
            DataSet dataSet = (DataSet) dataModel;

            if (dataSet.isDiscrete()) {
                return dataSet;
            } else if (dataSet.isContinuous()) {
                return dataSet;
            }

            throw new IllegalArgumentException("<html>" +
                    "This dataModel set contains a mixture of discrete and continuous " +
                    "<br>columns; there are no algorithms in Tetrad currently to " +
                    "<br>search over such data sets." + "</html>");
        } else if (dataModel instanceof ICovarianceMatrix) {
            return dataModel;
        } else if (dataModel instanceof TimeSeriesData) {
            return dataModel;
        }

        throw new IllegalArgumentException(
                "Unexpected dataModel source: " + dataModel);
    }

    private void transferVarNamesToParams(List names) {
        getParams().setVarNames(names);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


}





