/**
 * Copyright (c) 2008, Gaudenz Alder
 */
package com.faforever.neroxis.ngraph.swing.handler;

import com.faforever.neroxis.ngraph.swing.GraphComponent;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 *
 */
public class PanningHandler extends MouseAdapter {
    private static final long serialVersionUID = 7969814728058376339L;
    protected GraphComponent graphComponent;
    protected boolean enabled = true;
    protected transient Point start;

    public PanningHandler(GraphComponent graphComponent) {
        this.graphComponent = graphComponent;

        graphComponent.getGraphControl().addMouseListener(this);
        graphComponent.getGraphControl().addMouseMotionListener(this);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (isEnabled() && !e.isConsumed() && graphComponent.isPanningEvent(e) && !e.isPopupTrigger()) {
            start = e.getPoint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (!e.isConsumed() && start != null) {
            int dx = Math.abs(start.x - e.getX());
            int dy = Math.abs(start.y - e.getY());

            if (graphComponent.isSignificant(dx, dy)) {
                e.consume();
            }
        }

        start = null;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!e.isConsumed() && start != null) {
            int dx = e.getX() - start.x;
            int dy = e.getY() - start.y;

            Rectangle r = graphComponent.getViewport().getViewRect();

            int right = r.x + ((dx > 0) ? 0 : r.width) - dx;
            int bottom = r.y + ((dy > 0) ? 0 : r.height) - dy;

            graphComponent.getGraphControl().scrollRectToVisible(new Rectangle(right, bottom, 0, 0));

            e.consume();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Whether or not panning is currently active
     *
     * @return Whether or not panning is currently active
     */
    public boolean isActive() {
        return (start != null);
    }
}
