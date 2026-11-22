package com.example.hackapp.scaffold.screens.passport.sandbox.simulatedworld

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.hackapp.scaffold.screens.passport.sandbox.shape.PhysicalShape
import com.example.hackapp.scaffold.screens.passport.sandbox.shape.toMeters
import org.jbox2d.common.Vec2
import org.jbox2d.collision.shapes.CircleShape as Box2DCircle
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.collision.shapes.Shape as Box2DShape
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World

/**
 * Creates a [PhysicalShape] backed by a Box2D [Body] whose collision geometry
 * matches the given Compose [shape].
 */
fun World.createPhysicalShape(
    shape: Shape,
    density: Density,
    bodyType: BodyType = BodyType.DYNAMIC,
    dimensionsPixels: Pair<Float, Float> = 0f to 0f,
    initialPositionPixels: Pair<Float, Float> = 0f to 0f,
    linearDamping: Float = 0.05f,
    angularDamping: Float = 0.1f,
    restitution: Float = 0.4f,
    friction: Float = 0.02f,
): PhysicalShape {
    val (widthPx, heightPx) = dimensionsPixels
    val wMeters = widthPx.toMeters()
    val hMeters = heightPx.toMeters()

    val body = createPhysicsBody(
        bodyType = bodyType,
        initialPositionPixels = initialPositionPixels,
        linearDamping = linearDamping,
        angularDamping = angularDamping,
    )

    body.attachMatchingFixtures(
        composeShape = shape,
        widthPx = widthPx,
        heightPx = heightPx,
        wMeters = wMeters,
        hMeters = hMeters,
        density = density,
        isStatic = bodyType == BodyType.STATIC,
        restitution = restitution,
        friction = friction,
    )

    return PhysicalShape(
        shape = shape,
        widthPx = widthPx,
        heightPx = heightPx,
        body = body,
    )
}

/** Creates and registers a Box2D [Body] in this [World] using pixel coordinates. */
private fun World.createPhysicsBody(
    bodyType: BodyType,
    initialPositionPixels: Pair<Float, Float>,
    linearDamping: Float,
    angularDamping: Float,
): Body {
    val bodyDef = BodyDef().apply {
        type = bodyType
        position.set(
            initialPositionPixels.first.toMeters(),
            initialPositionPixels.second.toMeters(),
        )
        this.linearDamping = linearDamping
        this.angularDamping = angularDamping
    }
    return createBody(bodyDef)
}

/**
 * Computes the 2D cross product of vectors OA and OB.
 * A positive cross product indicates a counter-clockwise turn.
 */
private fun crossProduct(o: Vec2, a: Vec2, b: Vec2): Float {
    return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
}

/**
 * Computes the convex hull of a set of 2D points using Andrew's Monotone Chain algorithm.
 */
private fun computeConvexHull(points: List<Vec2>): List<Vec2> {
    if (points.size <= 3) return points

    val sorted = points.sortedWith(Comparator { p1, p2 ->
        if (p1.x != p2.x) p1.x.compareTo(p2.x) else p1.y.compareTo(p2.y)
    })

    val lower = mutableListOf<Vec2>()
    for (p in sorted) {
        while (lower.size >= 2 && crossProduct(lower[lower.size - 2], lower.last(), p) <= 0f) {
            lower.removeAt(lower.size - 1)
        }
        lower.add(p)
    }

    val upper = mutableListOf<Vec2>()
    for (i in sorted.indices.reversed()) {
        val p = sorted[i]
        while (upper.size >= 2 && crossProduct(upper[upper.size - 2], upper.last(), p) <= 0f) {
            upper.removeAt(upper.size - 1)
        }
        upper.add(p)
    }

    lower.removeAt(lower.size - 1)
    upper.removeAt(upper.size - 1)

    return lower + upper
}

/**
 * Calculates the signed area of a polygon. Positive indicates counter-clockwise (CCW) winding.
 */
private fun polygonSignedArea(polygon: List<Vec2>): Float {
    var area = 0f
    val n = polygon.size
    for (i in 0 until n) {
        val j = (i + 1) % n
        area += polygon[i].x * polygon[j].y
        area -= polygon[j].x * polygon[i].y
    }
    return area / 2f
}

