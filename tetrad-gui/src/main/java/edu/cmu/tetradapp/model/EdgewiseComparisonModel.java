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

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Compares a target workbench with a reference workbench by counting errors of
 * omission and commission.  (for edge presence only, not orientation).
 *
 * @author Joseph Ramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 */
public final class EdgewiseComparisonModel implements SessionModel, DoNotAddOldModel {
    static final long serialVersionUID = 23L;
    private Algorithm algorithm;

    private String name;
    private Parameters params;
    private List<Graph> targetGraphs;
    private List<Graph> referenceGraphs;
//    private Graph trueGraph;

    //=============================CONSTRUCTORS==========================//

//    public EdgewiseComparisonModel(GeneralAlgorithmRunner model, Parameters params) {
//        this(model, model.getDataWrapper(), params);
//    }

    /**
     * Compares the results of a PC to a reference workbench by counting errors
     * of omission and commission. The counts can be retrieved using the methods
     * <code>countOmissionErrors</code> and <code>countCommissionErrors</code>.
     */
    public EdgewiseComparisonModel(MultipleGraphSource model1, MultipleGraphSource model2,
                                   Parameters params) {
        if (params == null) {
            throw new NullPointerException("Parameters must not be null");
        }

        // Need to be able to construct this object even if the models are
        // null. Otherwise the interface is annoying.
//        if (model2 == null) {
//            model2 = new DagWrapper(new Dag());
//        }
//
//        if (model1 == null) {
//            model1 = new DagWrapper(new Dag());
//        }

//        if (!(model1 instanceof MultipleGraphSource) ||
//                !(model2 instanceof MultipleGraphSource)) {
//            throw new IllegalArgumentException("Must be graph sources.");
//        }

        if (model1 instanceof GeneralAlgorithmRunner && model2 instanceof GeneralAlgorithmRunner) {
            throw new IllegalArgumentException("Both parents can't be general algorithm runners.");
        }

        if (model1 instanceof GeneralAlgorithmRunner) {
            GeneralAlgorithmRunner generalAlgorithmRunner = (GeneralAlgorithmRunner) model1;
            this.algorithm = generalAlgorithmRunner.getAlgorithm();
        } else if (model2 instanceof GeneralAlgorithmRunner) {
            GeneralAlgorithmRunner generalAlgorithmRunner = (GeneralAlgorithmRunner) model2;
            this.algorithm = generalAlgorithmRunner.getAlgorithm();
        }

        this.params = params;

        String referenceName = this.params.getString("referenceGraphName", null);

        if (referenceName.equals(model1.getName())) {
            if (model1 instanceof Simulation && model2 instanceof GeneralAlgorithmRunner) {
                this.referenceGraphs = ((GeneralAlgorithmRunner) model2).getCompareGraphs(((Simulation) model1).getGraphs());
            } else if (model1 instanceof MultipleGraphSource) {
                this.referenceGraphs = ((MultipleGraphSource) model1).getGraphs();
            }

            if (model2 instanceof MultipleGraphSource) {
                this.targetGraphs = ((MultipleGraphSource) model2).getGraphs();
            }

            if (referenceGraphs.size() == 1 && targetGraphs.size() > 1) {
                Graph graph = referenceGraphs.get(0);
                referenceGraphs = new ArrayList<>();
                for (Graph _graph : targetGraphs) {
                    referenceGraphs.add(_graph);
                }
            }

            if (targetGraphs.size() == 1 && referenceGraphs.size() > 1) {
                Graph graph = targetGraphs.get(0);
                targetGraphs = new ArrayList<>();
                for (Graph _graph : referenceGraphs) {
                    targetGraphs.add(graph);
                }
            }

            if (referenceGraphs == null) {
                this.referenceGraphs = Collections.singletonList(((GraphSource) model1).getGraph());
            }

            if (targetGraphs == null) {
                this.targetGraphs = Collections.singletonList(((GraphSource) model2).getGraph());
            }
        } else if (referenceName.equals(model2.getName())) {
            if (model2 instanceof Simulation && model1 instanceof GeneralAlgorithmRunner) {
                this.referenceGraphs = ((GeneralAlgorithmRunner) model1).getCompareGraphs(((Simulation) model2).getGraphs());
            } else if (model1 instanceof MultipleGraphSource) {
                this.referenceGraphs = ((MultipleGraphSource) model2).getGraphs();
            }

            if (model1 instanceof MultipleGraphSource) {
                this.targetGraphs = ((MultipleGraphSource) model1).getGraphs();
            }

            if (referenceGraphs.size() == 1 && targetGraphs.size() > 1) {
                Graph graph = referenceGraphs.get(0);
                referenceGraphs = new ArrayList<>();
                for (Graph _graph : targetGraphs) {
                    referenceGraphs.add(_graph);
                }
            }

            if (targetGraphs.size() == 1 && referenceGraphs.size() > 1) {
                Graph graph = targetGraphs.get(0);
                targetGraphs = new ArrayList<>();
                for (Graph _graph : referenceGraphs) {
                    targetGraphs.add(graph);
                }
            }

            if (referenceGraphs == null) {
                this.referenceGraphs = Collections.singletonList(((GraphSource) model2).getGraph());
            }

            if (targetGraphs == null) {
                this.targetGraphs = Collections.singletonList(((GraphSource) model1).getGraph());
            }
        } else {
            throw new IllegalArgumentException(
                    "Neither of the supplied session models is named '" +
                            referenceName + "'.");
        }

        for (int i = 0; i < targetGraphs.size(); i++) {
            targetGraphs.set(i, GraphUtils.replaceNodes(targetGraphs.get(i), referenceGraphs.get(i).getNodes()));
        }

        if (algorithm != null) {
            for (int i = 0; i < referenceGraphs.size(); i++) {
                referenceGraphs.set(i, algorithm.getComparisonGraph(referenceGraphs.get(i)));
            }
        }

        if (referenceGraphs.size() != targetGraphs.size()) {
            throw new IllegalArgumentException("I was expecting the same number of graphs in each parent.");
        }

        TetradLogger.getInstance().log("info", "Graph Comparison");

        for (int i = 0; i < referenceGraphs.size(); i++) {
            TetradLogger.getInstance().log("comparison", "\nModel " + (i + 1));
            TetradLogger.getInstance().log("comparison", getComparisonString(i));
        }
    }

//    public EdgewiseComparisonModel(GraphWrapper referenceGraph,
//                                   AbstractAlgorithmRunner algorithmRunner,
//                                   Parameters params) {
//        this(referenceGraph, (SessionModel) algorithmRunner,
//                params);
//    }
//
//    public EdgewiseComparisonModel(GraphWrapper referenceWrapper,
//                                   GraphWrapper targetWrapper, Parameters params) {
//        this(referenceWrapper, (SessionModel) targetWrapper,
//                params);
//    }
//
//    public EdgewiseComparisonModel(DagWrapper referenceGraph,
//                                   AbstractAlgorithmRunner algorithmRunner,
//                                   Parameters params) {
//        this(referenceGraph, (SessionModel) algorithmRunner,
//                params);
//    }

//    public EdgewiseComparisonModel(DagWrapper referenceWrapper,
//                                   GraphWrapper targetWrapper, Parameters params) {
//        this(referenceWrapper, (SessionModel) targetWrapper,
//                params);
//    }

    //==============================PUBLIC METHODS========================//

    public DataSet getDataSet() {
        return (DataSet) params.get("dataSet", null);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComparisonString(int i) {
        String refName = getParams().getString("referenceGraphName", null);
        String targetName = getParams().getString("targetGraphName", null);
        return SearchGraphUtils.graphComparisonString(targetName, targetGraphs.get(i),
                refName, referenceGraphs.get(i), false);
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
    }
//
//    public Graph getTrueGraph() {
//        return trueGraph;
//    }
//
//    public void setTrueGraph(Graph trueGraph) {
//        this.trueGraph = trueGraph;
//    }

    private Parameters getParams() {
        return params;
    }

    public List<Graph> getTargetGraphs() {
        return targetGraphs;
    }
    public List<Graph> getReferenceGraphs() {
        return referenceGraphs;
    }

    public void setReferenceGraphs(List<Graph> referenceGraphs) {
        this.referenceGraphs = referenceGraphs;
    }
}


