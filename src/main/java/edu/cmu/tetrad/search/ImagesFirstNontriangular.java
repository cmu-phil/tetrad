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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * Increases the penalty from 1 .; by 1 until the first nontriangular graph is found.
 *
 * @author Joseph Ramsey
 */
public class ImagesFirstNontriangular implements GraphSearch, IImages {
    private IKnowledge knowledge = new Knowledge2();
    private List<DataSet> dataSets;
    private IImages images;
    private double penalty = 1.0;
    private Graph graph;

    public ImagesFirstNontriangular(List<DataSet> dataSets) {
        this.dataSets = dataSets;
        images = new Images(dataSets);
    }

    public boolean isAggressivelyPreventCycles() {
        return images.isAggressivelyPreventCycles();
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        images.setAggressivelyPreventCycles(aggressivelyPreventCycles);
    }

    public Graph search() {
        Graph pattern = new EdgeListGraph();

        for (int c = (int) penalty; c < penalty + 150; c++) {
            System.out.println("\n\n** c = " + c);

            images.setPenaltyDiscount(c + 1);
            images.setKnowledge(getKnowledge());
            Graph _pattern = images.search();

            Node node = _pattern.getNode("I");
            boolean nextDDisconnects = node != null && _pattern.getAdjacentNodes(node).isEmpty();

            images.setPenaltyDiscount(c);
            pattern = images.search();

            boolean containsTriangle = containsTriangle(pattern);

            if (!containsTriangle) {
                System.out.println("No triangle!");
            }

            if (nextDDisconnects) {
                System.out.println("Input variable disconnected for c = " + (c + 1));
            }

            if (!containsTriangle || nextDDisconnects) {
                break;
            }
        }

        return pattern;
    }

    public Graph search(List<Node> nodes) {
        throw new UnsupportedOperationException();
    }

    public long getElapsedTime() {
        return images.getElapsedTime();
    }

    public void setElapsedTime(long elapsedTime) {
        images.setElapsedTime(elapsedTime);
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        images.addPropertyChangeListener(l);
    }

    public double getPenaltyDiscount() {
        return images.getPenaltyDiscount();
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        images.setPenaltyDiscount(penaltyDiscount);
    }

    public int getMaxNumEdges() {
        return images.getMaxNumEdges();
    }

    public void setMaxNumEdges(int maxNumEdges) {
        images.setMaxNumEdges(maxNumEdges);
    }

    public double getModelScore() {
        return images.getModelScore();
    }

    public double getScore(Graph dag) {
        return images.getScore(dag);
    }

    public SortedSet<ScoredGraph> getTopGraphs() {
        return images.getTopGraphs();
    }

    public int getNumPatternsToStore() {
        return images.getNumPatternsToStore();
    }

    public void setNumPatternsToStore(int numPatternsToStore) {
        images.setNumPatternsToStore(numPatternsToStore);
    }

    public Map<Edge, Integer> getBoostrapCounts(int numBootstraps) {
        return images.getBoostrapCounts(numBootstraps);
    }

    public String bootstrapPercentagesString(int numBootstraps) {
        return images.bootstrapPercentagesString(numBootstraps);
    }

    public String gesCountsString() {
        return images.gesCountsString();
    }

    public Map<Edge, Double> averageStandardizedCoefficients() {
        return images.averageStandardizedCoefficients();
    }

    public Map<Edge, Double> averageStandardizedCoefficients(Graph graph) {
        return images.averageStandardizedCoefficients(graph);
    }

    public String averageStandardizedCoefficientsString() {
        return images.averageStandardizedCoefficientsString();
    }

    public String averageStandardizedCoefficientsString(Graph graph) {
        return images.averageStandardizedCoefficientsString(graph);
    }

    public String logEdgeBayesFactorsString(Graph dag) {
        return images.logEdgeBayesFactorsString(dag);
    }

    public Map<Edge, Double> logEdgeBayesFactors(Graph dag) {
        return images.logEdgeBayesFactors(dag);
    }


    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public double scoreDag(Graph dag) {
        return images.scoreDag(dag);
    }

//    public void setStructurePrior(double structurePrior) {
//        images.setStructurePrior(structurePrior);
//    }
//
//    public void setSamplePrior(double samplePrior) {
//        images.setSamplePrior(samplePrior);
//    }

    private boolean containsTriangle(Graph graph) {
        int numNodes = graph.getNumNodes();
        List<Node> nodes = graph.getNodes();

        for (int j1 = 0; j1 < numNodes; j1++) {
            for (int j2 = j1; j2 < numNodes; j2++) {
                for (int j3 = j2; j3 < numNodes; j3++) {
                    if (graph.isAdjacentTo(nodes.get(j1), nodes.get(j2))
                            && graph.isAdjacentTo(nodes.get(j1), nodes.get(j3))
                            && graph.isAdjacentTo(nodes.get(j2), nodes.get(j3))) {
                        System.out.println("Found triangle " + nodes.get(j1) + " " + nodes.get(j2) + " " + nodes.get(j3));

                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void setPenalty(double penalty) {
        this.penalty = penalty;
    }

//    public void setMinimumJump(double v) {
//        //To change body of implemented methods use File | Settings | File Templates.
//    }
//
//    public void setUseFCutoff(boolean useFCutoff) {
//        //To change body of implemented methods use File | Settings | File Templates.
//    }


    public void setMinJump(double minJump) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

}



