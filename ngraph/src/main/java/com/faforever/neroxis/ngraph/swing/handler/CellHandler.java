/**
 * Copyright (c) 2008-2012, JGraph Ltd
 */
package com.faforever.neroxis.ngraph.swing.handler;

import com.faforever.neroxis.ngraph.swing.GraphComponent;
import com.faforever.neroxis.ngraph.swing.util.SwingConstants;
import com.faforever.neroxis.ngraph.util.RectangleDouble;
import com.faforever.neroxis.ngraph.view.CellState;
import com.faforever.neroxis.ngraph.view.Graph;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Administrator
 */
public class CellHandler {
    public static final int HANDLE_SIZE = 7;
    public static final int LABEL_HANDLE_SIZE = 4;
    /**
     * Reference to the enclosing graph component.
     */
    protected GraphComponent graphComponent;
    /**
     * Holds the cell state associated with this handler.
     */
    protected CellState state;
    /**
     * Holds the rectangles that define the handles.
     */
    protected java.awt.Rectangle[] handles;
    /**
     * Specifies if the handles should be painted. Default is true.
     */
    protected boolean handlesVisible = true;
    /**
     * Holds the bounding box of the handler.
     */
    protected transient java.awt.Rectangle bounds;
    /**
     * Holds the component that is used for preview.
     */
    protected transient JComponent preview;
    /**
     * Holds the start location of the mouse gesture.
     */
    protected transient Point first;
    /**
     * Holds the index of the handle that was clicked.
     */
    protected transient int index;

    /**
     * Constructs a new cell handler for the given cell state.
     *
     * @param graphComponent Enclosing graph component.
     * @param state          Cell state for which the handler is created.
     */
    public CellHandler(GraphComponent graphComponent, CellState state) {
        this.graphComponent = graphComponent;
        refresh(state);
    }

    /**
     * Refreshes the cell handler.
     */
    public void refresh(CellState state) {
        this.state = state;
        handles = createHandles();
        Graph graph = graphComponent.getGraph();
        RectangleDouble tmp = graph.getBoundingBox(state.getCell());

        if (tmp != null) {
            bounds = tmp.getRectangle();

            if (handles != null) {
                for (int i = 0; i < handles.length; i++) {
                    if (isHandleVisible(i)) {
                        bounds.add(handles[i]);
                    }
                }
            }
        }
    }

    /**
     * Creates the rectangles that define the handles.
     */
    protected java.awt.Rectangle[] createHandles() {
        return null;
    }

    /**
     * Returns true if the handle at the specified index is visible.
     */
    protected boolean isHandleVisible(int index) {
        return !isLabel(index) || isLabelMovable();
    }

    /**
     * Returns true if the given index is the index of the last handle.
     */
    public boolean isLabel(int index) {
        return index == getHandleCount() - 1;
    }

    /**
     * Returns the number of handles in this handler.
     */
    protected int getHandleCount() {
        return (handles != null) ? handles.length : 0;
    }

    /**
     * Returns true if the label is movable.
     */
    public boolean isLabelMovable() {
        Graph graph = graphComponent.getGraph();
        String label = graph.getLabel(state.getCell());

        return graph.isLabelMovable(state.getCell()) && label != null && label.length() > 0;
    }

    public boolean isActive() {
        return first != null;
    }

    public GraphComponent getGraphComponent() {
        return graphComponent;
    }

    /**
     * Returns the cell state that is associated with this handler.
     */
    public CellState getState() {
        return state;
    }

    /**
     * Returns the index of the current handle.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns the bounding box of this handler.
     */
    public java.awt.Rectangle getBounds() {
        return bounds;
    }

    /**
     * Hook for subclassers to return tooltip texts for certain points on the
     * handle.
     */
    public String getToolTipText(MouseEvent e) {
        return null;
    }

    /**
     * Processes the given event.
     */
    public void mousePressed(MouseEvent e) {
        if (!e.isConsumed()) {
            int tmp = getIndexAt(e.getX(), e.getY());

            if (!isIgnoredEvent(e) && tmp >= 0 && isHandleEnabled(tmp)) {
                graphComponent.stopEditing(true);
                start(e, tmp);
                e.consume();
            }
        }
    }

