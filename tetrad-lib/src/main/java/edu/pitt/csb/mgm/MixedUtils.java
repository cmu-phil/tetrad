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

package edu.pitt.csb.mgm;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.PermutationGenerator;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.dist.Discrete;


import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;


/**
 * Created by ajsedgewick on 7/29/15.
 */
public class MixedUtils {

    public static int[] getDiscreteInds(List<Node> nodes){
        List<Integer> indList = new ArrayList<>();
        int curInd = 0;
        for(Node n: nodes){
            if(n instanceof DiscreteVariable){
                indList.add(curInd);
            }
            curInd++;
        }

        int[] inds = new int[indList.size()];
        for(int i = 0; i < inds.length; i++){
            inds[i] = indList.get(i);
        }
        return inds;
    }

    public static int[] getContinuousInds(List<Node> nodes){
        List<Integer> indList = new ArrayList<>();
        int curInd = 0;
        for(Node n: nodes){
            if(n instanceof ContinuousVariable){
                indList.add(curInd);
            }
            curInd++;
        }

        int[] inds = new int[indList.size()];
        for(int i = 0; i < inds.length; i++){
            inds[i] = indList.get(i);
        }
        return inds;
    }

    //Converts a Dataset with both ContinuousVariables and DiscreteVariables to only ContinuousVariables
    public static DataSet makeContinuousData(DataSet dsMix) {
        ArrayList<Node> contVars = new ArrayList<Node>();
        for(Node n: dsMix.getVariables()){
            if(n instanceof DiscreteVariable){
                ContinuousVariable nc = new ContinuousVariable(n.getName());
                contVars.add(nc);
            } else {
                contVars.add(n);
            }
        }

        return ColtDataSet.makeData(contVars, dsMix.getDoubleData());
    }

    //takes DataSet of all ContinuousVariables
    //convert variables to discrete if there is an entry with <NodeName, "Disc"> in nodeDists
    public static DataSet makeMixedData(DataSet dsCont,  Map<String, String> nodeDists, int numCategories) {
        ArrayList<Node> mixVars = new ArrayList<Node>();
        for(Node n: dsCont.getVariables()){
            if(nodeDists.get(n.getName()).equals("Disc")){
                DiscreteVariable nd = new DiscreteVariable(n.getName(), numCategories);
                mixVars.add(nd);
            } else {
                mixVars.add(n);
            }
        }

        return ColtDataSet.makeData(mixVars, dsCont.getDoubleData());
    }

    //takes DataSet of all ContinuousVariables
    //convert variables to discrete if there is an entry with <NodeName, x> with x > 0, num categories set to x
    public static DataSet makeMixedData(DataSet dsCont,  Map<String, Integer> nodeDists) {
        ArrayList<Node> mixVars = new ArrayList<Node>();
        for(Node n: dsCont.getVariables()){
            int nC = nodeDists.get(n.getName());
            if(nC>0){
                DiscreteVariable nd = new DiscreteVariable(n.getName(), nC);
                mixVars.add(nd);
            } else {
                mixVars.add(n);
            }
        }

        return ColtDataSet.makeData(mixVars, dsCont.getDoubleData());
    }

    //Takes a mixed dataset and returns only data corresponding to ContinuousVariables in order
    public static DataSet getContinousData(DataSet ds){
        ArrayList<Node> contVars = new ArrayList<Node>();
        for(Node n: ds.getVariables()){
            if(n instanceof ContinuousVariable)
                contVars.add(n);
        }
        return ds.subsetColumns(contVars);
    }

    //Takes a mixed dataset and returns only data corresponding to DiscreteVariables in order
    public static DataSet getDiscreteData(DataSet ds){
        ArrayList<Node> discVars = new ArrayList<Node>();
        for(Node n: ds.getVariables()){
            if(n instanceof DiscreteVariable)
                discVars.add(n);
        }
        return ds.subsetColumns(discVars);
    }

    public static int[] getDiscLevels(DataSet ds){
        //ArrayList<Integer> levels = new ArrayList<Integer>[];
        DataSet discDs = getDiscreteData(ds);
        int[] levels = new int[discDs.getNumColumns()];
        int i = 0;
        for(Node n: discDs.getVariables()){
            levels[i] = ((DiscreteVariable) n).getNumCategories();
            i++;
        }
        return levels;
    }

