package org.broad.igv.hic;

import com.jidesoft.swing.JidePopupMenu;
import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.hic.track.Feature2D;
import org.broad.igv.hic.data.ExpectedValueFunction;
import org.broad.igv.hic.data.MatrixZoomData;
import org.broad.igv.hic.track.HiCFragmentAxis;
import org.broad.igv.hic.track.HiCGridAxis;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.ObjectCache;
import org.broad.igv.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * @author jrobinso
 * @date Aug 2, 2010
 */
public class HeatmapPanel extends JComponent implements Serializable {

    private static Logger log = Logger.getLogger(HeatmapPanel.class);

    private NumberFormat formatter = NumberFormat.getInstance();

    enum DragMode {NONE, PAN, ZOOM, SELECT}

    private MainWindow mainWindow;
    private HiC hic;
    private Point lastCursorPoint;

    /**
     * Image tile width in pixels
     */
    private int imageTileWidth = 500;

    private ObjectCache<String, ImageTile> tileCache = new ObjectCache<String, ImageTile>(26);
    private Rectangle zoomRectangle;

    private transient List<Pair<Rectangle, Feature2D>> drawnLoopFeatures;

    /**
     * Chromosome boundaries in kbases for whole genome view.
     */
    private int[] chromosomeBoundaries;

    HeatmapRenderer renderer;

    public HeatmapPanel(MainWindow mainWindow, HiC hic) {
        this.mainWindow = mainWindow;
        this.hic = hic;
        renderer = new HeatmapRenderer(mainWindow, hic);
        final HeatmapMouseHandler mouseHandler = new HeatmapMouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        drawnLoopFeatures = new ArrayList<Pair<Rectangle, Feature2D>>();
        //setToolTipText(""); // Turns tooltip on
    }

    public void reset() {
        renderer.reset();
        clearTileCache();
    }

    public void setObservedRange(double min, double max) {
        renderer.setObservedRange(min, max);
        clearTileCache();
        repaint();
    }

    public void setOEMax(double max) {
        renderer.setOEMax(max);
        clearTileCache();
        repaint();

    }

    public void setChromosomeBoundaries(int[] chromosomeBoundaries) {
        this.chromosomeBoundaries = chromosomeBoundaries;
    }


    public int getMinimumDimension() {
        return Math.min(getWidth(), getHeight());
    }


    @Override
    protected void paintComponent(Graphics g) {

        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle clipBounds = g.getClipBounds();
        g.clearRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);

        // Are we ready to draw?
        final MatrixZoomData zd = hic.getZd();
        if (hic == null || zd == null || hic.getXContext() == null) return;

        if (hic.getDisplayOption() == MainWindow.MatrixType.PEARSON &&
                zd.getPearsons(hic.getNormalizationType()) == null) {
            // Possibly force asynchronous computation of pearsons
            if (!zd.isSmallEnoughForPearsonCalculation(hic.getNormalizationType())) {
                MessageUtils.showMessage("Pearson's matrix is not available at this resolution");
                return;
            } else {
                MainWindow.getInstance().showGlassPane();
                final ExpectedValueFunction df = hic.getDataset().getExpectedValues(zd.getZoom(), hic.getNormalizationType());
                zd.computePearsons(df, hic.getNormalizationType());
                MainWindow.getInstance().hideGlassPane();
            }
        }


        // Same scale used for X & Y (square pixels)
        final double scaleFactor = hic.getXContext().getScaleFactor();

        final int screenWidth = getBounds().width;
        final int screenHeight = getBounds().height;
        double binOriginX = hic.getXContext().getBinOrigin();
        double bRight = binOriginX + (screenWidth / scaleFactor);
        double binOriginY = hic.getYContext().getBinOrigin();
        double bBottom = binOriginY + (screenHeight / scaleFactor);

        // tile numbers
        int tLeft = (int) (binOriginX / imageTileWidth);
        int tRight = (int) Math.ceil(bRight / imageTileWidth);
        int tTop = (int) (binOriginY / imageTileWidth);
        int tBottom = (int) Math.ceil(bBottom / imageTileWidth);

