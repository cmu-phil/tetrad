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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.LatentStructureRunner;
import edu.cmu.tetradapp.util.FinalizingEditor;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;

/**
 * âBlock Searchâ session node editor that reuses GeneralAlgorithmEditor.
 */
public class LatentStructureEditor extends JPanel implements FinalizingEditor {
    @Serial
    private static final long serialVersionUID = 1L;

    private final GeneralAlgorithmEditor delegate;

    /**
     * Constructs a LatentStructureEditor instance, which requires a LatentStructureRunner.
     *
     * @param runner the instance of LatentStructureRunner; must not be null
     */
    public LatentStructureEditor(LatentStructureRunner runner) {
        super(new BorderLayout());
        this.delegate = new GeneralAlgorithmEditor(runner);
        add(delegate, BorderLayout.CENTER);
    }

    /**
     * Finalizes the editor by delegating to the GeneralAlgorithmEditor.
     *
     * @return true if the editor was finalized successfully, false otherwise
     */
    @Override
    public boolean finalizeEditor() {
        return delegate.finalizeEditor();
    }
}
