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

/**
 * A <code>FinalizingParameterEditor</code> (for the lack of a better name) is a parameter editor that deals
 * with editing material that can't easily update itself while the user edits matters.
 * The editor should be either a JComponent or a JDialog, if the form then <code>finalizeEdit()</code>
 * will be called once the user has indicate that they are finished editing
 * so that the editor can collect and commit all the edits the user made. If <code>finalizeEdit</code>
 * returns false then the edit is aborted, as if they canceled it. And the other hand if the
 * editor is a Dialog then <code>finalizeEdit()</code> will be called right after <code>setup()</code>
 * assuming that the Dialog is modal and is handling matters on the users behalf.
 *
 *
 * @author Tyler Gibson
 */
public interface FinalizingParameterEditor extends ParameterEditor {

    /**
     * Tells the editor to commit any final details before it is closed (only called when the
     * user selects "Ok" or something of that nature). If false is returned the edit is considered
     * invalid and it will be treated as if the user selected "cancel".
     *
     * @return - true iff the edit was committed.
     */
    boolean finalizeEdit();




}