    //generate PM from trueGraph for mixed Gaussian and Trinary variables
    //Don't use, buggy
    public static GeneralizedSemPm GaussianTrinaryPm(Graph trueGraph, HashMap<String, String> nodeDists, int maxSample, String paramTemplate) throws IllegalStateException{

        GeneralizedSemPm semPm = new GeneralizedSemPm(trueGraph);
        try {
            List<Node> variableNodes = semPm.getVariableNodes();
            int numVars = variableNodes.size();


            semPm.setStartsWithParametersTemplate("B", paramTemplate);
            semPm.setStartsWithParametersTemplate("D", paramTemplate);

            // empirically should give us a stddev of 1 - 2
            semPm.setStartsWithParametersTemplate("al", "U(.3,1.3)");
            semPm.setStartsWithParametersTemplate("s", "U(1,2)");

            //if we don't use NB error, we could do this instead
            String templateDisc = "DiscError(err, (TSUM(NEW(B)*$)), (TSUM(NEW(B)*$)), (TSUM(NEW(B)*$)))";
            //String templateDisc0 = "DiscError(err, 2,2,2)";
            String templateDisc0 = "DiscError(err, .001,.001,.001)";


            for (Node node : variableNodes) {

                List<Node> parents = trueGraph.getParents(node);
                //System.out.println("nParents: " + parents.size() );
                Node eNode = semPm.getErrorNode(node);

                //normal and nb work like normal sems
                String curEx = semPm.getNodeExpressionString(node);
                String errEx = semPm.getNodeExpressionString(eNode);
                String newTemp = "";

                //System.out.println("Node: " + node + "Type: " + nodeDists.get(node));

                if(nodeDists.get(node.getName()).equals("Disc")){
                    if(parents.size() == 0){
                        newTemp = templateDisc0;
                    } else {
                        newTemp = templateDisc;
                    }
                    newTemp = newTemp.replaceAll("err", eNode.getName());
                    curEx = TemplateExpander.getInstance().expandTemplate(newTemp, semPm, node);
                    //System.out.println("Disc CurEx: " + curEx);
                    errEx = TemplateExpander.getInstance().expandTemplate("U(0,1)", semPm, eNode);
                }

                //now for every discrete parent, swap for discrete params
                newTemp = "";
                if(parents.size() != 0) {
                    for (Node parNode : parents){
                        if(nodeDists.get(parNode.getName()).equals("Disc")){
                            //String curName = trueGraph.getParents(node).get(0).toString();
                            String curName = parNode.getName();
                            String disRep = "IF(" + curName + "=0,NEW(D),IF("+curName+"=1,NEW(D),NEW(D)))";
                            newTemp = curEx.replaceAll("(B[0-9]*\\*" + curName + ")(?![0-9])", disRep);
                        }
                    }
                }

                if(newTemp.length()!=0){
                    curEx = TemplateExpander.getInstance().expandTemplate(newTemp, semPm, node);
                }

                semPm.setNodeExpression(node, curEx);
                semPm.setNodeExpression(eNode, errEx);
            }
        } catch (ParseException e) {
            throw new IllegalStateException("Parse error in fixing parameters.", e);
        }

        return semPm;
    }

