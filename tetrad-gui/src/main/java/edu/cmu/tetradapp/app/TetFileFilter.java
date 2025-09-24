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

package edu.cmu.tetradapp.app;

import javax.swing.filechooser.FileFilter;
import java.io.File;

/**
 * Filters out all but .tet file when loading and saving.
 *
 * @author josephramsey
 */
final class TetFileFilter extends FileFilter {

    /**
     * {@inheritDoc}
     * <p>
     * Accepts a file if its name ends with ".tet".
     */
    public boolean accept(File file) {
        return file.isDirectory() || file.getName().endsWith(".tet");
    }

    /**
     * <p>getDescription.</p>
     *
     * @return the description of this file filter that will be displayed in a JFileChooser.
     */
    public String getDescription() {
        return "Tetrad serialized session workbench (.tet)";
    }
}






