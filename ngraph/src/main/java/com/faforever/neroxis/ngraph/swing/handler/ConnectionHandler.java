/**
 * Copyright (c) 2008, Gaudenz Alder
 */
package com.faforever.neroxis.ngraph.swing.handler;

import com.faforever.neroxis.ngraph.event.AfterPaintEvent;
import com.faforever.neroxis.ngraph.event.ChangeEvent;
import com.faforever.neroxis.ngraph.event.ConnectEvent;
import com.faforever.neroxis.ngraph.event.EventObject;
import com.faforever.neroxis.ngraph.event.EventSource;
import com.faforever.neroxis.ngraph.event.EventSource.IEventListener;
import com.faforever.neroxis.ngraph.event.ScaleAndTranslateEvent;
import com.faforever.neroxis.ngraph.event.ScaleEvent;
import com.faforever.neroxis.ngraph.event.TranslateEvent;
import com.faforever.neroxis.ngraph.model.Geometry;
import com.faforever.neroxis.ngraph.model.ICell;
import com.faforever.neroxis.ngraph.model.IGraphModel;
import com.faforever.neroxis.ngraph.swing.GraphComponent;
import com.faforever.neroxis.ngraph.swing.GraphControl;
import com.faforever.neroxis.ngraph.util.Constants;
import com.faforever.neroxis.ngraph.util.PointDouble;
import com.faforever.neroxis.ngraph.util.RectangleDouble;
import com.faforever.neroxis.ngraph.view.CellState;
import com.faforever.neroxis.ngraph.view.Graph;
import com.faforever.neroxis.ngraph.view.GraphView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.util.List;

/**
 * Connection handler creates new connections between cells. This control is used to display the connector
 * icon, while the preview is used to draw the line.
 * <p>
 * Event.CONNECT fires between begin- and endUpdate in mouseReleased. The <code>cell</code>
 * property contains the inserted edge, the <code>event</code> and <code>target</code>
 * properties contain the respective arguments that were passed to mouseReleased.
 */
@SuppressWarnings("unused")
public class ConnectionHandler extends MouseAdapter {
    private static final long serialVersionUID = -2543899557644889853L;
    public static Cursor CONNECT_CURSOR = new Cursor(Cursor.HAND_CURSOR);
    protected GraphComponent graphComponent;
    /**
     * Holds the event source.
     */
    protected EventSource eventSource = new EventSource(this);
    protected ConnectPreview connectPreview;
    /**
     * Specifies the icon to be used for creating new connections. If this is
     * specified then it is used instead of the handle. Default is null.
     */
    protected ImageIcon connectIcon = null;
    /**
     * Specifies the size of the handle to be used for creating new
     * connections. Default is Constants.CONNECT_HANDLE_SIZE.
     */
    protected int handleSize = Constants.CONNECT_HANDLE_SIZE;
    /**
     * Specifies if a handle should be used for creating new connections. This
     * is only used if no connectIcon is specified. If this is false, then the
     * source cell will be highlighted when the mouse is over the hotspot given
     * in the marker. Default is Constants.CONNECT_HANDLE_ENABLED.
     */
    protected boolean handleEnabled = Constants.CONNECT_HANDLE_ENABLED;
    protected boolean select = true;
    /**
     * Specifies if the source should be cloned and used as a target if no
     * target was selected. Default is false.
     */
    protected boolean createTarget = false;
    /**
     * Appearance and event handling order wrt subhandles.
     */
    protected boolean keepOnTop = true;
    protected boolean enabled = true;
    protected transient java.awt.Point first;
    protected transient boolean active = false;
    protected transient java.awt.Rectangle bounds;
    protected transient CellState source;
    protected transient CellMarker marker;
    protected transient String error;
    protected transient IEventListener<? extends EventObject> resetHandler = (source, evt) -> reset();

