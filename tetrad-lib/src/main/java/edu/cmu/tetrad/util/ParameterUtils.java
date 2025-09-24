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

import java.util.List;

/**
 * A utility to create/modify parameters.
 * <p>
 * Jun 4, 2019 4:35:51 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class ParameterUtils {

    private ParameterUtils() {
    }

    /**
     * Create parameters with their default values.
     *
     * @param names list of parameter names
     * @return Parameters object containing parameters from the given parameter name list
     */
    public static Parameters create(List<String> names) {
        Parameters parameters = new Parameters();
        ParamDescriptions paramDescs = ParamDescriptions.getInstance();
        names.forEach(name -> {
            ParamDescription paramDesc = paramDescs.get(name);
            parameters.set(name, paramDesc.getDefaultValue());
        });

        return parameters;
    }

}

