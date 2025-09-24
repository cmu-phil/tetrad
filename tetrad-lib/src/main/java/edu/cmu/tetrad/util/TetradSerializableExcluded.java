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

package edu.cmu.tetrad.util;

import java.io.Serializable;

/**
 * <p>Interface to tag a class that should be exluded from the set of
 * TetradSerializable classes, even if it implements the TetradSerializable interface. This is needed because some
 * interface need to implement TetradSerializable for reasons of sanity, but certain rogue implementations of those
 * interfaces are not actually serialized.&gt; 0
 * <p>See TestSerialization and TestSerializiableUtils.&gt; 0
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface TetradSerializableExcluded extends Serializable {
}






