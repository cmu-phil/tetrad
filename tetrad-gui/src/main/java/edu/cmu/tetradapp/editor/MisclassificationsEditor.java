///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.EdgewiseComparisonModel;
import edu.cmu.tetradapp.model.Misclassifications;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Provides a little display/editor for notes in the session workbench. This may
 * be elaborated in the future to allow marked up text.
 *
 * @author Joseph Ramsey
 */
public class MisclassificationsEditor extends JPanel {

    private static final long serialVersionUID = -5291697901326757833L;

    /**
     * The model for the note.
     */
    private final Misclassifications comparison;
    private JTextArea area;

    /**
     * Constructs the editor given the model
     */
    public MisclassificationsEditor(Misclassifications comparison) {
        this.comparison = comparison;
        setup();
    }

    private void setup() {
        setLayout(new BorderLayout());

        JPanel pane = new JPanel();

        Font font = new Font("Monospaced", Font.PLAIN, 14);
        area = new JTextArea();
        area.setText(this.comparison.getComparisonString());

        area.setFont(font);

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

        switch (comparison.getComparisonGraphType()) {
            case CPDAG:
                menu.setText("Compare to CPDAG...");
                cpdag.setSelected(true);
                break;
            case PAG:
                menu.setText("Compare to PAG...");
                pag.setSelected(true);
                break;
            case DAG:
                menu.setText("Compare to DAG...");
                graph.setSelected(true);
                break;
            default:
                throw new IllegalArgumentException("Unexpected comparison DAG type: " + comparison.getComparisonGraphType());
        }

        graph.addActionListener(e -> {
            comparison.setComparisonGraphType(Misclassifications.ComparisonType.DAG);

            menu.setText("Compare to DAG...");
            menu.setBackground(Color.WHITE);

            this.area.setText(this.comparison.getComparisonString());
            this.area.moveCaretPosition(0);
            this.area.setSelectionStart(0);
            this.area.setSelectionEnd(0);

            this.area.repaint();

        });

        cpdag.addActionListener(e -> {
            comparison.setComparisonGraphType(Misclassifications.ComparisonType.CPDAG);

            menu.setText("Compare to CPDAG...");
            menu.setBackground(Color.YELLOW);

            this.area.setText(this.comparison.getComparisonString());
            this.area.moveCaretPosition(0);
            this.area.setSelectionStart(0);
            this.area.setSelectionEnd(0);

            this.area.repaint();

        });

        pag.addActionListener(e -> {
            comparison.setComparisonGraphType(Misclassifications.ComparisonType.PAG);

            menu.setText("Compare to PAG...");
            menu.setBackground(Color.GREEN.brighter().brighter());

            this.area.setText(this.comparison.getComparisonString());
            this.area.moveCaretPosition(0);
            this.area.setSelectionStart(0);
            this.area.setSelectionEnd(0);
            this.area.repaint();
        });

        return menubar;
    }


}
