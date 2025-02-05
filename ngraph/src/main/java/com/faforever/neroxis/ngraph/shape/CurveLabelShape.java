/**
 * Copyright (c) 2010, David Benson, Gaudenz Alder
 */
package com.faforever.neroxis.ngraph.shape;

import com.faforever.neroxis.ngraph.canvas.Graphics2DCanvas;
import com.faforever.neroxis.ngraph.style.Style;
import com.faforever.neroxis.ngraph.util.Curve;
import com.faforever.neroxis.ngraph.util.LineDouble;
import com.faforever.neroxis.ngraph.util.PointDouble;
import com.faforever.neroxis.ngraph.util.RectangleDouble;
import com.faforever.neroxis.ngraph.util.Utils;
import com.faforever.neroxis.ngraph.view.CellState;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.text.Bidi;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws the edge label along a curve derived from the curve describing
 * the edge's path
 */
public class CurveLabelShape implements ITextShape {
    /**
     * Buffer at both ends of the label
     */
    public static double LABEL_BUFFER = 30;
    /**
     * Factor by which text on the inside of curve is stretched
     */
    public static double CURVE_TEXT_STRETCH_FACTOR = 20.0;
    /**
     * Indicates that a glyph does not have valid drawing bounds, usually
     * because it is not visible
     */
    public static RectangleDouble INVALID_GLYPH_BOUNDS = new RectangleDouble(0, 0, 0, 0);
    /**
     * Specifies if image aspect should be preserved in drawImage. Default is true.
     */
    public static Object FONT_FRACTIONALMETRICS = RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT;
    /**
     * Shared FRC for font size calculations
     */
    public static FontRenderContext frc = new FontRenderContext(null, false, false);
    /**
     * The index of the central glyph of the label that is visible
     */
    public int centerVisibleIndex = 0;
    /**
     * Cache of BIDI glyph vectors
     */
    public GlyphVector[] rtlGlyphVectors;
    /**
     * Cache of the label text
     */
    protected String lastValue;
    /**
     * Cache of the label font
     */
    protected Font lastFont;
    /**
     * Cache of the last set of guide points that this label was calculated for
     */
    protected List<PointDouble> lastPoints;
    /**
     * Cache of the points between which drawing straight lines views as a
     * curve
     */
    protected Curve curve;
    /**
     * Cache the state associated with this shape
     */
    protected CellState state;
    /**
     * Cache of information describing characteristics relating to drawing
     * each glyph of this label
     */
    protected LabelGlyphCache[] labelGlyphs;
    /**
     * Cache of the total length of the branch label
     */
    protected double labelSize;
    /**
     * Cache of the bounds of the label
     */
    protected RectangleDouble labelBounds;
    /**
     * ADT to encapsulate label positioning information
     */
    protected LabelPosition labelPosition = new LabelPosition();
    protected boolean rotationEnabled = true;

    public CurveLabelShape(CellState state, Curve value) {
        this.state = state;
        this.curve = value;
    }

    public boolean getRotationEnabled() {
        return rotationEnabled;
    }

    public void setRotationEnabled(boolean value) {
        rotationEnabled = value;
    }

