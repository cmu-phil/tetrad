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
    private Parameters params;
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
    public Misclassifications(MultipleGraphSource model1, MultipleGraphSource model2,
                              Parameters params) {
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

        String referenceName = params.getString("referenceGraphName", null);

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

        if (algorithm != null)

        {
            for (int i = 0; i < referenceGraphs.size(); i++) {
                referenceGraphs.set(i, algorithm.getComparisonGraph(referenceGraphs.get(i)));
            }
        }

        if (referenceGraphs.size() != targetGraphs.size())

        {
            throw new IllegalArgumentException("I was expecting the same number of graphs in each parent.");
        }

        TetradLogger.getInstance().

                log("info", "Graph Comparison");

        for (
                int i = 0;
                i < referenceGraphs.size(); i++)

        {
            TetradLogger.getInstance().log("comparison", "\nModel " + (i + 1));
            TetradLogger.getInstance().log("comparison", getComparisonString(i));
        }

        this.nf = NumberFormatUtil.getInstance().

                getNumberFormat();

    }

    private void setVcpcFields(VcpcRunner vcpc) {
        vcpcAdjacent = vcpc.getAdj();
        vcpcApparent = vcpc.getAppNon();
        vcpcDefinite = vcpc.getDefNon();
        vcpcNodes = vcpc.getGraph().getNodes();
    }

    private void setSvcpcFields(SampleVcpcRunner svcpc) {
        sVcpcAdjacent = svcpc.getAdj();
        sVcpcApparent = svcpc.getAppNon();
        sVcpcDefinite = svcpc.getDefNon();
        sVcpcNodes = svcpc.getGraph().getNodes();
    }

    private void setVcpcFastFields(VcpcFastRunner fvcpc) {
        fvcpcAdjacent = fvcpc.getAdj();
        fvcpcApparent = fvcpc.getAppNon();
        fvcpcDefinite = fvcpc.getDefNon();
        fvcpcNodes = fvcpc.getGraph().getNodes();
    }

    private void setSfvcpcFields(SampleVcpcFastRunner sfvcpc) {
        sfVcpcAdjacent = sfvcpc.getAdj();
        sfVcpcApparent = sfvcpc.getAppNon();
        sfVcpcDefinite = sfvcpc.getDefNon();
        sfVcpcNodes = sfvcpc.getGraph().getNodes();
    }

    private void setPcFields(PcRunner pc) {
        pcAdjacent = pc.getAdj();
        pcNonadjacent = pc.getNonAdj();
        pcNodes = pc.getGraph().getNodes();
    }

    private void setCpcFields(CpcRunner cpc) {
        cpcAdjacent = cpc.getAdj();
        cpcNonadjacent = cpc.getNonAdj();
        cpcNodes = cpc.getGraph().getNodes();
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
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComparisonString(int i) {

        if (this.useVcpcOutputs) {
            return (params.get("referenceGraphName", null) + " down the left; " +
                    params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsOne() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(targetGraphs.get(i), referenceGraphs.get(i));
        }

        if (this.useCpcOutputs) {
            return (params.get("referenceGraphName", null) + " down the left; " +
                    params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsTwo() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(targetGraphs.get(i), referenceGraphs.get(i));
        }
        if (this.usePcOutputs) {
            return (params.get("referenceGraphName", null) + " down the left; " +
                    params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsThree() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(targetGraphs.get(i), referenceGraphs.get(i));
        }

        if (this.useSvcpcOutputs) {
            return (params.get("targetGraphName", null) + " down the left; " +
                    params.get("referenceGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsFour() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(targetGraphs.get(i), referenceGraphs.get(i));
        }
        if (this.useScpcOutputs) {
            return (params.get("referenceGraphName", null) + " down the left; " +
                    params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsFive() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(targetGraphs.get(i), referenceGraphs.get(i));
        }

        if (this.useSFcpcOutputs) {
            return (params.get("referenceGraphName", null) + " down the left; " +
                    params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsSix() +
                    "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(targetGraphs.get(i), referenceGraphs.get(i));
        }

        if (this.useFcpcOutputs) {
            return (params.get("referenceGraphName", null) + " down the left; " +
                    params.get("targetGraphName", null) + " across the top.") +
                    "\n\nAdjacency Misclassification:\n" + adjacencyMisclassificationsSeven() +
                    "\n\nEndpoint Misclassification:\n" + "\nEdge Misclassifications:\n" +
                    MisclassificationUtils.edgeMisclassifications(targetGraphs.get(i), referenceGraphs.get(i));
        } else {
            return (params.get("referenceGraphName", null) + " down the left; " +
                    params.get("targetGraphName", null) + " across the top.") +
                    "\n\nEdge Misclassification:\n" +
                    MisclassificationUtils.edgeMisclassifications(targetGraphs.get(i), referenceGraphs.get(i)) +
                    "\nEndpoint Misclassification:\n" +
                    MisclassificationUtils.endpointMisclassification(targetGraphs.get(i), referenceGraphs.get(i));
        }
    }

    private String adjacencyMisclassificationsFour() {

        if (sVcpcNodes == null) {
            throw new NullPointerException("Please run SVCPC first, jerk");
        }
        if (vcpcNodes == null) {
            throw new NullPointerException("Please run VCPC first, or see Nich");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        Set<Edge> adjAppNonAdj = new HashSet<>();
        Set<Edge> adjDefNonAdj = new HashSet<>();
        Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        Set<Edge> nonAdjDefNonAdj = new HashSet<>();

        Set<Edge> sVcpcAdj = MisclassificationUtils.convertNodes(sVcpcAdjacent, vcpcNodes);
        Set<Edge> sVcpcAppNonadj = MisclassificationUtils.convertNodes(sVcpcApparent, vcpcNodes);
        Set<Edge> sVcpcDefNonadj = MisclassificationUtils.convertNodes(sVcpcDefinite, vcpcNodes);

        Set<Edge> vcpcAdj = new HashSet<>(vcpcAdjacent);


        for (Edge edge : sVcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (Edge edge : vcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }


        int[][] tableAdj = new int[3][3];

        for (Edge edge : sVcpcAppNonadj) {

            if (vcpcApparent.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[2][0]++;
                adjAppNonAdj.add(edge);
            }
            if (vcpcDefinite.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[2][0]++;
                adjDefNonAdj.add(edge);
            }

        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Apparent non-Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Apparent non-Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (Edge edge : sVcpcDefNonadj) {
            if (vcpcApparent.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[2][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (vcpcDefinite.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[2][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }


        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Definite Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Definite Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);

        StringBuilder builder = new StringBuilder();

        TextTable table9 = new TextTable(4, 4);


        table9.setToken(1, 0, "Apparently Nonadjacent");
        table9.setToken(2, 0, "Definitely Nonadjacent");
        table9.setToken(3, 0, "Total");

        table9.setToken(0, 1, "Apparently Nonadjacent");
        table9.setToken(0, 2, "Definitely Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9.toString());
        return builder.toString();
    }

    private String adjacencyMisclassificationsFive() {

        if (cpcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (sVcpcNodes == null) {
            throw new NullPointerException("Please run sVCPC first, jerk");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        Set<Edge> adjAppNonAdj = new HashSet<>();
        Set<Edge> adjDefNonAdj = new HashSet<>();
        Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        Set<Edge> nonAdjDefNonAdj = new HashSet<>();


        Set<Edge> cpcAdj = MisclassificationUtils.convertNodes(cpcAdjacent, sVcpcNodes);
        Set<Edge> cpcNonadj = MisclassificationUtils.convertNodes(cpcNonadjacent, sVcpcNodes);

        Set<Edge> svcpcAdj = new HashSet<>(sVcpcAdjacent);

        for (Edge edge : cpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (Edge edge : svcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }


        int[][] tableAdj = new int[4][3];

        for (Edge edge : cpcAdj) {

            if (svcpcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[3][0]++;
            }
            if (sVcpcApparent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[3][0]++;
                adjAppNonAdj.add(edge);
            }
            if (sVcpcDefinite.contains(edge)) {
                tableAdj[2][0]++;
                tableAdj[2][2]++;
                tableAdj[3][0]++;
                adjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (Edge edge : cpcNonadj) {
            if (svcpcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[3][1]++;
            }
            if (sVcpcApparent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[3][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (sVcpcDefinite.contains(edge)) {
                tableAdj[2][1]++;
                tableAdj[2][2]++;
                tableAdj[3][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);

        StringBuilder builder = new StringBuilder();

        TextTable table9 = new TextTable(5, 4);

        table9.setToken(1, 0, "Adjacent");
        table9.setToken(2, 0, "Apparently Nonadjacent");
        table9.setToken(3, 0, "Definitely Nonadjacent");
        table9.setToken(4, 0, "Total");

        table9.setToken(0, 1, "Adjacent");
        table9.setToken(0, 2, "Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9.toString());
        System.out.println("Sample CM: " + table9);
        return builder.toString();
    }


    private String adjacencyMisclassificationsSix() {

        if (cpcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (sfVcpcNodes == null) {
            throw new NullPointerException("Please run sfVCPC first, jerk");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        Set<Edge> adjAppNonAdj = new HashSet<>();
        Set<Edge> adjDefNonAdj = new HashSet<>();
        Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        Set<Edge> nonAdjDefNonAdj = new HashSet<>();


        Set<Edge> cpcAdj = MisclassificationUtils.convertNodes(cpcAdjacent, sfVcpcNodes);
        Set<Edge> cpcNonadj = MisclassificationUtils.convertNodes(cpcNonadjacent, sfVcpcNodes);

        Set<Edge> sfvcpcAdj = new HashSet<>(sfVcpcAdjacent);

        for (Edge edge : cpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (Edge edge : sfvcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }


        int[][] tableAdj = new int[4][3];

        for (Edge edge : cpcAdj) {

            if (sfvcpcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[3][0]++;
            }
            if (sfVcpcApparent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[3][0]++;
                adjAppNonAdj.add(edge);
            }
            if (sfVcpcDefinite.contains(edge)) {
                tableAdj[2][0]++;
                tableAdj[2][2]++;
                tableAdj[3][0]++;
                adjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (Edge edge : cpcNonadj) {
            if (sfvcpcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[3][1]++;
            }
            if (sfVcpcApparent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[3][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (sfVcpcDefinite.contains(edge)) {
                tableAdj[2][1]++;
                tableAdj[2][2]++;
                tableAdj[3][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);

        StringBuilder builder = new StringBuilder();

        TextTable table9 = new TextTable(5, 4);

        table9.setToken(1, 0, "Adjacent");
        table9.setToken(2, 0, "Apparently Nonadjacent");
        table9.setToken(3, 0, "Definitely Nonadjacent");
        table9.setToken(4, 0, "Total");

        table9.setToken(0, 1, "Adjacent");
        table9.setToken(0, 2, "Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9.toString());
        System.out.println("Sample Fast CM: " + table9);
        return builder.toString();
    }

    private String adjacencyMisclassificationsSeven() {

        if (cpcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (fvcpcNodes == null) {
            throw new NullPointerException("Please run fVCPC first, jerk");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        Set<Edge> adjAppNonAdj = new HashSet<>();
        Set<Edge> adjDefNonAdj = new HashSet<>();
        Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        Set<Edge> nonAdjDefNonAdj = new HashSet<>();


        Set<Edge> cpcAdj = MisclassificationUtils.convertNodes(cpcAdjacent, fvcpcNodes);
        Set<Edge> cpcNonadj = MisclassificationUtils.convertNodes(cpcNonadjacent, fvcpcNodes);

        Set<Edge> fvcpcAdj = new HashSet<>(fvcpcAdjacent);

        for (Edge edge : cpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (Edge edge : fvcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }


        int[][] tableAdj = new int[4][3];

        for (Edge edge : cpcAdj) {

            if (fvcpcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[3][0]++;
            }
            if (fvcpcApparent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[3][0]++;
                adjAppNonAdj.add(edge);
            }
            if (fvcpcDefinite.contains(edge)) {
                tableAdj[2][0]++;
                tableAdj[2][2]++;
                tableAdj[3][0]++;
                adjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (Edge edge : cpcNonadj) {
            if (fvcpcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[3][1]++;
            }
            if (fvcpcApparent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[3][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (fvcpcDefinite.contains(edge)) {
                tableAdj[2][1]++;
                tableAdj[2][2]++;
                tableAdj[3][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);

        StringBuilder builder = new StringBuilder();

        TextTable table9 = new TextTable(5, 4);

        table9.setToken(1, 0, "Adjacent");
        table9.setToken(2, 0, "Apparently Nonadjacent");
        table9.setToken(3, 0, "Definitely Nonadjacent");
        table9.setToken(4, 0, "Total");

        table9.setToken(0, 1, "Adjacent");
        table9.setToken(0, 2, "Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9.toString());
        System.out.println("Sample CM: " + table9);
        return builder.toString();
    }


    private String adjacencyMisclassificationsOne() {

        if (cpcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (vcpcNodes == null) {
            throw new NullPointerException("Please run VCPC first, or see Nich");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        Set<Edge> adjAppNonAdj = new HashSet<>();
        Set<Edge> adjDefNonAdj = new HashSet<>();
        Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        Set<Edge> nonAdjDefNonAdj = new HashSet<>();


        Set<Edge> cpcAdj = MisclassificationUtils.convertNodes(cpcAdjacent, vcpcNodes);
        Set<Edge> cpcNonadj = MisclassificationUtils.convertNodes(cpcNonadjacent, vcpcNodes);

        Set<Edge> vcpcAdj = new HashSet<>(vcpcAdjacent);

        for (Edge edge : cpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (Edge edge : vcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }


        int[][] tableAdj = new int[4][3];

        for (Edge edge : cpcAdj) {

            if (vcpcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[3][0]++;
            }
            if (vcpcApparent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[3][0]++;
                adjAppNonAdj.add(edge);
            }
            if (vcpcDefinite.contains(edge)) {
                tableAdj[2][0]++;
                tableAdj[2][2]++;
                tableAdj[3][0]++;
                adjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (Edge edge : cpcNonadj) {
            if (vcpcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[3][1]++;
            }
            if (vcpcApparent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[3][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (vcpcDefinite.contains(edge)) {
                tableAdj[2][1]++;
                tableAdj[2][2]++;
                tableAdj[3][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);

        StringBuilder builder = new StringBuilder();

        TextTable table9 = new TextTable(5, 4);

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
                table9.setToken(i + 1, j + 1, nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9.toString());
        System.out.println("VCPC CM: " + table9);
        return builder.toString();
    }

    private String adjacencyMisclassificationsTwo() {

        if (cpcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (pcNodes == null) {
            throw new NullPointerException("Please run PC first, or see Nich");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        Set<Edge> cpcAdj = MisclassificationUtils.convertNodes(cpcAdjacent, pcNodes);
        Set<Edge> cpcNonadj = MisclassificationUtils.convertNodes(cpcNonadjacent, pcNodes);


        Set<Edge> pcAdj = new HashSet<>(pcAdjacent);

        for (Edge edge : cpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (Edge edge : pcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        int[][] tableAdj = new int[3][3];

        for (Edge edge : cpcAdj) {

            if (pcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[2][0]++;
            }
            if (pcNonadjacent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[2][0]++;

            }
        }

        for (Edge edge : cpcNonadj) {
            if (pcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[2][1]++;
            }
            if (pcNonadjacent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[2][1]++;
            }

        }

        StringBuilder builder = new StringBuilder();

        TextTable table9 = new TextTable(4, 4);

        table9.setToken(1, 0, "Adjacent");
        table9.setToken(2, 0, "Nonadjacent");
        table9.setToken(3, 0, "Total");

        table9.setToken(0, 1, "Adjacent");
        table9.setToken(0, 2, "Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9.toString());
        System.out.println("PC CM: " + table9);
        return builder.toString();
    }

    private String adjacencyMisclassificationsThree() {

        if (pcNodes == null) {
            throw new NullPointerException("Please run CPC first, jerk");
        }
        if (vcpcNodes == null) {
            throw new NullPointerException("Please run VCPC first, or see Nich");
        }

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        Set<Edge> adjAppNonAdj = new HashSet<>();
        Set<Edge> adjDefNonAdj = new HashSet<>();
        Set<Edge> nonAdjAppNonAdj = new HashSet<>();
        Set<Edge> nonAdjDefNonAdj = new HashSet<>();


        Set<Edge> pcAdj = MisclassificationUtils.convertNodes(pcAdjacent, vcpcNodes);
        Set<Edge> pcNonadj = MisclassificationUtils.convertNodes(pcNonadjacent, vcpcNodes);


        Set<Edge> vcpcAdj = new HashSet<>(vcpcAdjacent);

        for (Edge edge : pcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        for (Edge edge : vcpcAdj) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }

        int[][] tableAdj = new int[4][3];

        for (Edge edge : pcAdj) {

            if (vcpcAdj.contains(edge)) {
                tableAdj[0][0]++;
                tableAdj[0][2]++;
                tableAdj[3][0]++;
            }
            if (vcpcApparent.contains(edge)) {
                tableAdj[1][0]++;
                tableAdj[1][2]++;
                tableAdj[3][0]++;
                adjAppNonAdj.add(edge);
            }
            if (vcpcDefinite.contains(edge)) {
                tableAdj[2][0]++;
                tableAdj[2][2]++;
                tableAdj[3][0]++;
                adjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("adjacenciesApp", "\n Adjacencies marked Apparent Non-adjacent" + adjAppNonAdj);
        TetradLogger.getInstance().log("adjacenciesDef", "\n Adjacencies marked Definite Non-adjacent" + adjDefNonAdj);

        for (Edge edge : pcNonadj) {
            if (vcpcAdj.contains(edge)) {
                tableAdj[0][1]++;
                tableAdj[0][2]++;
                tableAdj[3][1]++;
            }
            if (vcpcApparent.contains(edge)) {
                tableAdj[1][1]++;
                tableAdj[1][2]++;
                tableAdj[3][1]++;
                nonAdjAppNonAdj.add(edge);
            }
            if (vcpcDefinite.contains(edge)) {
                tableAdj[2][1]++;
                tableAdj[2][2]++;
                tableAdj[3][1]++;
                nonAdjDefNonAdj.add(edge);
            }
        }

        TetradLogger.getInstance().log("nonadjacenciesApp", "\n Non-Adjacencies marked Apparent Non-adjacent" + nonAdjAppNonAdj);
        TetradLogger.getInstance().log("nonadjacenciesDef", "\n Non-Adjacencies marked Definite Non-adjacent" + nonAdjDefNonAdj);


        StringBuilder builder = new StringBuilder();

        TextTable table9 = new TextTable(5, 4);

        table9.setToken(1, 0, "Adjacent");
        table9.setToken(2, 0, "Apparently Nonadjacent");
        table9.setToken(3, 0, "Definitely Nonadjacent");
        table9.setToken(4, 0, "Total");

        table9.setToken(0, 1, "Adjacent");
        table9.setToken(0, 2, "Nonadjacent");
        table9.setToken(0, 3, "Total");

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                table9.setToken(i + 1, j + 1, nf.format(tableAdj[i][j]));

            }
        }
        builder.append("\n").append(table9.toString());
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
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    public Parameters getParams() {
        return params;
    }

    public List<Graph> getReferenceGraphs() {
        return referenceGraphs;
    }

    public List<Graph> getTargetGraphs() {
        return targetGraphs;
    }
}