    //generate PM from trueGraph for mixed Gaussian and Categorical variables
    //public static GeneralizedSemPm GaussianCategoricalPm(Graph trueGraph, HashMap<String, Integer> nodeDists, String paramTemplate) throws IllegalStateException{
    public static GeneralizedSemPm GaussianCategoricalPm(Graph trueGraph, String paramTemplate) throws IllegalStateException{

        Map<String, Integer> nodeDists = getNodeDists(trueGraph);

        GeneralizedSemPm semPm = new GeneralizedSemPm(trueGraph);
        try {
            List<Node> variableNodes = semPm.getVariableNodes();
            int numVars = variableNodes.size();


            semPm.setStartsWithParametersTemplate("B", paramTemplate);
            semPm.setStartsWithParametersTemplate("C", paramTemplate);
            semPm.setStartsWithParametersTemplate("D", paramTemplate);

            // empirically should give us a stddev of 1 - 2
            semPm.setStartsWithParametersTemplate("s", "U(1,2)");

            //if we don't use NB error, we could do this instead
            //String templateDisc = "DiscError(err, (TSUM(NEW(B)*$)), (TSUM(NEW(B)*$)), (TSUM(NEW(B)*$)))";
//            String templateDisc0 = "DiscError(err, 1,1,1)";

            String templateDisc0 = "DiscError(err, ";

            for (Node node : variableNodes) {

                List<Node> parents = trueGraph.getParents(node);
                //System.out.println("nParents: " + parents.size() );
                Node eNode = semPm.getErrorNode(node);

                //normal and nb work like normal sems
                String curEx = semPm.getNodeExpressionString(node);
                String errEx = semPm.getNodeExpressionString(eNode);
                String newTemp = "";

                //System.out.println("Node: " + node + "Type: " + nodeDists.get(node));

                //dist of 0 means Gaussian
                int curDist = nodeDists.get(node.getName());
                if(curDist == 1)
                    throw new IllegalArgumentException("Dist for node " + node.getName() + " is set to one (i.e. constant) which is not supported.");


                //for each discrete node use DiscError for categorical draw
                if(curDist>0){
                    if(parents.size() == 0){
                        newTemp = "DiscError(err";
                        for(int l = 0; l < curDist; l++){
                            newTemp += ",1";
                        }
                        newTemp += ")";
//                        newTemp = templateDisc0;
                    } else {
                        newTemp = "DiscError(err";
                        for(int l = 0; l < curDist; l++){
                            newTemp += ", TSUM(NEW(C)*$)";
                        }
                        newTemp += ")";
                    }
                    newTemp = newTemp.replaceAll("err", eNode.getName());
                    curEx = TemplateExpander.getInstance().expandTemplate(newTemp, semPm, node);
                    //System.out.println("Disc CurEx: " + curEx);
                    errEx = TemplateExpander.getInstance().expandTemplate("U(0,1)", semPm, eNode);
                }

                //now for every discrete parent, swap for discrete params
                newTemp = curEx;
                if(parents.size() != 0) {
                    for (Node parNode : parents){
                        int parDist = nodeDists.get(parNode.getName());

                        if(parDist>0){
                            //String curName = trueGraph.getParents(node).get(0).toString();
                            String curName = parNode.getName();
                            String disRep = "Switch(" + curName;
                            for(int l = 0; l < parDist; l++){
                                if(curDist>0) {
                                    disRep += ",NEW(D)";
                                } else {
                                    disRep += ",NEW(C)";
                                }
                            }
                            disRep += ")";

                            //replaces BX * curName with new discrete expression
                            if(curDist > 0){
                                newTemp = newTemp.replaceAll("(C[0-9]*\\*" + curName + ")(?![0-9])", disRep);
                            } else {
                                newTemp = newTemp.replaceAll("(B[0-9]*\\*" + curName + ")(?![0-9])", disRep);
                            }
                        }
                    }
                }

                if(newTemp.length()!=0){
                    //System.out.println(newTemp);
                    curEx = TemplateExpander.getInstance().expandTemplate(newTemp, semPm, node);
                }

                semPm.setNodeExpression(node, curEx);
                semPm.setNodeExpression(eNode, errEx);
            }
        } catch (ParseException e) {
            throw new IllegalStateException("Parse error in fixing parameters.", e);
        }

        return semPm;
    }