        for (int tileRow = tTop; tileRow <= tBottom; tileRow++) {
            for (int tileColumn = tLeft; tileColumn <= tRight; tileColumn++) {

                ImageTile tile = getImageTile(zd, tileRow, tileColumn, hic.getDisplayOption());
                if (tile != null) {

                    int imageWidth = tile.image.getWidth(null);
                    int imageHeight = tile.image.getHeight(null);

                    int xSrc0 = 0;
                    int xSrc1 = imageWidth;
                    int ySrc0 = 0;
                    int ySrc1 = imageHeight;

                    int xDest0 = (int) ((tile.bLeft - binOriginX) * scaleFactor);
                    int xDest1 = (int) ((tile.bLeft + imageWidth - binOriginX) * scaleFactor);
                    int yDest0 = (int) ((tile.bTop - binOriginY) * scaleFactor);
                    int yDest1 = (int) ((tile.bTop + imageHeight - binOriginY) * scaleFactor);

                    // Trim off edges that are out of view -- take care if you attempt to simplify or rearrange this,
                    // its easy to introduce alias and round-off errors due to the int casts.  I suggest leaving it alone.
                    Rectangle bounds = getBounds();
                    final int screenRight = bounds.x + bounds.width;
                    final int screenBottom = bounds.y + bounds.height;
                    if (xDest0 < 0) {
                        int leftExcess = (int) (-xDest0 / scaleFactor);
                        xSrc0 += leftExcess;
                        xDest0 = (int) ((tile.bLeft - binOriginX + leftExcess) * scaleFactor);
                    }
                    if (xDest1 > screenRight) {
                        int rightExcess = (int) ((xDest1 - screenRight) / scaleFactor);
                        xSrc1 -= rightExcess;
                        xDest1 = (int) ((tile.bLeft + imageWidth - binOriginX - rightExcess) * scaleFactor);
                    }
                    if (yDest0 < 0) {
                        int topExcess = (int) (-yDest0 / scaleFactor);
                        ySrc0 += topExcess;
                        yDest0 = (int) ((tile.bTop - binOriginY + topExcess) * scaleFactor);
                    }
                    if (yDest1 > screenBottom) {
                        int bottomExcess = (int) ((yDest1 - screenBottom) / scaleFactor);
                        ySrc1 -= bottomExcess;
                        yDest1 = (int) ((tile.bTop + imageHeight - binOriginY - bottomExcess) * scaleFactor);
                    }

                    g.drawImage(tile.image, xDest0, yDest0, xDest1, yDest1, xSrc0, ySrc0, xSrc1, ySrc1, null);

                    // Uncomment to draw tile grid (for debugging)
                    // g.drawRect((int) xDest0, (int) yDest0, (int) (xDest1 - xDest0), (int) (yDest1 - yDest0));

                }
            }


            // Uncomment to draw bin grid (for debugging)
//            Graphics2D g2 = (Graphics2D) g.create();
//            g2.setColor(Color.green);
//            g2.setColor(new Color(0, 0, 1.0f, 0.3f));
//            for (int bin = (int) binOriginX; bin <= bRight; bin++) {
//                int pX = (int) ((bin - hic.getXContext().getBinOrigin()) * hic.getXContext().getScaleFactor());
//                g2.drawLine(pX, 0, pX, getHeight());
//            }
//            for (int bin = (int) binOriginY; bin <= bBottom; bin++) {
//                int pY = (int) ((bin - hic.getYContext().getBinOrigin()) * hic.getYContext().getScaleFactor());
//                g2.drawLine(0, pY, getWidth(), pY);
//            }
//            g2.dispose();

            boolean isWholeGenome = (hic.getXContext().getChromosome().getName().equals("All") &&
                    hic.getYContext().getChromosome().getName().equals("All"));


            // Draw grid
            if (isWholeGenome) {
                Color color = g.getColor();
                g.setColor(Color.lightGray);

                List<Chromosome> chromosomes = hic.getChromosomes();
                // Index 0 is whole genome
                int xGenomeCoord = 0;
                for (int i = 1; i < chromosomes.size(); i++) {
                    Chromosome c = chromosomes.get(i);
                    xGenomeCoord += (c.getLength() / 1000);
                    int xBin = zd.getXGridAxis().getBinNumberForGenomicPosition(xGenomeCoord);
                    int x = (int) (xBin * scaleFactor);
                    g.drawLine(x, 0, x, getHeight());
                }
                int yGenomeCoord = 0;
                for (int i = 1; i < chromosomes.size(); i++) {
                    Chromosome c = chromosomes.get(i);
                    yGenomeCoord += (c.getLength() / 1000);
                    int yBin = zd.getYGridAxis().getBinNumberForGenomicPosition(yGenomeCoord);
                    int y = (int) (yBin * hic.getYContext().getScaleFactor());
                    g.drawLine(0, y, getWidth(), y);
                }

                g.setColor(color);
            }

            Point cursorPoint = hic.getCursorPoint();
            if (cursorPoint != null) {
                g.setColor(MainWindow.RULER_LINE_COLOR);
                g.drawLine(cursorPoint.x, 0, cursorPoint.x, getHeight());
                g.drawLine(0, cursorPoint.y, getWidth(), cursorPoint.y);
            }

        }

