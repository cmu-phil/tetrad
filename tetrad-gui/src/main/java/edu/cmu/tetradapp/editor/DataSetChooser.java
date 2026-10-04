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
import edu.cmu.tetradapp.util.ArrowKeyNavigation;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A "Data set (of m)" drop-down for editors whose model can work from any one of several data sets (for instance,
 * the completed data sets of a multiple imputation). It always shows which data set is in use.
 * <p>
 * Beside the drop-down are previous and next buttons, which wrap at the ends. Unless asked not to, the chooser also
 * binds the arrow keys in the window it is shown in (see {@link ArrowKeyNavigation}): Ctrl or Cmd with Up or Down
 * steps through the data sets from anywhere in the window, and plain Up or Down does so when the focus is not in
 * something that uses those keys itself, such as a table or a list.
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
        return create(models, selected, onChoose, true);
    }

    /**
     * Builds the chooser.
     *
     * @param models    the data sets to choose among, in order
     * @param selected  the index of the one in use now
     * @param onChoose  called with the index of a newly chosen data set; not called when the choice is unchanged
     * @param arrowKeys whether to bind the arrow keys to step through the data sets; pass false where a change of
     *                  data set is costly or discards work, so that it is only ever made deliberately
     * @return the chooser with its label, or null if there are fewer than two data sets, in which case there is
     * nothing to choose
     */
    public static Box create(List<? extends DataModel> models, int selected, IntConsumer onChoose,
                             boolean arrowKeys) {
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

        JButton previous = stepButton("\u25B2", "Previous data set", chooser, -1);
        JButton next = stepButton("\u25BC", "Next data set", chooser, +1);

        Box box = Box.createHorizontalBox();
        box.add(new JLabel("Data set (of " + labels.length + "): "));
        box.add(chooser);
        box.add(Box.createHorizontalStrut(4));
        box.add(previous);
        box.add(next);

        if (arrowKeys) {
            String modifier = System.getProperty("os.name", "").toLowerCase().contains("mac") ? "Cmd" : "Ctrl";
            chooser.setToolTipText(modifier + " + Up or Down steps through the data sets from anywhere in this window.");

            // The keys are bound on the root pane of whatever window the chooser ends up in, which is not known
            // until it is shown. Binding is idempotent per root pane, so being shown again is harmless.
            box.addAncestorListener(new AncestorListener() {
                @Override
                public void ancestorAdded(AncestorEvent event) {
                    JRootPane rootPane = SwingUtilities.getRootPane(box);
                    if (rootPane != null) ArrowKeyNavigation.install(rootPane, chooser);
                }

                @Override
                public void ancestorRemoved(AncestorEvent event) {
                }

                @Override
                public void ancestorMoved(AncestorEvent event) {
                }
            });
        }

        return box;
    }

    private static JButton stepButton(String text, String toolTip, JComboBox<String> chooser, int delta) {
        JButton button = new JButton(text);
        button.setToolTipText(toolTip);
        button.setMargin(new Insets(1, 4, 1, 4));
        button.setFocusable(false);

        button.addActionListener(e -> {
            int count = chooser.getItemCount();
            if (count < 2) return;
            chooser.setSelectedIndex(Math.floorMod(chooser.getSelectedIndex() + delta, count));
        });

        return button;
    }
}
