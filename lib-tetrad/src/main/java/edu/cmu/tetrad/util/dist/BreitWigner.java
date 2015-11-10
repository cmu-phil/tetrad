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

package edu.cmu.tetrad.util.dist;
//
//import edu.cmu.tetrad.util.RandomUtil;
//
///**
// * Wraps a breitWigner distribution, allowing the values of its parameters to
// * be modified, generating random data.
// *
// * @author Joseph Ramsey
// */
//public class BreitWigner implements Distribution {
//    static final long serialVersionUID = 23L;
//
//    private double mean = 0;
//    private double gamma = 1;
//    private double cut = 2;
//
//    public BreitWigner(double mean, double gamma, double cut) {
//        this.mean = mean;
//        this.gamma = gamma;
//        this.cut = cut;
//    }
//
//    /**
//     * Generates a simple exemplar of this class to test serialization.
//     *
//     * @see edu.cmu.TestSerialization
//     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
//     */
//    @SuppressWarnings({"UnusedDeclaration"})
//    public static BreitWigner serializableInstance() {
//        return new BreitWigner(0, 1, 2);
//    }
//
//    public double nextRandom() {
//        return RandomUtil.getInstance().nextBreitWigner(mean, gamma, cut);
//    }
//
//    public void setParameter(int index, double value) {
//        if (index == 0) {
//            mean = value;
//        } else if (index == 1) {
//            gamma = value;
//        } else if (index == 2) {
//            cut = value;
//        } else {
//            throw new IllegalArgumentException("Illegal value: " + value);
//        }
//    }
//
//    public double getParameter(int index) {
//        if (index == 0) {
//            return mean;
//        } else if (index == 1) {
//            return gamma;
//        } else if (index == 2) {
//            return cut;
//        } else {
//            throw new IllegalArgumentException("Illegal index: " + index);
//        }
//    }
//
//    public String getParameterName(int index) {
//        if (index == 0) {
//            return "Mean";
//        } else if (index == 1) {
//            return "Gamma";
//        } else if (index == 2) {
//            return "Cut";
//        } else {
//            throw new IllegalArgumentException("Not a parameter index: " + index);
//        }
//    }
//
//    public int getNumParameters() {
//        return 2;
//    }
//
//
//    public String getName() {
//        return "Breit Wigner";
//    }
//}
//
//
//

