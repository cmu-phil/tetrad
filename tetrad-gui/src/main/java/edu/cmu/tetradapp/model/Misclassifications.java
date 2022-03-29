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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.NumberFormat;
import java.util.*;


/**
 * Compares a target workbench with a reference workbench using an edge type
 * misclassification matrix and an endpoint misclassification matrix.
 *
 * @author Joseph Ramsey
 */
public final class Misclassifications implements SessionModel, DoNotAddOldModel {
    static final long serialVersionUID = 23L;
    private Algorithm algorithm;
    private boolean useVcpcOutputs = false;
    private boolean useCpcOutputs = false;
    private boolean usePcOutputs = false;
    private boolean useSvcpcOutputs = false;
    private boolean useScpcOutputs = false;
    private boolean useSFcpcOutputs = false;
    private boolean useFcpcOutputs = false;

    private Set<Edge> vcpcAdjacent;
    private Set<Edge> vcpcApparent;
    private Set<Edge> vcpcDefinite;
    private List<Node> vcpcNodes;

    private Set<Edge> fvcpcAdjacent;
    private Set<Edge> fvcpcApparent;
    private Set<Edge> fvcpcDefinite;
    private List<Node> fvcpcNodes;

    private Set<Edge> sfVcpcAdjacent;
    private Set<Edge> sfVcpcApparent;
    private Set<Edge> sfVcpcDefinite;
    private List<Node> sfVcpcNodes;

    private Set<Edge> sVcpcAdjacent;
    private Set<Edge> sVcpcApparent;
    private Set<Edge> sVcpcDefinite;
    private List<Node> sVcpcNodes;

    private Set<Edge> pcAdjacent;
    private Set<Edge> pcNonadjacent;
    private List<Node> pcNodes;

    private Set<Edge> cpcAdjacent;
    private Set<Edge> cpcNonadjacent;
    private List<Node> cpcNodes;

    private String name;
    private final Parameters params;
    private List<Graph> targetGraphs = new ArrayList<>();
    private List<Graph> referenceGraphs = new ArrayList<>();

    private NumberFormat nf;

    //=============================CONSTRUCTORS==========================//

//    public Misclassifications(GeneralAlgorithmRunner model, Parameters params) {
//        this(model, model.getDataWrapper(), params);
//    }

