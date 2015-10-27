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

package edu.cmu.tetradapp.app;
//
//import javax.help.*;
//import javax.help.UnsupportedOperationException;
//import java.awt.*;
//import java.net.URL;
//import java.util.Locale;
//
//public class TetradHelpBroker implements HelpBroker {
//    private static final TetradHelpBroker ourInstance = new TetradHelpBroker();
//
//    /**
//     * Keeps a reference to the help broker for the application.
//     */
//    private HelpBroker helpBroker;
//
//    /**
//     * The path to the help set.
//     */
//    private final String helpsetName = "javahelp/TetradHelp";
//
//
//    public static TetradHelpBroker getInstance() {
//        return ourInstance;
//    }
//
//    private TetradHelpBroker() {
//        try {
//            ClassLoader cl = TetradDesktop.class.getClassLoader();
//            URL url = HelpSet.findHelpSet(cl, helpsetName);
//            HelpSet helpSet = new HelpSet(cl, url);
//            this.helpBroker = helpSet.createHelpBroker();
//        }
//        catch (Exception e) {
//            this.helpBroker = null;
//        }
//    }
//
//    public boolean isHelpDefined() {
//        return helpBroker != null;
//    }
//
//    public void enableHelp(Component component, String s, HelpSet helpSet) {
//        helpBroker.enableHelp(component, s, helpSet);
//    }
//
//    public void enableHelp(MenuItem menuItem, String s, HelpSet helpSet) {
//        helpBroker.enableHelp(menuItem, s, helpSet);
//    }
//
//    public void enableHelpKey(Component component, String s, HelpSet helpSet) {
//        helpBroker.enableHelpKey(component, s, helpSet);
//    }
//
////    @Override
//    public void enableHelpKey(Component component, String s, HelpSet helpSet, String s2, String s3) {
//        //To change body of implemented methods use File | Settings | File Templates.
//    }
//
//    public void enableHelpOnButton(Component component, String s,
//            HelpSet helpSet) throws IllegalArgumentException {
//        helpBroker.enableHelpOnButton(component, s, helpSet);
//    }
//
//    public void enableHelpOnButton(MenuItem menuItem, String s,
//            HelpSet helpSet) {
//        helpBroker.enableHelpOnButton(menuItem, s, helpSet);
//    }
//
////    @Override
//    public void enableHelpOnButton(Object o, String s, HelpSet helpSet, String s2, String s3) throws IllegalArgumentException {
//        //To change body of implemented methods use File | Settings | File Templates.
//    }
//
//    public Map.ID getCurrentID() {
//        return helpBroker.getCurrentID();
//    }
//
//    public URL getCurrentURL() {
//        return helpBroker.getCurrentURL();
//    }
//
//    public String getCurrentView() {
//        return helpBroker.getCurrentView();
//    }
//
//    public Font getFont() {
//        return helpBroker.getFont();
//    }
//
//    public HelpSet getHelpSet() {
//        return helpBroker.getHelpSet();
//    }
//
////    @Override
////    public void setHelpSetPresentation(HelpSet.Presentation presentation) {
////        // To change body of implemented methods use File | Settings | File Templates.
////    }
//
//    public Locale getLocale() {
//        return helpBroker.getLocale();
//    }
//
//    public Point getLocation() throws UnsupportedOperationException {
//        return helpBroker.getLocation();
//    }
//
//    public Dimension getSize() throws UnsupportedOperationException {
//        return helpBroker.getSize();
//    }
//
////    @Override
//    public void setScreen(int i) throws UnsupportedOperationException {
//        //To change body of implemented methods use File | Settings | File Templates.
//    }
//
////    @Override
//    public int getScreen() throws UnsupportedOperationException {
//        return 0;  //To change body of implemented methods use File | Settings | File Templates.
//    }
//
//    public void initPresentation() {
//        helpBroker.initPresentation();
//    }
//
//    public boolean isDisplayed() {
//        return helpBroker.isDisplayed();
//    }
//
//    public boolean isViewDisplayed() {
//        return helpBroker.isViewDisplayed();
//    }
//
////    @Override
//    public void showID(Map.ID id, String s, String s2) throws InvalidHelpSetContextException {
//        //To change body of implemented methods use File | Settings | File Templates.
//    }
//
////    @Override
//    public void showID(String s, String s2, String s3) throws BadIDException {
//        //To change body of implemented methods use File | Settings | File Templates.
//    }
//
//    public void setCurrentID(String s) throws BadIDException {
//        helpBroker.setCurrentID(s);
//    }
//
//    public void setCurrentID(Map.ID id) throws InvalidHelpSetContextException {
//        helpBroker.setCurrentID(id);
//    }
//
//    public void setCurrentURL(URL url) {
//        helpBroker.setCurrentURL(url);
//    }
//
//    public void setCurrentView(String s) {
//        helpBroker.setCurrentView(s);
//    }
//
//    public void setDisplayed(boolean b) throws UnsupportedOperationException {
//        helpBroker.setDisplayed(b);
//    }
//
//    public void setFont(Font font) {
//        helpBroker.setFont(font);
//    }
//
//    public void setHelpSet(HelpSet helpSet) {
//        helpBroker.setHelpSet(helpSet);
//    }
//
//    public void setLocale(Locale locale) {
//        helpBroker.setLocale(locale);
//    }
//
//    public void setLocation(Point point) throws UnsupportedOperationException {
//        helpBroker.setLocation(point);
//    }
//
//    public void setSize(Dimension dimension)
//            throws UnsupportedOperationException {
//        helpBroker.setSize(dimension);
//    }
//
//    public void setViewDisplayed(boolean b) {
//        helpBroker.setViewDisplayed(b);
//    }
//}





