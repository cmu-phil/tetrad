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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;

/**
 * Provides a simple model for notes that the user may want to add to the session. Notes are stored as styled documents,
 * on the theory that maybe at some point the ability to add styling will be nice. Names are also stored on the theory
 * that maybe someday the name of the node can be displayed in the interface. That day is not this day.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NoteModel implements SessionModel {
    private static final long serialVersionUID = 23L;

    private StyledDocument note = new DefaultStyledDocument();
    private String name;

    /**
     * <p>Constructor for NoteModel.</p>
     *
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public NoteModel(Parameters parameters) {

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     * @return a {@link edu.cmu.tetradapp.model.NoteModel} object
     */
    public static NoteModel serializableInstance() {
        return new NoteModel(new Parameters());
    }

    /**
     * <p>Getter for the field <code>note</code>.</p>
     *
     * @return a {@link javax.swing.text.StyledDocument} object
     */
    public StyledDocument getNote() {
        return this.note;
    }

    /**
     * <p>Setter for the field <code>note</code>.</p>
     *
     * @param note a {@link javax.swing.text.StyledDocument} object
     */
    public void setNote(StyledDocument note) {
        this.note = note;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /** {@inheritDoc} */
    public void setName(String name) {
        this.name = name;
    }
}



