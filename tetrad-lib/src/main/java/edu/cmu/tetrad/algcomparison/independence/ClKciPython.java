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

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.ProcessPythonCiService;
import edu.cmu.tetrad.search.test.PythonKciIndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for KCI test implemented via Python (causal-learn).
 */
@TestOfIndependence(
        name = "KCI, Causal Learn (Python)",
        command = "kci-cl-test",
        dataType = DataType.Continuous
)
@General
public class ClKciPython implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    public ClKciPython() {
    }

    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException(
                    "ClKciPython requires a DataSet (continuous). Got: " +
                            (dataModel == null ? "null" : dataModel.getClass().getName())
            );
        }

        String pythonExe = "/Users/josephramsey/venvs/kci/bin/python";  // or absolute path if needed
        String serverScriptPath = "/Users/josephramsey/IdeaProjects/py-tetrad/pytetrad/tools/kci_server.py";

        // Service that launches / talks to the Python-side CI server.
        ProcessPythonCiService service = new ProcessPythonCiService(
                pythonExe, serverScriptPath

        );

        PythonKciIndependenceTest test = new PythonKciIndependenceTest(dataSet, service);

        // Respect the standard alpha parameter if present.
        // (PythonKciIndependenceTest implements setAlpha/getAlpha.)
        if (parameters != null) {
            double alpha = parameters.getDouble(Params.ALPHA, 0.01);
            test.setAlpha(alpha);
        }

        return test;
    }

    @Override
    public String getDescription() {
        return "KCI-CL (Python)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        // Use the standard alpha parameter, so algcomparison UI + scripts can set it.
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        return params;
    }
}