    public ConnectionHandler(GraphComponent graphComponent) {
        this.graphComponent = graphComponent;
        // Installs the paint handler
        graphComponent.addListener(AfterPaintEvent.class, (sender, evt) -> paint(evt.getGraphics()));
        connectPreview = createConnectPreview();
        GraphControl graphControl = graphComponent.getGraphControl();
        graphControl.addMouseListener(this);
        graphControl.addMouseMotionListener(this);
        // Installs the graph listeners and keeps them in sync
        addGraphListeners(graphComponent.getGraph());
        graphComponent.addPropertyChangeListener(evt -> {
            if (evt.getPropertyName().equals("graph")) {
                removeGraphListeners((Graph) evt.getOldValue());
                addGraphListeners((Graph) evt.getNewValue());
            }
        });
        marker = new CellMarker(graphComponent) {
            @Serial
            private static final long serialVersionUID = 103433247310526381L;

            // Overrides to return cell at location only if valid (so that
            // there is no highlight for invalid cells that have no error
            // message when the mouse is released)
            @Override
            protected ICell getCell(MouseEvent e) {
                ICell cell = super.getCell(e);
                if (isConnecting()) {
                    if (source != null) {
                        error = validateConnection(source.getCell(), cell);

                        if (error != null && error.length() == 0) {
                            cell = null;

                            // Enables create target inside groups
                            if (createTarget) {
                                error = null;
                            }
                        }
                    }
                } else if (!isValidSource(cell)) {
                    cell = null;
                }

                return cell;
            }

            // Overrides to use hotspot only for source selection otherwise
            // intersects always returns true when over a cell
            @Override
            protected boolean intersects(CellState state, MouseEvent e) {
                if (!isHighlighting() || isConnecting()) {
                    return true;
                }

                return super.intersects(state, e);
            }

            // Sets the highlight color according to isValidConnection
            @Override
            protected boolean isValidState(CellState state) {
                if (isConnecting()) {
                    return error == null;
                } else {
                    return super.isValidState(state);
                }
            }

            // Overrides to use marker color only in highlight mode or for
            // target selection
            @Override
            protected Color getMarkerColor(MouseEvent e, CellState state, boolean isValid) {
                return (isHighlighting() || isConnecting()) ? super.getMarkerColor(e, state, isValid) : null;
            }
        };

        marker.setHotspotEnabled(true);
    }

