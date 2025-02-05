/**
 * Copyright (c) 2008, Gaudenz Alder
 */
package com.faforever.neroxis.ngraph.swing;

import com.faforever.neroxis.ngraph.event.EventSource.IEventListener;
import com.faforever.neroxis.ngraph.event.RepaintEvent;
import com.faforever.neroxis.ngraph.util.PointDouble;
import com.faforever.neroxis.ngraph.util.RectangleDouble;
import com.faforever.neroxis.ngraph.util.Utils;
import com.faforever.neroxis.ngraph.view.GraphView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An outline view for a specific graph component.
 */
public class GraphOutline extends JComponent {
    private static final Logger log = Logger.getLogger(GraphOutline.class.getName());
    @Serial
    private static final long serialVersionUID = -2521103946905154267L;
    public static Color DEFAULT_ZOOMHANDLE_FILL = new Color(0, 255, 255);
    protected GraphComponent graphComponent;
    /**
     * TODO: Not yet implemented.
     */
    protected BufferedImage tripleBuffer;
    /**
     * Holds the graphics of the triple buffer.
     */
    protected Graphics2D tripleBufferGraphics;
    /**
     * True if the triple buffer needs a full repaint.
     */
    protected boolean repaintBuffer = false;
    /**
     * Clip of the triple buffer to be repainted.
     */
    protected RectangleDouble repaintClip = null;
    protected boolean tripleBuffered = true;
    protected java.awt.Rectangle finderBounds = new java.awt.Rectangle();
    protected java.awt.Point zoomHandleLocation = null;
    protected boolean finderVisible = true;
    protected boolean zoomHandleVisible = true;
    protected boolean useScaledInstance = false;
    protected boolean antiAlias = false;
    protected boolean drawLabels = false;
    /**
     * Specifies if the outline should be zoomed to the page if the graph
     * component is in page layout mode. Default is true.
     */
    protected boolean fitPage = true;
    /**
     * Not yet implemented.
     * <p>
     * Border to add around the page bounds if wholePage is true.
     * Default is 4.
     */
    protected int outlineBorder = 10;
    protected MouseTracker tracker = new MouseTracker();
    protected double scale = 1;
    protected java.awt.Point translate = new java.awt.Point();
    protected transient boolean zoomGesture = false;
    protected IEventListener<RepaintEvent> repaintHandler = (source, evt) -> {
        updateScaleAndTranslate();
        RectangleDouble dirty = evt.getRegion();
        if (dirty != null) {
            repaintClip = new RectangleDouble(dirty);
        } else {
            repaintBuffer = true;
        }
        if (dirty != null) {
            updateFinder(true);
            dirty.grow(1 / scale);
            dirty.setX(dirty.getX() * scale + translate.x);
            dirty.setY(dirty.getY() * scale + translate.y);
            dirty.setWidth(dirty.getWidth() * scale);
            dirty.setHeight(dirty.getHeight() * scale);
            repaint(dirty.getRectangle());
        } else {
            updateFinder(false);
            repaint();
        }
    };
    protected ComponentListener componentHandler = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            if (updateScaleAndTranslate()) {
                repaintBuffer = true;
                updateFinder(false);
                repaint();
            } else {
                updateFinder(true);
            }
        }
    };
    protected AdjustmentListener adjustmentHandler = new AdjustmentListener() {
        @Override
        public void adjustmentValueChanged(AdjustmentEvent e) {
            if (updateScaleAndTranslate()) {
                repaintBuffer = true;
                updateFinder(false);
                repaint();
            } else {
                updateFinder(true);
            }
        }
    };

    public GraphOutline(GraphComponent graphComponent) {
        addComponentListener(componentHandler);
        addMouseMotionListener(tracker);
        addMouseListener(tracker);
        setGraphComponent(graphComponent);
        setEnabled(true);
        setOpaque(true);
    }

    public boolean isTripleBuffered() {
        return tripleBuffered;
    }

    /**
     * Fires a property change event for <code>tripleBuffered</code>.
     *
     * @param tripleBuffered the tripleBuffered to set
     */
    public void setTripleBuffered(boolean tripleBuffered) {
        boolean oldValue = this.tripleBuffered;
        this.tripleBuffered = tripleBuffered;

        if (!tripleBuffered) {
            destroyTripleBuffer();
        }

        firePropertyChange("tripleBuffered", oldValue, tripleBuffered);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        paintBackground(g);

        if (graphComponent != null) {
            // Creates or destroys the triple buffer as needed
            if (tripleBuffered) {
                checkTripleBuffer();
            } else if (tripleBuffer != null) {
                destroyTripleBuffer();
            }

            // Updates the dirty region from the buffered graph image
            if (tripleBuffer != null) {
                if (repaintBuffer) {
                    repaintTripleBuffer(null);
                } else if (repaintClip != null) {
                    repaintClip.grow(1 / scale);

                    repaintClip.setX(repaintClip.getX() * scale + translate.x);
                    repaintClip.setY(repaintClip.getY() * scale + translate.y);
                    repaintClip.setWidth(repaintClip.getWidth() * scale);
                    repaintClip.setHeight(repaintClip.getHeight() * scale);

                    repaintTripleBuffer(repaintClip.getRectangle());
                }

                Utils.drawImageClip(g, tripleBuffer, this);
            }

            // Paints the graph directly onto the graphics
            else {
                paintGraph(g);
            }

            paintForeground(g);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        // Frees memory if the outline is hidden
        if (!visible) {
            destroyTripleBuffer();
        }
    }

    public boolean isDrawLabels() {
        return drawLabels;
    }

    /**
     * Fires a property change event for <code>drawLabels</code>.
     *
     * @param drawLabels the drawLabels to set
     */
    public void setDrawLabels(boolean drawLabels) {
        boolean oldValue = this.drawLabels;
        this.drawLabels = drawLabels;
        repaintTripleBuffer(null);

        firePropertyChange("drawLabels", oldValue, drawLabels);
    }

    /**
     * Destroys the tripleBuffer and tripleBufferGraphics objects.
     */
    public void destroyTripleBuffer() {
        if (tripleBuffer != null) {
            tripleBuffer = null;
            tripleBufferGraphics.dispose();
            tripleBufferGraphics = null;
        }
    }

    /**
     * @return the antiAlias
     */
    public boolean isAntiAlias() {
        return antiAlias;
    }

    /**
     * Fires a property change event for <code>antiAlias</code>.
     *
     * @param antiAlias the antiAlias to set
     */
    public void setAntiAlias(boolean antiAlias) {
        boolean oldValue = this.antiAlias;
        this.antiAlias = antiAlias;
        repaintTripleBuffer(null);

        firePropertyChange("antiAlias", oldValue, antiAlias);
    }

    public void setFinderVisible(boolean visible) {
        finderVisible = visible;
    }

    public void setZoomHandleVisible(boolean visible) {
        zoomHandleVisible = visible;
    }

    public GraphComponent getGraphComponent() {
        return graphComponent;
    }

    /**
     * Fires a property change event for <code>graphComponent</code>.
     *
     * @param graphComponent the graphComponent to set
     */
    public void setGraphComponent(GraphComponent graphComponent) {
        GraphComponent oldValue = this.graphComponent;

        if (this.graphComponent != null) {
            this.graphComponent.getGraph().removeListener(repaintHandler);
            this.graphComponent.getGraphControl().removeComponentListener(componentHandler);
            this.graphComponent.getHorizontalScrollBar().removeAdjustmentListener(adjustmentHandler);
            this.graphComponent.getVerticalScrollBar().removeAdjustmentListener(adjustmentHandler);
        }

        this.graphComponent = graphComponent;

        if (this.graphComponent != null) {
            this.graphComponent.getGraph().addListener(RepaintEvent.class, repaintHandler);
            this.graphComponent.getGraphControl().addComponentListener(componentHandler);
            this.graphComponent.getHorizontalScrollBar().addAdjustmentListener(adjustmentHandler);
            this.graphComponent.getVerticalScrollBar().addAdjustmentListener(adjustmentHandler);
        }

        if (updateScaleAndTranslate()) {
            repaintBuffer = true;
            repaint();
        }

        firePropertyChange("graphComponent", oldValue, graphComponent);
    }

    /**
     * Paints the graph outline.
     */
    public void paintGraph(Graphics g) {
        if (graphComponent != null) {
            Graphics2D g2 = (Graphics2D) g;
            AffineTransform tx = g2.getTransform();

            try {
                java.awt.Point tr = graphComponent.getGraphControl().getTranslate();
                g2.translate(translate.x + tr.getX() * scale, translate.y + tr.getY() * scale);
                g2.scale(scale, scale);

                // Draws the scaled graph
                graphComponent.getGraphControl().drawGraph(g2, drawLabels);
            } finally {
                g2.setTransform(tx);
            }
        }
    }

    /**
     * Clears and repaints the triple buffer at the given rectangle or repaints
     * the complete buffer if no rectangle is specified.
     */
    public void repaintTripleBuffer(java.awt.Rectangle clip) {
        if (tripleBuffered && tripleBufferGraphics != null) {
            if (clip == null) {
                clip = new java.awt.Rectangle(tripleBuffer.getWidth(), tripleBuffer.getHeight());
            }

            // Clears and repaints the dirty rectangle using the
            // graphics canvas of the graph component as a renderer
            Utils.clearRect(tripleBufferGraphics, clip, null);
            tripleBufferGraphics.setClip(clip);
            paintGraph(tripleBufferGraphics);
            tripleBufferGraphics.setClip(null);

            repaintBuffer = false;
            repaintClip = null;
        }
    }

    /**
     * Returns true if the scale or translate has changed.
     */
    public boolean updateScaleAndTranslate() {
        double newScale = 1;
        int dx = 0;
        int dy = 0;

        if (this.graphComponent != null) {
            Dimension graphSize = graphComponent.getGraphControl().getSize();
            Dimension outlineSize = getSize();

            int gw = (int) graphSize.getWidth();
            int gh = (int) graphSize.getHeight();

            if (gw > 0 && gh > 0) {
                boolean magnifyPage = graphComponent.isPageVisible()
                                      && isFitPage()
                                      && graphComponent.getHorizontalScrollBar().isVisible()
                                      && graphComponent.getVerticalScrollBar().isVisible();
                double graphScale = graphComponent.getGraph().getView().getScale();
                PointDouble trans = graphComponent.getGraph().getView().getTranslate();

                int w = (int) outlineSize.getWidth() - 2 * outlineBorder;
                int h = (int) outlineSize.getHeight() - 2 * outlineBorder;

                if (magnifyPage) {
                    gw -= 2 * Math.round(trans.getX() * graphScale);
                    gh -= 2 * Math.round(trans.getY() * graphScale);
                }

                newScale = Math.min((double) w / gw, (double) h / gh);

                dx += (int) Math.round((outlineSize.getWidth() - gw * newScale) / 2);
                dy += (int) Math.round((outlineSize.getHeight() - gh * newScale) / 2);

                if (magnifyPage) {
                    dx -= Math.round(trans.getX() * newScale * graphScale);
                    dy -= Math.round(trans.getY() * newScale * graphScale);
                }
            }
        }

        if (newScale != scale || translate.x != dx || translate.y != dy) {
            scale = newScale;
            translate.setLocation(dx, dy);

            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks if the triple buffer exists and creates a new one if
     * it does not. Also compares the size of the buffer with the
     * size of the graph and drops the buffer if it has a
     * different size.
     */
    public void checkTripleBuffer() {
        if (tripleBuffer != null) {
            if (tripleBuffer.getWidth() != getWidth() || tripleBuffer.getHeight() != getHeight()) {
                // Resizes the buffer (destroys existing and creates new)
                destroyTripleBuffer();
            }
        }

        if (tripleBuffer == null) {
            createTripleBuffer(getWidth(), getHeight());
        }
    }

    /**
     * Creates the tripleBufferGraphics and tripleBuffer for the given
     * dimension and draws the complete graph onto the triplebuffer.
     */
    protected void createTripleBuffer(int width, int height) {
        try {
            tripleBuffer = Utils.createBufferedImage(width, height, null);
            tripleBufferGraphics = tripleBuffer.createGraphics();

            // Repaints the complete buffer
            repaintTripleBuffer(null);
        } catch (OutOfMemoryError error) {
            log.log(Level.SEVERE, "Failed to create a triple buffer", error);
        }
    }

    public boolean isFitPage() {
        return fitPage;
    }

    /**
     * Fires a property change event for <code>fitPage</code>.
     *
     * @param fitPage the fitPage to set
     */
    public void setFitPage(boolean fitPage) {
        boolean oldValue = this.fitPage;
        this.fitPage = fitPage;

        if (updateScaleAndTranslate()) {
            repaintBuffer = true;
            updateFinder(false);
        }

        firePropertyChange("fitPage", oldValue, fitPage);
    }

    /**
     * Paints the background.
     */
    protected void paintBackground(Graphics g) {
        if (graphComponent != null) {
            Graphics2D g2 = (Graphics2D) g;
            AffineTransform tx = g2.getTransform();

            try {
                // Draws the background of the outline if a graph exists
                g.setColor(graphComponent.getPageBackgroundColor());
                Utils.fillClippedRect(g, 0, 0, getWidth(), getHeight());

                g2.translate(translate.x, translate.y);
                g2.scale(scale, scale);

                // Draws the scaled page background
                if (!graphComponent.isPageVisible()) {
                    Color bg = graphComponent.getBackground();

                    if (graphComponent.getViewport().isOpaque()) {
                        bg = graphComponent.getViewport().getBackground();
                    }

                    g.setColor(bg);
                    Dimension size = graphComponent.getGraphControl().getSize();

                    // Paints the background of the drawing surface
                    Utils.fillClippedRect(g, 0, 0, size.width, size.height);
                    g.setColor(g.getColor().darker().darker());
                    g.drawRect(0, 0, size.width, size.height);
                } else {
                    // Paints the page background using the graphics scaling
                    graphComponent.paintBackgroundPage(g);
                }
            } finally {
                g2.setTransform(tx);
            }
        } else {
            // Draws the background of the outline if no graph exists
            g.setColor(getBackground());
            Utils.fillClippedRect(g, 0, 0, getWidth(), getHeight());
        }
    }

    /**
     * Paints the foreground. Foreground is dynamic and should never be made
     * part of the triple buffer. It is painted on top of the buffer.
     */
    protected void paintForeground(Graphics g) {
        if (graphComponent != null) {
            Graphics2D g2 = (Graphics2D) g;

            Stroke stroke = g2.getStroke();
            g.setColor(Color.BLUE);
            g2.setStroke(new BasicStroke(3));
            g.drawRect(finderBounds.x, finderBounds.y, finderBounds.width, finderBounds.height);

            if (zoomHandleVisible) {
                g2.setStroke(stroke);
                g.setColor(DEFAULT_ZOOMHANDLE_FILL);
                g.fillRect(finderBounds.x + finderBounds.width - 6, finderBounds.y + finderBounds.height - 6, 8, 8);
                g.setColor(Color.BLACK);
                g.drawRect(finderBounds.x + finderBounds.width - 6, finderBounds.y + finderBounds.height - 6, 8, 8);
            }
        }
    }

    public void updateFinder(boolean repaint) {
        java.awt.Rectangle rect = graphComponent.getViewport().getViewRect();

        int x = (int) Math.round(rect.x * scale);
        int y = (int) Math.round(rect.y * scale);
        int w = (int) Math.round((rect.x + rect.width) * scale) - x;
        int h = (int) Math.round((rect.y + rect.height) * scale) - y;

        updateFinderBounds(new java.awt.Rectangle(x + translate.x, y + translate.y, w + 1, h + 1), repaint);
    }

    public void updateFinderBounds(java.awt.Rectangle bounds, boolean repaint) {
        if (bounds != null && !bounds.equals(finderBounds)) {
            java.awt.Rectangle old = new java.awt.Rectangle(finderBounds);
            finderBounds = bounds;

            // LATER: Fix repaint region to be smaller
            if (repaint) {
                old = old.union(finderBounds);
                old.grow(3, 3);
                repaint(old);
            }
        }
    }

    public class MouseTracker implements MouseListener, MouseMotionListener {
        protected java.awt.Point start = null;

        @Override
        public void mouseDragged(MouseEvent e) {
            if (isEnabled() && start != null) {
                if (zoomGesture) {
                    java.awt.Rectangle bounds = graphComponent.getViewport().getViewRect();
                    double viewRatio = bounds.getWidth() / bounds.getHeight();

                    bounds = new java.awt.Rectangle(finderBounds);
                    bounds.width = (int) Math.max(0, (e.getX() - bounds.getX()));
                    bounds.height = (int) Math.max(0, (bounds.getWidth() / viewRatio));

                    updateFinderBounds(bounds, true);
                } else {
                    // TODO: To enable constrained moving, that is, moving
                    // into only x- or y-direction when shift is pressed,
                    // we need the location of the first mouse event, since
                    // the movement can not be constrained for incremental
                    // steps as used below.
                    int dx = (int) ((e.getX() - start.getX()) / scale);
                    int dy = (int) ((e.getY() - start.getY()) / scale);

                    // Keeps current location as start for delta movement
                    // of the scrollbars
                    start = e.getPoint();

                    graphComponent.getHorizontalScrollBar()
                                  .setValue(graphComponent.getHorizontalScrollBar().getValue() + dx);
                    graphComponent.getVerticalScrollBar()
                                  .setValue(graphComponent.getVerticalScrollBar().getValue() + dy);
                }
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            if (hitZoomHandle(e.getX(), e.getY())) {
                setCursor(new Cursor(Cursor.HAND_CURSOR));
            } else if (finderBounds.contains(e.getPoint())) {
                setCursor(new Cursor(Cursor.MOVE_CURSOR));
            } else {
                setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        }

        public boolean hitZoomHandle(int x, int y) {
            return new java.awt.Rectangle(finderBounds.x + finderBounds.width - 6,
                                          finderBounds.y + finderBounds.height - 6, 8, 8).contains(x, y);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            // ignore
        }

        @Override
        public void mousePressed(MouseEvent e) {
            zoomGesture = hitZoomHandle(e.getX(), e.getY());

            if (graphComponent != null && !e.isConsumed() && !e.isPopupTrigger() && (finderBounds.contains(e.getPoint())
                                                                                     || zoomGesture)) {
                start = e.getPoint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (start != null) {
                if (zoomGesture) {
                    double dx = e.getX() - start.getX();
                    double w = finderBounds.getWidth();

                    final JScrollBar hs = graphComponent.getHorizontalScrollBar();
                    final double sx;

                    if (hs != null) {
                        sx = (double) hs.getValue() / hs.getMaximum();
                    } else {
                        sx = 0;
                    }

                    final JScrollBar vs = graphComponent.getVerticalScrollBar();
                    final double sy;

                    if (vs != null) {
                        sy = (double) vs.getValue() / vs.getMaximum();
                    } else {
                        sy = 0;
                    }

                    GraphView view = graphComponent.getGraph().getView();
                    double scale = view.getScale();
                    double newScale = scale - (dx * scale) / w;
                    double factor = newScale / scale;
                    view.setScale(newScale);

                    if (hs != null) {
                        hs.setValue((int) (sx * hs.getMaximum() * factor));
                    }

                    if (vs != null) {
                        vs.setValue((int) (sy * vs.getMaximum() * factor));
                    }
                }

                zoomGesture = false;
                start = null;
            }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            // ignore
        }

        @Override
        public void mouseExited(MouseEvent e) {
            // ignore
        }
    }
}
