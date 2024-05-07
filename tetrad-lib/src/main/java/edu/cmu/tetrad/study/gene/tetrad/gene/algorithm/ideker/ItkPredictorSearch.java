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

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.ideker;

import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class contains methods which implement the algorithm described in the paper "  " by Ideker, Thorsen and Karp.
 *
 * @author Frank Wimberly
 * @version $Id: $Id
 */
public class ItkPredictorSearch {

    /**
     * The number of genes.
     */
    int ngenes;

    /**
     * The number of rows in the expression matrix.
     */
    int nrows;

    /**
     * The names of the genes.
     */
    String[] names;

    /**
     * The expression matrix.
     */
    int[][] expression;

    /**
     * <p>Constructor for ItkPredictorSearch.</p>
     *
     * @param ngenes     a int
     * @param expression an array of {@link int} objects
     * @param names      an array of {@link java.lang.String} objects
     */
    public ItkPredictorSearch(int ngenes, int[][] expression, String[] names) {

        this.ngenes = ngenes;
        this.expression = expression;
        this.names = names;
        this.nrows = expression.length;

    }

    /**
     * <p>predictor.</p>
     *
     * @param gene a int
     */
    public void predictor(int gene) {

        SortedSet[][] S = new TreeSet[this.nrows][this.nrows];
        Gene[] G = new Gene[this.ngenes];

        System.out.println("For gene " + this.names[gene] + ":");

        for (int i = 0; i < this.nrows; i++) {
            for (int j = 0; j < this.nrows; j++) {
                S[i][j] = new TreeSet();
            }
        }

        for (int k = 0; k < this.ngenes; k++) {
            G[k] = new Gene(k);
        }

        //Consider all pairs of rows of the expression matrix.
        ChoiceGenerator cg = new ChoiceGenerator(this.ngenes, 2);
        int[] rows;

        while ((rows = cg.next()) != null) {

            //Exclude row pairs in which the given gene was perturbed.
            if (this.expression[rows[0]][gene] == -1 ||
                this.expression[rows[0]][gene] == 2 ||
                this.expression[rows[1]][gene] == -1 ||
                this.expression[rows[1]][gene] == 2) {
                continue;
            }

            //Exclude row pairs were the level of the given gene does not
            //differ between the two rows (perturbations).
            if (!differByPerturbation(gene, rows[0], rows[1])) {
                continue;
            }

            //Find the set of other genes whose expression level differs
            //between the two perturbations.
            for (int gother = 0; gother < this.ngenes; gother++) {
                if (gother == gene) {
                    continue;  //Don't test this gene
                }
                //Exclude genes which do not differ between the two rows
                if (!differByPerturbation(gother, rows[0], rows[1])) {
                    continue;
                }
                S[rows[0]][rows[1]].add(G[gother]);
            }

            System.out.print("sem" + rows[0] + rows[1] + " = ");
            for (Object o : S[rows[0]][rows[1]]) {
                System.out.print(((Gene) o).getIndex());
            }
            System.out.println();

        }

        int sum = 0;
        for (int i = 0; i < this.nrows; i++) {
            for (int j = 0; j < this.nrows; j++) {
                sum += S[i][j].size();
            }
        }

        if (sum == 0) {
            System.out.println(
                    "Insufficient perturbations for gene " + this.names[gene]);
            System.out.println();
            return;
        }

        System.out.println("Smin:");
        SortedSet[] minCover;
        minCover = minCoveringSet(S);
        for (SortedSet<Gene> aMinCover : minCover) {
            display(aMinCover);
            inferFunction(gene, aMinCover);
        }
        System.out.println();
    }

    /**
     * <p>minCoveringSet.</p>
     *
     * @param sets an array of {@link java.util.SortedSet} objects
     * @return an array of {@link java.util.SortedSet} objects
     */
    public SortedSet[] minCoveringSet(SortedSet[][] sets) {

        SortedSet<Gene> union = new TreeSet<>();

        //Compute the union of all input sets
        for (SortedSet<Gene>[] set : sets) {
            for (int j = 0; j < sets[0].length; j++) {
                union.addAll(set[j]);
            }
        }

        //System.out.println("UNION");
        //display(union);

        //Compute the power set of the union
        int total = union.size();
        int sizePowerSet = 1;
        for (int i = 0; i < total; i++) {
            sizePowerSet *= 2;
        }

        //System.out.println("Size of power set= " + sizePowerSet);

        Gene[] geneArray = new Gene[total];
        int k = 0;
        for (Gene anUnion : union) {
            geneArray[k] = anUnion;
            k++;
        }
        //geneArray = (Gene[]) union.toArray();

        int[] indexArray = new int[total];
        for (int i = 0; i < total; i++) {
            indexArray[i] = geneArray[i].getIndex();
        }

        int[] sizes = new int[sizePowerSet];
        boolean[] covers = new boolean[sizePowerSet];
        sizes[0] = 0;
        covers[0] = false;
        int minSize = 100;

        for (int subSetIndex = 1; subSetIndex < sizePowerSet; subSetIndex++) {
            byte[] bool = booleanRepresentation(subSetIndex, total);
            //System.out.println("bool = " + bool[0] + bool[1] + bool[2]);
            SortedSet<Gene> subSet = new TreeSet<>();
            for (int i = 0; i < total; i++) {
                if (bool[i] == 1) {
                    subSet.add(geneArray[i]);
                }
            }

            sizes[subSetIndex] = subSet.size();
            if (sizes[subSetIndex] > minSize) {
                continue;
            }

            covers[subSetIndex] = true;
            for (SortedSet[] set : sets) {
                for (int j = 0; j < sets[0].length; j++) {
                    if (set[j].isEmpty()) {
                        continue;
                    }
                    if (!covered(set[j], subSet)) {
                        covers[subSetIndex] = false;
                    }
                }
            }

            if (!covers[subSetIndex]) {
                continue;
            }

            if (sizes[subSetIndex] < minSize) {
                minSize = sizes[subSetIndex];
            }
        }

        //System.out.println("Size of min covering set = " + minSize);

        int numCoveringSets = 0;
        for (int i = 0; i < sizePowerSet; i++) {
            if (sizes[i] == minSize && covers[i]) {
                numCoveringSets++;
            }
        }

        SortedSet[] coveringSets = new SortedSet[numCoveringSets];
        int number = 0;
        for (int i = 0; i < sizePowerSet; i++) {
            if (sizes[i] == minSize && covers[i]) {
                byte[] bool = booleanRepresentation(i, total);
                SortedSet<Gene> subSet = new TreeSet<>();
                for (int j = 0; j < total; j++) {
                    if (bool[j] == 1) {
                        subSet.add(geneArray[j]);
                    }
                }
                coveringSets[number] = subSet;
                number++;
            }
        }

        return coveringSets;
    }