        // Render loops
        drawnLoopFeatures.clear();

        List<Feature2D> loops = hic.getVisibleLoopList(zd.getChr1Idx(), zd.getChr2Idx());
        Graphics2D loopGraphics = (Graphics2D) g.create();
        if (loops != null && loops.size() > 0) {

            // Note: we're assuming feature.chr1 == zd.chr1, and that chr1 is on x-axis
            HiCGridAxis xAxis = zd.getXGridAxis();
            HiCGridAxis yAxis = zd.getYGridAxis();
            boolean sameChr = zd.getChr1Idx() == zd.getChr2Idx();

            for (Feature2D feature : loops) {

                loopGraphics.setColor(feature.getColor());

                int binStart1 = xAxis.getBinNumberForGenomicPosition(feature.getStart1());
                int binEnd1 = xAxis.getBinNumberForGenomicPosition(feature.getEnd1());
                int binStart2 = yAxis.getBinNumberForGenomicPosition(feature.getStart2());
                int binEnd2 = yAxis.getBinNumberForGenomicPosition(feature.getEnd2());

                int x = (int) ((binStart1 - binOriginX) * scaleFactor);
                int y = (int) ((binStart2 - binOriginY) * scaleFactor);
                int w = (int) Math.max(1, scaleFactor * (binEnd1 - binStart1));
                int h = (int) Math.max(1, scaleFactor * (binEnd2 - binStart2));
                loopGraphics.drawRect(x, y, w, h);
                //loopGraphics.drawLine(x,y,x,y+w);
                //loopGraphics.drawLine(x,y+w,x+h,y+w);
                System.out.println(binStart1 + "-" + binEnd1);
                if (w > 5) {
                    // Thick line if there is room.
                    loopGraphics.drawRect(x + 1, y + 1, w - 2, h - 2);
                 //   loopGraphics.drawLine(x+1,y+1,x+1,y+w-1);
                 //   loopGraphics.drawLine(x+1,y+w-1,x+h-1,y+w-1);
                }
                drawnLoopFeatures.add(new Pair<Rectangle, Feature2D>(new Rectangle(x - 1, y - 1, w + 2, h + 2), feature));

                if (sameChr && !(binStart1 == binStart2 && binEnd1 == binEnd2)) {
                    x = (int) ((binStart2 - binOriginX) * scaleFactor);
                    y = (int) ((binStart1 - binOriginY) * scaleFactor);
                    w = (int) Math.max(1, scaleFactor * (binEnd2 - binStart2));
                    h = (int) Math.max(1, scaleFactor * (binEnd1 - binStart1));
                    loopGraphics.drawRect(x, y, w, h);
                    if (w > 5) {
                        loopGraphics.drawRect(x, y, w, h);
                    }
                    drawnLoopFeatures.add(new Pair<Rectangle, Feature2D>(new Rectangle(x - 1, y - 1, w + 2, h + 2), feature));
                }

            }

            loopGraphics.dispose();
        }

        if (zoomRectangle != null) {
            ((Graphics2D) g).draw(zoomRectangle);
        }