/**
 * Ensures vertices are in counter-clockwise (CCW) winding order as required by Box2D.
 */
private fun ensureCCW(polygon: List<Vec2>): List<Vec2> {
    return if (polygonSignedArea(polygon) > 0f) polygon else polygon.reversed()
}

/**
 * Calculates the area of the triangle formed by points A, B, and C.
 */
private fun triangleArea(a: Vec2, b: Vec2, c: Vec2): Float {
    return kotlin.math.abs((a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y)) / 2f)
}

/**
 * Simplifies a convex polygon hull to at most [maxVertices] vertices by iteratively removing
 * the vertex that contributes the least triangle area.
 */
private fun simplifyHull(hull: List<Vec2>, maxVertices: Int = 8): List<Vec2> {
    if (hull.size <= maxVertices) return hull

    val result = hull.toMutableList()
    while (result.size > maxVertices) {
        var minArea = Float.MAX_VALUE
        var minIndex = 0
        val n = result.size

        for (i in 0 until n) {
            val prev = result[(i - 1 + n) % n]
            val curr = result[i]
            val next = result[(i + 1) % n]
            val area = triangleArea(prev, curr, next)
            if (area < minArea) {
                minArea = area
                minIndex = i
            }
        }
        result.removeAt(minIndex)
    }
    return result
}

/**
 * Prepares a valid Box2D [PolygonShape] from sampled points, ensuring convexity, vertex bounds,
 * and CCW winding.
 */
private fun prepareBox2DPolygon(points: List<Vec2>): PolygonShape? {
    if (points.size < 3) return null

    val hull = computeConvexHull(points)
    if (hull.size < 3) return null

    val simplified = simplifyHull(hull, maxVertices = 8)
    if (simplified.size < 3) return null

    val cleaned = mutableListOf<Vec2>()
    for (p in simplified) {
        if (cleaned.isEmpty() || cleaned.last().sub(p).lengthSquared() > 0.0001f) {
            cleaned.add(p)
        }
    }
    if (cleaned.size > 1 && cleaned.first().sub(cleaned.last()).lengthSquared() <= 0.0001f) {
        cleaned.removeAt(cleaned.size - 1)
    }
    if (cleaned.size < 3) return null

    val ccwPoints = ensureCCW(cleaned)

    return try {
        PolygonShape().apply {
            set(ccwPoints.toTypedArray(), ccwPoints.size)
        }
    } catch (_: Exception) {
        null
    }
}

