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

import edu.cmu.tetrad.util.RandomUtil;

public class NbGeneAnd extends AbstractNbComponent {
    public NbGeneAnd(double factor, double power, NbComponent[] parents,
            int[] inhibitExcite, String name, double sd) {
        super(factor, power, parents, inhibitExcite, name);
    }

    public void update() {
        //System.out.println("Updating " + name);
        double product = 1.0;
        for (int i = 0; i < getNparents(); i++) {
            double v = getParents()[i].getValue();
            if (getInhibitExcite()[i] > 0) {
                product *= v / (v + 1.0);
            }
            else {
                product *= (1.0 - (v / (v + 1.0)));
            }
        }
        setValue(product * getFactor());

        if (getSd() == 0.0) {
            return;
        }
        else {
            double r = 1.0 +
                    RandomUtil.getInstance().nextNormal(0, 1) * getSd();
            setValue(getValue() * r);
        }
    }
}





