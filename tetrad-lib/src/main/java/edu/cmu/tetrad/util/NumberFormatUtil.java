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

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.prefs.Preferences;

/**
 * Provides an application-wide "memory" of the number format to be used.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumberFormatUtil {
    private static final NumberFormatUtil INSTANCE = new NumberFormatUtil();
    private NumberFormat nf;

    private NumberFormatUtil() {

        try {
            this.nf = new DecimalFormat(Preferences.userRoot().get("numberFormat", "0.0000"));
        } catch (Exception e) {
            this.nf = new DecimalFormat("0.0000");
        }

    }

    /**
     * <p>getInstance.</p>
     *
     * @return Ibid.
     */
    public static NumberFormatUtil getInstance() {
        return NumberFormatUtil.INSTANCE;
    }

    /**
     * <p>getNumberFormat.</p>
     *
     * @return Ibid.
     */
    public NumberFormat getNumberFormat() {
        return this.nf;
    }

    /**
     * Sets the number format, <code>nf</code>.
     *
     * @param nf Ibid.
     * @throws java.lang.NullPointerException if nf is null.
     */
    public void setNumberFormat(NumberFormat nf) {
        if (nf == null) {
            throw new NullPointerException();
        }

        this.nf = nf;
    }
}




