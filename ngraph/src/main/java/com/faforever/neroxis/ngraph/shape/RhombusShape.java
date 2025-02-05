package com.faforever.neroxis.ngraph.shape;

import com.faforever.neroxis.ngraph.canvas.Graphics2DCanvas;
import com.faforever.neroxis.ngraph.view.CellState;

import java.awt.*;

public class RhombusShape extends BasicShape {
    @Override
    public Shape createShape(Graphics2DCanvas canvas, CellState state) {
        Rectangle temp = state.getRectangle();
        int x = temp.x;
        int y = temp.y;
        int w = temp.width;
        int h = temp.height;
        int halfWidth = w / 2;
        int halfHeight = h / 2;

        Polygon rhombus = new Polygon();
        rhombus.addPoint(x + halfWidth, y);
        rhombus.addPoint(x + w, y + halfHeight);
        rhombus.addPoint(x + halfWidth, y + h);
        rhombus.addPoint(x, y + halfHeight);

        return rhombus;
    }
}