        //UNCOMMENT TO OUTLINE "selected" BIN
//        if(hic.getSelectedBin() != null) {
//            int pX = (int) ((hic.getSelectedBin().x - hic.xContext.getBinOrigin()) * hic.xContext.getScaleFactor());
//            int pY = (int) ((hic.getSelectedBin().y - hic.yContext.getBinOrigin()) * hic.yContext.getScaleFactor());
//            int w = (int) hic.xContext.getScaleFactor() - 1;
//            int h = (int) hic.yContext.getScaleFactor() - 1;
//            g.setColor(Color.green);
//            g.drawRect(pX, pY, w, h);
//        }
    }

    public Image getThumbnailImage(MatrixZoomData zd0, int tw, int th, MainWindow.MatrixType displayOption) {
        if (hic.getDisplayOption() == MainWindow.MatrixType.PEARSON &&
                zd0.getPearsons(hic.getNormalizationType()) == null) {
            if (!zd0.isSmallEnoughForPearsonCalculation(hic.getNormalizationType())) {
                MessageUtils.showMessage("Pearson's matrix is not available at this resolution");
                return null;
            } else {
                final ExpectedValueFunction df = hic.getDataset().getExpectedValues(zd0.getZoom(), hic.getNormalizationType());
                zd0.computePearsons(df, hic.getNormalizationType());
            }
        }
        int maxBinCountX = zd0.getXGridAxis().getBinCount();
        int maxBinCountY = zd0.getYGridAxis().getBinCount();

        int wh = Math.max(maxBinCountX, maxBinCountY);

        BufferedImage image = (BufferedImage) createImage(wh, wh);
        Graphics2D g = image.createGraphics();
        boolean success = renderer.render(0, 0, maxBinCountX, maxBinCountY, zd0, displayOption, g);
        if (!success) return null;

        return image.getScaledInstance(tw, th, Image.SCALE_REPLICATE);

    }

    /**
     * Return the specified image tile, scaled by scaleFactor
     *
     * @param zd
     * @param tileRow    row index of tile
     * @param tileColumn column index of tile
     * @return
     */
    private ImageTile getImageTile(MatrixZoomData zd, int tileRow, int tileColumn, MainWindow.MatrixType displayOption) {

        String key = zd.getKey() + "_" + tileRow + "_" + tileColumn + "_ " + displayOption;
        ImageTile tile = tileCache.get(key);

        if (tile == null) {

            // Image size can be smaller than tile width when zoomed out, or near the edges.
            int maxBinCountX = hic.getZd().getXGridAxis().getBinCount();
            int maxBinCountY = hic.getZd().getYGridAxis().getBinCount();

            if (maxBinCountX < 0 || maxBinCountY < 0) return null;

            int imageWidth = maxBinCountX < imageTileWidth ? maxBinCountX : imageTileWidth;
            int imageHeight = maxBinCountY < imageTileWidth ? maxBinCountY : imageTileWidth;

            BufferedImage image = (BufferedImage) createImage(imageWidth, imageHeight);
            Graphics2D g2D = (Graphics2D) image.getGraphics();

            final int bx0 = tileColumn * imageTileWidth;
            final int by0 = tileRow * imageTileWidth;
            renderer.render(bx0, by0, imageWidth, imageHeight, hic.getZd(), displayOption, g2D);

            //           if (scaleFactor > 0.999 && scaleFactor < 1.001) {
            tile = new ImageTile(image, bx0, by0);

            tileCache.put(key, tile);
        }
        return tile;
    }


    public void clearTileCache() {
        tileCache.clear();
    }


    static class ImageTile {
        int bLeft;
        int bTop;
        Image image;

        ImageTile(Image image, int bLeft, int py0) {
            this.bLeft = bLeft;
            this.bTop = py0;
            this.image = image;
        }
    }


    class HeatmapMouseHandler extends MouseAdapter {


        DragMode dragMode = DragMode.NONE;
        private Point lastMousePoint;

        @Override
        public void mouseEntered(MouseEvent e) {
            if (straightEdgeEnabled) {
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            hic.setCursorPoint(null);
            lastCursorPoint = null;
            if (straightEdgeEnabled) {
                mainWindow.trackPanelX.repaint();
                mainWindow.trackPanelY.repaint();
            }
        }

        @Override
        public void mousePressed(final MouseEvent e) {

            if (hic.isWholeGenome()) {
                return;
            }

            if (e.isPopupTrigger()) {
                getPopupMenu().show(HeatmapPanel.this, e.getX(), e.getY());
            } else if (e.isAltDown()) {
                dragMode = DragMode.ZOOM;
            } else {
                dragMode = DragMode.PAN;
                setCursor(mainWindow.fistCursor);
            }


            lastMousePoint = e.getPoint();

        }


        @Override
        public void mouseReleased(final MouseEvent e) {

            if (e.isPopupTrigger()) {
                getPopupMenu().show(HeatmapPanel.this, e.getX(), e.getY());

            } else if ((dragMode == DragMode.ZOOM || dragMode == DragMode.SELECT) && zoomRectangle != null) {

                double binX = hic.getXContext().getBinOrigin() + (zoomRectangle.x / hic.getXContext().getScaleFactor());
                double binY = hic.getYContext().getBinOrigin() + (zoomRectangle.y / hic.getYContext().getScaleFactor());
                double wBins = (int) (zoomRectangle.width / hic.getXContext().getScaleFactor());
                double hBins = (int) (zoomRectangle.height / hic.getXContext().getScaleFactor());

                final MatrixZoomData currentZD = hic.getZd();
                int xBP0 = currentZD.getXGridAxis().getGenomicStart(binX);
                int xBP1 = currentZD.getXGridAxis().getGenomicEnd(binX + wBins);

                int yBP0 = currentZD.getYGridAxis().getGenomicEnd(binY);
                int yBP1 = currentZD.getYGridAxis().getGenomicEnd(binY + hBins);

                double newXBinSize = wBins * currentZD.getBinSize() / getWidth();
                double newYBinSize = hBins * currentZD.getBinSize() / getHeight();
                double newBinSize = Math.max(newXBinSize, newYBinSize);

                hic.zoomTo(xBP0, yBP0, newBinSize);
            }

            dragMode = DragMode.NONE;
            lastMousePoint = null;
            zoomRectangle = null;
            setCursor(straightEdgeEnabled ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : Cursor.getDefaultCursor());
            //repaint();
        }


        @Override
        final public void mouseDragged(final MouseEvent e) {

            if (hic.getZd() == null || hic.isWholeGenome()) {
                return;
            }

            if (lastMousePoint == null) {
                lastMousePoint = e.getPoint();
                return;
            }

            int deltaX = e.getX() - lastMousePoint.x;
            int deltaY = e.getY() - lastMousePoint.y;
            switch (dragMode) {
                case ZOOM:
                    Rectangle lastRectangle = zoomRectangle;

                    if (deltaX == 0 || deltaY == 0) {
                        return;
                    }

                    // Constrain aspect ratio of zoom rectangle to that of panel
                    double aspectRatio = (double) getWidth() / getHeight();
                    if (deltaX * aspectRatio > deltaY) {
                        deltaY = (int) (deltaX / aspectRatio);
                    } else {
                        deltaX = (int) (deltaY * aspectRatio);
                    }


                    int x = deltaX > 0 ? lastMousePoint.x : lastMousePoint.x + deltaX;
                    int y = deltaY > 0 ? lastMousePoint.y : lastMousePoint.y + deltaY;
                    zoomRectangle = new Rectangle(x, y, (int) Math.abs(deltaX), (int) Math.abs(deltaY));

                    Rectangle damageRect = lastRectangle == null ? zoomRectangle : zoomRectangle.union(lastRectangle);
                    damageRect.x--;
                    damageRect.y--;
                    damageRect.width += 2;
                    damageRect.height += 2;
                    paintImmediately(damageRect);

                    break;
                default:

                    // int dx = (int) (deltaX * hic.xContext.getScale());
                    // int dy = (int) (deltaY * hic.yContext.getScale());
                    lastMousePoint = e.getPoint();    // Always save the last Point

                    double deltaXBins = -deltaX / hic.getXContext().getScaleFactor();
                    double deltaYBins = -deltaY / hic.getXContext().getScaleFactor();
                    hic.moveBy(deltaXBins, deltaYBins);

            }

        }

        @Override
        public void mouseClicked(MouseEvent e) {

            if (hic == null) return;

            if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1 && !e.isControlDown()) {

                if (hic.isWholeGenome()) {
                    double binX = hic.getXContext().getBinOrigin() + (e.getX() / hic.getXContext().getScaleFactor());
                    double binY = hic.getYContext().getBinOrigin() + (e.getY() / hic.getYContext().getScaleFactor());

                    int xGenome = hic.getZd().getXGridAxis().getGenomicMid(binX);
                    int yGenome = hic.getZd().getYGridAxis().getGenomicMid(binY);

                    Chromosome xChrom = null;
                    Chromosome yChrom = null;
                    for (int i = 0; i < chromosomeBoundaries.length; i++) {
                        if (xChrom == null && chromosomeBoundaries[i] > xGenome) {
                            xChrom = hic.getChromosomes().get(i + 1);
                        }
                        if (yChrom == null && chromosomeBoundaries[i] > yGenome) {
                            yChrom = hic.getChromosomes().get(i + 1);
                        }
                        if (xChrom != null && yChrom != null) {
                            mainWindow.setSelectedChromosomes(xChrom, yChrom);
                        }
                    }


                } else if (e.getClickCount() > 1) {

                    // Double click,  zoom and center on click location
                    final HiCZoom currentZoom = hic.getZd().getZoom();
                    HiCZoom newZoom = mainWindow.isResolutionLocked() ? currentZoom :
                            hic.getDataset().getNextZoom(currentZoom, !e.isAltDown());

                    // If newZoom == currentZoom adjust scale factor (no change in resolution)
                    double centerBinX = hic.getXContext().getBinOrigin() + (e.getX() / hic.getXContext().getScaleFactor());
                    double centerBinY = hic.getYContext().getBinOrigin() + (e.getY() / hic.getYContext().getScaleFactor());

                    if (newZoom.equals(currentZoom)) {
                        double mult = e.isAltDown() ? 0.5 : 2.0;
                        double newScaleFactor = Math.max(1.0, hic.getXContext().getScaleFactor() * mult);
                        hic.getXContext().setScaleFactor(newScaleFactor);
                        hic.getYContext().setScaleFactor(newScaleFactor);
                        hic.getXContext().setBinOrigin(Math.max(0, (int) (centerBinX - (getWidth() / (2 * newScaleFactor)))));
                        hic.getYContext().setBinOrigin(Math.max(0, (int) (centerBinY - (getHeight() / (2 * newScaleFactor)))));
                        mainWindow.repaint();
                    } else {

                        int xGenome = hic.getZd().getXGridAxis().getGenomicMid(centerBinX);
                        int yGenome = hic.getZd().getYGridAxis().getGenomicMid(centerBinY);

                        hic.setZoom(newZoom, xGenome, yGenome);
                        mainWindow.updateZoom(newZoom);
                    }

                } else {

                    if (hic == null || hic.getXContext() == null) return;

                    int binX = (int) (hic.getXContext().getBinOrigin() + e.getX() / hic.getXContext().getScaleFactor());
                    int binY = (int) (hic.getYContext().getBinOrigin() + e.getY() / hic.getYContext().getScaleFactor());

                    hic.setSelectedBin(new Point(binX, binY));
                    repaint();

                    //If IGV is running open on loci
                    if (e.isShiftDown()) {

//                        String chr1 = hic.xContext.getChromosome().getName();
//                        int leftX = (int) hic.xContext.getChromosomePosition(0);
//                        int wX = (int) (hic.xContext.getScale() * getWidth());
//                        int rightX = leftX + wX;
//
//                        String chr2 = hic.yContext.getChromosome().getName();
//                        int leftY = (int) hic.yContext.getChromosomePosition(0);
//                        int wY = (int) (hic.xContext.getScale() * getHeight());
//                        int rightY = leftY + wY;
//
//                        String locus1 = "chr" + chr1 + ":" + leftX + "-" + rightX;
//                        String locus2 = "chr" + chr2 + ":" + leftY + "-" + rightY;
//
//                        IGVUtils.sendToIGV(locus1, locus2);
                    }
                }

            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            if (hic.getXContext() != null && hic.getZd() != null) {

                mainWindow.updateToolTipText(toolTipText(e.getX(), e.getY()));

                if (straightEdgeEnabled) {
                    synchronized (this) {
                        hic.setCursorPoint(e.getPoint());

//                        // Main panel
//                        Rectangle damageRectX = new Rectangle();
//                        damageRectX.x = (lastCursorPoint != null ? Math.min(lastCursorPoint.x, e.getX()) : e.getX()) - 1;
//                        damageRectX.y = 0;
//                        damageRectX.width = lastCursorPoint == null ? 2 : Math.abs(e.getX() - lastCursorPoint.x) + 2;
//                        damageRectX.height = getHeight();
//                        paintImmediately(damageRectX);
//
//                        Rectangle damageRectY = new Rectangle();
//                        damageRectY.x = 0;
//                        damageRectY.y = (lastCursorPoint != null ? Math.min(lastCursorPoint.y, e.getY()) : e.getY()) - 1;
//                        damageRectY.width = getWidth();
//                        damageRectY.height = lastCursorPoint == null ? 2 : Math.abs(e.getY() - lastCursorPoint.y) + 2;
//                        paintImmediately(damageRectY);
//
//                        // Track panels
//                        damageRectX.height = mainWindow.trackPanelX.getHeight();
//                        mainWindow.trackPanelX.paintImmediately(damageRectX);
//
//                        damageRectY.width = mainWindow.trackPanelY.getWidth();
//                        mainWindow.trackPanelY.paintImmediately(damageRectY);

                        repaint();
                        mainWindow.trackPanelX.repaint();
                        mainWindow.trackPanelY.repaint();

                        lastCursorPoint = e.getPoint();
                    }

                }


            }
        }
    }


    boolean straightEdgeEnabled = false;

    JidePopupMenu getPopupMenu() {

        JidePopupMenu menu = new JidePopupMenu();

        final JCheckBoxMenuItem mi = new JCheckBoxMenuItem("Enable straight edge");
        mi.setSelected(straightEdgeEnabled);
        mi.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (mi.isSelected()) {
                    straightEdgeEnabled = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                } else {
                    straightEdgeEnabled = false;
                    lastCursorPoint = null;
                    hic.setCursorPoint(null);
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                    mainWindow.trackPanelX.repaint();
                }

            }
        });
        menu.add(mi);

        final JMenuItem mi2 = new JMenuItem("Goto ...");
        mi2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String fragmentString = JOptionPane.showInputDialog(HeatmapPanel.this,
                        "Enter fragment or bp range in the form <bp|frag>:x:y");
                if (fragmentString != null) {
                    String[] tokens = Globals.colonPattern.split(fragmentString);
                    HiC.Unit unit = HiC.Unit.FRAG;
                    int idx = 0;
                    if (tokens.length == 3) {
                        if (tokens[idx++].toLowerCase().equals("bp")) {
                            unit = HiC.Unit.BP;
                        }
                    }
                    int x = Integer.parseInt(tokens[idx++].replace(",", ""));
                    int y = (tokens.length > idx) ? Integer.parseInt(tokens[idx].replace(",", "")) : x;

                    if (unit == HiC.Unit.FRAG) {
                        hic.centerFragment(x, y);
                    } else {
                        hic.centerBP(x, y);
                    }


                }
            }
        });

        final JMenuItem mi3 = new JMenuItem("Sync");
        mi3.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hic.broadcastState();
            }
        });


        final JCheckBoxMenuItem mi4 = new JCheckBoxMenuItem("Linked");
        mi4.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final boolean isLinked = mi4.isSelected();
                if(isLinked) {
                    hic.broadcastState();
                }
                hic.setLinkedMode(isLinked);
            }
        });

        if (hic != null) {
            menu.add(mi2);
            menu.add(mi3);

            mi4.setSelected(hic.isLinkedMode());
            menu.add(mi4);
        }


        return menu;

    }


