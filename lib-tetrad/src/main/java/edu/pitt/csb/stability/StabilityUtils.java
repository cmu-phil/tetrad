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

package edu.pitt.csb.stability;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.Functions;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.RandomSampler;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.GraphSearch;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.pitt.csb.mgm.MGM;
import edu.pitt.csb.mgm.MixedUtils;
import org.apache.commons.math3.util.CombinatoricsUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;

/**
 * Runs a search algorithm over a N subsamples of size b to asses stability
 * as in "Stability Selection" and "Stability Approach to Regularization Selection"
 * Created by ajsedgewick on 9/4/15.
 */
public class StabilityUtils {

    //returns an adjacency matrix containing the edgewise instability as defined in Liu et al
    public static DoubleMatrix2D StabilitySearch(DataSet data, DataGraphSearch gs, int N, int b){
        int numVars = data.getNumColumns();
        DoubleMatrix2D thetaMat = DoubleFactory2D.dense.make(numVars, numVars, 0.0);

        int[][] samps = subSampleNoReplacement(data.getNumRows(), b, N);

        for(int s = 0; s < N; s++){
            DataSet dataSubSamp = data.subsetRows(samps[s]);
            Graph g = gs.search(dataSubSamp);

            //TODO update graphToMatrix method
            DoubleMatrix2D curAdj = MixedUtils.skeletonToMatrix(g);
            thetaMat.assign(curAdj, Functions.plus);
        }
        thetaMat.assign(Functions.mult(1.0 / N));
        //thetaMat.assign(thetaMat.copy().assign(Functions.minus(1.0)), Functions.mult).assign(Functions.mult(-2.0));
        return thetaMat;
    }

