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

package edu.cmu.tetrad.util;

import javax.swing.*;
import java.awt.*;

/**
 * Stores some utility items for displaying JOptionPane messages.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class JOptionUtils {
    private static JComponent COMPONENT;

    /**
     * Private constructor to prevent instantiation.
     */
    private JOptionUtils() {
    }

    /**
     * Sets the centering component used throughout. May be null.
     *
     * @param component Ibid.
     */
    public static void setCenteringComp(JComponent component) {
        JOptionUtils.COMPONENT = component;
    }

    /**
     * <p>centeringComp.</p>
     *
     * @return Ibid.
     */
    public static JComponent centeringComp() {
        return JOptionUtils.COMPONENT;
    }

    /**
     * <p>getCenteringFrame.</p>
     *
     * @return a {@link java.awt.Frame} object
     */
    public static Frame getCenteringFrame() {
        for (Container c = JOptionUtils.COMPONENT; c != null; c = c.getParent()) {
            if (c instanceof Frame) {
                return (Frame) c;
            }
        }

        return null;
    }
}






