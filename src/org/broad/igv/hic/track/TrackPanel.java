package org.broad.igv.hic.track;

import org.broad.igv.hic.Context;
import org.broad.igv.hic.HiC;
import org.broad.igv.hic.MainWindow;
import org.broad.igv.renderer.GraphicUtils;
import org.broad.igv.ui.FontManager;
import org.broad.igv.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: neva
 * Date: 4/3/12
 * Time: 4:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class TrackPanel extends JPanel {

    private MouseAdapter mouseAdapter;

    public enum Orientation {X, Y}

    HiC hic;
    Orientation orientation;
    HiCTrack eigenvectorTrack;
    Collection<Pair<Rectangle, HiCTrack>> trackRectangles;
    MainWindow mainWindow;

    public TrackPanel(MainWindow mainWindow, HiC hiC, Orientation orientation) {
        this.mainWindow = mainWindow;
        this.hic = hiC;
        this.orientation = orientation;
        setAutoscrolls(true);
        trackRectangles = new ArrayList<Pair<Rectangle, HiCTrack>>();
        //setBackground(new Color(238, 238, 238));
        setBackground(Color.white);
        addMouseAdapter();

        //setToolTipText("");   // Has side affaect of turning on tt text
    }

    public void removeTrack(HiCTrack track) {
        hic.removeTrack(track);
        invalidate();
        mainWindow.invalidate();
        mainWindow.repaint();
    }

    private void addMouseAdapter() {
        mouseAdapter = new MouseAdapter() {

            @Override
            public void mouseMoved(MouseEvent e) {
                mainWindow.updateToolTipText(tooltipText(e.getX(), e.getY()));
            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent) {
                if (mouseEvent.isPopupTrigger()) {
                    handlePopupEvent(mouseEvent);
                }
            }

            @Override
            public void mouseClicked(MouseEvent mouseEvent) {

                Context context = orientation == Orientation.X ? hic.getXContext() : hic.getYContext();

                if (mouseEvent.isPopupTrigger()) {
                    handlePopupEvent(mouseEvent);
                } else {

                    int x = mouseEvent.getX();
                    int y = mouseEvent.getY();
                    if (orientation == Orientation.Y) {
                        y = mouseEvent.getX();
                        x = mouseEvent.getY();

                    }
                    for (Pair<Rectangle, HiCTrack> p : trackRectangles) {
                        Rectangle r = p.getFirst();
                        if (y >= r.y && y < r.y + r.height) {
                            HiCTrack track = p.getSecond();
                            track.mouseClicked(x, y, context, orientation);
                        }
                    }
                }

            }

            @Override
            public void mousePressed(MouseEvent mouseEvent) {
                if (mouseEvent.isPopupTrigger()) {
                    handlePopupEvent(mouseEvent);
                }
            }

            private void handlePopupEvent(MouseEvent mouseEvent) {
                for (Pair<Rectangle, HiCTrack> p : trackRectangles) {
                    Rectangle r = p.getFirst();
                    if (r.contains(mouseEvent.getPoint())) {

                        HiCTrack track = p.getSecond();
                        JPopupMenu menu = track.getPopupMenu(TrackPanel.this);
                        menu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                        repaint();

//                        Collection<Track> selectedTracks = Arrays.asList(p.getSecond());
//                        TrackClickEvent te = new TrackClickEvent(mouseEvent, null);
//                        IGVPopupMenu menu = TrackMenuUtils.getPopupMenu(selectedTracks, "", te);
//                        menu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                    }
                }
            }

        };

        this.addMouseListener(mouseAdapter);
        this.addMouseMotionListener(mouseAdapter);
    }

    public void setEigenvectorTrack(HiCTrack eigenvectorTrack) {
        this.eigenvectorTrack = eigenvectorTrack;
    }

    /**
     * Returns the current height of this component.
     * This method is preferable to writing
     * <code>component.getBounds().height</code>, or
     * <code>component.getSize().height</code> because it doesn't cause any
     * heap allocations.
     *
     * @return the current height of this component
     */
    @Override
    public int getHeight() {
        if (orientation == Orientation.X) {
            int h = 0;
            for (HiCTrack t : hic.getLoadedTracks()) {
                h += t.getHeight();
            }
            if (eigenvectorTrack != null) {
                h += eigenvectorTrack.getHeight();
            }
            return h;
        } else {
            return super.getHeight();
        }
    }

    @Override
    public int getWidth() {
        if (orientation == Orientation.Y) {
            int h = 0;
            for (HiCTrack t : hic.getLoadedTracks()) {
                h += t.getHeight();
            }
            if (eigenvectorTrack != null) {
                h += eigenvectorTrack.getHeight();
            }
            return h;
        } else {
            return super.getWidth();
        }
    }

    /**
     * If the <code>preferredSize</code> has been set to a
     * non-<code>null</code> value just returns it.
     * If the UI delegate's <code>getPreferredSize</code>
     * method returns a non <code>null</code> value then return that;
     * otherwise defer to the component's layout manager.
     *
     * @return the value of the <code>preferredSize</code> property
     * @see #setPreferredSize
     * @see javax.swing.plaf.ComponentUI
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(getWidth(), getHeight());
    }

    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D graphics = (Graphics2D) g;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


        AffineTransform t = graphics.getTransform();
        if (orientation == Orientation.Y) {
            AffineTransform rotateTransform = new AffineTransform();
            rotateTransform.quadrantRotate(1);
            rotateTransform.scale(1, -1);
            graphics.transform(rotateTransform);
        }


        trackRectangles.clear();
        java.util.List<HiCTrack> tracks = new ArrayList<HiCTrack>(hic.getLoadedTracks());
        if ((tracks == null || tracks.isEmpty()) && eigenvectorTrack == null) {
            return;
        }


        Rectangle rect = getBounds();
        graphics.setColor(getBackground());
        graphics.fillRect(rect.x, rect.y, rect.width, rect.height);

        int rectBottom = orientation == Orientation.X ? rect.y + rect.height : rect.x + rect.width;
        int y = orientation == Orientation.X ? rect.y : rect.x;

        HiCGridAxis gridAxis = orientation == Orientation.X ? hic.getZd().getXGridAxis() : hic.getZd().getYGridAxis();

        for (HiCTrack hicTrack : tracks) {
            if (hicTrack.getHeight() > 0) {
                int h = hicTrack.getHeight();

                Rectangle trackRectangle;
                if (orientation == Orientation.X) {
                    trackRectangle = new Rectangle(rect.x, y, rect.width, h);
                } else {
                    trackRectangle = new Rectangle(y, rect.y, h, rect.height);
                }

                if (getContext() != null) {

                    hicTrack.render(graphics, getContext(), trackRectangle, orientation, gridAxis);
                    renderName(hicTrack.getName(), trackRectangle, graphics);
                    y += h;

                    trackRectangles.add(new Pair(trackRectangle, hicTrack));
                }


            }
        }
        if (eigenvectorTrack != null) {
            int h = rectBottom - y;
            Rectangle trackRectangle;
            if (orientation == Orientation.X) {
                trackRectangle = new Rectangle(rect.x, y, rect.width, h);
            } else {
                trackRectangle = new Rectangle(y, rect.y, h, rect.height);
            }
            eigenvectorTrack.render(graphics, getContext(), trackRectangle, orientation, gridAxis);
            renderName("Eigenvector", trackRectangle, graphics);
            trackRectangles.add(new Pair(trackRectangle, eigenvectorTrack));


        }

        graphics.setTransform(t);
        Point cursorPoint = hic.getCursorPoint();
        if (cursorPoint != null) {
            graphics.setColor(MainWindow.RULER_LINE_COLOR);
            if (orientation == Orientation.X) {
                graphics.drawLine(cursorPoint.x, 0, cursorPoint.x, getHeight());
            } else {
                graphics.drawLine(0, cursorPoint.y, getWidth(), cursorPoint.y);
            }
        }

    }


    private Context getContext() {
        return orientation == Orientation.X ? hic.getXContext() : hic.getYContext();
    }


//    @Override
//    public String getToolTipText(MouseEvent event) {
//        return tooltipText(event.getX(), event.getY());
//    }

    private String tooltipText(int mx, int my) {

        int x = mx;
        int y = my;
        if (orientation == Orientation.Y) {
            y = mx;
            x = my;

        }
        for (Pair<Rectangle, HiCTrack> p : trackRectangles) {
            Rectangle r = p.getFirst();
            if(r.contains(mx, my)) {
                return p.getSecond().getToolTipText(x, y, orientation);
            }
        }
        return null;
    }

    private void renderName(String name, Rectangle rect, Graphics graphics) {

        if (orientation == Orientation.Y) return;

        Font font = FontManager.getFont(8);
        graphics.setFont(font);
        graphics.setColor(Color.black);
        GraphicUtils.drawRightJustifiedText(name, rect.x + rect.width - 10, rect.y + 15, graphics);
    }


}
