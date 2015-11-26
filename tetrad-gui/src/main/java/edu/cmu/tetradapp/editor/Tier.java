///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.data.IKnowledge;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * The actual area which displays a knowledge constraint
 *
 * @author Shane Harwood
 */
class Tier extends JPanel {

    private TierList knowList;    //knowlist constraint is contained in
    private int num;
    private String[] tierNames;
    private JPanel view = new JPanel();
    private JScrollPane jsp;
    private static IKnowledge know;

    /**
     * @return a panel with a tier name, and all vars in that tier.
     */
    public Tier(TierList kn, int thisTier, String[] tierNames) {

        jsp = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        this.knowList = kn;

        num = thisTier;

        this.tierNames = tierNames;

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        jsp.setViewportView(view);
    }

    public static void setKnowledge(IKnowledge k) {
        know = k;
    }

    /**
     * tell tier what info it should hold and represent, used for constant
     * updating of tiers
     */
    public void loadInfo() {
        removeAll();

        view.removeAll();

        add(new JLabel("Tier " + num));
        add(jsp);

        List t = know.getTier(num);

        view.setLayout(new BoxLayout(view, BoxLayout.X_AXIS));

        Iterator it = t.iterator();

        String temp;

        while (it.hasNext()) {
            temp = (String) it.next();

            String[] names = new String[tierNames.length + 1];

            names[0] = temp;

            System.arraycopy(tierNames, 0, names, 1, tierNames.length);

            JComboBox cBox = new JComboBox(names);
            cBox.setMaximumSize(new Dimension(80, 50));
            view.add(cBox);

            cBox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JComboBox cb = (JComboBox) e.getSource();
                    int newTier = cb.getSelectedIndex() - 2;
                    String s = (String) cb.getItemAt(0);

                    if (newTier == -2) {
                        return;
                    }

                    //know.unspecifyTier(s);
                    Tier.know.removeFromTiers(s);

                    if (newTier >= 0) {
                        Tier.know.addToTier(newTier, s);
                    }

                    knowList.refreshInfo();
                }
            });

        }
    }

    public void setUnspecified(List<String> varNames) {
        removeAll();
        view.removeAll();

        add(new JLabel("Unspecified"));
        add(jsp);

        List<String> vNames = new LinkedList<String>(varNames);

        System.out.println("edit unspecified list");
        System.out.println("vNames Contains: " + vNames);

        for (int i = 0; i < know.getNumTiers(); i++) {
            System.out.println("Tier " + i);

            List t = know.getTier(i);

            System.out.println("Tier contains: " + t);

            Iterator it = t.iterator();

            String temp;

            while (it.hasNext()) {
                temp = (String) it.next();

                System.out.println("Try removing: " + temp);

                vNames.remove(temp);
            }
        }

        System.out.println("vNames now Contains: " + vNames);

        view.setLayout(new BoxLayout(view, BoxLayout.X_AXIS));

        Iterator it = vNames.iterator();

        String temp;

        while (it.hasNext()) {
            temp = (String) it.next();

            String[] names = new String[tierNames.length + 1];

            names[0] = temp;

            System.arraycopy(tierNames, 0, names, 1, tierNames.length);

            JComboBox cBox = new JComboBox(names);
            cBox.setMaximumSize(new Dimension(80, 50));
            view.add(cBox);

            cBox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JComboBox cb = (JComboBox) e.getSource();
                    int newTier = cb.getSelectedIndex() - 2;
                    String s = (String) cb.getItemAt(0);

                    if (newTier == -2) {
                        return;
                    }

                    //know.unspecifyTier(s);
                    Tier.know.removeFromTiers(s);

                    if (newTier >= 0) {
                        Tier.know.addToTier(newTier, s);
                    }

                    knowList.refreshInfo();
                }
            });

        }

    }

    // ======= The following make sure constraint is the proper size ======//

    public Dimension getPreferredSize() {
        return new Dimension(1000, 60);
    }

    public Dimension getMinimumSize() {
        return new Dimension(250, 60);
    }

    public Dimension getMaximumSize() {
        return new Dimension(2000, 60);
    }
}





