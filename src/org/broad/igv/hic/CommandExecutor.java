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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.broad.igv.hic;

import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.PreferenceManager;
import org.broad.igv.feature.Locus;
import org.broad.igv.feature.RegionOfInterest;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.renderer.DataRange;
import org.broad.igv.sam.AlignmentTrack;
import org.broad.igv.track.RegionScoreType;
import org.broad.igv.track.Track;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.ui.util.SnapshotUtilities;
import org.broad.igv.util.*;
import org.broad.igv.util.collections.LRUCache;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;
import java.util.List;

public class CommandExecutor {

    private static Logger log = Logger.getLogger(CommandExecutor.class);
    HiC hic;

    public CommandExecutor(HiC hic) {
        this.hic = hic;
    }

    private List<String> getArgs(String[] tokens) {
        List<String> args = new ArrayList(tokens.length);
        for (String s : tokens) {
            if (s.trim().length() > 0) {
                args.add(s.trim());
            }
        }
        return args;
    }

    public String execute(String command) {

        List<String> args = getArgs(StringUtils.breakQuotedString(command, ' ').toArray(new String[]{}));

        String result = "OK";
        log.debug("Executing: " + command);
        try {
            if (args.size() > 0) {

                String cmd = args.get(0).toLowerCase();
                if (cmd.equals("setstate")) {
                    //    public void setState(String chrXName, String chrYName, String unitName, int binSize, int xOrigin, int yOrigin, double scalefactor) {
                    if (args.size() > 7) {
                        String chrXName = args.get(1);
                        String chrYName = args.get(2);
                        String unitName = args.get(3);
                        int binSize = Integer.parseInt(args.get(4));
                        double xOrigin = Double.parseDouble(args.get(5));
                        double yOrigin = Double.parseDouble(args.get(6));
                        double scaleFactor = Double.parseDouble(args.get(7));
                        hic.setState(chrXName, chrYName, unitName, binSize, xOrigin, yOrigin, scaleFactor);
                    } else {
                        result = "Not enough parameters";
                    }
                }
            } else {
                result = "Unknown command string";
            }

        } catch (Exception e) {
            log.error(e);
            result = "Error: " + e.getMessage();
        }
        return result;
    }

}
