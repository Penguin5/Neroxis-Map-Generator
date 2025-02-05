package com.faforever.neroxis.mask;

import com.faforever.neroxis.annotations.GraphMethod;
import com.faforever.neroxis.annotations.GraphParameter;
import com.faforever.neroxis.map.Symmetry;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.map.SymmetryType;
import com.faforever.neroxis.util.BezierCurve;
import com.faforever.neroxis.util.functional.BiIntBooleanConsumer;
import com.faforever.neroxis.util.functional.ToBooleanBiIntFunction;
import com.faforever.neroxis.util.vector.Vector2;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.faforever.neroxis.brushes.Brushes.loadBrush;

@SuppressWarnings({"unchecked", "UnusedReturnValue", "unused"})
public class BooleanMask extends PrimitiveMask<Boolean, BooleanMask> {
    private static final int BOOLEANS_PER_LONG = 64;
    private static final long SINGLE_BIT_VALUE = 1;
    protected long[] mask;
    private int maskBooleanSize;

    public BooleanMask(int size, Long seed, SymmetrySettings symmetrySettings) {
        this(size, seed, symmetrySettings, null, false);
    }

    /**
     * Create a new boolean mask
     *
     * @param size             Size of the mask
     * @param seed             Random seed of the mask
     * @param symmetrySettings symmetrySettings to enforce on the mask
     * @param name             name of the mask
     * @param parallel         whether to parallelize mask operations
     */
    @GraphMethod
    @GraphParameter(name = "name", value = "identifier")
    @GraphParameter(name = "parallel", value = "true")
    @GraphParameter(name = "seed", value = "random.nextLong()")
    @GraphParameter(name = "symmetrySettings", value = "symmetrySettings")
    public BooleanMask(int size, Long seed, SymmetrySettings symmetrySettings, String name, boolean parallel) {
        super(size, seed, symmetrySettings, name, parallel);
    }

    public BooleanMask(int size, Long seed, SymmetrySettings symmetrySettings, String name) {
        this(size, seed, symmetrySettings, name, false);
    }

    protected BooleanMask(BooleanMask other) {
        this(other, (String) null);
    }

    protected BooleanMask(BooleanMask other, String name) {
        super(other, name);
    }

    protected <T extends ComparableMask<U, ?>, U extends Comparable<U>> BooleanMask(T other, U minValue) {
        this(other, minValue, (String) null);
    }

    protected <T extends ComparableMask<U, ?>, U extends Comparable<U>> BooleanMask(T other, U minValue, String name) {
        this(other.getSize(), other.getNextSeed(), other.getSymmetrySettings(), name, other.isParallel());
        enqueue(dependencies -> {
            T source = (T) dependencies.get(0);
            apply((x, y) -> setPrimitive(x, y, source.valueAtGreaterThanEqualTo(x, y, minValue)));
        }, other);
    }

    public <T extends ComparableMask<U, ?>, U extends Comparable<U>> BooleanMask(T other, U minValue, U maxValue) {
        this(other, minValue, maxValue, null);
    }

    public <T extends ComparableMask<U, ?>, U extends Comparable<U>> BooleanMask(T other, U minValue, U maxValue,
                                                                                 String name) {
        this(other.getSize(), other.getNextSeed(), other.getSymmetrySettings(), name, other.isParallel());
        enqueue(dependencies -> {
            T source = (T) dependencies.get(0);
            apply((x, y) -> setPrimitive(x, y, source.valueAtGreaterThanEqualTo(x, y, minValue)
                                               && source.valueAtLessThanEqualTo(x, y, maxValue)));
        }, other);
    }

    protected static void setBit(int x, int y, boolean value, int size, long[] mask) {
        setBit(bitIndex(x, y, size), value, mask);
    }

    protected static void setBit(int bitIndex, boolean value, long[] mask) {
        if (value) {
            mask[arrayIndex(bitIndex)] |= SINGLE_BIT_VALUE << bitIndex;
        } else {
            mask[arrayIndex(bitIndex)] &= ~(SINGLE_BIT_VALUE << bitIndex);
        }
    }

    private static int arrayIndex(int bitIndex) {
        return bitIndex / BOOLEANS_PER_LONG;
    }

    private static int bitIndex(int x, int y, int size) {
        return x * size + y;
    }

    protected static boolean getBit(int x, int y, int size, long[] mask) {
        return getBit(bitIndex(x, y, size), mask);
    }

    protected static boolean getBit(int bitIndex, long[] mask) {
        return (mask[arrayIndex(bitIndex)] & (SINGLE_BIT_VALUE << bitIndex)) != 0;
    }

    private static int minimumArraySize(int size) {
        return (size * size / BOOLEANS_PER_LONG) + 1;
    }

    protected void setPrimitive(int x, int y, boolean value) {
        setBit(x, y, value, getSize(), mask);
    }

    @Override
    public BooleanMask blur(int radius) {
        return blur(radius, .5f);
    }

    @Override
    public BooleanMask blur(int radius, BooleanMask other) {
        assertCompatibleMask(other);
        int[][] innerCount = getInnerCount();
        return enqueue(dependencies -> {
            BooleanMask limiter = (BooleanMask) dependencies.get(0);
            apply((x, y) -> {
                if (limiter.get(x, y)) {
                    setPrimitive(x, y, transformAverage(calculateAreaAverageAsInts(radius, x, y, innerCount), .5f));
                }
            });
        }, other);
    }

