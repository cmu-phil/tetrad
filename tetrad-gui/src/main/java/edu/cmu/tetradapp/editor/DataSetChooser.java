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

import edu.cmu.tetrad.data.DataModel;

import javax.swing.*;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A "Data set (of m)" drop-down for editors whose model can work from any one of several data sets (for instance,
 * the completed data sets of a multiple imputation). It always shows which data set is in use.
 *
 * @author josephramsey
 */
public final class DataSetChooser {

    private DataSetChooser() {
    }

    /**
     * Builds the chooser.
     *
     * @param models   the data sets to choose among, in order
     * @param selected the index of the one in use now
     * @param onChoose called with the index of a newly chosen data set; not called when the choice is unchanged
     * @return the chooser with its label, or null if there are fewer than two data sets, in which case there is
     * nothing to choose
     */
    public static Box create(List<? extends DataModel> models, int selected, IntConsumer onChoose) {
        if (models == null || models.size() < 2) return null;

        String[] labels = new String[models.size()];

        for (int i = 0; i < models.size(); i++) {
            String name = models.get(i) == null ? null : models.get(i).getName();
            labels[i] = (i + 1) + (name == null || name.isBlank() ? "" : ": " + name);
        }

        JComboBox<String> chooser = new JComboBox<>(labels);
        chooser.setMaximumSize(chooser.getPreferredSize());
        if (selected >= 0 && selected < labels.length) chooser.setSelectedIndex(selected);
        int[] current = {chooser.getSelectedIndex()};

        chooser.addActionListener(e -> {
            int chosen = chooser.getSelectedIndex();
            if (chosen < 0 || chosen == current[0]) return;
            current[0] = chosen;
            onChoose.accept(chosen);
        });

        Box box = Box.createHorizontalBox();
        box.add(new JLabel("Data set (of " + labels.length + "): "));
        box.add(chooser);
        return box;
    }
}
