/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import edu.cmu.tetradapp.Tetrad;

import javax.swing.*;
import java.io.Serial;

/**
 * A checkbox that lets one editor include algorithms, tests, or scores marked {@code @Experimental} in its own lists,
 * without changing the global "show experimental everywhere" preference. Added 2026-8-24.
 * <p>
 * The initial state is the global preference ({@link Tetrad#enableExperimental}), so an editor opened by a user who
 * has turned experimental on everywhere starts with it on. Changing this box affects only the editor that owns it.
 */
public class ExperimentalToggle extends JCheckBox {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the toggle.
     *
     * @param onChange run (on the EDT) after the state changes; typically repopulates the owning editor's lists.
     */
    public ExperimentalToggle(Runnable onChange) {
        super("Include experimental");
        setSelected(Tetrad.enableExperimental);
        setToolTipText("<html><div style='width:300px'>Also list algorithms, tests, and scores that are marked "
                       + "experimental in the registry. This affects only this editor. To show them everywhere, "
                       + "use File > Settings > Show experimental algorithms everywhere.</div></html>");
        addActionListener(e -> {
            if (onChange != null) onChange.run();
        });
    }

    /**
     * @return true if experimental entries should be listed in the owning editor.
     */
    public boolean includeExperimental() {
        return isSelected();
    }
}