    @Override
    protected BooleanMask copyFrom(BooleanMask other) {
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            fill(source.mask, source.maskBooleanSize);
        }, other);
    }

    @Override
    protected void initializeMask(int size) {
        enqueue(() -> {
            mask = new long[minimumArraySize(size)];
            maskBooleanSize = size;
        });
    }

    @Override
    protected int getImmediateSize() {
        return maskBooleanSize;
    }

    @Override
    public BufferedImage writeToImage(BufferedImage image) {
        assertSize(image.getHeight());
        int size = getSize();
        DataBuffer imageBuffer = image.getRaster().getDataBuffer();
        loop((x, y) -> imageBuffer.setElem(x + y * size, getPrimitive(x, y) ? 255 : 0));
        return image;
    }

    @Override
    public BufferedImage toImage() {
        int size = getSize();
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
        writeToImage(image);
        return image;
    }

    @Override
    public String toHash() throws NoSuchAlgorithmException {
        int size = getSize();
        ByteBuffer bytes = ByteBuffer.allocate(size * size);
        loopWithSymmetry(SymmetryType.SPAWN, (x, y) -> bytes.put(getPrimitive(x, y) ? (byte) 1 : 0));
        byte[] data = MessageDigest.getInstance("MD5").digest(bytes.array());
        StringBuilder stringBuilder = new StringBuilder();
        for (byte datum : data) {
            stringBuilder.append(String.format("%02x", datum));
        }
        return stringBuilder.toString();
    }

    @Override
    public Boolean get(int x, int y) {
        return getPrimitive(x, y);
    }

    @Override
    protected void set(int x, int y, Boolean value) {
        setPrimitive(x, y, value);
    }

    @Override
    protected BooleanMask fill(Boolean value) {
        return enqueue(() -> {
            int arrayLength = mask.length;
            mask[0] = value ? ~0 : 0;
            for (int i = 1; i < arrayLength; i += i) {
                System.arraycopy(mask, 0, mask, i, StrictMath.min((arrayLength - i), i));
            }
        });
    }

    @Override
    protected Boolean getZeroValue() {
        return false;
    }

    @Override
    protected BooleanMask setSizeInternal(int newSize) {
        return enqueue(() -> {
            int oldSize = getSize();
            if (oldSize == 1) {
                boolean value = getPrimitive(0, 0);
                initializeMask(newSize);
                fill(value);
            } else if (oldSize != newSize) {
                long[] oldMask = mask;
                initializeMask(newSize);
                Map<Integer, Integer> coordinateMap = getSymmetricScalingCoordinateMap(oldSize, newSize);
                applyWithSymmetry(SymmetryType.SPAWN, (x, y) -> {
                    boolean value = getBit(coordinateMap.get(x), coordinateMap.get(y), oldSize, oldMask);
                    applyAtSymmetryPoints(x, y, SymmetryType.SPAWN, (sx, sy) -> setPrimitive(sx, sy, value));
                });
            }
        });
    }

    public boolean getPrimitive(int x, int y) {
        return getBit(x, y, getSize(), mask);
    }

    protected BooleanMask fill(long[] arrayToFillFrom, int maskBooleanSize) {
        int arraySize = arrayToFillFrom.length;
        mask = new long[arraySize];
        this.maskBooleanSize = maskBooleanSize;
        System.arraycopy(arrayToFillFrom, 0, mask, 0, arraySize);
        return this;
    }

    /**
     * Blur the mask setting the pixel to true when more than {@code density}
     * pixels in the 2*radius square are true
     *
     * @param radius  half length of the square filter to use
     * @param density percentage of pixels in the filter require to be true to set the pixel to try (Values: 0-1)
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask blur(int radius, float density) {
        int[][] innerCount = getInnerCount();
        return apply((x, y) -> setPrimitive(x, y, transformAverage(calculateAreaAverageAsInts(radius, x, y, innerCount),
                                                                   density)));
    }

    @Override
    protected int[][] getInnerCount() {
        int size = getSize();
        int[][] innerCount = new int[size][size];
        apply((x, y) -> calculateInnerValue(innerCount, x, y, getPrimitive(x, y) ? 1 : 0));
        return innerCount;
    }

    private boolean transformAverage(float value, float threshold) {
        return value >= threshold;
    }

    @Override
    public Boolean getSum() {
        throw new UnsupportedOperationException("Sum not supported for BooleanMask");
    }

    @Override
    public BooleanMask add(BooleanMask other) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            for (int i = 0; i < mask.length; i++) {
                mask[i] |= source.mask[i];
            }
        }, other);
    }

    @Override
    protected void addValueAt(int x, int y, Boolean value) {
        setPrimitive(x, y, value | getPrimitive(x, y));
    }

    @Override
    public BooleanMask add(BooleanMask other, Boolean value) {
        assertCompatibleMask(other);
        boolean val = value;
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            apply((x, y) -> {
                if (source.getPrimitive(x, y)) {
                    addPrimitiveAt(x, y, val);
                }
            });
        }, other);
    }

    @Override
    public BooleanMask add(BooleanMask other, BooleanMask values) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            BooleanMask val = (BooleanMask) dependencies.get(1);
            apply((x, y) -> {
                if (source.getPrimitive(x, y)) {
                    addPrimitiveAt(x, y, val.getPrimitive(x, y));
                }
            });
        }, other, values);
    }

    @Override
    public BooleanMask addWithOffset(BooleanMask other, int xOffset, int yOffset, boolean center, boolean wrapEdges) {
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            applyWithOffset(source, (BiIntBooleanConsumer) this::addPrimitiveAt, xOffset, yOffset, center, wrapEdges);
        }, other);
    }

    @Override
    protected void subtractValueAt(int x, int y, Boolean value) {
        setPrimitive(x, y, !value & getPrimitive(x, y));
    }

    @Override
    public Boolean getAvg() {
        assertNotPipelined();
        float size = getSize();
        return getCount() / size / size > .5f;
    }

    @Override
    public BooleanMask subtract(BooleanMask other) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            for (int i = 0; i < mask.length; i++) {
                mask[i] &= ~source.mask[i];
            }
        }, other);
    }

    @Override
    public BooleanMask subtract(BooleanMask other, Boolean value) {
        assertCompatibleMask(other);
        boolean val = value;
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            apply((x, y) -> {
                if (source.getPrimitive(x, y)) {
                    subtractPrimitiveAt(x, y, val);
                }
            });
        }, other);
    }

    @Override
    public BooleanMask subtract(BooleanMask other, BooleanMask values) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            BooleanMask val = (BooleanMask) dependencies.get(1);
            apply((x, y) -> {
                if (source.getPrimitive(x, y)) {
                    subtractPrimitiveAt(x, y, val.getPrimitive(x, y));
                }
            });
        }, other, values);
    }

    @Override
    public BooleanMask subtractWithOffset(BooleanMask other, int xOffset, int yOffset, boolean center,
                                          boolean wrapEdges) {
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            applyWithOffset(source, (BiIntBooleanConsumer) this::subtractPrimitiveAt, xOffset, yOffset, center,
                            wrapEdges);
        }, other);
    }

    @Override
    public BooleanMask multiply(BooleanMask other) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            for (int i = 0; i < mask.length; i++) {
                mask[i] &= source.mask[i];
            }
        }, other);
    }

    @Override
    protected void multiplyValueAt(int x, int y, Boolean value) {
        setPrimitive(x, y, value & getPrimitive(x, y));
    }

    @Override
    public BooleanMask multiply(BooleanMask other, Boolean value) {
        assertCompatibleMask(other);
        boolean val = value;
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            apply((x, y) -> {
                if (source.getPrimitive(x, y)) {
                    multiplyPrimitiveAt(x, y, val);
                }
            });
        }, other);
    }

    @Override
    public BooleanMask multiply(BooleanMask other, BooleanMask values) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            BooleanMask val = (BooleanMask) dependencies.get(1);
            apply((x, y) -> {
                if (source.getPrimitive(x, y)) {
                    multiplyPrimitiveAt(x, y, val.getPrimitive(x, y));
                }
            });
        }, other, values);
    }

    @Override
    public BooleanMask multiplyWithOffset(BooleanMask other, int xOffset, int yOffset, boolean center,
                                          boolean wrapEdges) {
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            applyWithOffset(source, (BiIntBooleanConsumer) this::multiplyPrimitiveAt, xOffset, yOffset, center,
                            wrapEdges);
        }, other);
    }

    @Override
    public BooleanMask divide(BooleanMask other) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            for (int i = 0; i < mask.length; i++) {
                mask[i] ^= source.mask[i];
            }
        }, other);
    }

    @Override
    protected void divideValueAt(int x, int y, Boolean value) {
        setPrimitive(x, y, value ^ getPrimitive(x, y));
    }

    @Override
    public BooleanMask divide(BooleanMask other, Boolean value) {
        assertCompatibleMask(other);
        boolean val = value;
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            apply((x, y) -> {
                if (source.getPrimitive(x, y)) {
                    dividePrimitiveAt(x, y, val);
                }
            });
        }, other);
    }

    @Override
    public BooleanMask divide(BooleanMask other, BooleanMask value) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            BooleanMask val = (BooleanMask) dependencies.get(1);
            apply((x, y) -> {
                if (source.getPrimitive(x, y)) {
                    dividePrimitiveAt(x, y, val.getPrimitive(x, y));
                }
            });
        }, other, value);
    }

    @Override
    public BooleanMask divideWithOffset(BooleanMask other, int xOffset, int yOffset, boolean center,
                                        boolean wrapEdges) {
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            applyWithOffset(source, (BiIntBooleanConsumer) this::dividePrimitiveAt, xOffset, yOffset, center,
                            wrapEdges);
        }, other);
    }

    public int getCount() {
        assertNotPipelined();
        int count = 0;
        int size = getSize();
        for (long l : mask) {
            count += Long.bitCount(l);
        }
        return count;
    }

    protected BooleanMask add(ToBooleanBiIntFunction valueFunction) {
        return apply((x, y) -> addPrimitiveAt(x, y, valueFunction.apply(x, y)));
    }

    protected BooleanMask subtract(ToBooleanBiIntFunction valueFunction) {
        return apply((x, y) -> subtractPrimitiveAt(x, y, valueFunction.apply(x, y)));
    }

    protected BooleanMask multiply(ToBooleanBiIntFunction valueFunction) {
        return apply((x, y) -> multiplyPrimitiveAt(x, y, valueFunction.apply(x, y)));
    }

    protected BooleanMask divide(ToBooleanBiIntFunction valueFunction) {
        return apply((x, y) -> dividePrimitiveAt(x, y, valueFunction.apply(x, y)));
    }

    protected void addPrimitiveAt(int x, int y, boolean value) {
        setPrimitive(x, y, value | getPrimitive(x, y));
    }

    protected void subtractPrimitiveAt(int x, int y, boolean value) {
        setPrimitive(x, y, !value & getPrimitive(x, y));
    }

    protected void multiplyPrimitiveAt(int x, int y, boolean value) {
        setPrimitive(x, y, value & getPrimitive(x, y));
    }

    protected void dividePrimitiveAt(int x, int y, boolean value) {
        setPrimitive(x, y, value ^ getPrimitive(x, y));
    }

    @Override
    public Boolean getMin() {
        throw new UnsupportedOperationException("Min not supported for BooleanMask");
    }

    @Override
    public Boolean getMax() {
        throw new UnsupportedOperationException("Max not supported for BooleanMask");
    }

    public boolean getPrimitive(Vector2 location) {
        return getPrimitive(StrictMath.round(location.getX()), StrictMath.round(location.getY()));
    }

    protected void setPrimitive(Vector2 location, boolean value) {
        setPrimitive(StrictMath.round(location.getX()), StrictMath.round(location.getY()), value);
    }

    public <T extends Comparable<T>, U extends ComparableMask<T, U>> BooleanMask init(ComparableMask<T, U> other,
                                                                                      T minValue) {
        init(other.copyAsBooleanMask(minValue));
        return this;
    }

    /**
     * Randomize the mask by setting each pixel to true with a probability of {@code density}
     * Respects the terrain symmetry
     *
     * @param density probability that a given pixel will be true (Values: 0-1)
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask randomize(float density) {
        return randomize(density, SymmetryType.TERRAIN);
    }

    public BooleanMask randomize(float density, SymmetryType symmetryType) {
        return setWithSymmetry(symmetryType, (x, y) -> random.nextFloat() < density);
    }

    /**
     * Flips each pixel with a probability of {@code density}
     * Respects the terrain symmetry
     *
     * @param density probability that a given pixel will be flipped (Values: 0-1)
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask flipValues(float density) {
        return setWithSymmetry(SymmetryType.SPAWN, (x, y) -> getPrimitive(x, y) && random.nextFloat() < density);
    }

    public <T extends Comparable<T>, U extends ComparableMask<T, U>> BooleanMask init(ComparableMask<T, U> other,
                                                                                      T minValue, T maxValue) {
        init(other.copyAsBooleanMask(minValue, maxValue));
        return this;
    }

    /**
     * Starting from a random point simulate brownian motion by choosing a random neighbor to visit
     * each neighbor visited is set to true. A maximum of {@code numSteps} neighbors are visited.
     * The same pixel may be visited multipl times
     *
     * @param numWalkers number of times to simulate brownian motion
     * @param numSteps   number of neighbors to visit
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask randomWalk(int numWalkers, int numSteps) {
        return enqueue(() -> {
            for (int i = 0; i < numWalkers; i++) {
                int maxXBound = getMaxXBound(SymmetryType.TERRAIN);
                int minXBound = getMinXBound(SymmetryType.TERRAIN);
                int x = random.nextInt(maxXBound - minXBound) + minXBound;
                int maxYBound = getMaxYBound(x, SymmetryType.TERRAIN);
                int minYBound = getMinYBound(x, SymmetryType.TERRAIN);
                int y = random.nextInt(maxYBound - minYBound + 1) + minYBound;
                for (int j = 0; j < numSteps; j++) {
                    if (inBounds(x, y)) {
                        applyAtSymmetryPoints(x, y, SymmetryType.TERRAIN, (sx, sy) -> setPrimitive(sx, sy, true));
                    }
                    switch (random.nextInt(4)) {
                        case 0 -> x++;
                        case 1 -> x--;
                        case 2 -> y++;
                        case 3 -> y--;
                    }
                }
            }
        });
    }

    public BooleanMask guidedWalkWithBrush(Vector2 start, Vector2 target, String brushName, int size, int numberOfUses,
                                           float minValue, float maxValue, int maxStepSize, boolean wrapEdges) {
        return enqueue(() -> {
            Vector2 location = new Vector2(start);
            BooleanMask brush = loadBrush(brushName, null).setSize(size).copyAsBooleanMask(minValue, maxValue);
            float targetX = target.getX();
            float targetY = target.getY();
            if (wrapEdges) {
                int maskSize = getSize();
                int halfSize = maskSize / 2;
                float startX = start.getX();
                float startY = start.getY();
                float distanceToMidX = targetX - startX;
                float distanceToMidY = targetY - startY;
                if (StrictMath.abs(distanceToMidX) > halfSize) {
                    if (distanceToMidX > 0) {
                        targetX -= maskSize;
                    } else {
                        targetX += maskSize;
                    }
                }
                if (StrictMath.abs(distanceToMidY) > halfSize) {
                    if (distanceToMidY > 0) {
                        targetY -= maskSize;
                    } else {
                        targetY += maskSize;
                    }
                }
            }

            for (int i = 0; i < numberOfUses; i++) {
                addWithOffset(brush, location, true, wrapEdges);
                int dx = (targetX > location.getX() ? 1 : -1) * random.nextInt(maxStepSize + 1);
                int dy = (targetY > location.getY() ? 1 : -1) * random.nextInt(maxStepSize + 1);
                location.add(new Vector2(dx, dy));
            }
        });
    }

    public BooleanMask pathBezier(Vector2 start, Vector2 end, int minOrder, int maxOrder, int numMiddlePoints,
                                  float midPointMaxDistance, float midPointMinDistance) {
        int size = getSize();
        List<Vector2> checkPoints = new ArrayList<>();
        checkPoints.add(new Vector2(start));
        for (int i = 0; i < numMiddlePoints; i++) {
            Vector2 previousLoc = checkPoints.get(checkPoints.size() - 1);
            float angle = (float) ((random.nextFloat() - .5f) * 2 * StrictMath.PI / 2f) + previousLoc.angleTo(end);
            float magnitude = random.nextFloat() * start.getDistance(end) / numMiddlePoints;
            Vector2 nextLoc = previousLoc.copy().addPolar(angle, magnitude);
            checkPoints.add(nextLoc);
        }
        checkPoints.add(end.copy());
        checkPoints.forEach(point -> point.round().clampMin(0f).clampMax(size - 1));
        for (int i = 0; i < checkPoints.size() - 1; i++) {
            Vector2 location = checkPoints.get(i);
            Vector2 nextLoc = checkPoints.get(i + 1);
            BezierCurve bezierCurve = new BezierCurve(random.nextInt(maxOrder - minOrder) + minOrder,
                                                      random.nextLong());
            bezierCurve.transformTo(location, nextLoc);
            List<Vector2> points = new ArrayList<>();
            for (float j = 0; j <= 1; j += 1f / size) {
                points.add(bezierCurve.getPoint(j));
            }
            fillCoordinates(points.stream().filter(this::inBounds).collect(Collectors.toList()), true);
        }
        return this;
    }

    public BooleanMask connect(Vector2 start, Vector2 end, float maxStepSize, int numMiddlePoints,
                               float midPointMaxDistance, float midPointMinDistance, float maxAngleError,
                               SymmetryType symmetryType) {
        return enqueue(() -> {
            path(start, end, maxStepSize, numMiddlePoints, midPointMaxDistance, midPointMinDistance, maxAngleError,
                 symmetryType);
            if (symmetrySettings.getSymmetry(symmetryType).getNumSymPoints() > 1) {
                List<Vector2> symmetryPoints = getSymmetryPointsWithOutOfBounds(end, symmetryType);
                path(start, symmetryPoints.get(0), maxStepSize, numMiddlePoints, midPointMaxDistance,
                     midPointMinDistance, maxAngleError, symmetryType);
            }
        });
    }

    public BooleanMask path(Vector2 start, Vector2 end, float maxStepSize, int numMiddlePoints,
                            float midPointMaxDistance, float midPointMinDistance, float maxAngleError,
                            SymmetryType symmetryType) {
        return enqueue(() -> {
            int size = getSize();
            List<Vector2> checkPoints = new ArrayList<>();
            checkPoints.add(new Vector2(start));
            for (int i = 0; i < numMiddlePoints; i++) {
                Vector2 previousLoc = checkPoints.get(checkPoints.size() - 1);
                float angle = (float) ((random.nextFloat() - .5f) * 2 * StrictMath.PI / 2f) + previousLoc.angleTo(end);
                if (symmetrySettings.getTerrainSymmetry() == Symmetry.POINT4
                    && angle % (StrictMath.PI / 2) < StrictMath.PI / 8) {
                    angle += (random.nextBoolean() ? -1 : 1) * (random.nextFloat() * .5f + .5f) * 2f * StrictMath.PI
                             / 4f;
                }
                float magnitude = random.nextFloat() * (midPointMaxDistance - midPointMinDistance)
                                  + midPointMinDistance;
                Vector2 nextLoc = new Vector2(previousLoc).addPolar(angle, magnitude);
                checkPoints.add(nextLoc);
            }
            checkPoints.add(new Vector2(end));
            checkPoints.forEach(point -> point.round().clampMin(0f).clampMax(size - 1));
            int numSteps = 0;
            for (int i = 0; i < checkPoints.size() - 1; i++) {
                Vector2 location = checkPoints.get(i);
                Vector2 nextLoc = checkPoints.get(i + 1);
                float oldAngle = location.angleTo(nextLoc) + (random.nextFloat() - .5f) * 2f * maxAngleError;
                while (location.getDistance(nextLoc) > maxStepSize && numSteps < size * size) {
                    List<Vector2> symmetryPoints = getSymmetryPoints(location, symmetryType);
                    if (inBounds(location) && symmetryPoints.stream().allMatch(this::inBounds)) {
                        applyAtSymmetryPoints((int) location.getX(), (int) location.getY(), SymmetryType.TERRAIN,
                                              (sx, sy) -> setPrimitive(sx, sy, true));
                    }
                    float magnitude = StrictMath.max(1, random.nextFloat() * maxStepSize);
                    float angle = oldAngle * .5f
                                  + location.angleTo(nextLoc) * .5f
                                  + (random.nextFloat() - .5f) * 2f * maxAngleError;
                    location.addPolar(angle, magnitude).round();
                    oldAngle = angle;
                    numSteps++;
                }
                if (numSteps >= size * size) {
                    break;
                }
            }
        });
    }

    /**
     * Force the distance between and two true pixels to be in the range of {@code [minSpacing, maxSpacing]}
     * This is done by setting all other pixels to false. Spaced pixels are chosen randomly
     *
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask space(float minSpacing, float maxSpacing) {
        return enqueue(() -> {
            List<Vector2> coordinates = getRandomCoordinates(minSpacing, maxSpacing);
            clear();
            fillCoordinates(coordinates, true);
        });
    }

    /**
     * Flip all pixels to the opposite value
     */
    @GraphMethod
    public BooleanMask invert() {
        return enqueue(() -> {
            for (int i = 0; i < mask.length; i++) {
                mask[i] = ~mask[i];
            }
        });
    }

    /**
     * Set all pixels within the circle defined by the {@code radius} around true pixels to true
     *
     * @param radius radius around true pixels to set to true
     */
    @GraphMethod
    public BooleanMask inflate(float radius) {
        return enqueue(() -> {
            long[] maskCopy = getMaskCopy();
            apply((x, y) -> {
                if (getPrimitive(x, y) && isEdge(x, y)) {
                    markInRadius(radius, maskCopy, x, y, true);
                }
            });
            mask = maskCopy;
        });
    }

    /**
     * Set all pixels within the circle defined by the {@code radius} around false pixel to false
     *
     * @param radius radius around true pixels to set pixels to false
     */
    @GraphMethod
    public BooleanMask deflate(float radius) {
        return enqueue(() -> {
            long[] maskCopy = getMaskCopy();
            apply((x, y) -> {
                if (!getPrimitive(x, y) && isEdge(x, y)) {
                    markInRadius(radius, maskCopy, x, y, false);
                }
            });
            mask = maskCopy;
        });
    }

    /**
     * Starting from a random point simulate weighted brownian motion by choosing a random neighbor to visit
     * each neighbor visited is set to true. The first direction chosen will be more likely to be selected in future iterations
     * A maximum of {@code numSteps} neighbors are visited.
     * The same pixel may be visited multipl times
     *
     * @param numWalkers number of times to simulate brownian motion
     * @param numSteps   number of neighbors to visit
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask progressiveWalk(int numWalkers, int numSteps) {
        for (int i = 0; i < numWalkers; i++) {
            int x = random.nextInt(getMaxXBound(SymmetryType.TERRAIN) - getMinXBound(SymmetryType.TERRAIN))
                    + getMinXBound(SymmetryType.TERRAIN);
            int y = random.nextInt(getMaxYBound(x, SymmetryType.TERRAIN) - getMinYBound(x, SymmetryType.TERRAIN) + 1)
                    + getMinYBound(x, SymmetryType.TERRAIN);
            List<Integer> directions = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
            int regressiveDir = random.nextInt(directions.size());
            directions.remove(regressiveDir);
            for (int j = 0; j < numSteps; j++) {
                if (inBounds(x, y)) {
                    applyAtSymmetryPoints(x, y, SymmetryType.TERRAIN, (sx, sy) -> setPrimitive(sx, sy, true));
                }
                switch (directions.get(random.nextInt(directions.size()))) {
                    case 0 -> x++;
                    case 1 -> x--;
                    case 2 -> y++;
                    case 3 -> y--;
                }
            }
        }
        return this;
    }

    /**
     * Set all pixels on a "corner" to false
     *
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask cutCorners() {
        return enqueue(() -> {
            int size = getSize();
            long[] maskCopy = getMaskCopy();
            apply((x, y) -> {
                int count = 0;
                if (x > 0 && !getPrimitive(x - 1, y)) {
                    count++;
                }
                if (y > 0 && !getPrimitive(x, y - 1)) {
                    count++;
                }
                if (x < size - 1 && !getPrimitive(x + 1, y)) {
                    count++;
                }
                if (y < size - 1 && !getPrimitive(x, y + 1)) {
                    count++;
                }

                if (count > 1) {
                    setBit(bitIndex(x, y, size), false, maskCopy);
                }
            });
            mask = maskCopy;
        });
    }

    private long[] getMaskCopy() {
        assertNotPipelined();
        int arraySize = mask.length;
        long[] maskCopy = new long[arraySize];
        System.arraycopy(mask, 0, maskCopy, 0, arraySize);
        return maskCopy;
    }

    private void markInRadius(float radius, long[] maskCopy, int x, int y, boolean value) {
        float radius2 = (radius + 0.5f) * (radius + 0.5f);
        int searchRange = (int) StrictMath.ceil(radius);
        int minX = x - searchRange;
        int maxX = x + searchRange + 1;
        int minY = y - searchRange;
        int maxY = y + searchRange + 1;
        for (int x2 = minX; x2 < maxX; ++x2) {
            for (int y2 = minY; y2 < maxY; ++y2) {
                int bitIndex = bitIndex(x2, y2, getSize());
                if (inBounds(x2, y2)
                    && getBit(bitIndex, maskCopy) != value
                    && (x - x2) * (x - x2) + (y - y2) * (y - y2) <= radius2) {
                    setBit(bitIndex, value, maskCopy);
                }
            }
        }
    }

    /**
     * Set true pixels with non-like neighbors to false
     * with a probability of {@code strength}
     *
     * @param strength probability an edge pixel will be set to false (Values: 0-1)
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask erode(float strength) {
        return erode(strength, 1);
    }

    /**
     * Create holes of false with radius {@code size} in true sections of the maske
     *
     * @param strength probability an edge pixel will be set to false (Values: 0-1)
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask acid(float strength, float size) {
        BooleanMask holes = new BooleanMask(this, getName() + "holes");
        holes.randomize(strength, SymmetryType.SPAWN).inflate(size);
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            subtract(source);
        }, holes);
    }

    /**
     * Create holes of true with radius {@code size} in false sections of the maske
     *
     * @param strength probability an edge pixel will be set to false (Values: 0-1)
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask splat(float strength, float size) {
        BooleanMask holes = new BooleanMask(this, getName() + "splat");
        holes.randomize(strength, SymmetryType.SPAWN).inflate(size);
        return enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            add(source);
        }, holes);
    }

    /**
     * On the boundaries between true and false pixels randomly set each false pixel to true
     * with a probability of {@code strength}
     *
     * @param strength probability an edge pixel will be set to true (Values: 0-1)
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask dilute(float strength) {
        return dilute(strength, 1);
    }

    /**
     * Set only pixels with non-like neighbors to true
     *
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask outline() {
        return enqueue(() -> {
            int size = getSize();
            long[] maskCopy = new long[minimumArraySize(size)];
            apply((x, y) -> setBit(x, y, isEdge(x, y), size, maskCopy));
            mask = maskCopy;
        });
    }

    public boolean isEdge(int x, int y) {
        boolean value = getPrimitive(x, y);
        int size = getSize();
        return ((x > 0 && getPrimitive(x - 1, y) != value)
                || (y > 0 && getPrimitive(x, y - 1) != value)
                || (x
                    < size - 1
                    && getPrimitive(x
                                    + 1, y)
                       != value)
                || (y < size - 1 && getPrimitive(x, y + 1) != value));
    }

    /**
     * Set false pixels with non-like neighbors to true
     * with a probability of {@code strength} {@code count} times
     *
     * @param strength probability an edge pixel will be set to true (Values: 0-1)
     * @param count    number of times to dilute
     * @return the modified mask
     */
    @GraphMethod
    public BooleanMask dilute(float strength, int count) {
        SymmetryType symmetryType = SymmetryType.SPAWN;
        return enqueue(() -> {
            int size = getSize();
            for (int i = 0; i < count; i++) {
                long[] maskCopy = getMaskCopy();
                applyWithSymmetry(symmetryType, (x, y) -> {
                    if (!getPrimitive(x, y) && random.nextFloat() < strength && isEdge(x, y)) {
                        applyAtSymmetryPoints(x, y, symmetryType, (sx, sy) -> setBit(sx, sy, true, size, maskCopy));
                    }
                });
                mask = maskCopy;
            }
        });
    }

    /**
     * Perform erode {@code count} times
     *
     * @param strength probability an edge pixel is set to false (Values 0-1)
     * @param count    number of times to perform erosion
     */
    @GraphMethod
    public BooleanMask erode(float strength, int count) {
        SymmetryType symmetryType = SymmetryType.SPAWN;
        return enqueue(() -> {
            int size = getSize();
            for (int i = 0; i < count; i++) {
                long[] maskCopy = getMaskCopy();
                applyWithSymmetry(symmetryType, (x, y) -> {
                    if (getPrimitive(x, y) && random.nextFloat() < strength && isEdge(x, y)) {
                        applyAtSymmetryPoints(x, y, symmetryType, (sx, sy) -> setBit(sx, sy, false, size, maskCopy));
                    }
                });
                mask = maskCopy;
            }
        });
    }

    public BooleanMask addBrush(Vector2 location, String brushName, float minValue, float maxValue, int size) {
        return enqueue(() -> {
            FloatMask brush = loadBrush(brushName, null).setSize(size);
            addWithOffset(brush, minValue, maxValue, location, false);
        });
    }

    private <T extends ComparableMask<U, ?>, U extends Comparable<U>> BooleanMask addWithOffset(T other, U minValue,
                                                                                                U maxValue,
                                                                                                Vector2 offset,
                                                                                                boolean wrapEdges) {
        return addWithOffset(other.copyAsBooleanMask(minValue, maxValue), offset, true, wrapEdges);
    }

    public <T extends Comparable<T>, U extends ComparableMask<T, U>> BooleanMask initMaxima(ComparableMask<T, U> other,
                                                                                            T minValue, T maxValue) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            ComparableMask<T, U> source = (ComparableMask<T, U>) dependencies.get(0);
            setWithSymmetry(SymmetryType.SPAWN, (x, y) -> {
                T value = source.get(x, y);
                return value.compareTo(minValue) >= 0 && value.compareTo(maxValue) < 0 && source.isLocalMax(x, y);
            });
        }, other);
    }

    public <T extends Comparable<T>, U extends ComparableMask<T, U>> BooleanMask initMinima(ComparableMask<T, U> other,
                                                                                            T minValue, T maxValue) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            ComparableMask<T, U> source = (ComparableMask<T, U>) dependencies.get(0);
            setWithSymmetry(SymmetryType.SPAWN, (x, y) -> {
                T value = source.get(x, y);
                return value.compareTo(minValue) >= 0 && value.compareTo(maxValue) < 0 && source.isLocalMin(x, y);
            });
        }, other);
    }

    public <T extends Comparable<T>, U extends ComparableMask<T, U>> BooleanMask init1DMaxima(
            ComparableMask<T, U> other, T minValue, T maxValue) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            ComparableMask<T, U> source = (ComparableMask<T, U>) dependencies.get(0);
            setWithSymmetry(SymmetryType.SPAWN, (x, y) -> {
                T value = source.get(x, y);
                return value.compareTo(minValue) >= 0 && value.compareTo(maxValue) < 0 && source.isLocal1DMax(x, y);
            });
        }, other);
    }

    public <T extends Comparable<T>, U extends ComparableMask<T, U>> BooleanMask init1DMinima(
            ComparableMask<T, U> other, T minValue, T maxValue) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            ComparableMask<T, U> source = (ComparableMask<T, U>) dependencies.get(0);
            setWithSymmetry(SymmetryType.SPAWN, (x, y) -> {
                T value = source.get(x, y);
                return value.compareTo(minValue) >= 0 && value.compareTo(maxValue) < 0 && source.isLocal1DMin(x, y);
            });
        }, other);
    }

    /**
     * Set all values outside the team symmetry region to false
     */
    @GraphMethod
    public BooleanMask limitToSymmetryRegion() {
        return limitToSymmetryRegion(SymmetryType.TEAM);
    }

    public BooleanMask limitToSymmetryRegion(SymmetryType symmetryType) {
        int minXBound = getMinXBound(symmetryType);
        int maxXBound = getMaxXBound(symmetryType);
        return apply((x, y) -> {
            setPrimitive(x, y, getPrimitive(x, y) && !(x < minXBound
                                                       || x >= maxXBound
                                                       || y < getMinYBound(x, symmetryType)
                                                       || y >= getMaxYBound(x, symmetryType)));
        });
    }

    /**
     * Set all values outside the circle with radius {@code circleRadius} to false
     *
     * @param circleRadius radius of the circle
     */
    @GraphMethod
    public BooleanMask limitToCenteredCircle(float circleRadius) {
        int size = getSize();
        BooleanMask symmetryLimit = new BooleanMask(size, null, symmetrySettings, getName() + "symmetryLimit",
                                                    isParallel());
        symmetryLimit.fillCircle(size / 2f, size / 2f, circleRadius, true);
        return multiply(symmetryLimit);
    }

    public BooleanMask fillShape(Vector2 location) {
        return fillCoordinates(getShapeCoordinates(location), !getPrimitive(location));
    }

    /**
     * Set all values where the distance between pixels with non-like neighbors
     * is less than {@code minDist} to the local majority
     *
     * @param minDist threshold distance between pixels
     */
    @GraphMethod
    public BooleanMask fillGaps(int minDist) {
        BooleanMask filledGaps = copyAsDistanceField().copyAsLocalMaximums(1f, minDist / 2f);
        filledGaps.inflate(minDist / 2f);
        return add(filledGaps);
    }

    /**
     * Set all values where the distance between pixels with non-like neighbors
     * is less than {@code minDist} to the local minority
     *
     * @param minDist threshold distance between pixels
     */
    @GraphMethod
    public BooleanMask widenGaps(int minDist) {
        BooleanMask filledGaps = copyAsDistanceField().copyAsLocalMaximums(1f, minDist / 2f);
        filledGaps.inflate(minDist / 2f);
        return subtract(filledGaps);
    }

    /**
     * Flip all pixels in continguous areas with an area less than {@code minArea}
     *
     * @param maxArea maximum number of pixels in a contiguous area to qualify for flipping
     */
    @GraphMethod
    public BooleanMask removeAreasSmallerThan(int maxArea) {
        int size = getSize();
        Set<Vector2> seen = new HashSet<>(size * size, 1f);
        return applyWithSymmetry(SymmetryType.SPAWN, (x, y) -> {
            Vector2 location = new Vector2(x, y);
            if (!seen.contains(location)) {
                boolean value = getPrimitive(location);
                Set<Vector2> coordinates = getShapeCoordinates(location, maxArea);
                seen.addAll(coordinates);
                if (coordinates.size() < maxArea) {
                    fillCoordinates(coordinates, !value);
                }
            }
        });
    }

    /**
     * Convert to a new {@link FloatMask} where true pixels are set to {@code high}
     * and false pixels are set to {@code low}
     *
     * @param high value for true pixels
     * @param low  value for false pixels
     */
    @GraphMethod(returnsSelf = false)
    public FloatMask copyAsFloatMask(float low, float high) {
        return new FloatMask(this, low, high, getName() + "toFloat");
    }

    public FloatMask copyAsFloatMask(float low, float high, String name) {
        return new FloatMask(this, low, high, name);
    }

    /**
     * Convert to a new {@link IntegerMask} where true pixels are set to {@code high}
     * and false pixels are set to {@code low}
     *
     * @param high value for true pixels
     * @param low  value for false pixels
     */
    @GraphMethod(returnsSelf = false)
    public IntegerMask copyAsIntegerMask(int low, int high) {
        return copyAsIntegerMask(low, high, getName() + "toInteger");
    }

    public IntegerMask copyAsIntegerMask(int low, int high, String name) {
        return new IntegerMask(this, low, high, name);
    }

    /**
     * Flip all pixels in contiguous areas with an area greater than {@code maxArea}
     *
     * @param minArea minimum number of contiguous pixels to qualify for flipping
     */
    @GraphMethod
    public BooleanMask removeAreasBiggerThan(int minArea) {
        return subtract(copy().removeAreasSmallerThan(minArea));
    }

    /**
     * Remove all areas with contiguous areas outside the given values
     *
     * @param minArea minimum number of contiguous pixels to remove an area
     * @param maxArea maximum number of contiguous pixels to remove an area
     */
    @GraphMethod
    public BooleanMask removeAreasOutsideSizeRange(int minArea, int maxArea) {
        return removeAreasSmallerThan(minArea).removeAreasBiggerThan(maxArea);
    }

    /**
     * Remove all areas with contiguous areas between the given values
     *
     * @param minArea minimum number of contiguous pixels to remove an area
     * @param maxArea maximum number of contiguous pixels to remove an area
     */
    @GraphMethod
    public BooleanMask removeAreasInSizeRange(int minArea, int maxArea) {
        return subtract(copy().removeAreasOutsideSizeRange(minArea, maxArea));
    }

    public LinkedHashSet<Vector2> getShapeCoordinates(Vector2 location) {
        int size = getSize();
        return getShapeCoordinates(location, size * size);
    }

    public LinkedHashSet<Vector2> getShapeCoordinates(Vector2 location, int maxSize) {
        assertNotPipelined();
        LinkedHashSet<Vector2> areaHash = new LinkedHashSet<>();
        LinkedHashSet<Vector2> edgeHash = new LinkedHashSet<>();
        List<Vector2> queue = new ArrayList<>();
        LinkedHashSet<Vector2> queueHash = new LinkedHashSet<>();
        List<int[]> edges = Arrays.asList(new int[]{0, 1}, new int[]{-1, 0}, new int[]{0, -1}, new int[]{1, 0});
        boolean value = getPrimitive(location);
        queue.add(location);
        queueHash.add(location);
        while (queue.size() > 0) {
            Vector2 next = queue.remove(0);
            queueHash.remove(next);
            if (getPrimitive(next) == value && !areaHash.contains(next)) {
                areaHash.add(next);
                edges.forEach((e) -> {
                    Vector2 newLocation = new Vector2(next.getX() + e[0], next.getY() + e[1]);
                    if (!queueHash.contains(newLocation) && !areaHash.contains(newLocation) && !edgeHash.contains(
                            newLocation) && inBounds(newLocation)) {
                        queue.add(newLocation);
                        queueHash.add(newLocation);
                    }
                });
            } else if (getPrimitive(next) != value) {
                edgeHash.add(next);
            }
            if (areaHash.size() > maxSize) {
                break;
            }
        }
        return areaHash;
    }

    /**
     * Return a {@link FloatMask} which represents any pixels distance from the nearest true pixel
     */
    @GraphMethod(returnsSelf = false)
    public FloatMask copyAsDistanceField() {
        return copyAsDistanceField(getName() + "DistanceField");
    }

    public FloatMask copyAsDistanceField(String name) {
        int size = getSize();
        FloatMask distanceField = new FloatMask(this, (float) (size * size), 0f, name);
        distanceField.parabolicMinimization();
        return distanceField;
    }

    public List<Vector2> getSpacedCoordinates(float radius, int spacing) {
        List<Vector2> coordinateList = getAllCoordinates(spacing);
        return spaceCoordinates(radius, coordinateList);
    }

    private List<Vector2> spaceCoordinates(float radius, List<Vector2> coordinateList) {
        List<Vector2> chosenCoordinates = new ArrayList<>();
        while (coordinateList.size() > 0) {
            Vector2 location = coordinateList.remove(0);
            chosenCoordinates.add(location);
            coordinateList.removeIf((loc) -> location.getDistance(loc) < radius);
        }
        return chosenCoordinates;
    }

    public List<Vector2> getAllCoordinates(int spacing) {
        int size = getSize();
        List<Vector2> coordinates = new ArrayList<>(size * size);
        for (int x = 0; x < size; x += spacing) {
            for (int y = 0; y < size; y += spacing) {
                Vector2 location = new Vector2(x, y);
                coordinates.add(location);
            }
        }
        return coordinates;
    }

    public List<Vector2> getSpacedCoordinatesEqualTo(boolean value, float radius, int spacing) {
        List<Vector2> coordinateList = getAllCoordinatesEqualTo(value, spacing);
        return spaceCoordinates(radius, coordinateList);
    }

    public List<Vector2> getAllCoordinatesEqualTo(boolean value, int spacing) {
        assertNotPipelined();
        int size = getSize();
        int numPossibleCoordinates;
        int numTrue = getCount();
        if (value) {
            numPossibleCoordinates = numTrue;
        } else {
            numPossibleCoordinates = size * size - numTrue;
        }
        List<Vector2> coordinates = new ArrayList<>(numPossibleCoordinates / spacing);
        for (int x = 0; x < size; x += spacing) {
            for (int y = 0; y < size; y += spacing) {
                if (getPrimitive(x, y) == value) {
                    coordinates.add(new Vector2(x, y));
                }
            }
        }
        return coordinates;
    }

    public List<Vector2> getRandomCoordinates(float minSpacing, float maxSpacing, SymmetryType symmetryType) {
        List<Vector2> coordinateList;
        if (symmetryType != null) {
            coordinateList = copy().limitToSymmetryRegion().getAllCoordinatesEqualTo(true);
        } else {
            coordinateList = getAllCoordinatesEqualTo(true);
        }
        List<Vector2> chosenCoordinates = new ArrayList<>();
        enqueue(() -> {
            while (coordinateList.size() > 0) {
                Vector2 location = coordinateList.remove(random.nextInt(coordinateList.size()));
                float spacing = random.nextFloat() * (maxSpacing - minSpacing) + minSpacing;
                chosenCoordinates.add(location);
                coordinateList.removeIf((loc) -> location.getDistance(loc) < spacing);
                if (symmetryType != null) {
                    List<Vector2> symmetryPoints = getSymmetryPoints(location, symmetryType);
                    symmetryPoints.forEach(
                            symPoint -> coordinateList.removeIf((loc) -> symPoint.getDistance(loc) < spacing));
                }
            }
        });
        return chosenCoordinates;
    }

    public List<Vector2> getRandomCoordinates(float spacing) {
        return getRandomCoordinates(spacing, SymmetryType.TEAM);
    }

    public List<Vector2> getRandomCoordinates(float spacing, SymmetryType symmetryType) {
        return getRandomCoordinates(spacing, spacing, symmetryType);
    }

    public List<Vector2> getRandomCoordinates(float minSpacing, float maxSpacing) {
        return getRandomCoordinates(minSpacing, maxSpacing, SymmetryType.TEAM);
    }

    public List<Vector2> getAllCoordinatesEqualTo(boolean value) {
        int size = getSize();
        List<Vector2> coordinates = new ArrayList<>((int) (size * size * .25));
        apply((x, y) -> {
            if (getPrimitive(x, y) == value) {
                coordinates.add(new Vector2(x, y));
            }
        });
        return coordinates;
    }

    public Vector2 getRandomPosition() {
        assertNotPipelined();
        List<Vector2> coordinates = new ArrayList<>(getAllCoordinatesEqualTo(true, 1));
        if (coordinates.size() == 0) {
            return null;
        }
        int cell = random.nextInt(coordinates.size());
        return coordinates.get(cell);
    }

    public BooleanMask addPrimitiveWithSymmetry(SymmetryType symmetryType, ToBooleanBiIntFunction valueFunction) {
        return applyWithSymmetry(symmetryType, (x, y) -> {
            boolean value = valueFunction.apply(x, y);
            applyAtSymmetryPoints(x, y, symmetryType, (sx, sy) -> addPrimitiveAt(sx, sy, value));
        });
    }

    public BooleanMask subtractPrimitiveWithSymmetry(SymmetryType symmetryType,
                                                     ToBooleanBiIntFunction valueFunction) {
        return applyWithSymmetry(symmetryType, (x, y) -> {
            boolean value = valueFunction.apply(x, y);
            applyAtSymmetryPoints(x, y, symmetryType, (sx, sy) -> subtractPrimitiveAt(sx, sy, value));
        });
    }

    public BooleanMask multiplyPrimitiveWithSymmetry(SymmetryType symmetryType,
                                                     ToBooleanBiIntFunction valueFunction) {
        return applyWithSymmetry(symmetryType, (x, y) -> {
            boolean value = valueFunction.apply(x, y);
            applyAtSymmetryPoints(x, y, symmetryType, (sx, sy) -> multiplyPrimitiveAt(sx, sy, value));
        });
    }

    public BooleanMask dividePrimitiveWithSymmetry(SymmetryType symmetryType, ToBooleanBiIntFunction valueFunction) {
        return applyWithSymmetry(symmetryType, (x, y) -> {
            boolean value = valueFunction.apply(x, y);
            applyAtSymmetryPoints(x, y, symmetryType, (sx, sy) -> dividePrimitiveAt(sx, sy, value));
        });
    }

    protected BooleanMask applyWithOffset(BooleanMask other, BiIntBooleanConsumer action, int xOffset, int yOffset,
                                          boolean center, boolean wrapEdges) {
        return enqueue(() -> {
            int size = getSize();
            int otherSize = other.getSize();
            int smallerSize = StrictMath.min(size, otherSize);
            int biggerSize = StrictMath.max(size, otherSize);
            if (smallerSize == otherSize) {
                if (symmetrySettings.getSpawnSymmetry().isPerfectSymmetry()) {
                    Map<Integer, Integer> coordinateXMap = getShiftedCoordinateMap(xOffset, center, wrapEdges,
                                                                                   otherSize, size);
                    Map<Integer, Integer> coordinateYMap = getShiftedCoordinateMap(yOffset, center, wrapEdges,
                                                                                   otherSize, size);
                    other.apply((x, y) -> {
                        int shiftX = coordinateXMap.get(x);
                        int shiftY = coordinateYMap.get(y);
                        if (inBounds(shiftX, shiftY)) {
                            boolean value = other.getPrimitive(x, y);
                            applyAtSymmetryPoints(shiftX, shiftY, SymmetryType.SPAWN,
                                                  (sx, sy) -> action.accept(sx, sy, value));
                        }
                    });
                } else {
                    applyAtSymmetryPointsWithOutOfBounds(xOffset, yOffset, SymmetryType.SPAWN, (sx, sy) -> {
                        Map<Integer, Integer> coordinateXMap = getShiftedCoordinateMap(sx, center, wrapEdges, otherSize,
                                                                                       size);
                        Map<Integer, Integer> coordinateYMap = getShiftedCoordinateMap(sy, center, wrapEdges, otherSize,
                                                                                       size);
                        other.apply((x, y) -> {
                            int shiftX = coordinateXMap.get(x);
                            int shiftY = coordinateYMap.get(y);
                            if (inBounds(shiftX, shiftY)) {
                                action.accept(shiftX, shiftY, other.getPrimitive(x, y));
                            }
                        });
                    });
                }
            } else {
                Map<Integer, Integer> coordinateXMap = getShiftedCoordinateMap(xOffset, center, wrapEdges, size,
                                                                               otherSize);
                Map<Integer, Integer> coordinateYMap = getShiftedCoordinateMap(yOffset, center, wrapEdges, size,
                                                                               otherSize);
                apply((x, y) -> {
                    int shiftX = coordinateXMap.get(x);
                    int shiftY = coordinateYMap.get(y);
                    if (other.inBounds(shiftX, shiftY)) {
                        action.accept(x, y, other.getPrimitive(shiftX, shiftY));
                    }
                });
            }
        });
    }
}
