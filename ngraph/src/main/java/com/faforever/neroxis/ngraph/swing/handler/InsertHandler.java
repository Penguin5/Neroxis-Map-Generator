package com.faforever.neroxis.ngraph.swing.handler;

import com.faforever.neroxis.ngraph.event.AfterPaintEvent;
import com.faforever.neroxis.ngraph.event.EventObject;
import com.faforever.neroxis.ngraph.event.EventSource;
import com.faforever.neroxis.ngraph.event.EventSource.IEventListener;
import com.faforever.neroxis.ngraph.event.InsertEvent;
import com.faforever.neroxis.ngraph.model.ICell;
import com.faforever.neroxis.ngraph.swing.GraphComponent;
import com.faforever.neroxis.ngraph.util.PointDouble;
import com.faforever.neroxis.ngraph.util.RectangleDouble;
import com.faforever.neroxis.ngraph.view.Graph;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class InsertHandler extends MouseAdapter {
    /**
     * Reference to the enclosing graph component.
     */
    protected GraphComponent graphComponent;
    /**
     * Specifies if this handler is enabled. Default is true.
     */
    protected boolean enabled = true;
    protected String style;
    protected java.awt.Point first;
    protected float lineWidth = 1;
    protected Color lineColor = Color.black;
    protected boolean rounded = false;
    protected RectangleDouble current;
    protected EventSource eventSource = new EventSource(this);

    public InsertHandler(GraphComponent graphComponent, String style) {
        this.graphComponent = graphComponent;
        this.style = style;

        // Installs the paint handler
        graphComponent.addListener(AfterPaintEvent.class, (sender, evt) -> paint(evt.getGraphics()));

        // Listens to all mouse events on the rendering control
        graphComponent.getGraphControl().addMouseListener(this);
        graphComponent.getGraphControl().addMouseMotionListener(this);
    }

    public void paint(Graphics g) {
        if (first != null && current != null) {
            ((Graphics2D) g).setStroke(new BasicStroke(lineWidth));
            g.setColor(lineColor);
            java.awt.Rectangle rect = current.getRectangle();
            if (rounded) {
                g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 8, 8);
            } else {
                g.drawRect(rect.x, rect.y, rect.width, rect.height);
            }
        }
    }

    public GraphComponent getGraphComponent() {
        return graphComponent;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (graphComponent.isEnabled() && isEnabled() && !e.isConsumed() && isStartEvent(e)) {
            start(e);
            e.consume();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (graphComponent.isEnabled() && isEnabled() && !e.isConsumed() && current != null) {
            Graph graph = graphComponent.getGraph();
            double scale = graph.getView().getScale();
            PointDouble tr = graph.getView().getTranslate();
            current.setX(current.getX() / scale - tr.getX());
            current.setY(current.getY() / scale - tr.getY());
            current.setWidth(current.getWidth() / scale);
            current.setHeight(current.getHeight() / scale);
            ICell cell = insertCell(current);
            eventSource.fireEvent(new InsertEvent(cell));
            e.consume();
        }

        reset();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (graphComponent.isEnabled() && isEnabled() && !e.isConsumed() && first != null) {
            RectangleDouble dirty = current;
            current = new RectangleDouble(first.x, first.y, 0, 0);
            current.add(new RectangleDouble(e.getX(), e.getY(), 0, 0));
            if (dirty != null) {
                dirty.add(current);
            } else {
                dirty = current;
            }
            java.awt.Rectangle tmp = dirty.getRectangle();
            int b = (int) Math.ceil(lineWidth);
            graphComponent.getGraphControl().repaint(tmp.x - b, tmp.y - b, tmp.width + 2 * b, tmp.height + 2 * b);

            e.consume();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public boolean isStartEvent(MouseEvent e) {
        return true;
    }

    public void start(MouseEvent e) {
        first = e.getPoint();
    }

    public ICell insertCell(RectangleDouble bounds) {
        // FIXME: Clone prototype cell for insert
        return graphComponent.getGraph()
                             .insertVertex(null, null, "", bounds.getX(), bounds.getY(), bounds.getWidth(),
                                           bounds.getHeight(), style);
    }

    public void reset() {
        java.awt.Rectangle dirty = null;

        if (current != null) {
            dirty = current.getRectangle();
        }

        current = null;
        first = null;

        if (dirty != null) {
            int b = (int) Math.ceil(lineWidth);
            graphComponent.getGraphControl()
                          .repaint(dirty.x - b, dirty.y - b, dirty.width + 2 * b, dirty.height + 2 * b);
        }
    }

    public <T extends EventObject> void addListener(Class<T> eventClass, IEventListener<T> listener) {
        eventSource.addListener(eventClass, listener);
    }

    public void removeListener(IEventListener<?> listener) {
        eventSource.removeListener(listener);
    }
}
