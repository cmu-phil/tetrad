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

    /**
     * Default constructor for the ClKciPython class.
     *
     * This constructor initializes an instance of the ClKciPython class, which serves
     * as a wrapper for the Kernel-based Conditional Independence (KCI) test implemented
     * using causal-learn in Python. This test is specifically designed to work with
     * continuous data.
     */
    public ClKciPython() {
    }

    /**
     * Creates and returns an independence test based on the Kernel-based Conditional Independence
     * (KCI) test implemented in Python using the causal-learn library.
     *
     * @param dataModel    The data model to be used with the independence test. Must be an
     *                     instance of {@code DataSet} containing continuous data.
     * @param parameters   The parameters to configure the independence test, such as the
     *                     significance level (alpha). Can be {@code null}.
     * @return             An instance of {@code IndependenceTest} setup for the KCI method.
     * @throws IllegalArgumentException If the provided {@code dataModel} is not an instance of
     *                                  {@code DataSet}.
     */
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

    /**
     * Provides a textual description of the KCI-CL (Kernel-based Conditional Independence
     * with Causal-Learn in Python) method.
     *
     * @return A string representation of the method description, specifically "KCI-CL (Python)".
     */
    @Override
    public String getDescription() {
        return "KCI-CL";
    }

    /**
     * Returns the type of data handled by this method, which is continuous.
     *
     * @return The data type, represented as {@code DataType.Continuous}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameters used by the KCI-CL (Kernel-based Conditional Independence
     * with Causal-Learn in Python) method.
     *
     * @return A list of parameter names required by this method, specifically including
     *         the alpha parameter for configuring the significance level.
     */
    @Override
    public List<String> getParameters() {
        // Use the standard alpha parameter, so algcomparison UI + scripts can set it.
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        return params;
    }
}