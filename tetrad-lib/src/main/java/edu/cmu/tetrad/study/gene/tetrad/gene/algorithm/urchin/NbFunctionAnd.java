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

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.urchin;


/**
 * <p>NbFunctionAnd class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NbFunctionAnd extends AbstractNbComponent {
    /**
     * <p>Constructor for NbFunctionAnd.</p>
     *
     * @param factor        a double
     * @param power         a double
     * @param parents       an array of {@link edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.urchin.NbComponent}
     *                      objects
     * @param inhibitExcite an array of  objects
     * @param name          a {@link java.lang.String} object
     */
    public NbFunctionAnd(double factor, double power, NbComponent[] parents,
                         int[] inhibitExcite, String name) {

        super(factor, power, parents, inhibitExcite, name);
    }

    /**
     * <p>update.</p>
     */
    public void update() {
        //System.out.println("Updating " + name);
        double product = 1.0;
        for (int i = 0; i < getNparents(); i++) {
            double v = getParents()[i].getValue();
            if (getInhibitExcite()[i] > 0) {
                product *= v;
            } else {
                product *= (1.0 - v);
            }
        }
        setValue(product * getFactor());
        //System.out.println("Value = " + value);
    }
}






