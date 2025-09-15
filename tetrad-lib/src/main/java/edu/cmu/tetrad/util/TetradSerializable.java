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
 * <p>Interface to tag a class that is part of the set of serializable classes
 * in the Tetrad API. These classes must have all of their serialiable fields marked with @serial tags, and they may
 * only have serializable fields that are primitive, TetradSerializable, arrays of some TetradSerializable type,
 * Collection or Map classes, or String or Class fields. They must also have a static final long field called
 * 'serialVerUID' set to 23L. Classes in this set may never change their class name or package path once published, and
 * the type of any serializable member field may never be changed to an incompatible type. (For primitives, change the
 * type at all constitutes an incompatible change.) If these conditions are all met, then Tetrad sessions saved out in
 * one version will load in later versions of Tetrad. They may load with incorrect information if, for instance, the
 * name of a field is changed or the interpretation of that field changes. So in general, when making a class
 * TetradSerializable, please make sure that its member fields all have good names (that you won't want to change later)
 * and all have clear interpretations (that you won't want to change later).&gt; 0 <p>If that all sounds like a pain,
 * the payoff is that even very large Tetrad sessions will load quickly. This isn't currently true, from what I can
 * tell, for any XML renderer/parser on the market. If a Tetrad session contains a dataset with 50 and 5000 cases, for
 * instance, binary serialization will load it in well under a second, whereas XML parsers that I've checked don't come
 * back in under 5 minutes.&gt; 0 <p>The test class that checks the above conditions are TestSerialization, which in
 * turn uses methods in TetradSerializableUtils. More details can be find there.&gt; 0
 * </p>
 * <p>See TestSerialization and TestSerializiableUtils.&gt; 0</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface TetradSerializable extends Serializable {
}






