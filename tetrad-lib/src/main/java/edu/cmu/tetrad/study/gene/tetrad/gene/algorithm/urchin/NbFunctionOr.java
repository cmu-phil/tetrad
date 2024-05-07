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

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.urchin;


/**
 * <p>NbFunctionOr class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NbFunctionOr extends AbstractNbComponent {
    /**
     * <p>Constructor for NbFunctionOr.</p>
     *
     * @param factor        a double
     * @param power         a double
     * @param parents       an array of {@link edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.urchin.NbComponent}
     *                      objects
     * @param inhibitExcite an array of {@link int} objects
     * @param name          a {@link java.lang.String} object
     */
    public NbFunctionOr(double factor, double power, NbComponent[] parents,
                        int[] inhibitExcite, String name) {

        super(factor, power, parents, inhibitExcite, name);
        if (parents.length < 2) {
            System.out.println(
                    "Exception:  Or function must have >= 2 inputs.");
            System.exit(0);
        }
    }

    /**
     * <p>update.</p>
     */
    public void update() {
        //System.out.println("Updating " + name);
        double sum = 0.0;

        if (getInhibitExcite()[0] > 0) {
            sum = getParents()[0].getValue();
        } else {
            sum = 1.0 - getParents()[0].getValue();
        }

        for (int i = 1; i < getNparents(); i++) {
            double v = 0.0;
            if (getInhibitExcite()[i] > 0) {
                v = getParents()[i].getValue();
            } else {
                v = (1.0 - getParents()[i].getValue());
            }
            sum += v * (1.0 - sum);
        }
        setValue(sum * getFactor());
        //System.out.println("Value = " + value);
    }
}