    /**
     * Compares the results of a PC to a reference workbench by counting errors
     * of omission and commission. The counts can be retrieved using the methods
     * <code>countOmissionErrors</code> and <code>countCommissionErrors</code>.
     */
    public Misclassifications(final MultipleGraphSource model1, final MultipleGraphSource model2,
                              final Parameters params) {
        if (params == null) {
            throw new NullPointerException("Parameters must not be null");
        }

        if (model1 instanceof VcpcRunner && model2 instanceof PcRunner) {
            this.usePcOutputs = true;
            setVcpcFields((VcpcRunner) model1);
            setPcFields((PcRunner) model2);
        }

        if ((model2 instanceof VcpcRunner && model1 instanceof PcRunner)) {
            this.usePcOutputs = true;
            setVcpcFields((VcpcRunner) model2);
            setPcFields((PcRunner) model1);
        }

        if (model1 instanceof CpcRunner && model2 instanceof PcRunner) {
            this.useCpcOutputs = true;
            setCpcFields((CpcRunner) model1);
            setPcFields((PcRunner) model2);
        }

        if ((model2 instanceof CpcRunner && model1 instanceof PcRunner)) {
            this.useCpcOutputs = true;
            setCpcFields((CpcRunner) model2);
            setPcFields((PcRunner) model1);
        }

        if (model1 instanceof VcpcRunner && model2 instanceof CpcRunner) {
            this.useVcpcOutputs = true;
            setVcpcFields((VcpcRunner) model1);
            setCpcFields((CpcRunner) model2);
        }

        if ((model2 instanceof VcpcRunner && model1 instanceof CpcRunner)) {
            this.useVcpcOutputs = true;
            setVcpcFields((VcpcRunner) model2);
            setCpcFields((CpcRunner) model1);
        }

        if (model1 instanceof CpcRunner && model2 instanceof SampleVcpcRunner) {
            this.useScpcOutputs = true;
            setCpcFields((CpcRunner) model1);
            setSvcpcFields((SampleVcpcRunner) model2);
        }

        if ((model2 instanceof CpcRunner && model1 instanceof SampleVcpcRunner)) {
            this.useScpcOutputs = true;
            setCpcFields((CpcRunner) model2);
            setSvcpcFields((SampleVcpcRunner) model1);
        }


        if (model1 instanceof VcpcRunner && model2 instanceof SampleVcpcRunner) {
            this.useSvcpcOutputs = true;
            setVcpcFields((VcpcRunner) model1);
            setSvcpcFields((SampleVcpcRunner) model2);
        }

        if ((model2 instanceof VcpcRunner && model1 instanceof SampleVcpcRunner)) {
            this.useSvcpcOutputs = true;
            setVcpcFields((VcpcRunner) model2);
            setSvcpcFields((SampleVcpcRunner) model1);

        }

        if (model1 instanceof CpcRunner && model2 instanceof SampleVcpcFastRunner) {
            this.useSFcpcOutputs = true;
            setCpcFields((CpcRunner) model1);
            setSfvcpcFields((SampleVcpcFastRunner) model2);
        }

        if ((model2 instanceof CpcRunner && model1 instanceof SampleVcpcFastRunner)) {
            this.useSFcpcOutputs = true;
            setCpcFields((CpcRunner) model2);
            setSfvcpcFields((SampleVcpcFastRunner) model1);

        }

        if (model1 instanceof CpcRunner && model2 instanceof VcpcFastRunner) {
            this.useFcpcOutputs = true;
            setCpcFields((CpcRunner) model1);
            setVcpcFastFields((VcpcFastRunner) model2);
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

        this.params = params;

        final String referenceName = params.getString("referenceGraphName", null);

        if (referenceName == null) {
            throw new IllegalArgumentException("Must specify a reference graph.");
        }

        if (referenceName.equals(model1.getName())) {
            if (model1 instanceof Simulation && model2 instanceof GeneralAlgorithmRunner) {
                this.referenceGraphs = ((GeneralAlgorithmRunner) model2).getCompareGraphs(((Simulation) model1).getGraphs());
            } else if (model1 instanceof MultipleGraphSource) {
                this.referenceGraphs = ((MultipleGraphSource) model1).getGraphs();
            }

            if (model2 instanceof MultipleGraphSource) {
                this.targetGraphs = ((MultipleGraphSource) model2).getGraphs();
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
                this.referenceGraphs = ((GeneralAlgorithmRunner) model1).getCompareGraphs(((Simulation) model2).getGraphs());
            } else if (model1 instanceof MultipleGraphSource) {
                this.referenceGraphs = ((MultipleGraphSource) model2).getGraphs();
            }

            if (model1 instanceof MultipleGraphSource) {
                this.targetGraphs = ((MultipleGraphSource) model1).getGraphs();
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

//        if (model1 instanceof GeneralAlgorithmRunner && model2 instanceof GeneralAlgorithmRunner) {
//            throw new IllegalArgumentException("Both parents can't be general algorithm runners.");
//        }
//
//        if (model1 instanceof GeneralAlgorithmRunner) {
//            GeneralAlgorithmRunner generalAlgorithmRunner = (GeneralAlgorithmRunner) model1;
//            this.algorithm = generalAlgorithmRunner.getAlgorithm();
//        } else if (model2 instanceof GeneralAlgorithmRunner) {
//            GeneralAlgorithmRunner generalAlgorithmRunner = (GeneralAlgorithmRunner) model2;
//            this.algorithm = generalAlgorithmRunner.getAlgorithm();
//        }

        if (this.algorithm != null) {
            for (int i = 0; i < this.referenceGraphs.size(); i++) {
                this.referenceGraphs.set(i, this.algorithm.getComparisonGraph(this.referenceGraphs.get(i)));
            }
        }

        if (this.referenceGraphs.size() != this.targetGraphs.size()) {
            throw new IllegalArgumentException("I was expecting the same number of graphs in each parent.");
        }

        TetradLogger.getInstance().

                log("info", "Graph Comparison");

        for (
                int i = 0;
                i < this.referenceGraphs.size(); i++) {
            TetradLogger.getInstance().log("comparison", "\nModel " + (i + 1));
            TetradLogger.getInstance().log("comparison", getComparisonString(i));
        }

        this.nf = NumberFormatUtil.getInstance().

                getNumberFormat();

    }

    private void setVcpcFields(final VcpcRunner vcpc) {
        this.vcpcAdjacent = vcpc.getAdj();
        this.vcpcApparent = vcpc.getAppNon();
        this.vcpcDefinite = vcpc.getDefNon();
        this.vcpcNodes = vcpc.getGraph().getNodes();
    }

    private void setSvcpcFields(final SampleVcpcRunner svcpc) {
        this.sVcpcAdjacent = svcpc.getAdj();
        this.sVcpcApparent = svcpc.getAppNon();
        this.sVcpcDefinite = svcpc.getDefNon();
        this.sVcpcNodes = svcpc.getGraph().getNodes();
    }

    private void setVcpcFastFields(final VcpcFastRunner fvcpc) {
        this.fvcpcAdjacent = fvcpc.getAdj();
        this.fvcpcApparent = fvcpc.getAppNon();
        this.fvcpcDefinite = fvcpc.getDefNon();
        this.fvcpcNodes = fvcpc.getGraph().getNodes();
    }

    private void setSfvcpcFields(final SampleVcpcFastRunner sfvcpc) {
        this.sfVcpcAdjacent = sfvcpc.getAdj();
        this.sfVcpcApparent = sfvcpc.getAppNon();
        this.sfVcpcDefinite = sfvcpc.getDefNon();
        this.sfVcpcNodes = sfvcpc.getGraph().getNodes();
    }

    private void setPcFields(final PcRunner pc) {
        this.pcAdjacent = pc.getAdj();
        this.pcNonadjacent = pc.getNonAdj();
        this.pcNodes = pc.getGraph().getNodes();
    }

    private void setCpcFields(final CpcRunner cpc) {
        this.cpcAdjacent = cpc.getAdj();
        this.cpcNonadjacent = cpc.getNonAdj();
        this.cpcNodes = cpc.getGraph().getNodes();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static Node serializableInstance() {
        return new GraphNode("X");
    }

    //==============================PUBLIC METHODS========================//

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getComparisonString(final int i) {

        if (this.useVcpcOutputs) {
            return (this.params.get("referenceGraphName", null) + " down the left; " +
                    this.params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsOne() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(this.targetGraphs.get(i), this.referenceGraphs.get(i));
        }

        if (this.useCpcOutputs) {
            return (this.params.get("referenceGraphName", null) + " down the left; " +
                    this.params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsTwo() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(this.targetGraphs.get(i), this.referenceGraphs.get(i));
        }
        if (this.usePcOutputs) {
            return (this.params.get("referenceGraphName", null) + " down the left; " +
                    this.params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsThree() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(this.targetGraphs.get(i), this.referenceGraphs.get(i));
        }

        if (this.useSvcpcOutputs) {
            return (this.params.get("targetGraphName", null) + " down the left; " +
                    this.params.get("referenceGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsFour() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(this.targetGraphs.get(i), this.referenceGraphs.get(i));
        }
        if (this.useScpcOutputs) {
            return (this.params.get("referenceGraphName", null) + " down the left; " +
                    this.params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsFive() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(this.targetGraphs.get(i), this.referenceGraphs.get(i));
        }

        if (this.useSFcpcOutputs) {
            return (this.params.get("referenceGraphName", null) + " down the left; " +
                    this.params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsSix() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(this.targetGraphs.get(i), this.referenceGraphs.get(i));
        }

        if (this.useFcpcOutputs) {
            return (this.params.get("referenceGraphName", null) + " down the left; " +
                    this.params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsSeven() +
                    "\n\nEndpoint Misclassification:\n" + "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(this.targetGraphs.get(i), this.referenceGraphs.get(i));
        } else {
            return (this.params.get("referenceGraphName", null) + " down the left; " +
                    this.params.get("targetGraphName", null) + " across the top.") +
                    "\n\nEdge Misclassification:\n" +
                    MisclassificationUtils.edgeMisclassifications(this.targetGraphs.get(i), this.referenceGraphs.get(i)) +
                    "\nEndpoint Misclassification:\n" +
                    MisclassificationUtils.endpointMisclassification(this.targetGraphs.get(i), this.referenceGraphs.get(i));
        }
    }

    private String adjacencyMisclassificationsFour() {

        if (this.sVcpcNodes == null) {
            throw new NullPointerException("Please run SVCPC first, jerk");
        }
        if (this.vcpcNodes == null) {
            throw new NullPointerException("Please run VCPC first, or see Nich");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        final Set<Edge> adjAppNonAdj = new HashSet<>();
        final Set<Edge> adjDefNonAdj = new HashSet<>();
        final Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        final Set<Edge> nonAdjDefNonAdj = new HashSet<>();

        final Set<Edge> sVcpcAdj = MisclassificationUtils.convertNodes(this.sVcpcAdjacent, this.vcpcNodes);
        final Set<Edge> sVcpcAppNonadj = MisclassificationUtils.convertNodes(this.sVcpcApparent, this.vcpcNodes);
        final Set<Edge> sVcpcDefNonadj = MisclassificationUtils.convertNodes(this.sVcpcDefinite, this.vcpcNodes);

        final Set<Edge> vcpcAdj = new HashSet<>(this.vcpcAdjacent);


        for (final Edge edge : sVcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (final Edge edge : vcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }


        final int[][] tableAdj = new int[3][3];

        for (final Edge edge : sVcpcAppNonadj) {

            if (this.vcpcApparent.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[2][0]++;
                adjAppNonAdj.add(edge);
            }
            if (this.vcpcDefinite.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[2][0]++;
                adjDefNonAdj.add(edge);
            }

        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Apparent non-Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Apparent non-Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (final Edge edge : sVcpcDefNonadj) {
            if (this.vcpcApparent.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[2][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (this.vcpcDefinite.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[2][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }


        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Definite Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Definite Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);

        final StringBuilder builder = new StringBuilder();

        final TextTable table9 = new TextTable(4, 4);


        table9.setToken(1, 0, "Apparently Nonadjacent");
        table9.setToken(2, 0, "Definitely Nonadjacent");
        table9.setToken(3, 0, "Total");

        table9.setToken(0, 1, "Apparently Nonadjacent");
        table9.setToken(0, 2, "Definitely Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, this.nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9);
        return builder.toString();
    }

    private String adjacencyMisclassificationsFive() {

        if (this.cpcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (this.sVcpcNodes == null) {
            throw new NullPointerException("Please run sVCPC first, jerk");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        final Set<Edge> adjAppNonAdj = new HashSet<>();
        final Set<Edge> adjDefNonAdj = new HashSet<>();
        final Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        final Set<Edge> nonAdjDefNonAdj = new HashSet<>();


        final Set<Edge> cpcAdj = MisclassificationUtils.convertNodes(this.cpcAdjacent, this.sVcpcNodes);
        final Set<Edge> cpcNonadj = MisclassificationUtils.convertNodes(this.cpcNonadjacent, this.sVcpcNodes);

        final Set<Edge> svcpcAdj = new HashSet<>(this.sVcpcAdjacent);

        for (final Edge edge : cpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (final Edge edge : svcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }


        final int[][] tableAdj = new int[4][3];

        for (final Edge edge : cpcAdj) {

            if (svcpcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[3][0]++;
            }
            if (this.sVcpcApparent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[3][0]++;
                adjAppNonAdj.add(edge);
            }
            if (this.sVcpcDefinite.contains(edge)) {
                tableAdj[2][0]++;
                tableAdj[2][2]++;
                tableAdj[3][0]++;
                adjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (final Edge edge : cpcNonadj) {
            if (svcpcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[3][1]++;
            }
            if (this.sVcpcApparent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[3][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (this.sVcpcDefinite.contains(edge)) {
                tableAdj[2][1]++;
                tableAdj[2][2]++;
                tableAdj[3][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);

        final StringBuilder builder = new StringBuilder();

        final TextTable table9 = new TextTable(5, 4);

        table9.setToken(1, 0, "Adjacent");
        table9.setToken(2, 0, "Apparently Nonadjacent");
        table9.setToken(3, 0, "Definitely Nonadjacent");
        table9.setToken(4, 0, "Total");

        table9.setToken(0, 1, "Adjacent");
        table9.setToken(0, 2, "Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, this.nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9);
        System.out.println("Sample CM: " + table9);
        return builder.toString();
    }


    private String adjacencyMisclassificationsSix() {

        if (this.cpcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (this.sfVcpcNodes == null) {
            throw new NullPointerException("Please run sfVCPC first, jerk");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        final Set<Edge> adjAppNonAdj = new HashSet<>();
        final Set<Edge> adjDefNonAdj = new HashSet<>();
        final Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        final Set<Edge> nonAdjDefNonAdj = new HashSet<>();


        final Set<Edge> cpcAdj = MisclassificationUtils.convertNodes(this.cpcAdjacent, this.sfVcpcNodes);
        final Set<Edge> cpcNonadj = MisclassificationUtils.convertNodes(this.cpcNonadjacent, this.sfVcpcNodes);

        final Set<Edge> sfvcpcAdj = new HashSet<>(this.sfVcpcAdjacent);

        for (final Edge edge : cpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (final Edge edge : sfvcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }


        final int[][] tableAdj = new int[4][3];

        for (final Edge edge : cpcAdj) {

            if (sfvcpcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[3][0]++;
            }
            if (this.sfVcpcApparent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[3][0]++;
                adjAppNonAdj.add(edge);
            }
            if (this.sfVcpcDefinite.contains(edge)) {
                tableAdj[2][0]++;
                tableAdj[2][2]++;
                tableAdj[3][0]++;
                adjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (final Edge edge : cpcNonadj) {
            if (sfvcpcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[3][1]++;
            }
            if (this.sfVcpcApparent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[3][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (this.sfVcpcDefinite.contains(edge)) {
                tableAdj[2][1]++;
                tableAdj[2][2]++;
                tableAdj[3][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);

        final StringBuilder builder = new StringBuilder();

        final TextTable table9 = new TextTable(5, 4);

        table9.setToken(1, 0, "Adjacent");
        table9.setToken(2, 0, "Apparently Nonadjacent");
        table9.setToken(3, 0, "Definitely Nonadjacent");
        table9.setToken(4, 0, "Total");

        table9.setToken(0, 1, "Adjacent");
        table9.setToken(0, 2, "Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, this.nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9);
        System.out.println("Sample Fast CM: " + table9);
        return builder.toString();
    }

    private String adjacencyMisclassificationsSeven() {

        if (this.cpcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (this.fvcpcNodes == null) {
            throw new NullPointerException("Please run fVCPC first, jerk");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        final Set<Edge> adjAppNonAdj = new HashSet<>();
        final Set<Edge> adjDefNonAdj = new HashSet<>();
        final Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        final Set<Edge> nonAdjDefNonAdj = new HashSet<>();


        final Set<Edge> cpcAdj = MisclassificationUtils.convertNodes(this.cpcAdjacent, this.fvcpcNodes);
        final Set<Edge> cpcNonadj = MisclassificationUtils.convertNodes(this.cpcNonadjacent, this.fvcpcNodes);

        final Set<Edge> fvcpcAdj = new HashSet<>(this.fvcpcAdjacent);

        for (final Edge edge : cpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (final Edge edge : fvcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }


        final int[][] tableAdj = new int[4][3];

        for (final Edge edge : cpcAdj) {

            if (fvcpcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[3][0]++;
            }
            if (this.fvcpcApparent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[3][0]++;
                adjAppNonAdj.add(edge);
            }
            if (this.fvcpcDefinite.contains(edge)) {
                tableAdj[2][0]++;
                tableAdj[2][2]++;
                tableAdj[3][0]++;
                adjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (final Edge edge : cpcNonadj) {
            if (fvcpcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[3][1]++;
            }
            if (this.fvcpcApparent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[3][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (this.fvcpcDefinite.contains(edge)) {
                tableAdj[2][1]++;
                tableAdj[2][2]++;
                tableAdj[3][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);

        final StringBuilder builder = new StringBuilder();

        final TextTable table9 = new TextTable(5, 4);

        table9.setToken(1, 0, "Adjacent");
        table9.setToken(2, 0, "Apparently Nonadjacent");
        table9.setToken(3, 0, "Definitely Nonadjacent");
        table9.setToken(4, 0, "Total");

        table9.setToken(0, 1, "Adjacent");
        table9.setToken(0, 2, "Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, this.nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9);
        System.out.println("Sample CM: " + table9);
        return builder.toString();
    }


    private String adjacencyMisclassificationsOne() {

        if (this.cpcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (this.vcpcNodes == null) {
            throw new NullPointerException("Please run VCPC first, or see Nich");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        final Set<Edge> adjAppNonAdj = new HashSet<>();
        final Set<Edge> adjDefNonAdj = new HashSet<>();
        final Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        final Set<Edge> nonAdjDefNonAdj = new HashSet<>();


        final Set<Edge> cpcAdj = MisclassificationUtils.convertNodes(this.cpcAdjacent, this.vcpcNodes);
        final Set<Edge> cpcNonadj = MisclassificationUtils.convertNodes(this.cpcNonadjacent, this.vcpcNodes);

        final Set<Edge> vcpcAdj = new HashSet<>(this.vcpcAdjacent);

        for (final Edge edge : cpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (final Edge edge : vcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }


        final int[][] tableAdj = new int[4][3];

        for (final Edge edge : cpcAdj) {

            if (vcpcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[3][0]++;
            }
            if (this.vcpcApparent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[3][0]++;
                adjAppNonAdj.add(edge);
            }
            if (this.vcpcDefinite.contains(edge)) {
                tableAdj[2][0]++;
                tableAdj[2][2]++;
                tableAdj[3][0]++;
                adjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (final Edge edge : cpcNonadj) {
            if (vcpcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[3][1]++;
            }
            if (this.vcpcApparent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[3][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (this.vcpcDefinite.contains(edge)) {
                tableAdj[2][1]++;
                tableAdj[2][2]++;
                tableAdj[3][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);

        final StringBuilder builder = new StringBuilder();

        final TextTable table9 = new TextTable(5, 4);

        table9.setToken(1, 0, "Adjacent");
        table9.setToken(2, 0, "Apparently Nonadjacent");
        table9.setToken(3, 0, "Definitely Nonadjacent");
        System.out.println(table9);
        table9.setToken(4, 0, "Total");

        table9.setToken(0, 1, "Adjacent");
        table9.setToken(0, 2, "Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, this.nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9);
        System.out.println("VCPC CM: " + table9);
        return builder.toString();
    }

    private String adjacencyMisclassificationsTwo() {

        if (this.cpcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (this.pcNodes == null) {
            throw new NullPointerException("Please run PC first, or see Nich");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        final Set<Edge> cpcAdj = MisclassificationUtils.convertNodes(this.cpcAdjacent, this.pcNodes);
        final Set<Edge> cpcNonadj = MisclassificationUtils.convertNodes(this.cpcNonadjacent, this.pcNodes);


        final Set<Edge> pcAdj = new HashSet<>(this.pcAdjacent);

        for (final Edge edge : cpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (final Edge edge : pcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        final int[][] tableAdj = new int[3][3];

        for (final Edge edge : cpcAdj) {

            if (pcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[2][0]++;
            }
            if (this.pcNonadjacent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[2][0]++;

            }
        }

        for (final Edge edge : cpcNonadj) {
            if (pcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[2][1]++;
            }
            if (this.pcNonadjacent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[2][1]++;
            }

        }

        final StringBuilder builder = new StringBuilder();

        final TextTable table9 = new TextTable(4, 4);

        table9.setToken(1, 0, "Adjacent");
        table9.setToken(2, 0, "Nonadjacent");
        table9.setToken(3, 0, "Total");

        table9.setToken(0, 1, "Adjacent");
        table9.setToken(0, 2, "Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, this.nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9);
        System.out.println("PC CM: " + table9);
        return builder.toString();
    }

    private String adjacencyMisclassificationsThree() {

        if (this.pcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (this.vcpcNodes == null) {
            throw new NullPointerException("Please run VCPC first, or see Nich");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        final Set<Edge> adjAppNonAdj = new HashSet<>();
        final Set<Edge> adjDefNonAdj = new HashSet<>();
        final Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        final Set<Edge> nonAdjDefNonAdj = new HashSet<>();


        final Set<Edge> pcAdj = MisclassificationUtils.convertNodes(this.pcAdjacent, this.vcpcNodes);
        final Set<Edge> pcNonadj = MisclassificationUtils.convertNodes(this.pcNonadjacent, this.vcpcNodes);


        final Set<Edge> vcpcAdj = new HashSet<>(this.vcpcAdjacent);

        for (final Edge edge : pcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (final Edge edge : vcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        final int[][] tableAdj = new int[4][3];

        for (final Edge edge : pcAdj) {

            if (vcpcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[3][0]++;
            }
            if (this.vcpcApparent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[3][0]++;
                adjAppNonAdj.add(edge);
            }
            if (this.vcpcDefinite.contains(edge)) {
                tableAdj[2][0]++;
                tableAdj[2][2]++;
                tableAdj[3][0]++;
                adjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (final Edge edge : pcNonadj) {
            if (vcpcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[3][1]++;
            }
            if (this.vcpcApparent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[3][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (this.vcpcDefinite.contains(edge)) {
                tableAdj[2][1]++;
                tableAdj[2][2]++;
                tableAdj[3][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);


        final StringBuilder builder = new StringBuilder();

        final TextTable table9 = new TextTable(5, 4);

        table9.setToken(1, 0, "Adjacent");
        table9.setToken(2, 0, "Apparently Nonadjacent");
        table9.setToken(3, 0, "Definitely Nonadjacent");
        table9.setToken(4, 0, "Total");

        table9.setToken(0, 1, "Adjacent");
        table9.setToken(0, 2, "Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, this.nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9);
        return builder.toString();
    }


    //============================PRIVATE METHODS=========================//

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

    public Parameters getParams() {
        return this.params;
    }

    public List<Graph> getReferenceGraphs() {
        return this.referenceGraphs;
    }

    public List<Graph> getTargetGraphs() {
        return this.targetGraphs;
    }
}


