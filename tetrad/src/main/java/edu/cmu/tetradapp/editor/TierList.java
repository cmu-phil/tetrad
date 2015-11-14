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
import java.util.List;

/**
 * Displays a scrollable of Temporal Tiers.
 *
 * @author Shane Harwood
 */
public class TierList extends JScrollPane {
    private IKnowledge knowledge;
    private JPanel constList = new JPanel();

    /**
     * Field TierListEditor
     */
    private TemporalTierEditor tierListEditor;

    private List<String> vNames;

    private Tier[] tiers;

    public TierList(IKnowledge know, List<String> varNames,
            TemporalTierEditor tierListEditor) {

        super(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        vNames = varNames;

        if (know == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        if (varNames == null) {
            throw new NullPointerException(
                    "AbstractVariable name list must not be " + "null.");
        }

        if (tierListEditor == null) {
            throw new NullPointerException(
                    "TierListEditor must not be " + "null.");
        }

        this.tierListEditor = tierListEditor;

        setViewportView(constList);

        System.out.println("TierList Knowledge is: " + know);

        //the same kowledge
        if (know != null) {
            this.knowledge = know;
        }
        else {
            throw new NullPointerException();
        }

        constList.setLayout(new BoxLayout(constList, BoxLayout.Y_AXIS));

        String[] names = varNames.toArray(new String[0]);

        String[] tierNames = new String[names.length + 1];

        tierNames[0] = "Unspecified";

        for (int i = 0; i < names.length; i++) {
            tierNames[i + 1] = "Tier " + i;
        }

        tiers = new Tier[names.length + 1];

        Tier.setKnowledge(knowledge);

        //tiers[names.length] = new Tier(this, "Unspecified", tierNames);


        tiers[names.length] = new Tier(this, -1, tierNames);
        //        constList.add(tiers[names.length]);

        Box b = Box.createHorizontalBox();
        b.add(tiers[names.length]);
        //        b.add(Box.createGlue());
        constList.add(b);

        for (int i = 0; i < names.length; i++) {
            //tiers[i] = new Tier(this, "Tier " + i, tierNames);
            tiers[i] = new Tier(this, i, tierNames);
            //	 		constList.add(tiers[i]);


            Box b1 = Box.createHorizontalBox();
            b1.add(tiers[i]);
            //            b1.add(Box.createGlue());
            constList.add(b1);
        }

        //temp.addPropertyChangeListener(this);
        refreshInfo();    //set tiers to agree with BK
    }

    //load background knowledge info into constraint list
    public void refreshInfo() {
        tiers[tiers.length - 1].setUnspecified(vNames);
        tiers[tiers.length - 1].repaint();
        tiers[tiers.length - 1].validate();

        for (int i = 0; i < tiers.length - 1; i++) {
            tiers[i].loadInfo();
            tiers[i].repaint();
            tiers[i].validate();
        }

        repaint();
        validate();
        tierListEditor.repaint();
        tierListEditor.validate();
        repaint();
        validate();
    }

    /**
     * @return modified knowledge allowing saving.
     */
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }
}