    //This method is needed to normalize edge parameters for an Instantiated Mixed Model
    //public static GeneralizedSemIm GaussianCategoricalIm(GeneralizedSemPm pm, HashMap<String, Integer> nodeDists){
    public static GeneralizedSemIm GaussianCategoricalIm(GeneralizedSemPm pm){

        Map<String, Integer> nodeDists = getNodeDists(pm.getGraph());

        GeneralizedSemIm im = new GeneralizedSemIm(pm);
        //System.out.println(im);
        List<Node> nodes = pm.getVariableNodes();

        //this needs to be changed for cyclic graphs...
        for(Node n: nodes){
            Set<Node> parNodes = pm.getReferencedNodes(n);
            if(parNodes.size()==0){
                continue;
            }
            for(Node par: parNodes){
                if(par.getNodeType()==NodeType.ERROR){
                    continue;
                }
                int cL = nodeDists.get(n.getName());
                int pL = nodeDists.get(par.getName());
                if(cL==0 && pL==0){
                    continue;
                }

                List<String> params = getEdgeParams(n, par, pm);
                //just use the first parameter as the "weight" for the whole edge
                double w = im.getParameterValue(params.get(0));
                double[] newWeights;

                //d-d edges use one vector and permute edges, could use different strategy
                if(cL > 0 && pL > 0) {
                    newWeights = new double[pL*cL];
                    PermutationGenerator pg = new PermutationGenerator(pL);
                    int[] permInd = pg.next();
                    double[] weightVals = generateMixedEdgeParams(w, pL);
                    for(int i = 0; i < cL; i++){
                        for(int j = 0; j < pL; j++){
                            int index = i*pL + j;
                            im.setParameterValue(params.get(index), weightVals[permInd[j]]);
                        }
                        permInd = pg.next();
                    }

                } else {
                    int curL = (pL > 0 ? pL: cL);
                    newWeights = generateMixedEdgeParams(w, curL);
                    int count = 0;
                    for(String p : params){
                        im.setParameterValue(p, newWeights[count]);
                        count++;
                    }
                }
            }
            //pm.

            //if(p.startsWith("B")){
            //    continue;
            //} else if(p.startsWith())
        }


        return im;
    }

    //Given two node names and a parameterized model return list of parameters corresponding to edge between them
    public static List<String> getEdgeParams(String s1, String s2, GeneralizedSemPm pm){
        Node n1 = pm.getNode(s1);
        Node n2 = pm.getNode(s2);
        return getEdgeParams(n1, n2, pm);
    }

    //Given two nodes and a parameterized model return list of parameters corresponding to edge between them
    public static List<String> getEdgeParams(Node n1, Node n2, GeneralizedSemPm pm){
        //there may be a better way to do this using recursive calls of Expression.getExpressions
        Set<String> allParams = pm.getParameters();

        Node child;
        Node parent;
        if(pm.getReferencedNodes(n1).contains(n2)){
            child = n1;
            parent = n2;
        } else if (pm.getReferencedNodes(n2).contains(n1)){
            child = n2;
            parent = n1;
        } else {
            return null;
        }

        java.util.regex.Pattern parPat;
        if(parent instanceof DiscreteVariable){
            parPat = java.util.regex.Pattern.compile("Switch\\(" + parent.getName() + ",.*?\\)");
        } else {
            parPat = java.util.regex.Pattern.compile("([BC][0-9]*\\*" + parent.getName() + ")(?![0-9])");
        }

        ArrayList<String> paramList = new ArrayList<>();
        String ex = pm.getNodeExpressionString(child);
        java.util.regex.Matcher mat = parPat.matcher(ex);
        while(mat.find()){
            String curGroup = mat.group();
            if(parent instanceof DiscreteVariable){
                curGroup = curGroup.substring(("Switch(" + parent.getName()).length()+1, curGroup.length()-1);
                String[] pars = curGroup.split(",");
                for(String p : pars){
                    //if(!allParams.contains(p))
                    //    throw exception;
                    paramList.add(p);
                }
            } else{
                String p = curGroup.split("\\*")[0];
                paramList.add(p);
            }
        }
        //ex.
        //if(child instanceof DiscreteVariable){
        //    if(parent instanceof DiscreteVariable)
        //}

        /*Expression exp = pm.getNodeExpression(child);
        List<Expression> test = exp.getExpressions();
        for(Expression t : test){
            List<Expression> test2 = t.getExpressions();
            for(Expression t2: test2) {
                System.out.println(t2.toString());
            }
        }*/

        return paramList;
    }

