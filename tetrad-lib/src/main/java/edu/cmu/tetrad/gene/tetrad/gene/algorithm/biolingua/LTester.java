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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.biolingua;

import edu.cmu.tetrad.gene.tetrad.gene.algorithm.util.*;

/**
 * Class that makes some very simple tests
 * on the classes LtMatrix, Graph, and Biolingua<p>
 * <p>
 * TODO: make it a junit testing class
 *
 * @author Raul Saavedra, rsaavedr@ai.uwf.edu
 */
public class LTester {
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




