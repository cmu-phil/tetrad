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

package edu.cmu.tetradapp.util;

import javax.swing.*;

/**
 * Extends JComboBox so that the items it contains are automatically sorted as they are added.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SortingComboBox extends JComboBox {

    /**
     * {@inheritDoc}
     * <p>
     * Overrides the addItem() method of JComboBox to automatically sort items as they are added.  Items are sorted by
     * the strings they return in their toString() methods.
     */
    public void addItem(Object anItem) {

        String name = anItem.toString();

        for (int i = 0; i < getItemCount(); i++) {
            Object o = getItemAt(i);
            String oName = o.toString();

            if (oName.compareTo(name) > 0) {
                insertItemAt(anItem, i);

                return;
            }
        }

        super.addItem(anItem);
    }
}






