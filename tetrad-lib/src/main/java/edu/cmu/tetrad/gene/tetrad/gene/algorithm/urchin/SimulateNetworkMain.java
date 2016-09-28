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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.urchin;


public class SimulateNetworkMain {
    public static void main(String[] argv) {

        double[] inputs = new double[4];
        inputs[1] = 0.0;

        /*
        for(int i0 = 0; i0 < 11; i0++)
          for(int i2 = 0; i2 < 11; i2++)
            for(int i3 = 0; i3 < 11; i3++) {
              inputs[0] = 0.5 + i0*0.1;
              inputs[2] = 0.5 + i2*0.1;
              inputs[3] = 0.5 + i3*0.1;
              NetBuilderModel nbm = new NetBuilderModel(inputs, 10);
        }
        */

        inputs[0] = 1.0;
        inputs[2] = 1.0;
        inputs[3] = 1.0;

        NetBuilderModel nbm = new NetBuilderModel(inputs, 401);
    }
}




