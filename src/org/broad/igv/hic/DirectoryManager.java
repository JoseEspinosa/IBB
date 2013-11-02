/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.hic;

import org.apache.log4j.*;
import org.broad.igv.Globals;
import org.broad.igv.exceptions.DataLoadException;
import org.broad.igv.ui.util.FileDialogUtils;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.IOException;
import java.util.prefs.Preferences;

/**
 * @author Jim Robinson
 * @date 3/19/12
 */
public class DirectoryManager {

    private static Logger log = Logger.getLogger(DirectoryManager.class);

    private static File USER_HOME;
    private static File USER_DIRECTORY;    // FileSystemView.getFileSystemView().getDefaultDirectory();
    private static File HIC_DIRECTORY;     // The HIC application directory
    final public static String HIC_DIR_USERPREF = "hicDir";


    private static File getUserHome() {
        if (USER_HOME == null) {
            String userHomeString = System.getProperty("user.home");
            USER_HOME = new File(userHomeString);
        }
        return USER_HOME;
    }

    /**
     * The user directory.  On Mac and Linux this should be the user home directory.  On Windows platforms this
     * is the "My Documents" directory.
     */
    public static synchronized File getUserDirectory() {
        if (USER_DIRECTORY == null) {
            System.out.print("Fetching user directory... ");
            USER_DIRECTORY = FileSystemView.getFileSystemView().getDefaultDirectory();
            //Mostly for testing, in some environments USER_DIRECTORY can be null
            if (USER_DIRECTORY == null) {
                USER_DIRECTORY = getUserHome();
            }
        }
        return USER_DIRECTORY;
    }


    public static File getHiCDirectory() {

        if (HIC_DIRECTORY == null) {

            // Hack for known Java / Windows bug.   Attempt to remvoe (possible) read-only bit from user directory
            if (System.getProperty("os.name").startsWith("Windows")) {
                try {
                    Runtime.getRuntime().exec("attrib -r \"" + getUserDirectory().getAbsolutePath() + "\"");
                } catch (Exception e) {
                    // We tried
                }
            }

            HIC_DIRECTORY = getHiCDirectoryOverride();

            // If still null, try the default place
            if (HIC_DIRECTORY == null) {
                File rootDir = getUserHome();
                HIC_DIRECTORY = new File(rootDir, "hic");

                if (!HIC_DIRECTORY.exists()) {
                    try {
                        boolean wasSuccessful = HIC_DIRECTORY.mkdir();
                        if (!wasSuccessful) {
                            System.err.println("Failed to create user directory!");
                            HIC_DIRECTORY = null;
                        }
                    } catch (Exception e) {
                        log.error("Error creating hic directory", e);
                    }
                }
            }


            // The IGV directory either doesn't exist or isn't writeable.  This situation can arise with Windows Vista
            // and Windows 7 due to a Java bug (http://bugs.sun.com/view_bug.do?bug_id=4787931)
            if (HIC_DIRECTORY == null || !HIC_DIRECTORY.exists() || !canWrite(HIC_DIRECTORY)) {
                if (Globals.isHeadless() || Globals.isSuppressMessages()) {
                    System.err.println("Cannot write to hic directory: " + HIC_DIRECTORY.getAbsolutePath());
                    HIC_DIRECTORY = (new File(".")).getParentFile();
                } else {
                    int option = JOptionPane.showConfirmDialog(null,
                            "<html>The default IGV directory (" + HIC_DIRECTORY + ") " +
                                    "cannot be accessed.  Click Yes to choose a new folder or No to exit.<br>" +
                                    "This folder will be used to create the 'hic' directory",
                            "IGV Directory Error", JOptionPane.YES_NO_OPTION);

                    if (option == JOptionPane.YES_OPTION) {
                        File parentDirectory = FileDialogUtils.chooseDirectory("Select a location for the hic directory", null);
                        if (parentDirectory != null) {
                            HIC_DIRECTORY = new File(parentDirectory, "hic");
                            HIC_DIRECTORY.mkdir();
                            Preferences prefs = Preferences.userNodeForPackage(Globals.class);
                            prefs.put(HIC_DIR_USERPREF, HIC_DIRECTORY.getAbsolutePath());
                        }
                    }
                }
            }


            if (HIC_DIRECTORY == null || !HIC_DIRECTORY.canRead()) {
                throw new DataLoadException("Cannot read from user directory", HIC_DIRECTORY.getAbsolutePath());
            } else if (!canWrite(HIC_DIRECTORY)) {
                throw new DataLoadException("Cannot write to user directory", HIC_DIRECTORY.getAbsolutePath());
            }

            log.info("IGV Directory: " + HIC_DIRECTORY.getAbsolutePath());
        }
        return HIC_DIRECTORY;
    }

    private static File getHiCDirectoryOverride() {
        Preferences userPrefs = null;
        File override = null;
        try {
            // See if an override location has been specified.  This is stored with the Java Preferences API
            userPrefs = Preferences.userNodeForPackage(Globals.class);
            String userDir = userPrefs.get(HIC_DIR_USERPREF, null);
            if (userDir != null) {
                override = new File(userDir);
                if (!override.exists()) {
                    override = null;
                    userPrefs.remove(HIC_DIR_USERPREF);
                }
            }
        } catch (Exception e) {
            userPrefs.remove(HIC_DIR_USERPREF);
            override = null;
            System.err.println("Error creating user directory");
            e.printStackTrace();
        }
        return override;
    }


    public static synchronized File getLogFile() throws IOException {

        File logFile = new File(getHiCDirectory(), "hic.log");
        if (!logFile.exists()) {
            logFile.createNewFile();
        }
        return logFile;

    }




    private static boolean canWrite(File directory) {
        // There are bugs in the Windows Java JVM that can cause user directories to be non-writable (target fix is
        // Java 7).  The only way to know if the directory is writable for sure is to try to write something.
        if (Globals.IS_WINDOWS) {
            File testFile = null;
            try {
                testFile = new File(directory, "hic332415dsfjdsklt.testfile");
                if (testFile.exists()) {
                    testFile.delete();
                }
                testFile.deleteOnExit();
                testFile.createNewFile();
                return testFile.exists();
            } catch (IOException e) {
                return false;
            } finally {
                if (testFile.exists()) {
                    testFile.delete();
                }
            }
        } else {
            return directory.canWrite();
        }

    }

    public static void initializeLog() {

        Logger logger = Logger.getRootLogger();

        PatternLayout layout = new PatternLayout();
        layout.setConversionPattern("%p [%d{ISO8601}] [%F:%L]  %m%n");

        // Create a log file that is ready to have text appended to it
        try {
            File logFile = getLogFile();
            RollingFileAppender appender = new RollingFileAppender();
            appender.setName("IGV_ROLLING_APPENDER");
            appender.setFile(logFile.getAbsolutePath());
            appender.setThreshold(Level.ALL);
            appender.setMaxFileSize("1000KB");
            appender.setMaxBackupIndex(1);
            appender.setLayout(layout);
            appender.setAppend(true);
            appender.activateOptions();
            logger.addAppender(appender);

        } catch (IOException e) {
            // Can't create log file, just log to console
            System.err.println("Error creating log file");
            e.printStackTrace();
            ConsoleAppender consoleAppender = new ConsoleAppender();
            logger.addAppender(consoleAppender);
        }
    }
}
