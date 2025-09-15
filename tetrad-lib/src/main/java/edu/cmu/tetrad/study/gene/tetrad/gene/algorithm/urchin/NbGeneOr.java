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

import edu.cmu.tetrad.util.RandomUtil;

/**
 * <p>NbGeneOr class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NbGeneOr extends AbstractNbComponent {
    /**
     * <p>Constructor for NbGeneOr.</p>
     *
     * @param factor        a double
     * @param power         a double
     * @param parents       an array of {@link edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.urchin.NbComponent}
     *                      objects
     * @param inhibitExcite an array of  objects
     * @param name          a {@link java.lang.String} object
     * @param sd            a double
     */
    public NbGeneOr(double factor, double power, NbComponent[] parents,
                    int[] inhibitExcite, String name, double sd) {
        super(factor, power, parents, inhibitExcite, name);
    }

    /**
     * <p>update.</p>
     */
    public void update() {
        //System.out.println("Updating " + name);
        double sum = 0.0;

        if (getInhibitExcite()[0] > 0) {
            sum = getParents()[0].getValue() /
                  (getParents()[0].getValue() + 1.0);
        } else {
            sum = 1.0 - (getParents()[0].getValue() /
                         (getParents()[0].getValue() + 1.0));
        }

        for (int i = 1; i < getNparents(); i++) {
            double v = getParents()[i].getValue();
            if (getInhibitExcite()[i] > 0) {
                sum += v / (v + 1.0) * (1.0 - sum);
            } else {
                sum += (1.0 - (v / (v + 1.0))) * (1.0 - sum);
            }
        }
        setValue(sum * getFactor());

        if (getSd() == 0.0) {
        } else {
            double r = 1.0 +
                       RandomUtil.getInstance().nextGaussian(0, 1) * getSd();
            setValue(getValue() * r);
        }

    }
}






