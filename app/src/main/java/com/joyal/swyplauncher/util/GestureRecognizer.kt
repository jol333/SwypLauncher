package com.joyal.swyplauncher.util

import com.joyal.swyplauncher.domain.model.CustomGesture
import com.joyal.swyplauncher.domain.model.InkStroke
import com.joyal.swyplauncher.domain.model.NormalizedPoint
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object GestureRecognizer {

    const val N = 32
    // Tight: well below cross-class noise floor (~0.10), well above within-class (~0.02).
    private const val MATCH_THRESHOLD = 0.06f
    // Reject if best is not clearly better than the runner-up (prevents accidental letter matches).
    private const val MARGIN_RATIO = 0.70f

    fun normalize(strokes: List<InkStroke>): List<NormalizedPoint> {
        val points = strokes.flatMap { it.points.map { p -> NormalizedPoint(p.x, p.y) } }
        if (points.size < 2) return emptyList()
        return translateToOrigin(scaleToUnitSquare(resample(points, N)))
    }

    fun normalizeStrokesForPreview(strokes: List<InkStroke>): List<List<NormalizedPoint>> {
        val all = strokes.flatMap { it.points }
        if (all.isEmpty()) return emptyList()
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        all.forEach { p ->
            if (p.x < minX) minX = p.x; if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x; if (p.y > maxY) maxY = p.y
        }
        val w = (maxX - minX).coerceAtLeast(1e-3f)
        val h = (maxY - minY).coerceAtLeast(1e-3f)
        val s = max(w, h)
        val ox = (s - w) / 2f; val oy = (s - h) / 2f
        return strokes.map { stroke ->
            stroke.points.map { p ->
                NormalizedPoint((p.x - minX + ox) / s, (p.y - minY + oy) / s)
            }
        }
    }

    fun findBestMatch(strokes: List<InkStroke>, gestures: List<CustomGesture>): CustomGesture? {
        if (gestures.isEmpty()) return null
        val candidate = normalize(strokes)
        if (candidate.size != N) return null
        var best: CustomGesture? = null
        var bestScore = Float.POSITIVE_INFINITY
        var secondScore = Float.POSITIVE_INFINITY
        gestures.forEach { g ->
            if (g.template.size == N) {
                val s = greedyCloudMatch(candidate, g.template)
                if (s < bestScore) {
                    secondScore = bestScore; bestScore = s; best = g
                } else if (s < secondScore) {
                    secondScore = s
                }
            }
        }
        if (best == null) return null
        val normalized = bestScore / N.toFloat()
        if (normalized >= MATCH_THRESHOLD) return null
        // If multiple templates compete, require best to be clearly ahead.
        if (secondScore.isFinite() && bestScore > secondScore * MARGIN_RATIO) return null
        return best
    }

    private fun resample(points: List<NormalizedPoint>, n: Int): List<NormalizedPoint> {
        if (points.size < 2) return List(n) { points.firstOrNull() ?: NormalizedPoint(0f, 0f) }
        val total = pathLength(points)
        if (total <= 0f) return List(n) { points.first() }
        val interval = total / (n - 1)
        val out = ArrayList<NormalizedPoint>(n).apply { add(points[0]) }
        val pts = ArrayList(points)
        var d = 0f
        var i = 1
        while (i < pts.size && out.size < n) {
            val d1 = distance(pts[i - 1], pts[i])
            if (d + d1 >= interval) {
                val t = ((interval - d) / d1).coerceIn(0f, 1f)
                val q = NormalizedPoint(
                    pts[i - 1].x + t * (pts[i].x - pts[i - 1].x),
                    pts[i - 1].y + t * (pts[i].y - pts[i - 1].y)
                )
                out.add(q); pts.add(i, q); d = 0f
            } else d += d1
            i++
        }
        while (out.size < n) out.add(points.last())
        return out.take(n)
    }

    private fun pathLength(points: List<NormalizedPoint>): Float {
        var len = 0f
        for (i in 1 until points.size) len += distance(points[i - 1], points[i])
        return len
    }

    private fun distance(a: NormalizedPoint, b: NormalizedPoint): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun scaleToUnitSquare(points: List<NormalizedPoint>): List<NormalizedPoint> {
        if (points.isEmpty()) return points
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        points.forEach { p ->
            if (p.x < minX) minX = p.x; if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x; if (p.y > maxY) maxY = p.y
        }
        val s = max((maxX - minX).coerceAtLeast(1e-3f), (maxY - minY).coerceAtLeast(1e-3f))
        return points.map { NormalizedPoint((it.x - minX) / s, (it.y - minY) / s) }
    }

    private fun translateToOrigin(points: List<NormalizedPoint>): List<NormalizedPoint> {
        if (points.isEmpty()) return points
        var cx = 0f; var cy = 0f
        points.forEach { cx += it.x; cy += it.y }
        cx /= points.size; cy /= points.size
        return points.map { NormalizedPoint(it.x - cx, it.y - cy) }
    }

    private fun greedyCloudMatch(a: List<NormalizedPoint>, b: List<NormalizedPoint>): Float {
        val n = a.size
        val step = max(1, floor(n.toDouble().pow(0.5)).toInt())
        var best = Float.POSITIVE_INFINITY
        var i = 0
        while (i < n) {
            val d = min(cloudDistance(a, b, i), cloudDistance(b, a, i))
            if (d < best) best = d
            i += step
        }
        return best
    }

    private fun cloudDistance(
        a: List<NormalizedPoint>, b: List<NormalizedPoint>, start: Int
    ): Float {
        val n = a.size
        val matched = BooleanArray(n)
        var sum = 0f
        var i = start
        do {
            var idx = -1; var minD = Float.POSITIVE_INFINITY
            for (j in 0 until n) if (!matched[j]) {
                val d = distance(a[i], b[j])
                if (d < minD) { minD = d; idx = j }
            }
            if (idx >= 0) matched[idx] = true
            sum += (1f - ((i - start + n) % n).toFloat() / n) * minD
            i = (i + 1) % n
        } while (i != start)
        return sum
    }

    fun serialize(gestures: List<CustomGesture>): String {
        val arr = JSONArray()
        gestures.forEach { g ->
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("template", pointsToJson(g.template))
            val preview = JSONArray()
            g.previewStrokes.forEach { preview.put(pointsToJson(it)) }
            obj.put("preview", preview)
            val ids = JSONArray()
            g.appIds.forEach { ids.put(it) }
            obj.put("apps", ids)
            val shortcutIds = JSONArray()
            g.shortcutIds.forEach { shortcutIds.put(it) }
            obj.put("shortcuts", shortcutIds)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun deserialize(json: String?): List<CustomGesture> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    val preview = obj.optJSONArray("preview")?.let { p ->
                        (0 until p.length()).map { jsonToPoints(p.getJSONArray(it)) }
                    } ?: emptyList()
                    val appsArr = obj.getJSONArray("apps")
                    // Older gestures (pre app-shortcut support) have no "shortcuts" key.
                    val shortcutsArr = obj.optJSONArray("shortcuts")
                    CustomGesture(
                        id = obj.getString("id"),
                        template = jsonToPoints(obj.getJSONArray("template")),
                        previewStrokes = preview,
                        appIds = (0 until appsArr.length()).map { appsArr.getString(it) }.toSet(),
                        shortcutIds = shortcutsArr?.let { s ->
                            (0 until s.length()).map { s.getString(it) }.toSet()
                        } ?: emptySet()
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun pointsToJson(points: List<NormalizedPoint>): JSONArray {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(JSONArray().apply { put(p.x.toDouble()); put(p.y.toDouble()) })
        }
        return arr
    }

    private fun jsonToPoints(arr: JSONArray): List<NormalizedPoint> =
        (0 until arr.length()).map {
            val pair = arr.getJSONArray(it)
            NormalizedPoint(pair.getDouble(0).toFloat(), pair.getDouble(1).toFloat())
        }
}
