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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.ideker;

import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class contains methods which implement the algorithm described in the
 * paper "  " by Ideker, Thorsen and Karp.
 *
 * @author Frank Wimberly
 */
public class ItkPredictorSearch {
    int ngenes;
    int nrows;
    String[] names;
    int[][] expression;

    public class Gene implements Comparable {
        int gene;

        public Gene(final int gene) {
            this.gene = gene;
        }

        public int getIndex() {
            return this.gene;
        }

        public int compareTo(final Object o) {
            final int ret;
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

    public ItkPredictorSearch(final int ngenes, final int[][] expression, final String[] names) {

        this.ngenes = ngenes;
        this.expression = expression;
        this.names = names;
        this.nrows = expression.length;

    }

    public void predictor(final int gene) {

        final SortedSet[][] S = new TreeSet[this.nrows][this.nrows];
        final Gene[] G = new Gene[this.ngenes];

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
        final ChoiceGenerator cg = new ChoiceGenerator(this.ngenes, 2);
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
            for (final Iterator it = S[rows[0]][rows[1]].iterator(); it.hasNext(); ) {
                System.out.print(((Gene) it.next()).getIndex());
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
        final SortedSet[] minCover;
        minCover = minCoveringSet(S);
        for (final SortedSet<Gene> aMinCover : minCover) {
            display(aMinCover);
            inferFunction(gene, aMinCover);
        }
        System.out.println();
    }

    public SortedSet[] minCoveringSet(final SortedSet[][] sets) {

        final SortedSet<Gene> union = new TreeSet<>();

        //Compute the union of all input sets
        for (final SortedSet<Gene>[] set : sets) {
            for (int j = 0; j < sets[0].length; j++) {
                union.addAll(set[j]);
            }
        }

        //System.out.println("UNION");
        //display(union);

        //Compute the power set of the union
        final int total = union.size();
        int sizePowerSet = 1;
        for (int i = 0; i < total; i++) {
            sizePowerSet *= 2;
        }

        //System.out.println("Size of power set= " + sizePowerSet);

        final Gene[] geneArray = new Gene[total];
        int k = 0;
        for (final Gene anUnion : union) {
            geneArray[k] = anUnion;
            k++;
        }
        //geneArray = (Gene[]) union.toArray();

        final int[] indexArray = new int[total];
        for (int i = 0; i < total; i++) {
            indexArray[i] = geneArray[i].getIndex();
        }

        final int[] sizes = new int[sizePowerSet];
        final boolean[] covers = new boolean[sizePowerSet];
        sizes[0] = 0;
        covers[0] = false;
        int minSize = 100;

        for (int subSetIndex = 1; subSetIndex < sizePowerSet; subSetIndex++) {
            final byte[] bool = booleanRepresentation(subSetIndex, total);
            //System.out.println("bool = " + bool[0] + bool[1] + bool[2]);
            final SortedSet<Gene> subSet = new TreeSet<>();
            for (int i = 0; i < total; i++) {
                if (bool[i] == 1) {
                    subSet.add(geneArray[i]);
                }
            }

            //System.out.println("index = " + subSetIndex + " subset = ");
            //display(subSet);

            sizes[subSetIndex] = subSet.size();
            if (sizes[subSetIndex] > minSize) {
                continue;
            }

            covers[subSetIndex] = true;
            for (final SortedSet[] set : sets) {
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

        final SortedSet[] coveringSets = new SortedSet[numCoveringSets];
        int number = 0;
        for (int i = 0; i < sizePowerSet; i++) {
            if (sizes[i] == minSize && covers[i]) {
                final byte[] bool = booleanRepresentation(i, total);
                final SortedSet<Gene> subSet = new TreeSet<>();
                for (int j = 0; j < total; j++) {
                    if (bool[j] == 1) {
                        subSet.add(geneArray[j]);
                    }
                }
                coveringSets[number] = subSet;
                number++;
            }
        }

        //for(int i = 0; i < coveringSets.length; i++) {
        //  display(coveringSets[i]);
        //}
        return coveringSets;
    }

    public void inferFunction(final int g, final SortedSet<Gene> s) {

        final int n = s.size();
        final int[] ss = new int[n];

        int c = 0;
        for (final Gene value : s) {
            ss[c] = value.getIndex();
            c++;
        }

        int twoToN = 1;
        for (int i = 0; i < n; i++) {
            twoToN *= 2;
        }

        final int[] f = new int[twoToN];
        for (int i = 0; i < twoToN; i++) {
            f[i] = 9;
        }

        for (final Gene value1 : s) {
            System.out.print(this.names[value1.getIndex()] + " ");
        }
        System.out.println("f");

        for (int i = twoToN - 1; i >= 0; i--) {
            final byte[] b = booleanRepresentation(i, n);

            row:
            for (int j = 0; j < this.nrows; j++) {
                if (this.expression[j][g] == -1 || this.expression[j][g] == 2) {
                    continue;
                }
                for (int k = 0; k < n; k++) {
                    //if(expression[j][ss[k]] != b[k]) continue row;
                    if (differExpressions(this.expression[j][ss[k]], (int) b[k])) {
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
    public boolean covered(final SortedSet<Gene> a, final SortedSet<Gene> b) {
        boolean result = false;
        for (final Gene anA : a) {
            if (b.contains(anA)) {
                result = true;
                return result;
            }
        }
        return result;
    }

    public void display(final SortedSet<Gene> s) {
        for (final Gene value : s) {
            System.out.print(value.getIndex() + " ");
        }
        System.out.println();
    }

    /**
     * This method determines whether the levels for a given gene differ between
     * two perturbations p0 and p1 (rows of the perturbation matrix).  It
     * returns true if they do differ and false otherwise.
     */
    public boolean differByPerturbation(final int gene, final int p0, final int p1) {
        return !(this.expression[p0][gene] == this.expression[p1][gene] ||
                (this.expression[p0][gene] == -1 && this.expression[p1][gene] == 0) ||
                (this.expression[p1][gene] == -1 && this.expression[p0][gene] == 0) ||
                (this.expression[p0][gene] * this.expression[p1][gene] == 2));
    }

    public boolean differExpressions(final int e1, final int e2) {
//        return !((e1 == e2) || (e1 == -1 && e2 == 0) || (e2 == -1 && e2 == 0) ||
//                (e1 * e2 == 2));
        return true;
    }

    /**
     * Computes a byte vector which corresponds to the argument ind.  rep[0] is
     * the high order bit. E.g.  if n=3 and ind=6 the vector will be (1, 1, 0).
     */
    public byte[] booleanRepresentation(int ind, final int n) {
        final byte[] rep = new byte[n];

        for (int i = 0; i < n; i++) {
            rep[i] = (byte) 0;
        }

        for (int i = 0; i < n; i++) {
            final int rem = ind % 2;
            if (rem == 1) {
                rep[n - i - 1] = (byte) 1;
                ind -= 1;
            }
            ind /= 2;
        }

        return rep;
    }

}