    //generates a vector of length L with maximum value w that sums to 0
    public static double[] generateMixedEdgeParams(double w, int L){
        double[] vec = new double[L];
        RandomUtil ru = RandomUtil.getInstance();

        for(int i=0; i < L; i++){
            vec[i] = ru.nextUniform(0, 1);
        }

        double vMean = StatUtils.mean(vec);
        double vMax = 0;
        for(int i=0; i < L; i++){
            vec[i] = vec[i] - vMean;
            if(Math.abs(vec[i])> Math.abs(vMax))
                vMax = vec[i];
        }

        double scale = w/vMax;
        //maintain sign of w;
        if(vMax<0)
            scale *=-1;

        for(int i=0; i < L; i++){
            vec[i] *= scale;
        }

        return vec;
    }

    //labels corresponding to values from allEdgeStats
    public static final String EdgeStatHeader = "TD\tTU\tFL\tFD\tFU\tFPD\tFPU\tFND\tFNU\tBidir";

    //assumes Graphs have properly assigned variable types
    public static int[][] allEdgeStats(Graph pT, Graph pE){
        HashMap<String, String> nd = new HashMap<String, String>();

        //Estimated graph more likely to have correct node types...
        for(Node n : pE.getNodes()){
            if(n instanceof DiscreteVariable){
                nd.put(n.getName(), "Disc");
            } else {
                nd.put(n.getName(), "Norm");
            }
        }
        return allEdgeStats(pT, pE, nd);
    }

    // break out stats by node distributions, here only "Norm" and "Disc"
    // so three types of possible edges, cc, cd, dd, output is edge type by stat type
    // counts bidirected
    public static int[][] allEdgeStats(Graph pT, Graph pE, HashMap<String, String> nodeDists) {
        int[][] stats = new int[3][10];
        for(int i=0; i<stats.length; i++){
            for(int j=0; j<stats[0].length; j++){
                stats[i][j] = 0;
            }
        }
        //enforce patterns?
        //Graph pT = SearchGraphUtils.patternFromDag(tg);
        //Graph pE = SearchGraphUtils.patternFromDag(eg);

        //check that variable names are the same...

        Set<Edge> edgesT = pT.getEdges();
        Set<Edge> edgesE = pE.getEdges();

        //differences += Math.abs(e1.size() - e2.size());

        //for (int i = 0; i < e1.size(); i++) {
        int edgeType;
        for(Edge eT: edgesT){
            Node n1 = pE.getNode(eT.getNode1().getName());
            Node n2 = pE.getNode(eT.getNode2().getName());
            if(nodeDists.get(n1.getName()).equals("Norm") && nodeDists.get(n2.getName()).equals("Norm")) {
                edgeType = 0;
            } else if(nodeDists.get(n1.getName()).equals("Disc") && nodeDists.get(n2.getName()).equals("Disc")) {
                edgeType = 2;
            } else {
                edgeType = 1;
            }

            Edge eE = pE.getEdge(n1, n2);
            if (eE == null) {
                if (eT.isDirected()) {
                    stats[edgeType][7]++; //False Negative Directed -- FND
                } else {
                    stats[edgeType][8]++; //False Negative Undirected -- FNU
                }
            } else if (eE.isDirected()){
                if (eT.isDirected() && eT.pointsTowards(eT.getNode1()) == eE.pointsTowards(n1)){
                    stats[edgeType][0]++; //True Directed -- TD
                } else if (eT.isDirected()){
                    stats[edgeType][2]++; //FLip
                } else {
                    stats[edgeType][3]++; //Falsely Directed -- FD
                }
            } else { //so eE is undirected
                if(eT.isDirected()) {
                    stats[edgeType][4]++; //Falsely Undirected -- FU
                } else {
                    stats[edgeType][1]++; //True Undirected -- TU
                }
            }
        }

        for(Edge eE: edgesE){
            Node n1 = pT.getNode(eE.getNode1().getName());
            Node n2 = pT.getNode(eE.getNode2().getName());

            if(nodeDists.get(n1.getName()).equals("Norm") && nodeDists.get(n2.getName()).equals("Norm")) {
                edgeType = 0;
            } else if(nodeDists.get(n1.getName()).equals("Disc") && nodeDists.get(n2.getName()).equals("Disc")) {
                edgeType = 2;
            } else {
                edgeType = 1;
            }

            if(eE.getEndpoint1()== Endpoint.ARROW && eE.getEndpoint2()==Endpoint.ARROW)
                stats[edgeType][9]++; //bidirected

            Edge eT = pT.getEdge(n1, n2);
            if(eT == null) {
                if(eE.isDirected()){
                    stats[edgeType][5]++; //False Positive Directed -- FPD
                } else {
                    stats[edgeType][6]++; //False Positive Undirected -- FUD
                }
            }
        }
        return stats;
    }

