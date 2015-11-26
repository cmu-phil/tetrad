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

package edu.cmu.tetrad.util;

import java.io.*;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the getModel Tetrad version. This needs to be modified manually
 * each time a new version is released. The form of the version is a.b.c-d,
 * where "a" is the major version, "b" is the minor version, "c" is the minor
 * subversion, and "d" is the incremental release number for subversions.
 *
 * @author Joseph Ramsey
 */
public class Version implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The major version number. In release a.b.c-d, a. At time of creating this
     * class, it's 4, and the minor version is 3. This should increase only for
     * truly substantial and essentially complete new releases of Tetrad.
     *
     * @serial Range greater than or equal to 0.
     */
    private int majorVersion;

    /**
     * The minor version number. In release a.b.c-d, b. This number increases
     * without bound until the next major release, at which point it goes back
     * to zero.
     *
     * @serial Range greater than or equal to 0.
     */
    private int minorVersion;

    /**
     * The minor release number. In release a.b.c-d, c. This number increases
     * without bound until the next major or minor release, at which point it
     * goes back to zero.
     *
     * @serial Range greater than or equal to 0.
     */
    private int minorSubversion;

    /**
     * The incremental release number. In release a.b.c-d, d. This number
     * increases without bound until the next major version, minor version, or
     * minor subversion release, at which point is goes back to zero. If it is
     * zero, release a.b.c.dx may be referred to as a.b.c.
     *
     * @serial Range greater than or equal to 0.
     */
    private int incrementalRelease;

    //===========================CONSTRUCTORS============================//

    /**
     * Parses string version specs into Versions.
     *
     * @param spec A string of the form a.b.c-d.
     */
    public Version(String spec) {
        if (spec == null) {
            throw new NullPointerException();
        }

        Pattern pattern2 = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)");
        Matcher matcher2 = pattern2.matcher(spec);

        if (matcher2.matches()) {
            this.majorVersion = Integer.parseInt(matcher2.group(1));
            this.minorVersion = Integer.parseInt(matcher2.group(2));
            this.minorSubversion = Integer.parseInt(matcher2.group(3));
            this.incrementalRelease = 0;
        } else {
            Pattern pattern3 = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)-(\\d*)");
            Matcher matcher3 = pattern3.matcher(spec);

            if (matcher3.matches()) {
                this.majorVersion = Integer.parseInt(matcher3.group(1));
                this.minorVersion = Integer.parseInt(matcher3.group(2));
                this.minorSubversion = Integer.parseInt(matcher3.group(3));
                this.incrementalRelease = Integer.parseInt(matcher3.group(4));
            } else {
                throw new IllegalArgumentException("Version should be either of the " +
                        "form a.b.c (Maven) or a.b.c-d (old): " + spec);
            }
        }
    }

    public Version(int majorVersion, int minorVersion, int minorSubversion,
                   int incrementalRelease) {
        if (majorVersion < 0) {
            throw new IllegalArgumentException();
        }

        if (minorVersion < 0) {
            throw new IllegalArgumentException();
        }

        if (minorSubversion < 0) {
            throw new IllegalArgumentException();
        }

        if (incrementalRelease < 0) {
            throw new IllegalArgumentException();
        }

        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.minorSubversion = minorSubversion;
        this.incrementalRelease = incrementalRelease;
    }

    /**
     * @return the model version as stored in project/resources/version. This
     * will be the same as the getModel viewable version when the ejar and task
     * is run. (The file is copied over.)
     */
    public static Version currentRepositoryVersion() {
        try {
            File file = new File("project_tetrad/resources/version");
            FileReader fileReader = new FileReader(file);
            BufferedReader bufReader = new BufferedReader(fileReader);
            String spec = bufReader.readLine();
            bufReader.close();
            return new Version(spec);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Please correct the file project/resources/version " +
                            "\nso that it contains a version number of the form " +
                            "\na.b.c-d, where a, b, c, and a are integers. It should have " +
                            "\nbeen in CVS. Try doing cvs up or looking in an recent version " +
                            "\nof the repository in case someone removed it. This should be " +
                            "\nthe version of last published version of Tetrad.");
        }
    }

    /**
     * @return the model version as stored in the ejar. To sync this with the
     * version at project/resources/version, run the ejar ant task.
     */
    public static Version currentViewableVersion() {
        try {
            String path = "/resources/version";
            URL url = Version.class.getResource(path);

            if (url == null) {
                throw new RuntimeException(
                        "Please run 'ant copyresources' and try again. The problem " +
                                "\nis that the file /resources/version is not in the ejar build.");
            }

            InputStream inStream = url.openStream();
            Reader reader = new InputStreamReader(inStream);
            BufferedReader bufReader = new BufferedReader(reader);
            String spec = bufReader.readLine();
            bufReader.close();

            return new Version(spec);
        } catch (IOException e) {
            e.printStackTrace();
        }

        throw new RuntimeException(
                "Please correct the file project/resources/version " +
                        "\nso that it contains a version number of the form " +
                        "\na.b.c-d, where a, b, c, and a are integers. It should have " +
                        "\nbeen in CVS. Try doing cvs up or looking in an recent version " +
                        "\nof the repository in case someone removed it. This should be " +
                        "\nthe version of last published version of Tetrad.");
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Version serializableInstance() {
        return new Version("1.2.3");
    }

    //==========================PUBLIC METHODS===========================//

    public int majorVersion() {
        return majorVersion;
    }

    public int minorVersion() {
        return minorVersion;
    }

    public int minorSubversion() {
        return minorSubversion;
    }

    public int incrementalRelease() {
        return incrementalRelease;
    }

    public int hashCode() {
        int hashCode = 61;
        hashCode += 61 * majorVersion;
        hashCode += 61 * minorVersion;
        hashCode += 61 * minorSubversion;
        hashCode += incrementalRelease;
        return hashCode;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof Version)) {
            return false;
        }

        Version other = (Version) o;

        if (!(majorVersion == other.majorVersion)) {
            return false;
        }

        if (!(minorVersion == other.minorVersion)) {
            return false;
        }

        if (!(minorSubversion == other.minorSubversion)) {
            return false;
        }

        if (!(incrementalRelease == other.incrementalRelease)) {
            return false;
        }

        return true;
    }


    public String toString() {
        return majorVersion() + "." + minorVersion() + "." + minorSubversion()
                + "-" + incrementalRelease();
    }

    //===========================PRIVATE METHODS=========================//

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (majorVersion < 0) {
            throw new IllegalStateException();
        }

        if (minorVersion < 0) {
            throw new IllegalStateException();
        }

        if (minorSubversion < 0) {
            throw new IllegalStateException();
        }

        if (incrementalRelease < 0) {
            throw new IllegalStateException();
        }
    }

    public Version nextMajorVersion() {
        int majorVersion = this.majorVersion + 1;
        int minorVersion = 0;
        int minorSubversion = 0;
        int incrementalRelease = 0;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }

    public Version nextMinorVersion() {
        int majorVersion = this.majorVersion;
        int minorVersion = this.minorVersion + 1;
        int minorSubversion = 0;
        int incrementalRelease = 0;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }

    public Version nextMinorSubversion() {
        int majorVersion = this.majorVersion;
        int minorVersion = this.minorVersion;
        int minorSubversion = this.minorSubversion + 1;
        int incrementalRelease = 0;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }

    public Version nextIncrementalRelease() {
        int majorVersion = this.majorVersion;
        int minorVersion = this.minorVersion;
        int minorSubversion = this.minorSubversion;
        int incrementalRelease = this.incrementalRelease + 1;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }
}