    //returns an adjacency matrix containing the edgewise instability as defined in Liu et al
    public static DoubleMatrix2D StabilitySearchPar(final DataSet data, final DataGraphSearch gs, int N, int b){

        final int numVars = data.getNumColumns();
        final DoubleMatrix2D thetaMat = DoubleFactory2D.dense.make(numVars, numVars, 0.0);

        final int[][] samps = subSampleNoReplacement(data.getNumRows(), b, N);

        final ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

        class StabilityAction extends RecursiveAction{
            private int chunk;
            private int from;
            private int to;

            public StabilityAction(int chunk, int from, int to){
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            //could avoid using syncronized if we keep track of array of mats and add at end, but that needs lots of
            //memory
            private synchronized void addToMat(DoubleMatrix2D matSum, DoubleMatrix2D curMat){
                matSum.assign(curMat, Functions.plus);
            }

            @Override
            protected void compute(){
                if (to - from <= chunk) {
                    for (int s = from; s < to; s++) {
                        DataSet dataSubSamp = data.subsetRows(samps[s]).copy();
                        DataGraphSearch curGs = gs.copy();
                        Graph g = curGs.search(dataSubSamp);

                        //TODO update graphToMatrix method
                        DoubleMatrix2D curAdj = MixedUtils.skeletonToMatrix(g); //set weights so that undirected stability works
                        addToMat(thetaMat, curAdj);
                    }

                    return;
                } else {
                    List<StabilityAction> tasks = new ArrayList<StabilityAction>();

                    final int mid = (to - from) / 2;

                    tasks.add(new StabilityAction(chunk, from, from + mid));
                    tasks.add(new StabilityAction(chunk, from + mid, to));

                    invokeAll(tasks);

                    return;
                }
            }

        }

        final int chunk = 2;

        pool.invoke(new StabilityAction(chunk, 0, N));

        thetaMat.assign(Functions.mult(1.0 / N));

        //do this elsewhere
        //thetaMat.assign(thetaMat.copy().assign(Functions.minus(1.0)), Functions.mult).assign(Functions.mult(-2.0));
        return thetaMat;
    }

    //needs a symmetric matrix
    //array of averages of instability matrix over [all, cc, cd, dd] edges
    //TODO directed version
    public static double[] totalInstabilityUndir(DoubleMatrix2D xi, List<Node> vars){
        if (vars.size()!= xi.columns() || vars.size()!= xi.rows()) {
            throw new IllegalArgumentException("stability mat must have same number of rows and columns as there are vars");
        }

        Algebra al = new Algebra();
        //DoubleMatrix2D xiu = MGM.upperTri(xi.copy().assign(al.transpose(xi)),1);

        DoubleMatrix2D xiu = xi.copy().assign(xi.copy().assign(Functions.minus(1.0)), Functions.mult).assign(Functions.mult(-2.0));

        double[] D = new double[4];
        int[] discInds = MixedUtils.getDiscreteInds(vars);
        int[] contInds = MixedUtils.getContinuousInds(vars);
        int p = contInds.length;
        int q = discInds.length;
        double temp = MGM.upperTri(xiu.copy(),1).zSum();
        D[0] = temp/ ((p+q-1.0)*(p+q)/2.0);
        temp = MGM.upperTri(xiu.viewSelection(contInds, contInds).copy(), 1).zSum();
        D[1] = temp/(p*(p-1.0)/2.0);
        temp = xiu.viewSelection(contInds, discInds).zSum();
        D[2] = temp/(p*q);
        temp = MGM.upperTri(xiu.viewSelection(discInds, discInds).copy(), 1).zSum();
        D[3] = temp/(q*(q-1.0)/2.0);

        return D;
    }

    //array of averages of instability matrix over [all, cc, cd, dd] edges
    //TODO directed version
    public static double[] totalInstabilityDir(DoubleMatrix2D xi, List<Node> vars){
        if (vars.size()!= xi.columns() || vars.size()!= xi.rows()) {
            throw new IllegalArgumentException("stability mat must have same number of rows and columns as there are vars");
        }

        double[] D = new double[4];
        int[] discInds = MixedUtils.getDiscreteInds(vars);
        int[] contInds = MixedUtils.getContinuousInds(vars);
        int p = contInds.length;
        int q = discInds.length;
        D[0] = xi.zSum()/ ((p+q-1)*(p+q)/2);

        D[1] = xi.viewSelection(contInds, contInds).zSum()/(p*(p-1));
        D[2] = xi.viewSelection(contInds, discInds).zSum()/(p*q);
        D[3] = xi.viewSelection(discInds, discInds).zSum()/(q*(q-1));

        return D;
    }

    //returns an numSub by subSize matrix of subsamples of the sequence 1:sampSize
    public static int[][] subSampleNoReplacement(int sampSize, int subSize, int numSub){

        if (subSize < 1) {
            throw new IllegalArgumentException("Sample size must be > 0.");
        }

        List<Integer> indices = new ArrayList<Integer>(sampSize);
        for (int i = 0; i < sampSize; i++) {
            indices.add(i);
        }

        int[][] sampMat = new int[numSub][subSize];

        for(int i = 0; i < numSub; i++) {
            Collections.shuffle(indices);
            int[] curSamp;
            SAMP:
            while(true){
                curSamp = subSampleIndices(sampSize, subSize);
                for(int j = 0; j < i; j++){
                    if(Arrays.equals(curSamp, sampMat[j])){
                        continue SAMP;
                    }
                }
                break;
            }
            sampMat[i] = curSamp;
        }

        return sampMat;
    }

    private static int[] subSampleIndices(int N, int subSize){
        List<Integer> indices = new ArrayList<Integer>(N);
        for (int i = 0; i < N; i++) {
            indices.add(i);
        }

        Collections.shuffle(indices);
        int[] samp = new int[subSize];
        for(int i = 0; i < subSize; i++){
            samp[i] = indices.get(i);
        }
        return samp;
    }



    //some tests...
    public static void main(String[] args){
        String fn = "/Users/ajsedgewick/tetrad_mgm_runs/run2/networks/DAG_0_graph.txt";
        Graph trueGraph = GraphUtils.loadGraphTxt(new File(fn));
        DataSet ds = null;
        try {
            ds = MixedUtils.loadData("/Users/ajsedgewick/tetrad_mgm_runs/run2/data/", "DAG_0_data.txt");
        } catch (Throwable t) {t.printStackTrace();}

        double lambda = .1;
        SearchWrappers.MGMWrapper mgm = new SearchWrappers.MGMWrapper(new double[]{lambda, lambda, lambda});
        long start = System.currentTimeMillis();
        DoubleMatrix2D xi = StabilitySearch(ds, mgm, 8, 200);
        long end = System.currentTimeMillis();
        System.out.println("Not parallel: " + ((end-start)/1000.0));

        start = System.currentTimeMillis();
        DoubleMatrix2D xi2 = StabilitySearchPar(ds, mgm, 8, 200);
        end = System.currentTimeMillis();
        System.out.println("Parallel: " + ((end-start)/1000.0));

        System.out.println(xi);
        System.out.println(xi2);
    }
}

