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
    private final Parameters params;
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

    public EdgewiseComparisonModel(final MultipleGraphSource model1, final MultipleGraphSource model2,
                                   final Parameters params) {
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
            final GeneralAlgorithmRunner generalAlgorithmRunner = (GeneralAlgorithmRunner) model1;
            this.algorithm = generalAlgorithmRunner.getAlgorithm();
        } else if (model2 instanceof GeneralAlgorithmRunner) {
            final GeneralAlgorithmRunner generalAlgorithmRunner = (GeneralAlgorithmRunner) model2;
            this.algorithm = generalAlgorithmRunner.getAlgorithm();
        }

        this.params = params;

        final String referenceName = this.params.getString("referenceGraphName", null);

        if (referenceName.equals(model1.getName())) {
            if (model1 instanceof Simulation && model2 instanceof GeneralAlgorithmRunner) {
                this.referenceGraphs = ((GeneralAlgorithmRunner) model2).getCompareGraphs(model1.getGraphs());
            } else if (model1 instanceof MultipleGraphSource) {
                this.referenceGraphs = model1.getGraphs();
            }

            if (model2 instanceof MultipleGraphSource) {
                this.targetGraphs = model2.getGraphs();
            }

            if (this.referenceGraphs.size() == 1 && this.targetGraphs.size() > 1) {
                final Graph graph = this.referenceGraphs.get(0);
                this.referenceGraphs = new ArrayList<>();
                for (final Graph _graph : this.targetGraphs) {
                    this.referenceGraphs.add(_graph);
                }
            }

            if (this.targetGraphs.size() == 1 && this.referenceGraphs.size() > 1) {
                final Graph graph = this.targetGraphs.get(0);
                this.targetGraphs = new ArrayList<>();
                for (final Graph _graph : this.referenceGraphs) {
                    this.targetGraphs.add(graph);
                }
            }

            if (this.referenceGraphs == null) {
                this.referenceGraphs = Collections.singletonList(((GraphSource) model1).getGraph());
            }

            if (this.targetGraphs == null) {
                this.targetGraphs = Collections.singletonList(((GraphSource) model2).getGraph());
            }
        } else if (referenceName.equals(model2.getName())) {
            if (model2 instanceof Simulation && model1 instanceof GeneralAlgorithmRunner) {
                this.referenceGraphs = ((GeneralAlgorithmRunner) model1).getCompareGraphs(model2.getGraphs());
            } else if (model1 instanceof MultipleGraphSource) {
                this.referenceGraphs = model2.getGraphs();
            }

            if (model1 instanceof MultipleGraphSource) {
                this.targetGraphs = model1.getGraphs();
            }

            if (this.referenceGraphs.size() == 1 && this.targetGraphs.size() > 1) {
                final Graph graph = this.referenceGraphs.get(0);
                this.referenceGraphs = new ArrayList<>();
                for (final Graph _graph : this.targetGraphs) {
                    this.referenceGraphs.add(_graph);
                }
            }

            if (this.targetGraphs.size() == 1 && this.referenceGraphs.size() > 1) {
                final Graph graph = this.targetGraphs.get(0);
                this.targetGraphs = new ArrayList<>();
                for (final Graph _graph : this.referenceGraphs) {
                    this.targetGraphs.add(graph);
                }
            }

            if (this.referenceGraphs == null) {
                this.referenceGraphs = Collections.singletonList(((GraphSource) model2).getGraph());
            }

            if (this.targetGraphs == null) {
                this.targetGraphs = Collections.singletonList(((GraphSource) model1).getGraph());
            }
        } else {
            throw new IllegalArgumentException(
                    "Neither of the supplied session models is named '" +
                            referenceName + "'.");
        }

        for (int i = 0; i < this.targetGraphs.size(); i++) {
            this.targetGraphs.set(i, GraphUtils.replaceNodes(this.targetGraphs.get(i), this.referenceGraphs.get(i).getNodes()));
        }

        if (this.algorithm != null) {
            for (int i = 0; i < this.referenceGraphs.size(); i++) {
                this.referenceGraphs.set(i, this.algorithm.getComparisonGraph(this.referenceGraphs.get(i)));
            }
        }

        if (this.referenceGraphs.size() != this.targetGraphs.size()) {
            throw new IllegalArgumentException("I was expecting the same number of graphs in each parent.");
        }

        TetradLogger.getInstance().log("info", "Graph Comparison");

        for (int i = 0; i < this.referenceGraphs.size(); i++) {
            TetradLogger.getInstance().log("comparison", "\nModel " + (i + 1));
            TetradLogger.getInstance().log("comparison", getComparisonString(i));
        }
    }

    //==============================PUBLIC METHODS========================//

    public DataSet getDataSet() {
        return (DataSet) this.params.get("dataSet", null);
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getComparisonString(final int i) {
        final String refName = getParams().getString("referenceGraphName", null);
        final String targetName = getParams().getString("targetGraphName", null);
        return SearchGraphUtils.graphComparisonString(targetName, this.targetGraphs.get(i),
                refName, this.referenceGraphs.get(i), false);
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
    private void readObject(final ObjectInputStream s)
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
        return this.params;
    }

    public List<Graph> getTargetGraphs() {
        return this.targetGraphs;
    }

    public List<Graph> getReferenceGraphs() {
        return this.referenceGraphs;
    }

    public void setReferenceGraphs(final List<Graph> referenceGraphs) {
        this.referenceGraphs = referenceGraphs;
    }
}


