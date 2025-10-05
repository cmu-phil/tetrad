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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * May 10, 2019 2:31:48 PM
 *
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public class TestParamDescriptions {
    @Test
    public void testUndocumentedParams() {
        Set<String> allParams = Params.getParameters();

        List<String> undocumentedParams = new ArrayList<>();

        for (String param : allParams) {
            if (ParamDescriptions.getInstance().get(param) == null) {
                System.out.println("Undocumented parameter found in HTML manual: " + param);
                undocumentedParams.add(param);
            }
        }

        assertEquals(0, undocumentedParams.size());
    }

    @Test
    public void testUnsupportedParamsValueType() {
        List<String> paramsWithUnsupportedValueType = ParamDescriptions.getInstance().getParamsWithUnsupportedValueType();

        paramsWithUnsupportedValueType.forEach(e -> {
            System.out.println("Unsupported parameter value type found in HTML manual for: " + e);
        });

        // Require all params in edu.cmu.tetrad.util.Params have value type specified in HTML manual
        // Except the system paramters, like printStream
        assertEquals(0, paramsWithUnsupportedValueType.size());
    }
}