    public void paint(Graphics g) {
        if (bounds != null) {
            if (connectIcon != null) {
                g.drawImage(connectIcon.getImage(), bounds.x, bounds.y, bounds.width, bounds.height, null);
            } else if (handleEnabled) {
                g.setColor(Color.BLACK);
                g.draw3DRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, true);
                g.setColor(Color.GREEN);
                g.fill3DRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2, true);
                g.setColor(Color.BLUE);
                g.drawRect(bounds.x + bounds.width / 2 - 1, bounds.y + bounds.height / 2 - 1, 1, 1);
            }
        }
    }

    protected ConnectPreview createConnectPreview() {
        return new ConnectPreview(graphComponent);
    }

    /**
     * Installs the listeners to update the handles after any changes.
     */
    protected void addGraphListeners(Graph graph) {
        // LATER: Install change listener for graph model, view
        if (graph != null) {
            GraphView view = graph.getView();
            view.addListener(ScaleEvent.class, (IEventListener<ScaleEvent>) resetHandler);
            view.addListener(TranslateEvent.class, (IEventListener<TranslateEvent>) resetHandler);
            view.addListener(ScaleAndTranslateEvent.class, (IEventListener<ScaleAndTranslateEvent>) resetHandler);
            graph.getModel().addListener(ChangeEvent.class, (IEventListener<ChangeEvent>) resetHandler);
        }
    }

    /**
     * Removes all installed listeners.
     */
    protected void removeGraphListeners(Graph graph) {
        if (graph != null) {
            GraphView view = graph.getView();
            view.removeListener(resetHandler);
            graph.getModel().removeListener(resetHandler);
        }
    }

    /**
     * Returns true if the source terminal has been clicked and a new
     * connection is currently being previewed.
     */
    public boolean isConnecting() {
        return connectPreview.isActive();
    }

    /**
     * Returns true if no connectIcon is specified and handleEnabled is false.
     */
    public boolean isHighlighting() {
        return connectIcon == null && !handleEnabled;
    }

    /**
     * Returns the error message or an empty string if the connection for the
     * given source target pair is not valid. Otherwise it returns null.
     */
    public String validateConnection(ICell source, ICell target) {
        if (target == null && createTarget) {
            return null;
        }

        if (!isValidTarget(target)) {
            return "";
        }

        return graphComponent.getGraph()
                             .getEdgeValidationError(connectPreview.getPreviewState().getCell(), source, target);
    }

    /**
     * Returns true. The call to Graph.isValidTarget is implicit by calling
     * Graph.getEdgeValidationError in validateConnection. This is an
     * additional hook for disabling certain targets in this specific handler.
     */
    public boolean isValidTarget(ICell cell) {
        return true;
    }

    public boolean isValidSource(ICell cell) {
        return graphComponent.getGraph().isValidSource(cell);
    }

    public ConnectPreview getConnectPreview() {
        return connectPreview;
    }

    public void setConnectPreview(ConnectPreview value) {
        connectPreview = value;
    }

    public boolean isKeepOnTop() {
        return keepOnTop;
    }

    public void setKeepOnTop(boolean value) {
        keepOnTop = value;
    }

    public void setConnectIcon(ImageIcon value) {
        connectIcon = value;
    }

    public ImageIcon getConnecIcon() {
        return connectIcon;
    }

    public boolean isHandleEnabled() {
        return handleEnabled;
    }

    public void setHandleEnabled(boolean value) {
        handleEnabled = value;
    }

    public int getHandleSize() {
        return handleSize;
    }

    public void setHandleSize(int value) {
        handleSize = value;
    }

    public CellMarker getMarker() {
        return marker;
    }

    public void setMarker(CellMarker value) {
        marker = value;
    }

    public boolean isSelect() {
        return select;
    }

    public void setSelect(boolean value) {
        select = value;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!graphComponent.isForceMarqueeEvent(e)
            && !graphComponent.isPanningEvent(e)
            && !e.isPopupTrigger()
            && graphComponent.isEnabled()
            && isEnabled()
            && !e.isConsumed()
            && ((isHighlighting() && marker.hasValidState()) || (!isHighlighting() && bounds != null && bounds.contains(
                e.getPoint())))) {
            start(e, marker.getValidState());
            e.consume();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (isActive()) {
            if (error != null) {
                if (error.length() > 0) {
                    JOptionPane.showMessageDialog(graphComponent, error);
                }
            } else if (first != null) {
                Graph graph = graphComponent.getGraph();
                double dx = first.getX() - e.getX();
                double dy = first.getY() - e.getY();

                if (connectPreview.isActive() && (marker.hasValidState()
                                                  || isCreateTarget()
                                                  || graph.isAllowDanglingEdges())) {
                    graph.getModel().beginUpdate();

                    try {
                        ICell dropTarget = null;

                        if (!marker.hasValidState() && isCreateTarget()) {
                            ICell vertex = createTargetVertex(e, source.getCell());
                            dropTarget = graph.getDropTarget(List.of(vertex), e.getPoint(),
                                                             graphComponent.getCellAt(e.getX(), e.getY()));
                            // Disables edges as drop targets if the target cell was created
                            if (dropTarget == null || !graph.getModel().isEdge(dropTarget)) {
                                CellState pstate = graph.getView().getState(dropTarget);
                                if (pstate != null) {
                                    Geometry geo = graph.getModel().getGeometry(vertex);
                                    PointDouble origin = pstate.getOrigin();
                                    geo.setX(geo.getX() - origin.getX());
                                    geo.setY(geo.getY() - origin.getY());
                                }
                            } else {
                                dropTarget = graph.getDefaultParent();
                            }
                            graph.addCells(List.of(vertex), dropTarget);
                            // FIXME: Here we pre-create the state for the vertex to be
                            // inserted in order to invoke update in the connectPreview.
                            // This means we have a cell state which should be created
                            // after the model.update, so this should be fixed.
                            CellState targetState = graph.getView().getState(vertex, true);
                            connectPreview.update(e, targetState, e.getX(), e.getY());
                        }

                        ICell cell = connectPreview.stop(graphComponent.isSignificant(dx, dy), e);

                        if (cell != null) {
                            graphComponent.getGraph().setSelectionCell(cell);
                            eventSource.fireEvent(new ConnectEvent(cell, e, dropTarget));
                        }

                        e.consume();
                    } finally {
                        graph.getModel().endUpdate();
                    }
                }
            }
        }

        reset();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!e.isConsumed() && graphComponent.isEnabled() && isEnabled()) {
            // Activates the handler
            if (!active && first != null) {
                double dx = Math.abs(first.getX() - e.getX());
                double dy = Math.abs(first.getY() - e.getY());
                int tol = graphComponent.getTolerance();

                if (dx > tol || dy > tol) {
                    active = true;
                }
            }

            if (e.getButton() == 0 || (isActive() && connectPreview.isActive())) {
                CellState state = marker.process(e);

                if (connectPreview.isActive()) {
                    connectPreview.update(e, marker.getValidState(), e.getX(), e.getY());
                    setBounds(null);
                    e.consume();
                } else {
                    source = state;
                }
            }
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseDragged(e);

        if (isHighlighting() && !marker.hasValidState()) {
            source = null;
        }

        if (!isHighlighting() && source != null) {
            int imgWidth = handleSize;
            int imgHeight = handleSize;

            if (connectIcon != null) {
                imgWidth = connectIcon.getIconWidth();
                imgHeight = connectIcon.getIconHeight();
            }

            int x = (int) source.getCenterX() - imgWidth / 2;
            int y = (int) source.getCenterY() - imgHeight / 2;

            if (graphComponent.getGraph().isSwimlane(source.getCell())) {
                RectangleDouble size = graphComponent.getGraph().getStartSize(source.getCell());

                if (size.getWidth() > 0) {
                    x = (int) (source.getX() + size.getWidth() / 2 - imgWidth / 2);
                } else {
                    y = (int) (source.getY() + size.getHeight() / 2 - imgHeight / 2);
                }
            }

            setBounds(new java.awt.Rectangle(x, y, imgWidth, imgHeight));
        } else {
            setBounds(null);
        }

        if (source != null && (bounds == null || bounds.contains(e.getPoint()))) {
            graphComponent.getGraphControl().setCursor(CONNECT_CURSOR);
            e.consume();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public void start(MouseEvent e, CellState state) {
        first = e.getPoint();
        connectPreview.start(e, state, "");
    }

    public boolean isActive() {
        return active;
    }

    public boolean isCreateTarget() {
        return createTarget;
    }

    public void setCreateTarget(boolean value) {
        createTarget = value;
    }

    public ICell createTargetVertex(MouseEvent e, ICell source) {
        Graph graph = graphComponent.getGraph();
        ICell clone = graph.cloneCells(List.of(source)).get(0);
        IGraphModel model = graph.getModel();
        Geometry geo = model.getGeometry(clone);

        if (geo != null) {
            PointDouble point = graphComponent.getPointForEvent(e);
            geo.setX(graph.snap(point.getX() - geo.getWidth() / 2));
            geo.setY(graph.snap(point.getY() - geo.getHeight() / 2));
        }

        return clone;
    }

    public void reset() {
        connectPreview.stop(false);
        setBounds(null);
        marker.reset();
        active = false;
        source = null;
        first = null;
        error = null;
    }

    public void setBounds(java.awt.Rectangle value) {
        if (bounds == null && value != null || bounds != null && value == null || bounds != null && !bounds.equals(
                value)) {
            java.awt.Rectangle tmp = bounds;
            if (tmp != null) {
                if (value != null) {
                    tmp.add(value);
                }
            } else {
                tmp = value;
            }
            bounds = value;
            graphComponent.getGraphControl().repaint(tmp);
        }
    }

    /**
     * Adds the given event listener.
     */
    public <T extends EventObject> void addListener(Class<T> eventClass, IEventListener<T> listener) {
        eventSource.addListener(eventClass, listener);
    }

    /**
     * Removes the given event listener.
     */
    public void removeListener(IEventListener<?> listener) {
        eventSource.removeListener(listener);
    }
}
