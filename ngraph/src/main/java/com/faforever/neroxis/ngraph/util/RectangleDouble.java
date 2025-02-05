/**
 * Copyright (c) 2007-2010, Gaudenz Alder, David Benson
 */
package com.faforever.neroxis.ngraph.util;

import lombok.Getter;
import lombok.Setter;

import java.awt.geom.Rectangle2D;
import java.io.Serial;

/**
 * Implements a 2-dimensional rectangle with double precision coordinates.
 */
@Getter
@Setter
public class RectangleDouble extends Rectangle2D.Double {
    @Serial
    private static final long serialVersionUID = -3793966043543578946L;

    /**
     * Constructs a new rectangle at (0, 0) with the width and height set to 0.
     */
    public RectangleDouble() {
        this(0, 0, 0, 0);
    }

    /**
     * Constructs a rectangle using the given parameters.
     *
     * @param x      X-coordinate of the new rectangle.
     * @param y      Y-coordinate of the new rectangle.
     * @param width  Width of the new rectangle.
     * @param height Height of the new rectangle.
     */
    public RectangleDouble(double x, double y, double width, double height) {
        super(x, y, width, height);
    }

    /**
     * Constructs a copy of the given rectangle.
     *
     * @param rect Rectangle to construct a copy of.
     */
    public RectangleDouble(Rectangle2D rect) {
        this(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }

    /**
     * Constructs a copy of the given rectangle.
     *
     * @param rect Rectangle to construct a copy of.
     */
    public RectangleDouble(RectangleDouble rect) {
        this(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }

    public void setX(double value) {
        x = value;
    }

    public void setY(double value) {
        y = value;
    }

    /**
     * Returns the x-coordinate of the center.
     *
     * @return Returns the x-coordinate of the center.
     */
    @Override
    public double getCenterX() {
        return getX() + getWidth() / 2;
    }

    /**
     * Returns the y-coordinate of the center.
     *
     * @return Returns the y-coordinate of the center.
     */
    @Override
    public double getCenterY() {
        return getY() + getHeight() / 2;
    }

    /**
     * Returns a new instance of the same rectangle.
     */
    @Override
    public RectangleDouble clone() {
        RectangleDouble clone = (RectangleDouble) super.clone();
        clone.setWidth(getWidth());
        clone.setHeight(getHeight());
        return clone;
    }

    /**
     * Sets the width of the rectangle.
     *
     * @param value Double that specifies the new width.
     */
    public void setWidth(double value) {
        width = value;
    }

    /**
     * Sets the height of the rectangle.
     *
     * @param value Double that specifies the new height.
     */
    public void setHeight(double value) {
        height = value;
    }

    /**
     * Grows the rectangle by the given amount, that is, this method subtracts
     * the given amount from the x- and y-coordinates and adds twice the amount
     * to the width and height.
     *
     * @param amount Amount by which the rectangle should be grown.
     */
    public void grow(double amount) {
        x -= amount;
        y -= amount;
        width += 2 * amount;
        height += 2 * amount;
    }

    /**
     * Returns the point at which the specified point intersects the perimeter
     * of this rectangle or null if there is no intersection.
     *
     * @param x0 the x co-ordinate of the first point of the line
     * @param y0 the y co-ordinate of the first point of the line
     * @param x1 the x co-ordinate of the second point of the line
     * @param y1 the y co-ordinate of the second point of the line
     * @return the point at which the line intersects this rectangle, or null
     * if there is no intersection
     */
    public PointDouble intersectLine(double x0, double y0, double x1, double y1) {
        PointDouble result = null;
        result = Utils.intersection(x, y, x + width, y, x0, y0, x1, y1);
        if (result == null) {
            result = Utils.intersection(x + width, y, x + width, y + height, x0, y0, x1, y1);
        }
        if (result == null) {
            result = Utils.intersection(x + width, y + height, x, y + height, x0, y0, x1, y1);
        }
        if (result == null) {
            result = Utils.intersection(x, y, x, y + height, x0, y0, x1, y1);
        }
        return result;
    }

    public void add(RectangleDouble rect) {
        if (rect != null) {
            double minX = Math.min(x, rect.x);
            double minY = Math.min(y, rect.y);
            double maxX = Math.max(x + width, rect.x + rect.width);
            double maxY = Math.max(y + height, rect.y + rect.height);
            x = minX;
            y = minY;
            width = maxX - minX;
            height = maxY - minY;
        }
    }

    /**
     * Returns the bounds as a new rectangle.
     *
     * @return Returns a new rectangle for the bounds.
     */
    public java.awt.Rectangle getRectangle() {
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        int iw = (int) Math.round(width - ix + x);
        int ih = (int) Math.round(height - iy + y);

        return new java.awt.Rectangle(ix, iy, iw, ih);
    }

    /**
     * Rotates this rectangle by 90 degree around its center point.
     */
    public void rotate90() {
        double t = (this.width - this.height) / 2;
        this.x += t;
        this.y -= t;
        double tmp = this.width;
        this.width = this.height;
        this.height = tmp;
    }

    /**
     * Returns true if the given object equals this rectangle.
     */
    public boolean equals(Object obj) {
        if (obj instanceof RectangleDouble rect) {
            return rect.getX() == getX()
                   && rect.getY() == getY()
                   && rect.getWidth() == getWidth()
                   && rect.getHeight() == getHeight();
        }
        return false;
    }

    /**
     * Returns the <code>String</code> representation of this
     * <code>Rectangle</code>.
     *
     * @return a <code>String</code> representing this
     * <code>Rectangle</code>.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName()
               + " ["
               + "x="
               + x
               + ", y="
               + y
               + ", width="
               + width
               + ", height="
               + height
               + "]";
    }
}
