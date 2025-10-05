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


import edu.cmu.tetrad.util.Version;

/**
 * Some utilities for loading license-related files and making sure they get the right versions stamped in them.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class LicenseUtils {
    /**
     * <p>copyright.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public static String copyright() {
        String copyright =
                FileLoadingUtils.fromResources("/resources/copyright");
        String currentVersion = Version.currentViewableVersion().toString();
        copyright = copyright.replaceAll("VERSION", currentVersion);
        return copyright;
    }

    /**
     * <p>license.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public static String license() {
        return FileLoadingUtils.fromResources("/resources/license");
    }
}




