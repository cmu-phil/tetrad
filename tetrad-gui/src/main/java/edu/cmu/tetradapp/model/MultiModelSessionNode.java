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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.session.SessionNode;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.app.SessionEditorNode;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.NumberFormat;
import java.util.*;


/**
 * Compares a target workbench with a reference workbench using an edge type
 * misclassification matrix and an endpoint misclassification matrix.
 *
 * @author Joseph Ramsey
 */
public final class MultiModelSessionNode<T> implements SessionModel {
    static final long serialVersionUID = 23L;
    private final List<List<SessionModel>> parentModels;
    private String name;
    private SessionEditorNode sessionEditorNode;

    //=============================CONSTRUCTORS==========================//

    public MultiModelSessionNode(List<List<SessionModel>> parentModels, Parameters params) {
        this.parentModels = parentModels;


    }

    private boolean createModel(boolean simulation, SessionEditorNode sessionEditorNode) throws Exception {
        SessionNode sessionNode = sessionEditorNode.getSessionNode();

        if (sessionNode.getModel() != null) {
            return true;
        }

        this.sessionEditorNode = sessionEditorNode;
        Class modelClass = sessionEditorNode.determineTheModelClass(sessionNode);

        if (modelClass == null) {
            return false;
        }

        // Must determine whether that model has a parameterizing object
        // associated with it and if so edit that. (This has to be done
        // before creating the model since it will be an argument to the
        // constructor of the model.)
        if (sessionNode.existsParameterizedConstructor(modelClass)) {
            Parameters params = sessionNode.getParam(modelClass);
            Object[] arguments = sessionNode.getModelConstructorArguments(modelClass);

            if (params != null) {
                boolean edited = sessionEditorNode.editParameters(modelClass, params, arguments);

                if (!edited) {
                    return false;
                }
            }
        }

        // Finally, create the model. Don't worry, if anything goes wrong
        // in the process, an exception will be thrown with an
        // appropriate message.
        sessionNode.createModel(modelClass, simulation);
        return true;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public int size() {
        return 0;
    }

    public SessionModel get(int i) {
        return null;
    }
}


