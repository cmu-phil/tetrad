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
@SuppressWarnings("RedundantIfStatement")
public class Version implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The major version number. In release a.b.c-d, a. At time of creating this
     * class, it's 4, and the minor version is 3. This should increase only for
     * truly substantial and essentially complete new releases of Tetrad.
     *
     * @serial Range greater than or equal to 0.
     */
    private final int majorVersion;

    /**
     * The minor version number. In release a.b.c-d, b. This number increases
     * without bound until the next major release, at which point it goes back
     * to zero.
     *
     * @serial Range greater than or equal to 0.
     */
    private final int minorVersion;

    /**
     * The minor release number. In release a.b.c-d, c. This number increases
     * without bound until the next major or minor release, at which point it
     * goes back to zero.
     *
     * @serial Range greater than or equal to 0.
     */
    private final int minorSubversion;

    /**
     * The incremental release number. In release a.b.c-d, d. This number
     * increases without bound until the next major version, minor version, or
     * minor subversion release, at which point is goes back to zero. If it is
     * zero, release a.b.c.dx may be referred to as a.b.c.
     * <p/>
     * With maven snapshots the incremental release number refers to the snapshot build
     * date.  If this field is SNAPSHOT it is set to max int
     *
     * @serial Range greater than or equal to 0.
     */
    private final int incrementalRelease;

    //===========================CONSTRUCTORS============================//

    /**
     * Parses string version specs into Versions.
     *
     * @param spec A string of the form a.b.c-d.
     */
    public Version(final String spec) {
        if (spec == null) {
            throw new NullPointerException();
        }

        final Pattern pattern2 = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)");
        final Matcher matcher2 = pattern2.matcher(spec);

        final Pattern pattern3 = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)-(\\d*)");
        final Matcher matcher3 = pattern3.matcher(spec);

        final Pattern pattern4 = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)-SNAPSHOT");
        final Matcher matcher4 = pattern4.matcher(spec);

        final Pattern pattern5 = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)-(\\d*)\\.(\\d*)");
        final Matcher matcher5 = pattern5.matcher(spec);

        if (matcher2.matches()) {
            this.majorVersion = Integer.parseInt(matcher2.group(1));
            this.minorVersion = Integer.parseInt(matcher2.group(2));
            this.minorSubversion = Integer.parseInt(matcher2.group(3));
            this.incrementalRelease = 0;
        } else if (matcher3.matches()) {
            this.majorVersion = Integer.parseInt(matcher3.group(1));
            this.minorVersion = Integer.parseInt(matcher3.group(2));
            this.minorSubversion = Integer.parseInt(matcher3.group(3));
            this.incrementalRelease = Integer.parseInt(matcher3.group(4));
        } else if (matcher4.matches()) {
            this.majorVersion = Integer.parseInt(matcher4.group(1));
            this.minorVersion = Integer.parseInt(matcher4.group(2));
            this.minorSubversion = Integer.parseInt(matcher4.group(3));
            this.incrementalRelease = 0;
        } else if (matcher5.matches()) {
            this.majorVersion = Integer.parseInt(matcher5.group(1));
            this.minorVersion = Integer.parseInt(matcher5.group(2));
            this.minorSubversion = Integer.parseInt(matcher5.group(3));
            this.incrementalRelease = Integer.parseInt(matcher5.group(4));
        } else {
            throw new IllegalArgumentException("Version should be either of the " +
                    "form a.b.c or a.b.c-d or a.b.c-SNAPSHOT or a.b.c-d.e " + spec);
        }

    }

    private Version(final int majorVersion, final int minorVersion, final int minorSubversion,
                    final int incrementalRelease) {
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
            final File file = new File("project_tetrad/resources/version");
            final FileReader fileReader = new FileReader(file);
            final BufferedReader bufReader = new BufferedReader(fileReader);
            final String spec = bufReader.readLine();
            bufReader.close();
            return new Version(spec);
        } catch (final IOException e) {
            throw new RuntimeException(
                    "Please correct the file project/resources/version " +
                            "\nso that it contains a version number of the form " +
                            "\na.b.c or a.b.c-d or a.b.c-SNAPSHOT or a.b.c-d.e. " +
                            "\nNormally this is set automatically by the Maven build " +
                            "\nsystem.");
        }
    }

    /**
     * @return the model version as stored in the ejar. To sync this with the
     * version at project/resources/version, run the ejar ant task.
     */
    public static Version currentViewableVersion() {
        try {
            final String path = "/resources/version";
            final URL url = Version.class.getResource(path);

            if (url == null) {
                throw new RuntimeException(
                        "Please correct the file project/resources/version " +
                                "\nso that it contains a version number of the form " +
                                "\na.b.c or a.b.c-d or a.b.c-SNAPSHOT or a.b.c-d.e. " +
                                "\nNormally this is set automatically by the Maven build " +
                                "\nsystem.");
            }

            final InputStream inStream = url.openStream();
            final Reader reader = new InputStreamReader(inStream);
            final BufferedReader bufReader = new BufferedReader(reader);
            final String spec = bufReader.readLine();

            bufReader.close();

            return new Version(spec);
        } catch (final IOException e) {
            e.printStackTrace();
        }

        throw new RuntimeException(
                "Please correct the file project/resources/version " +
                        "\nso that it contains a version number of the form " +
                        "\na.b.c or a.b.c-d or a.b.c-SNAPSHOT or a.b.c-d.e. " +
                        "\nNormally this is set automatically by the Maven build " +
                        "\nsystem.");
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Version serializableInstance() {
        return new Version("1.2.3");
    }

    //==========================PUBLIC METHODS===========================//

    private int majorVersion() {
        return this.majorVersion;
    }

    private int minorVersion() {
        return this.minorVersion;
    }

    private int minorSubversion() {
        return this.minorSubversion;
    }

    private int incrementalRelease() {
        return this.incrementalRelease;
    }

    public int hashCode() {
        int hashCode = 61;
        hashCode += 61 * this.majorVersion;
        hashCode += 61 * this.minorVersion;
        hashCode += 61 * this.minorSubversion;
        hashCode += this.incrementalRelease;
        return hashCode;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof Version)) {
            return false;
        }

        final Version other = (Version) o;

        if (!(this.majorVersion == other.majorVersion)) {
            return false;
        }

        if (!(this.minorVersion == other.minorVersion)) {
            return false;
        }

        if (!(this.minorSubversion == other.minorSubversion)) {
            return false;
        }

        if (!(this.incrementalRelease == other.incrementalRelease)) {
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
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.majorVersion < 0) {
            throw new IllegalStateException();
        }

        if (this.minorVersion < 0) {
            throw new IllegalStateException();
        }

        if (this.minorSubversion < 0) {
            throw new IllegalStateException();
        }

        if (this.incrementalRelease < 0) {
            throw new IllegalStateException();
        }
    }

    public Version nextMajorVersion() {
        final int majorVersion = this.majorVersion + 1;
        final int minorVersion = 0;
        final int minorSubversion = 0;
        final int incrementalRelease = 0;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }

    public Version nextMinorVersion() {
        final int majorVersion = this.majorVersion;
        final int minorVersion = this.minorVersion + 1;
        final int minorSubversion = 0;
        final int incrementalRelease = 0;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }

    public Version nextMinorSubversion() {
        final int majorVersion = this.majorVersion;
        final int minorVersion = this.minorVersion;
        final int minorSubversion = this.minorSubversion + 1;
        final int incrementalRelease = 0;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }

    public Version nextIncrementalRelease() {
        final int majorVersion = this.majorVersion;
        final int minorVersion = this.minorVersion;
        final int minorSubversion = this.minorSubversion;
        final int incrementalRelease = this.incrementalRelease + 1;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }
}