/** Inspects the Compose [composeShape] and attaches the corresponding Box2D fixture(s). */
private fun Body.attachMatchingFixtures(
    composeShape: Shape,
    widthPx: Float,
    heightPx: Float,
    wMeters: Float,
    hMeters: Float,
    density: Density,
    isStatic: Boolean,
    restitution: Float,
    friction: Float,
) {
    val fixtureDensity = if (isStatic) 0f else 1.0f

    if (composeShape == CircleShape && widthPx == heightPx) {
        attachFixture(
            box2DShape = Box2DCircle().apply { m_radius = wMeters / 2f },
            density = fixtureDensity,
            restitution = restitution,
            friction = friction,
        )
        return
    }

    val size = Size(widthPx, heightPx)
    val outline = composeShape.createOutline(size, LayoutDirection.Ltr, density)

    when (outline) {
        is Outline.Rectangle -> {
            attachFixture(
                box2DShape = PolygonShape().apply { setAsBox(wMeters / 2f, hMeters / 2f) },
                density = fixtureDensity,
                restitution = restitution,
                friction = friction,
            )
        }

        is Outline.Rounded -> {
            val rRect = outline.roundRect
            val maxRadius = minOf(wMeters, hMeters) / 2f
            val cornerRadiusPx = rRect.topLeftCornerRadius.x
            val cornerRadiusMeters = cornerRadiusPx.toMeters()

            if (cornerRadiusMeters >= maxRadius * 0.8f && widthPx == heightPx) {
                attachFixture(
                    box2DShape = Box2DCircle().apply { m_radius = maxRadius },
                    density = fixtureDensity,
                    restitution = restitution,
                    friction = friction,
                )
            } else {
                attachFixture(
                    box2DShape = PolygonShape().apply { setAsBox(wMeters / 2f, hMeters / 2f) },
                    density = fixtureDensity,
                    restitution = restitution,
                    friction = friction,
                )
            }
        }

        is Outline.Generic -> {
            val path = outline.path
            val pathMeasure = PathMeasure()
            pathMeasure.setPath(path, forceClosed = true)
            val length = pathMeasure.length

            if (length > 0f) {
                val numSamples = 32
                val rawPoints = ArrayList<Vec2>(numSamples)

                for (i in 0 until numSamples) {
                    val distance = (i.toFloat() / numSamples) * length
                    val pos = pathMeasure.getPosition(distance)
                    val localXMeters = (pos.x - widthPx / 2f).toMeters()
                    val localYMeters = (pos.y - heightPx / 2f).toMeters()
                    rawPoints.add(Vec2(localXMeters, localYMeters))
                }

                val radii = rawPoints.map { it.length() }
                val rMin = radii.minOrNull() ?: 0f
                val rMax = radii.maxOrNull() ?: 1f
                val rAvg = radii.average().toFloat()

                if (rMax > 0f && (rMin / rMax) > 0.88f) {
                    attachFixture(
                        box2DShape = Box2DCircle().apply { m_radius = rAvg },
                        density = fixtureDensity,
                        restitution = restitution,
                        friction = friction,
                    )
                } else {
                    val box2dPoly = prepareBox2DPolygon(rawPoints)
                    if (box2dPoly != null) {
                        attachFixture(
                            box2DShape = box2dPoly,
                            density = fixtureDensity,
                            restitution = restitution,
                            friction = friction,
                        )
                    } else {
                        attachFixture(
                            box2DShape = PolygonShape().apply {
                                setAsBox(
                                    wMeters / 2f,
                                    hMeters / 2f
                                )
                            },
                            density = fixtureDensity,
                            restitution = restitution,
                            friction = friction,
                        )
                    }
                }
            } else {
                attachFixture(
                    box2DShape = PolygonShape().apply { setAsBox(wMeters / 2f, hMeters / 2f) },
                    density = fixtureDensity,
                    restitution = restitution,
                    friction = friction,
                )
            }
        }
    }
}

/** Attaches a single [Box2DShape] to this [Body] with physical material properties. */
private fun Body.attachFixture(
    box2DShape: Box2DShape,
    density: Float,
    restitution: Float,
    friction: Float,
) {
    val fixtureDef = FixtureDef().apply {
        shape = box2DShape
        this.density = density
        this.restitution = restitution
        this.friction = friction
    }
    createFixture(fixtureDef)
}

fun World.createBoundaryWalls(
    screenWidthPx: Float,
    screenHeightPx: Float,
    density: Density,
) {
    val wallThicknessPx = 200f
    val halfThickness = wallThicknessPx / 2f
    val extra = 200f

    // Top Wall
    createPhysicalShape(
        shape = RectangleShape,
        density = density,
        bodyType = BodyType.STATIC,
        dimensionsPixels = (screenWidthPx + extra * 2) to wallThicknessPx,
        initialPositionPixels = (screenWidthPx / 2f) to -halfThickness,
        friction = 0.02f,
        restitution = 0.4f,
    )

    // Bottom Wall
    createPhysicalShape(
        shape = RectangleShape,
        density = density,
        bodyType = BodyType.STATIC,
        dimensionsPixels = (screenWidthPx + extra * 2) to wallThicknessPx,
        initialPositionPixels = (screenWidthPx / 2f) to (screenHeightPx + halfThickness),
        friction = 0.02f,
        restitution = 0.4f,
    )

    // Left Wall
    createPhysicalShape(
        shape = RectangleShape,
        density = density,
        bodyType = BodyType.STATIC,
        dimensionsPixels = wallThicknessPx to (screenHeightPx + extra * 2),
        initialPositionPixels = -halfThickness to (screenHeightPx / 2f),
        friction = 0.02f,
        restitution = 0.4f,
    )

    // Right Wall
    createPhysicalShape(
        shape = RectangleShape,
        density = density,
        bodyType = BodyType.STATIC,
        dimensionsPixels = wallThicknessPx to (screenHeightPx + extra * 2),
        initialPositionPixels = (screenWidthPx + halfThickness) to (screenHeightPx / 2f),
        friction = 0.02f,
        restitution = 0.4f,
    )
}