    /**
     * <p>inferFunction.</p>
     *
     * @param g a int
     * @param s a {@link java.util.SortedSet} object
     */
    public void inferFunction(int g, SortedSet<Gene> s) {

        int n = s.size();
        int[] ss = new int[n];

        int c = 0;
        for (Gene value : s) {
            ss[c] = value.getIndex();
            c++;
        }

        int twoToN = 1;
        for (int i = 0; i < n; i++) {
            twoToN *= 2;
        }

        int[] f = new int[twoToN];
        for (int i = 0; i < twoToN; i++) {
            f[i] = 9;
        }

        for (Gene value1 : s) {
            System.out.print(this.names[value1.getIndex()] + " ");
        }
        System.out.println("f");

        for (int i = twoToN - 1; i >= 0; i--) {
            byte[] b = booleanRepresentation(i, n);

            row:
            for (int j = 0; j < this.nrows; j++) {
                if (this.expression[j][g] == -1 || this.expression[j][g] == 2) {
                    continue;
                }
                for (int k = 0; k < n; k++) {
                    //if(expression[j][ss[k]] != b[k]) continue row;
                    if (differExpressions(this.expression[j][ss[k]], b[k])) {
                        continue row;
                    }
                }
                f[i] = this.expression[j][g];
            }

            for (int k = 0; k < n; k++) {
                System.out.print(b[k] + "  ");
            }
            System.out.println(f[i]);
        }
    }

    //Returns true of b covers a, false otherwise

    /**
     * <p>covered.</p>
     *
     * @param a a {@link java.util.SortedSet} object
     * @param b a {@link java.util.SortedSet} object
     * @return a boolean
     */
    public boolean covered(SortedSet<Gene> a, SortedSet<Gene> b) {
        boolean result = false;
        for (Gene anA : a) {
            if (b.contains(anA)) {
                result = true;
                return result;
            }
        }
        return result;
    }

    /**
     * <p>display.</p>
     *
     * @param s a {@link java.util.SortedSet} object
     */
    public void display(SortedSet<Gene> s) {
        for (Gene value : s) {
            System.out.print(value.getIndex() + " ");
        }
        System.out.println();
    }

    /**
     * This method determines whether the levels for a given gene differ between two perturbations p0 and p1 (rows of
     * the perturbation matrix).  It returns true if they do differ and false otherwise.
     *
     * @param gene a int
     * @param p0   a int
     * @param p1   a int
     * @return a boolean
     */
    public boolean differByPerturbation(int gene, int p0, int p1) {
        return !(this.expression[p0][gene] == this.expression[p1][gene] ||
                 (this.expression[p0][gene] == -1 && this.expression[p1][gene] == 0) ||
                 (this.expression[p1][gene] == -1 && this.expression[p0][gene] == 0) ||
                 (this.expression[p0][gene] * this.expression[p1][gene] == 2));
    }

    /**
     * <p>differExpressions.</p>
     *
     * @param e1 a int
     * @param e2 a int
     * @return a boolean
     */
    public boolean differExpressions(int e1, int e2) {
        return true;
    }

    /**
     * Computes a byte vector which corresponds to the argument ind.  rep[0] is the high order bit. E.g.  if n=3 and
     * ind=6 the vector will be (1, 1, 0).
     *
     * @param ind a int
     * @param n   a int
     * @return an array of {@link byte} objects
     */
    public byte[] booleanRepresentation(int ind, int n) {
        byte[] rep = new byte[n];

        for (int i = 0; i < n; i++) {
            rep[i] = (byte) 0;
        }

        for (int i = 0; i < n; i++) {
            int rem = ind % 2;
            if (rem == 1) {
                rep[n - i - 1] = (byte) 1;
                ind -= 1;
            }
            ind /= 2;
        }

        return rep;
    }

    /**
     * A gene.
     */
    public static class Gene implements Comparable {

        /**
         * The index of the gene.
         */
        int gene;

        /**
         * <p>Constructor for Gene.</p>
         *
         * @param gene a int
         */
        public Gene(int gene) {
            this.gene = gene;
        }

        /**
         * <p>getIndex.</p>
         *
         * @return a int
         */
        public int getIndex() {
            return this.gene;
        }

        /**
         * {@inheritDoc}
         */
        public int compareTo(Object o) {
            int ret;
            if (this.gene < ((Gene) o).getIndex()) {
                ret = -1;
            } else if (this.gene == ((Gene) o).getIndex()) {
                ret = 0;
            } else {
                ret = 1;
            }
            return ret;
        }
    }

}