    //Utils
    public static Graph makeMixedGraph(Graph g, Map<String, Integer> m){
        List<Node> nodes = g.getNodes();
        for(int i = 0; i < nodes.size(); i++){
            Node n = nodes.get(i);
            int nL = m.get(n.getName());
            if(nL > 0){
                Node nNew = new DiscreteVariable(n.getName(), nL);
                nodes.set(i, nNew);
            }
        }

        Graph outG = new EdgeListGraph(nodes);
        for(Edge e: g.getEdges()){
            Node n1 = e.getNode1();
            Node n2 = e.getNode2();
            Edge eNew = new Edge(outG.getNode(n1.getName()), outG.getNode(n2.getName()), e.getEndpoint1(), e.getEndpoint2());
            outG.addEdge(eNew);
        }

        return outG;
    }

    public static String stringFrom2dArray(int[][] arr){
        String outStr = "";
        for(int i = 0; i < arr.length; i++){
            for(int j = 0; j < arr[i].length; j++){
                outStr += Integer.toString(arr[i][j]);
                if(j != arr[i].length-1)
                    outStr += "\t";
            }
            outStr+="\n";
        }
        return outStr;
    }

    public static DataSet loadDataSet(String dir, String filename) throws IOException {
        File file = new File(dir, filename);
        DataReader reader = new DataReader();
        reader.setVariablesSupplied(true);
        return reader.parseTabular(file);
    }

    public static DataSet loadDelim(String dir, String filename) throws IOException {
        File file = new File(dir, filename);
        DataReader reader = new DataReader();
        reader.setVariablesSupplied(false);
        return reader.parseTabular(file);
    }

    //Gives a map of number of categories of DiscreteVariables in g. ContinuousVariables are mapped to 0
    public static Map<String, Integer> getNodeDists(Graph g){
        HashMap<String, Integer> map = new HashMap<>();
        List<Node> nodes = g.getNodes();
        for(Node n: nodes){
            if(n instanceof DiscreteVariable)
                map.put(n.getName(), ((DiscreteVariable) n).getNumCategories());
            else
                map.put(n.getName(), 0);
        }
        return map;
    }

    public static DataSet loadData(String dir, String filename) throws IOException {
        File file = new File(dir, filename);
        DataReader reader = new DataReader();
        reader.setVariablesSupplied(true);
        return reader.parseTabular(file);
    }

    public static DoubleMatrix2D graphToMatrix(Graph graph, double undirectedWeight, double directedWeight) {
        // initialize matrix
        int n = graph.getNumNodes();
        DoubleMatrix2D matrix = DoubleFactory2D.dense.make(n, n, 0.0);

        // map node names in order of appearance
        HashMap<Node, Integer> map = new HashMap<Node, Integer>();
        int i = 0;
        for (Node node : graph.getNodes()) {
            map.put(node, i);
            i++;
        }

        // mark edges
        for (Edge edge : graph.getEdges()) {
            // if directed find which is parent/child
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            //treat bidirected as undirected...
            if (!edge.isDirected() || (edge.getEndpoint1()== Endpoint.ARROW && edge.getEndpoint2()==Endpoint.ARROW)) {
                matrix.set(map.get(node1), map.get(node2), undirectedWeight);
                matrix.set(map.get(node2), map.get(node1), undirectedWeight);
            } else {
                if (edge.pointsTowards(node1)) {
                    matrix.set(map.get(node2), map.get(node1), directedWeight);
                } else {
                //if (edge.pointsTowards(node2)) {
                    matrix.set(map.get(node1), map.get(node2), directedWeight);
                }
            }
        }
        return matrix;
    }