    /**
     * Returns the index of the handle at the given location.
     *
     * @param x X-coordinate of the location.
     * @param y Y-coordinate of the location.
     * @return Returns the handle index for the given location.
     */
    public int getIndexAt(int x, int y) {
        if (handles != null && isHandlesVisible()) {
            int tol = graphComponent.getTolerance();
            java.awt.Rectangle rect = new java.awt.Rectangle(x - tol / 2, y - tol / 2, tol, tol);

            for (int i = handles.length - 1; i >= 0; i--) {
                if (isHandleVisible(i) && handles[i].intersects(rect)) {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * Returns true if the given event should be ignored.
     */
    protected boolean isIgnoredEvent(MouseEvent e) {
        return graphComponent.isEditEvent(e);
    }

    /**
     * Returns true if the handle at the specified index is enabled.
     */
    protected boolean isHandleEnabled(int index) {
        return true;
    }

    /**
     * Starts handling a gesture at the given handle index.
     */
    public void start(MouseEvent e, int index) {
        this.index = index;
        first = e.getPoint();
        preview = createPreview();

        if (preview != null) {
            graphComponent.getGraphControl().add(preview, 0);
        }
    }

    /**
     * Creates the preview for this handler.
     */
    protected JComponent createPreview() {
        return null;
    }

    /**
     * Returns true if the handles should be painted.
     */
    public boolean isHandlesVisible() {
        return handlesVisible;
    }

    /**
     * Specifies if the handles should be painted.
     */
    public void setHandlesVisible(boolean handlesVisible) {
        this.handlesVisible = handlesVisible;
    }

    /**
     * Processes the given event.
     */
    public void mouseMoved(MouseEvent e) {
        if (!e.isConsumed() && handles != null) {
            int index = getIndexAt(e.getX(), e.getY());

            if (index >= 0 && isHandleEnabled(index)) {
                Cursor cursor = getCursor(e, index);

                if (cursor != null) {
                    graphComponent.getGraphControl().setCursor(cursor);
                    e.consume();
                } else {
                    graphComponent.getGraphControl().setCursor(new Cursor(Cursor.HAND_CURSOR));
                }
            }
        }
    }

    /**
     * Returns the cursor for the given event and handle.
     */
    protected Cursor getCursor(MouseEvent e, int index) {
        return null;
    }

    /**
     * Processes the given event.
     */
    public void mouseDragged(MouseEvent e) {
        // empty
    }

    /**
     * Processes the given event.
     */
    public void mouseReleased(MouseEvent e) {
        reset();
    }

    /**
     * Resets the state of the handler and removes the preview.
     */
    public void reset() {
        if (preview != null) {
            preview.setVisible(false);
            preview.getParent().remove(preview);
            preview = null;
        }

        first = null;
    }

    /**
     * Paints the visible handles of this handler.
     */
    public void paint(Graphics g) {
        if (handles != null && isHandlesVisible()) {
            for (int i = 0; i < handles.length; i++) {
                if (isHandleVisible(i) && g.hitClip(handles[i].x, handles[i].y, handles[i].width, handles[i].height)) {
                    g.setColor(getHandleFillColor(i));
                    g.fillRect(handles[i].x, handles[i].y, handles[i].width, handles[i].height);

                    g.setColor(getHandleBorderColor(i));
                    g.drawRect(handles[i].x, handles[i].y, handles[i].width - 1, handles[i].height - 1);
                }
            }
        }
    }

    /**
     * Returns the color used to draw the selection border. This implementation
     * returns null.
     */
    public Color getSelectionColor() {
        return null;
    }

    /**
     * Returns the stroke used to draw the selection border. This implementation
     * returns null.
     */
    public Stroke getSelectionStroke() {
        return null;
    }

    /**
     * Returns the color to be used to fill the handle at the specified index.
     */
    protected Color getHandleFillColor(int index) {
        if (isLabel(index)) {
            return SwingConstants.LABEL_HANDLE_FILLCOLOR;
        }

        return SwingConstants.HANDLE_FILLCOLOR;
    }

    /**
     * Returns the border color of the handle at the specified index.
     */
    protected Color getHandleBorderColor(int index) {
        return SwingConstants.HANDLE_BORDERCOLOR;
    }

    /**
     * Invoked when the handler is no longer used. This is an empty
     * hook for subclassers.
     */
    protected void destroy() {
        // nop
    }
}