//    @Override
//    public String getToolTipText(MouseEvent e) {
//        return toolTipText(e.getX(), e.getY());
//
//    }

    private String toolTipText(int x, int y) {
        // Update popup text
        final MatrixZoomData zd = hic.getZd();
        if (zd == null) return "";
        HiCGridAxis xGridAxis = zd.getXGridAxis();
        HiCGridAxis yGridAxis = zd.getYGridAxis();

        int binX = (int) (hic.getXContext().getBinOrigin() + x / hic.getXContext().getScaleFactor());
        int binY = (int) (hic.getYContext().getBinOrigin() + y / hic.getYContext().getScaleFactor());

        int xGenomeStart = xGridAxis.getGenomicStart(binX) + 1; // Conversion from in internal "0" -> 1 base coordinates
        int yGenomeStart = yGridAxis.getGenomicStart(binY) + 1;
        int xGenomeEnd = xGridAxis.getGenomicEnd(binX);
        int yGenomeEnd = yGridAxis.getGenomicEnd(binY);

        if (hic.isWholeGenome()) {

            Chromosome xChrom = null;
            Chromosome yChrom = null;
            for (int i = 0; i < chromosomeBoundaries.length; i++) {
                if (xChrom == null && chromosomeBoundaries[i] > xGenomeStart) {
                    xChrom = hic.getChromosomes().get(i + 1);
                }
                if (yChrom == null && chromosomeBoundaries[i] > yGenomeStart) {
                    yChrom = hic.getChromosomes().get(i + 1);
                }
                if (xChrom != null && yChrom != null) {

                    int leftBoundaryX = xChrom.getIndex() == 1 ? 0 : chromosomeBoundaries[xChrom.getIndex() - 2];
                    int leftBoundaryY = yChrom.getIndex() == 1 ? 0 : chromosomeBoundaries[yChrom.getIndex() - 2];


                    int xChromPos = (int) ((xGenomeStart - leftBoundaryX) * 1000);
                    int yChromPos = (int) ((yGenomeStart - leftBoundaryY) * 1000);

                    StringBuffer txt = new StringBuffer();
                    txt.append("<html>");
                    txt.append(xChrom.getName());
                    txt.append(":");
                    txt.append(String.valueOf(xChromPos));
                    txt.append("<br>");

                    txt.append(yChrom.getName());
                    txt.append(":");
                    txt.append(String.valueOf(yChromPos));

                    return txt.toString();

                }
            }

        } else {


            //int binX = (int) ((mainWindow.xContext.getOrigin() + e.getX() * mainWindow.xContext.getScale()) / getBinWidth());
            //int binY = (int) ((mainWindow.yContext.getOrigin() + e.getY() * mainWindow.yContext.getScale()) / getBinWidth());
            StringBuffer txt = new StringBuffer();
            txt.append("<html>");
            txt.append(hic.getXContext().getChromosome().getName());
            txt.append(":");
            txt.append(formatter.format(xGenomeStart));
            txt.append("-");
            txt.append(formatter.format(xGenomeEnd));

            if (xGridAxis instanceof HiCFragmentAxis) {
                String fragNumbers;
                int binSize = zd.getZoom().getBinSize();
                if (binSize == 1) {
                    fragNumbers = formatter.format(binX);
                } else {
                    int leftFragment = binX * binSize;
                    int rightFragment = ((binX + 1) * binSize) - 1;
                    fragNumbers = formatter.format(leftFragment) + "-" + formatter.format(rightFragment);
                }
                String len = formatter.format(xGenomeEnd - xGenomeStart);
                txt.append("  (" + fragNumbers + "  len=" + len + ")");
            }

            txt.append("<br>");
            txt.append(hic.getYContext().getChromosome().getName());
            txt.append(":");
            txt.append(formatter.format(yGenomeStart));
            txt.append("-");
            txt.append(formatter.format(yGenomeEnd));

            if (yGridAxis instanceof HiCFragmentAxis) {
                String fragNumbers;
                int binSize = zd.getZoom().getBinSize();
                if (binSize == 1) {
                    fragNumbers = formatter.format(binY);
                } else {
                    int leftFragment = binY * binSize;
                    int rightFragment = ((binY + 1) * binSize) - 1;
                    fragNumbers = formatter.format(leftFragment) + "-" + formatter.format(rightFragment);
                }
                String len = formatter.format(yGenomeEnd - yGenomeStart);
                txt.append("  (" + fragNumbers + "  len=" + len + ")");
            }

            if (hic.getDisplayOption() == MainWindow.MatrixType.PEARSON) {
                float value = zd.getPearsonValue(binX, binY, hic.getNormalizationType());
                txt.append("<br>value = " + value);
            } else {
                float value = hic.getNormalizedObservedValue(binX, binY);
                txt.append("<br>observed value = " + getFloatString(value));

                int c1 = hic.getXContext().getChromosome().getIndex();
                int c2 = hic.getYContext().getChromosome().getIndex();
                double ev = 0;
                if (c1 == c2) {
                    ExpectedValueFunction df = hic.getExpectedValues();
                    if (df != null) {
                        int distance = Math.abs(binX - binY);
                        ev = df.getExpectedValue(c1, distance);
                    }
                } else {
                    ev = zd.getAverageCount();
                }
                /* if (hic.getNormalizationOption() != MainWindow.NormalizationOption.NONE) {
                    double normSumRatio = hic.getNormSumRatio();
                    ev = ev*normSumRatio;
                }*/

                String evString = ev < 0.001 || Double.isNaN(ev) ? String.valueOf(ev) : formatter.format(ev);
                txt.append("<br>expected value = " + evString);
                if (ev > 0 && !Float.isNaN(value)) {
                    txt.append("<br>O/E            = " + formatter.format(value / ev));
                } else {
                    txt.append("<br>O/E            = NaN");
                }

                MatrixZoomData controlZD = hic.getControlZd();
                if (controlZD != null) {
                    float controlValue = controlZD.getObservedValue(binX, binY, hic.getNormalizationType());
                    txt.append("<br><br>control value = " + getFloatString(controlValue));

                    double obsValue = (value / zd.getAverageCount());
                    txt.append("<br>observed/average = " + getFloatString((float) obsValue));

                    double ctlValue = (float) (controlValue / controlZD.getAverageCount());
                    txt.append("<br>control/average = " + getFloatString((float) ctlValue));

                    if (value > 0 && controlValue > 0) {
                        double ratio = obsValue / ctlValue;
                        txt.append("<br>O'/C' = " + getFloatString((float) ratio));

                    }

                }


            }

            for (Pair<Rectangle, Feature2D> loop : drawnLoopFeatures) {
                if (loop.getFirst().contains(x, y)) {
                    txt.append("<br><br>");
                    txt.append(loop.getSecond().tooltipText());
                }
            }


            return txt.toString();
        }

        return null;
    }

    private String getFloatString(float value) {
        String valueString;
        if (Float.isNaN(value)) {
            valueString = "NaN";
        } else if (value < 0.001) {
            valueString = String.valueOf(value);
        } else {
            valueString = formatter.format(value);
        }
        return valueString;
    }

}
