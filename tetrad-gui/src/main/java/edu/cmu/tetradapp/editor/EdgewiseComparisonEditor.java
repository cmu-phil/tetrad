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

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.EdgewiseComparisonModel;
import edu.cmu.tetradapp.util.WatchedProcess;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;

/**
 * Provides a little display/editor for notes in the session workbench. This may be elaborated in the future to allow
 * marked up text.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class EdgewiseComparisonEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = 7921819261142670181L;

    /**
     * The model for the note.
     */
    private final EdgewiseComparisonModel comparison;

    /**
     * The parameters for the comparison.
     */
    private final Parameters params;

    /**
     * The text area for the note.
     */
    private JTextArea area;

    /**
     * Constructs the editor given the model
     *
     * @param comparison a {@link edu.cmu.tetradapp.model.EdgewiseComparisonModel} object
     */
    public EdgewiseComparisonEditor(EdgewiseComparisonModel comparison) {
        this.comparison = comparison;
        this.params = comparison.getParams();
        setup();
    }

    //============================ Private Methods =========================//
    private void setup() {
        setLayout(new BorderLayout());

        JPanel pane = new JPanel();

        Font font = new Font("Monospaced", Font.PLAIN, 14);
        area = new JTextArea();
        area.setText(tableTextWithHeader());

        area.setFont(font);

        int position = area.getText().indexOf("True graph");

        // If the word is found, scroll to it
        if (position >= 0) {
            area.setCaretPosition(position);
        }

        JScrollPane scrollTextPane = new JScrollPane(area);
        scrollTextPane.setPreferredSize(new Dimension(500, 600));

        pane.add(scrollTextPane, new BorderLayout());

        add(pane);

        add(menubar(), BorderLayout.NORTH);
    }

    @NotNull
    private JMenuBar menubar() {
        JMenuBar menubar = new JMenuBar();
        JMenu menu = new JMenu("Compare To...");
        JMenuItem graph = new JCheckBoxMenuItem("DAG");
        graph.setBackground(Color.WHITE);
        JMenuItem cpdag = new JCheckBoxMenuItem("CPDAG");
        cpdag.setBackground(Color.YELLOW);
        JMenuItem pag = new JCheckBoxMenuItem("PAG");
        pag.setBackground(Color.GREEN.brighter().brighter());

        ButtonGroup group = new ButtonGroup();
        group.add(graph);
        group.add(cpdag);
        group.add(pag);

        menu.add(graph);
        menu.add(cpdag);
        menu.add(pag);

        menubar.add(menu);

        switch (this.params.getString("graphComparisonType")) {
            case "CPDAG":
                menu.setText("Compare to CPDAG...");
                cpdag.setSelected(true);
                break;
            case "PAG":
                menu.setText("Compare to PAG...");
                pag.setSelected(true);
                break;
            default:
                menu.setText("Compare to Truth...");
                graph.setSelected(true);
                break;
        }

        graph.addActionListener(e -> {
            this.params.set("graphComparisonType", "DAG");
            menu.setText("Compare to Truth...");
            menu.setBackground(Color.WHITE);

            new WatchedProcess() {
                @Override
                public void watch() {
                    SwingUtilities.invokeLater(() -> {
                        area.setText(comparison.getComparisonString());
                        area.moveCaretPosition(0);
                        area.setSelectionStart(0);
                        area.setSelectionEnd(0);
                        area.repaint();
                    });
                }
            };

//            SwingUtilities.invokeLater(() -> {
//                this.area.setText(comparison.getComparisonString());
//                this.area.moveCaretPosition(0);
//                this.area.setSelectionStart(0);
//                this.area.setSelectionEnd(0);
//                this.area.repaint();
//            });
//            this.area.setText(comparison.getComparisonString());
//            this.area.moveCaretPosition(0);
//            this.area.setSelectionStart(0);
//            this.area.setSelectionEnd(0);
//
//            this.area.repaint();

        });

        cpdag.addActionListener(e -> {
            this.params.set("graphComparisonType", "CPDAG");
            menu.setText("Compare to CPDAG...");
            menu.setBackground(Color.YELLOW);

            new WatchedProcess() {
                @Override
                public void watch() {
                    SwingUtilities.invokeLater(() -> {
                        area.setText(comparison.getComparisonString());
                        area.moveCaretPosition(0);
                        area.setSelectionStart(0);
                        area.setSelectionEnd(0);
                        area.repaint();
                    });
                }
            };

//            SwingUtilities.invokeLater(() -> {
//                this.area.setText(comparison.getComparisonString());
//                this.area.moveCaretPosition(0);
//                this.area.setSelectionStart(0);
//                this.area.setSelectionEnd(0);
//                this.area.repaint();
//            });
//            this.area.setText(comparison.getComparisonString());
//            this.area.moveCaretPosition(0);
//            this.area.setSelectionStart(0);
//            this.area.setSelectionEnd(0);
//
//            this.area.repaint();

        });

        pag.addActionListener(e -> {
            this.params.set("graphComparisonType", "PAG");
            menu.setText("Compare to PAG...");
            menu.setBackground(Color.GREEN.brighter().brighter());

            new WatchedProcess() {
                @Override
                public void watch() {
                    SwingUtilities.invokeLater(() -> {
                        area.setText(comparison.getComparisonString());
                        area.moveCaretPosition(0);
                        area.setSelectionStart(0);
                        area.setSelectionEnd(0);
                        area.repaint();
                    });
                }
            };

//            SwingUtilities.invokeLater(() -> {
//                this.area.setText(comparison.getComparisonString());
//                this.area.moveCaretPosition(0);
//                this.area.setSelectionStart(0);
//                this.area.setSelectionEnd(0);
//                this.area.repaint();
//            });
//            this.area.setText(comparison.getComparisonString());
//            this.area.moveCaretPosition(0);
//            this.area.setSelectionStart(0);
//            this.area.setSelectionEnd(0);
//
//            this.area.repaint();
        });

        return menubar;
    }

    private String tableTextWithHeader() {
        return this.comparison.getComparisonString();
    }
}

