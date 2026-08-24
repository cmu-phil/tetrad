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

package edu.cmu.tetradapp.editor.search;

import edu.cmu.tetradapp.ui.model.AlgorithmModel;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * What {@code GeneralAlgorithmEditor} needs from the first card of the search box: a way to know which algorithm (with
 * test and score) the user picked, and hooks to persist and restore that choice. Implemented by the classic
 * {@link AlgorithmCard} and by {@link GuidedAlgorithmCard}. Added 2026-8-24.
 * <p>
 * Implementations fire a {@code "algoFwdBtn"} property change with a Boolean value to enable or disable the editor's
 * forward button.
 */
public interface AlgorithmChooser {

    /**
     * @return the currently selected algorithm model, or null if none.
     */
    AlgorithmModel getSelectedAlgorithm();

    /**
     * Re-reads the persisted selections from the runner and updates the display.
     */
    void refresh();

    /**
     * Writes the current selections to the runner so they survive save/reload.
     */
    void saveStates();

    /**
     * Checks that the selection is complete (test and score present where required), tells the user if not, and on
     * success installs the configured algorithm on the runner.
     *
     * @return true if the editor may advance to the parameter card.
     */
    boolean isAllValid();

    /**
     * @return this chooser as a Swing component for placement in the editor.
     */
    JComponent asComponent();

    /**
     * @param listener listener for the {@code "algoFwdBtn"} property.
     */
    void addPropertyChangeListener(PropertyChangeListener listener);
}