    //returns undirected skeleton matrix (symmetric
    public static DoubleMatrix2D skeletonToMatrix(Graph graph) {
        // initialize matrix
        int n = graph.getNumNodes();
        DoubleMatrix2D matrix = DoubleFactory2D.dense.make(n, n, 0.0);

        // map node names in order of appearance
        HashMap<Node, Integer> map = new HashMap<Node, Integer>();
        int i = 0;
        for (Node node : graph.getNodes()) {
            map.put(node, i);
            i++;
        }

        // mark edges
        for (Edge edge : graph.getEdges()) {
            // if directed find which is parent/child
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            matrix.set(map.get(node1), map.get(node2), 1.0);
            matrix.set(map.get(node2), map.get(node1), 1.0);

        }
        return matrix;
    }

    public static DoubleMatrix2D graphToMatrix(Graph graph){
        return graphToMatrix(graph, 1, 1);
    }

    //main for testing
    public static void main(String[] args){
        //Graph g = GraphConverter.convert("X1-->X2,X2-->X3,X3-->X4");
        Graph g = GraphConverter.convert("X1-->X2,X2-->X3,X3-->X4, X5-->X4");
        //simple graph pm im gen example

        HashMap<String, Integer> nd = new HashMap<>();
        nd.put("X1", 0);
        nd.put("X2", 0);
        nd.put("X3", 4);
        nd.put("X4", 4);
        nd.put("X5", 0);

        g = makeMixedGraph(g, nd);

        /*Graph g = new EdgeListGraph();
        g.addNode(new ContinuousVariable("X1"));
        g.addNode(new ContinuousVariable("X2"));
        g.addNode(new DiscreteVariable("X3", 4));
        g.addNode(new DiscreteVariable("X4", 4));
        g.addNode(new ContinuousVariable("X5"));

        g.addDirectedEdge(g.getNode("X1"), g.getNode("X2"));
        g.addDirectedEdge(g.getNode("X2"), g.getNode("X3"));
        g.addDirectedEdge(g.getNode("X3"), g.getNode("X4"));
        g.addDirectedEdge(g.getNode("X4"), g.getNode("X5"));
        */
        GeneralizedSemPm pm = GaussianCategoricalPm(g, "Split(-1.5,-.5,.5,1.5)");
        System.out.println(pm);

        GeneralizedSemIm im = GaussianCategoricalIm(pm);
        System.out.println(im);

        int samps = 15;
        DataSet ds = im.simulateDataAvoidInfinity(samps, false);
        System.out.println(ds);

        System.out.println("num cats " + ((DiscreteVariable) g.getNode("X4")).getNumCategories());

        /*Graph trueGraph = DataGraphUtils.loadGraphTxt(new File(MixedUtils.class.getResource("test_data").getPath(), "DAG_0_graph.txt"));
        HashMap<String, Integer> nd = new HashMap<>();
        List<Node> nodes = trueGraph.getNodes();
        for(int i = 0; i < nodes.size(); i++){
            int coin = RandomUtil.getInstance().nextInt(2);
            int dist = (coin==0) ? 0 : 3; //continuous if coin == 0
            nd.put(nodes.get(i).getName(), dist);
        }


        //System.out.println(getEdgeParams(g.getNode("X3"), g.getNode("X2"), pm).toString());
        //System.out.println(getEdgeParams(g.getNode("X4"), g.getNode("X3"), pm).toString());
        //System.out.println(getEdgeParams(g.getNode("X5"), g.getNode("X4"), pm).toString());
        //System.out.println("num cats " + ((DiscreteVariable) g.getNode("X4")).getNumCategories());

        /*
        HashMap<String, String> nd2 = new HashMap<>();
        nd2.put("X1", "Norm");
        nd2.put("X2", "Norm");
        nd2.put("X3", "Disc");
        nd2.put("X4", "Disc");
        nd2.put("X5", "Disc");

        GeneralizedSemPm pm2 = GaussianTrinaryPm(g, nd2, 10, "Split(-1.5,-.5,.5,1.5)");
        System.out.println("OLD pm:");
        System.out.print(pm2);
        */
    }
}

