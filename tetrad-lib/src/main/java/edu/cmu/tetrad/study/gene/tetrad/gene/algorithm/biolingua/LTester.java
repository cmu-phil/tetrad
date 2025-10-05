///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.biolingua;

import edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.util.*;

/**
 * <p>Class that makes some very simple tests on the classes LtMatrix, Graph, and Biolingua</p>
 *
 * @author Raul Saavedra, rsaavedr@ai.uwf.edu
 * @version $Id: $Id
 */
public class LTester {

    /**
     * Private constructor.
     */
    private LTester() {
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        LTMatrix ltm;
        LTMatrixF ltmf;
        SymMatrix sm /*, cm*/;
        SymMatrixF smf /*, cmf*/;
        Matrix m;
        MatrixF mf;
        Digraph g;

        try {
            ltm = new LTMatrix("ltm.txt");
            ltmf = new LTMatrixF("ltm.txt");
            System.out.println(ltm);
            System.out.println(ltmf);

            sm = new SymMatrix("ltm.txt");
            smf = new SymMatrixF("ltm.txt");
            System.out.println(sm);
            System.out.println(smf);

            m = new Matrix("m.txt");
            mf = new MatrixF("m.txt");
            System.out.println(m);
            System.out.println(mf);

            g = new Digraph("g.txt");
            System.out.println(g);
        } catch (Exception xcp) {
            System.out.println("WATCH OUT!!!  There was an exception:");
            xcp.printStackTrace();
        }
    }

}