    @Override
    public void paintShape(Graphics2DCanvas canvas, String text, CellState state, Style style) {
        java.awt.Rectangle rect = state.getLabelBounds().getRectangle();
        Graphics2D g = canvas.getGraphics();
        if (labelGlyphs == null) {
            updateLabelBounds(text, style);
        }
        if (labelGlyphs != null && (g.getClipBounds() == null || g.getClipBounds().intersects(rect))) {
            // Creates a temporary graphics instance for drawing this shape
            float opacity = style.getShape().getOpacity();
            Graphics2D previousGraphics = g;
            g = canvas.createTemporaryGraphics(style, opacity, state);
            Font font = Utils.getFont(style, canvas.getScale());
            g.setFont(font);
            Color fontColor = style.getLabel().getTextColor();
            g.setColor(fontColor);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, FONT_FRACTIONALMETRICS);
            for (LabelGlyphCache labelGlyph : labelGlyphs) {
                LineDouble parallel = labelGlyph.glyphGeometry;
                if (labelGlyph.visible && parallel != null && parallel != Curve.INVALID_POSITION) {
                    PointDouble parallelEnd = parallel.getP2();
                    double x = parallelEnd.getX();
                    double rotation = (Math.atan(parallelEnd.getY() / x));
                    if (x < 0) {
                        // atan only ranges from -PI/2 to PI/2, have to offset
                        // for negative x values
                        rotation += Math.PI;
                    }
                    final AffineTransform old = g.getTransform();
                    g.translate(parallel.getX1(), parallel.getY1());
                    g.rotate(rotation);
                    Shape letter = labelGlyph.glyphShape;
                    g.fill(letter);
                    g.setTransform(old);
                }
            }

            g.dispose();
            g = previousGraphics;
        }
    }

    /**
     * Updates the cached position and size of each glyph in the edge label.
     *
     * @param label the entire string of the label.
     * @param style the edge style
     */
    public RectangleDouble updateLabelBounds(String label, Style style) {
        double scale = state.getView().getScale();
        Font font = Utils.getFont(style, scale);
        FontMetrics fm = Utils.getFontMetrics(font);
        int descent = 0;
        int ascent = 0;
        if (fm != null) {
            descent = fm.getDescent();
            ascent = fm.getAscent();
        }

        // Check that the size of the widths array matches
        // that of the label size
        if (labelGlyphs == null || (!label.equals(lastValue))) {
            labelGlyphs = new LabelGlyphCache[label.length()];
        }

        if (!label.equals(lastValue) || !font.equals(lastFont)) {
            char[] labelChars = label.toCharArray();
            ArrayList<LabelGlyphCache> glyphList = new ArrayList<LabelGlyphCache>();
            boolean bidiRequired = Bidi.requiresBidi(labelChars, 0, labelChars.length);

            labelSize = 0;

            if (bidiRequired) {
                Bidi bidi = new Bidi(label, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);

                int runCount = bidi.getRunCount();

                if (rtlGlyphVectors == null || rtlGlyphVectors.length != runCount) {
                    rtlGlyphVectors = new GlyphVector[runCount];
                }

                for (int i = 0; i < bidi.getRunCount(); i++) {
                    final String labelSection = label.substring(bidi.getRunStart(i), bidi.getRunLimit(i));
                    rtlGlyphVectors[i] = font.layoutGlyphVector(CurveLabelShape.frc, labelSection.toCharArray(), 0,
                                                                labelSection.length(), Font.LAYOUT_RIGHT_TO_LEFT);
                }

                int charCount = 0;

                for (GlyphVector gv : rtlGlyphVectors) {
                    float vectorOffset = 0.0f;

                    for (int j = 0; j < gv.getNumGlyphs(); j++) {
                        Shape shape = gv.getGlyphOutline(j, -vectorOffset, 0);

                        LabelGlyphCache qlyph = new LabelGlyphCache();
                        glyphList.add(qlyph);
                        qlyph.glyphShape = shape;
                        RectangleDouble size = new RectangleDouble(gv.getGlyphLogicalBounds(j).getBounds2D());
                        qlyph.labelGlyphBounds = size;
                        labelSize += size.getWidth();
                        vectorOffset += size.getWidth();

                        charCount++;
                    }
                }
            } else {
                rtlGlyphVectors = null;
                //String locale = System.getProperty("user.language");
                // Character iterator required where character is split over
                // string elements
                BreakIterator it = BreakIterator.getCharacterInstance(Locale.getDefault());
                it.setText(label);

                for (int i = 0; i < label.length(); ) {
                    int next = it.next();
                    int characterLen = 1;

                    if (next != BreakIterator.DONE) {
                        characterLen = next - i;
                    }

                    String glyph = label.substring(i, i + characterLen);

                    LabelGlyphCache labelGlyph = new LabelGlyphCache();
                    glyphList.add(labelGlyph);
                    labelGlyph.glyph = glyph;
                    GlyphVector vector = font.createGlyphVector(frc, glyph);
                    labelGlyph.glyphShape = vector.getOutline();

                    if (fm == null) {
                        RectangleDouble size = new RectangleDouble(font.getStringBounds(glyph, CurveLabelShape.frc));
                        labelGlyph.labelGlyphBounds = size;
                        labelSize += size.getWidth();
                    } else {
                        double width = fm.stringWidth(glyph);
                        labelGlyph.labelGlyphBounds = new RectangleDouble(0, 0, width, ascent);
                        labelSize += width;
                    }

                    i += characterLen;
                }
            }

            // Update values used to determine whether or not the label cache
            // is valid or not
            lastValue = label;
            lastFont = font;
            lastPoints = curve.getGuidePoints();
            this.labelGlyphs = glyphList.toArray(new LabelGlyphCache[glyphList.size()]);
        }

        // Store the start/end buffers that pad out the ends of the branch so the label is
        // visible. We work initially as the start section being at the start of the
        // branch and the end at the end of the branch. Note that the actual label curve
        // might be reversed, so we allow for this after completing the buffer calculations,
        // otherwise they'd need to be constant isReversed() checks throughout
        labelPosition.startBuffer = LABEL_BUFFER * scale;
        labelPosition.endBuffer = LABEL_BUFFER * scale;

        calculationLabelPosition(style, label);

        if (curve.isLabelReversed()) {
            double temp = labelPosition.startBuffer;
            labelPosition.startBuffer = labelPosition.endBuffer;
            labelPosition.endBuffer = temp;
        }

        double curveLength = curve.getCurveLength(Curve.LABEL_CURVE);
        double currentPos = labelPosition.startBuffer / curveLength;
        double endPos = 1.0 - (labelPosition.endBuffer / curveLength);
        RectangleDouble overallLabelBounds = null;
        centerVisibleIndex = 0;

        double currentCurveDelta = 0.0;
        double curveDeltaSignificant = 0.3;
        double curveDeltaMax = 0.5;
        LineDouble nextParallel = null;

        // TODO on translation just move the points, don't recalculate
        // Might be better than the curve is the only thing updated and
        // the curve shapes listen to curve events
        // !lastPoints.equals(curve.getGuidePoints())
        for (int j = 0; j < labelGlyphs.length; j++) {
            if (currentPos > endPos) {
                labelGlyphs[j].visible = false;
                continue;
            }
            LineDouble parallel = nextParallel;

            if (currentCurveDelta > curveDeltaSignificant || nextParallel == null) {
                parallel = curve.getCurveParallel(Curve.LABEL_CURVE, currentPos);

                currentCurveDelta = 0.0;
                nextParallel = null;
            }

            labelGlyphs[j].glyphGeometry = parallel;
            if (parallel == Curve.INVALID_POSITION) {
                continue;
            }
            // Get the four corners of the rotated rectangle bounding the glyph
            // The drawing bounds of the glyph is the unrotated rect that
            // just bounds those four corners
            final double w = labelGlyphs[j].labelGlyphBounds.getWidth();
            final double h = labelGlyphs[j].labelGlyphBounds.getHeight();
            final double x = parallel.getP2().getX();
            final double y = parallel.getP2().getY();
            // Bottom left
            double p1X = parallel.getX1() - (descent * y);
            double minX = p1X, maxX = p1X;
            double p1Y = parallel.getY1() + (descent * x);
            double minY = p1Y, maxY = p1Y;
            // Top left
            double p2X = p1X + ((h + descent) * y);
            double p2Y = p1Y - ((h + descent) * x);
            minX = Math.min(minX, p2X);
            maxX = Math.max(maxX, p2X);
            minY = Math.min(minY, p2Y);
            maxY = Math.max(maxY, p2Y);
            // Bottom right
            double p3X = p1X + (w * x);
            double p3Y = p1Y + (w * y);
            minX = Math.min(minX, p3X);
            maxX = Math.max(maxX, p3X);
            minY = Math.min(minY, p3Y);
            maxY = Math.max(maxY, p3Y);
            // Top right
            double p4X = p2X + (w * x);
            double p4Y = p2Y + (w * y);
            minX = Math.min(minX, p4X);
            maxX = Math.max(maxX, p4X);
            minY = Math.min(minY, p4Y);
            maxY = Math.max(maxY, p4Y);

            minX -= 2 * scale;
            minY -= 2 * scale;
            maxX += 2 * scale;
            maxY += 2 * scale;

            // Hook for sub-classers
            postprocessGlyph(curve, label, j, currentPos);

            // Need to allow for text on inside of curve bends. Need to get the
            // parallel for the next section, if there is an excessive
            // inner curve, advance the current position accordingly

            double currentPosCandidate = currentPos
                                         + (labelGlyphs[j].labelGlyphBounds.getWidth()
                                            + labelPosition.defaultInterGlyphSpace) / curveLength;

            nextParallel = curve.getCurveParallel(Curve.LABEL_CURVE, currentPosCandidate);

            currentPos = currentPosCandidate;
            PointDouble nextVector = nextParallel.getP2();
            double end2X = nextVector.getX();
            double end2Y = nextVector.getY();

            if (nextParallel != Curve.INVALID_POSITION && j + 1 < label.length()) {
                // Extend the current parallel line in its direction
                // by the length of the next parallel. Use the approximate
                // deviation to work out the angle change
                double deltaX = Math.abs(x - end2X);
                double deltaY = Math.abs(y - end2Y);

                // The difference as a proportion of the length of the next
                // vector. 1 means a variation of 60 degrees.
                currentCurveDelta = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            }

            if (currentCurveDelta > curveDeltaSignificant) {
                // Work out which direction the curve is going in
                int ccw = Line2D.relativeCCW(0, 0, x, y, end2X, end2Y);

                if (ccw == 1) {
                    // Text is on inside of curve
                    if (currentCurveDelta > curveDeltaMax) {
                        // Don't worry about excessive deltas, if they
                        // are big the label curve will be screwed anyway
                        currentCurveDelta = curveDeltaMax;
                    }

                    double textBuffer = currentCurveDelta * CURVE_TEXT_STRETCH_FACTOR / curveLength;
                    currentPos += textBuffer;
                    endPos += textBuffer;
                }
            }

            if (labelGlyphs[j].drawingBounds != null) {
                labelGlyphs[j].drawingBounds.setRect(minX, minY, maxX - minX, maxY - minY);
            } else {
                labelGlyphs[j].drawingBounds = new RectangleDouble(minX, minY, maxX - minX, maxY - minY);
            }

            if (overallLabelBounds == null) {
                overallLabelBounds = labelGlyphs[j].drawingBounds.clone();
            } else {
                overallLabelBounds.add(labelGlyphs[j].drawingBounds);
            }

            labelGlyphs[j].visible = true;
            centerVisibleIndex++;
        }

        centerVisibleIndex /= 2;

        if (overallLabelBounds == null) {
            // Return a small rectangle in the center of the label curve
            // Null label bounds causes NPE when editing
            LineDouble labelCenter = curve.getCurveParallel(Curve.LABEL_CURVE, 0.5);
            overallLabelBounds = new RectangleDouble(labelCenter.getX1(), labelCenter.getY1(), 1, 1);
        }

        this.labelBounds = overallLabelBounds;
        return overallLabelBounds;
    }

    /**
     * Hook method to override how the label is positioned on the curve
     *
     * @param style the style of the curve
     * @param label the string label to be displayed on the curve
     */
    protected void calculationLabelPosition(Style style, String label) {
        double curveLength = curve.getCurveLength(Curve.LABEL_CURVE);
        double availableLabelSpace = curveLength - labelPosition.startBuffer - labelPosition.endBuffer;
        labelPosition.startBuffer = Math.max(labelPosition.startBuffer,
                                             labelPosition.startBuffer + availableLabelSpace / 2 - labelSize / 2);
        labelPosition.endBuffer = Math.max(labelPosition.endBuffer,
                                           labelPosition.endBuffer + availableLabelSpace / 2 - labelSize / 2);
    }

    /**
     * Hook for sub-classers to perform additional processing on
     * each glyph
     *
     * @param curve      The curve object holding the label curve
     * @param label      the text label of the curve
     * @param j          the index of the label
     * @param currentPos the distance along the label curve the glyph is
     */
    protected void postprocessGlyph(Curve curve, String label, int j, double currentPos) {
    }

    /**
     * Returns whether or not the rectangle passed in hits any part of this
     * curve.
     *
     * @param rect the rectangle to detect for a hit
     * @return whether or not the rectangle hits this curve
     */
    public boolean intersectsRect(java.awt.Rectangle rect) {
        // To save CPU, we can test if the rectangle intersects the entire
        // bounds of this label
        if ((labelBounds != null && (!labelBounds.getRectangle().intersects(rect))) || labelGlyphs == null) {
            return false;
        }

        for (int i = 0; i < labelGlyphs.length; i++) {
            if (labelGlyphs[i].visible && rect.intersects(labelGlyphs[i].drawingBounds.getRectangle())) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the curve
     */
    public Curve getCurve() {
        return curve;
    }

    /**
     * @param curve the curve to set
     */
    public void setCurve(Curve curve) {
        this.curve = curve;
    }

    public RectangleDouble getLabelBounds() {
        return labelBounds;
    }

    /**
     * Returns the drawing bounds of the central indexed visible glyph
     *
     * @return the centerVisibleIndex
     */
    public RectangleDouble getCenterVisiblePosition() {
        return labelGlyphs[centerVisibleIndex].drawingBounds;
    }

    /**
     * Utility class to describe the characteristics of each glyph of a branch
     * branch label. Each instance represents one glyph
     */
    public class LabelGlyphCache {
        /**
         * Cache of the bounds of the individual element of the label of this
         * edge. Note that these are the unrotated values used to determine the
         * width of each glyph.
         */
        public RectangleDouble labelGlyphBounds;
        /**
         * The un-rotated rectangle that just bounds this character
         */
        public RectangleDouble drawingBounds;
        /**
         * The glyph being drawn
         */
        public String glyph;
        /**
         * A line parallel to the curve segment at which the element is to be
         * drawn
         */
        public LineDouble glyphGeometry;
        /**
         * The cached shape of the glyph
         */
        public Shape glyphShape;
        /**
         * Whether or not the glyph should be drawn
         */
        public boolean visible;
    }

    /**
     * Utility class that stores details of how the label is positioned
     * on the curve
     */
    public class LabelPosition {
        public double startBuffer = LABEL_BUFFER;
        public double endBuffer = LABEL_BUFFER;
        public double defaultInterGlyphSpace = 0;
    }
}
