package com.gympulse.app.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

private typealias Pt = Pair<Float, Float>

/** Equipment drawn next to the figure. Coordinates are in a 100 x 100 space. */
private sealed interface Prop {
    class Bar(val a: Pt, val b: Pt) : Prop
    class Plate(val c: Pt, val r: Float) : Prop
    class Dumbbell(val c: Pt, val vertical: Boolean = false) : Prop
    class Line(val a: Pt, val b: Pt) : Prop
    class Pulley(val c: Pt) : Prop
    class Bench(val a: Pt, val b: Pt, val legs: Boolean = true) : Prop
    class Wall(val x: Float) : Prop
}

/** Stick figure of one movement: [tint] is `push`, `pull`, `legs` or a preset key, [limbs] are polylines. */
private class Art(val tint: String, val head: Pt, val limbs: List<List<Pt>>, val props: List<Prop>)

private fun l(vararg v: Int): List<Pt> = v.toList().chunked(2).map { it[0].toFloat() to it[1].toFloat() }

private fun pt(x: Int, y: Int): Pt = x.toFloat() to y.toFloat()

private fun art(tint: String, head: Pt, limbs: List<List<Pt>>, vararg props: Prop) = Art(tint, head, limbs, props.toList())

private fun bar(x1: Int, y1: Int, x2: Int, y2: Int) = Prop.Bar(pt(x1, y1), pt(x2, y2))
private fun plate(x: Int, y: Int, r: Int = 9) = Prop.Plate(pt(x, y), r.toFloat())
private fun db(x: Int, y: Int, vertical: Boolean = false) = Prop.Dumbbell(pt(x, y), vertical)
private fun cable(x1: Int, y1: Int, x2: Int, y2: Int) = Prop.Line(pt(x1, y1), pt(x2, y2))
private fun pulley(x: Int, y: Int) = Prop.Pulley(pt(x, y))
private fun bench(x1: Int, y1: Int, x2: Int, y2: Int, legs: Boolean = true) = Prop.Bench(pt(x1, y1), pt(x2, y2), legs)

/** Front view standing: head, torso and two legs, arms supplied by the caller. */
private fun standingFront(tint: String, arms: List<List<Pt>>, vararg props: Prop) = art(
    tint, pt(50, 19),
    listOf(l(50, 27, 50, 56), l(50, 56, 42, 88), l(50, 56, 58, 88)) + arms,
    *props,
)

/** Side view standing facing right, with one working arm. */
private fun standingSide(tint: String, arm: List<Pt>, vararg props: Prop) = art(
    tint, pt(50, 17),
    listOf(l(50, 26, 50, 54), l(50, 54, 50, 72, 52, 88), arm),
    *props,
)

/** Lying on a flat bench, facing the ceiling. */
private fun lying(tint: String, arm: List<Pt>, vararg props: Prop) = art(
    tint, pt(20, 57),
    listOf(l(28, 62, 62, 62), l(62, 62, 80, 58, 82, 86), arm),
    bench(14, 68, 72, 68),
    *props,
)

/** Colour code by training split: push (orange), pull (blue), legs (green). */
private val TrainingSplitColors: Map<String, List<Color>> = mapOf(
    "push" to listOf(Color(0xFF7A2A00), Color(0xFFFF8A3D)),
    "pull" to listOf(Color(0xFF003B57), Color(0xFF2BB7E8)),
    "legs" to listOf(Color(0xFF00473A), Color(0xFF3DDC97)),
)

private object Arts {
    val all: Map<String, Art> = mapOf(
        "bench_db" to lying("push", l(32, 62, 32, 44, 32, 30), db(32, 30)),
        "bench_bb" to lying("push", l(32, 62, 32, 44, 32, 30), bar(14, 30, 50, 30)),
        "incline_db" to art(
            "push", pt(30, 32),
            listOf(l(36, 42, 58, 66), l(58, 66, 74, 62, 76, 86), l(36, 42, 40, 30, 42, 18)),
            bench(28, 38, 54, 66, legs = false), bench(54, 72, 72, 72), db(42, 18),
        ),
        "cable_fly" to standingFront(
            "push",
            listOf(l(46, 29, 30, 34, 38, 54), l(54, 29, 70, 34, 62, 54)),
            pulley(10, 8), pulley(90, 8), cable(10, 8, 38, 54), cable(90, 8, 62, 54),
        ),
        "pushup" to art(
            "push", pt(82, 44),
            listOf(l(72, 50, 14, 74), l(72, 50, 72, 82)),
        ),
        "ohp_bb" to standingFront(
            "push",
            listOf(l(46, 30, 34, 30, 34, 10), l(54, 30, 66, 30, 66, 10)),
            bar(14, 10, 86, 10),
        ),
        "ohp_db" to standingFront(
            "push",
            listOf(l(46, 30, 34, 30, 34, 12), l(54, 30, 66, 30, 66, 12)),
            db(34, 10), db(66, 10),
        ),
        "lateral_raise" to standingFront(
            "push",
            listOf(l(46, 29, 28, 31, 18, 34), l(54, 29, 72, 31, 82, 34)),
            db(16, 34, vertical = true), db(84, 34, vertical = true),
        ),
        "face_pull" to art(
            "pull", pt(50, 17),
            listOf(l(50, 26, 50, 54), l(50, 54, 50, 72, 52, 88), l(50, 30, 40, 34, 30, 22)),
            pulley(10, 18), cable(10, 18, 30, 22),
        ),
        "dips" to art(
            "push", pt(52, 22),
            listOf(l(50, 34, 50, 62), l(50, 62, 52, 78, 36, 80), l(50, 34, 60, 46, 62, 58)),
            bar(52, 58, 80, 58),
        ),
        "triceps_pushdown" to standingSide(
            "push", l(50, 30, 54, 46, 62, 52),
            pulley(62, 6), cable(62, 6, 62, 52), bar(56, 52, 70, 52),
        ),
        "skull_crusher" to lying("push", l(32, 62, 34, 40, 22, 44), db(22, 44)),
        "pullup" to art(
            "pull", pt(50, 22),
            listOf(l(50, 32, 50, 58), l(50, 58, 47, 90), l(50, 58, 53, 90), l(44, 32, 34, 26, 30, 10), l(56, 32, 66, 26, 70, 10)),
            bar(16, 10, 84, 10),
        ),
        "lat_pulldown" to art(
            "pull", pt(50, 28),
            listOf(l(50, 40, 50, 68), l(50, 68, 64, 70, 64, 88), l(44, 42, 34, 40, 28, 16), l(56, 42, 66, 40, 72, 16)),
            bar(18, 16, 82, 16), cable(50, 2, 50, 16), pulley(50, 3), bench(36, 74, 64, 74),
        ),
        "cable_row" to art(
            "pull", pt(58, 22),
            listOf(l(58, 34, 58, 64), l(58, 64, 40, 62, 28, 66), l(58, 36, 66, 50, 40, 50)),
            cable(8, 50, 40, 50), pulley(8, 50), bench(52, 72, 70, 72),
        ),
        "row_bb" to art(
            "pull", pt(24, 30),
            listOf(l(32, 38, 64, 52), l(64, 52, 62, 70, 60, 88), l(32, 38, 44, 52, 36, 62)),
            plate(36, 64, 9),
        ),
        "row_db" to art(
            "pull", pt(22, 36),
            listOf(l(30, 40, 66, 44), l(66, 44, 62, 62), l(66, 44, 78, 66, 80, 88), l(34, 40, 34, 64), l(30, 40, 42, 50, 38, 58)),
            bench(20, 68, 70, 68), db(38, 60),
        ),
        "deadlift" to art(
            "pull", pt(28, 26),
            listOf(l(34, 34, 60, 50), l(60, 50, 56, 70, 52, 88), l(34, 36, 42, 76)),
            plate(44, 78, 11),
        ),
        "curl_db" to standingSide("pull", l(50, 30, 52, 48, 62, 32), db(64, 30)),
        "curl_ez" to standingSide("pull", l(50, 30, 52, 48, 62, 32), plate(64, 32, 7)),
        "hammer_curl" to standingSide("pull", l(50, 30, 52, 48, 62, 32), db(63, 30, vertical = true)),
        "squat_bb" to art(
            "legs", pt(54, 22),
            listOf(l(48, 34, 38, 62), l(38, 62, 64, 64, 54, 88), l(48, 34, 58, 40)),
            plate(46, 30, 9),
        ),
        "goblet_squat" to art(
            "legs", pt(52, 24),
            listOf(l(48, 36, 38, 62), l(38, 62, 64, 64, 54, 88), l(48, 38, 54, 50, 60, 44)),
            db(62, 42, vertical = true),
        ),
        "bulgarian_split" to art(
            "legs", pt(50, 18),
            listOf(l(50, 28, 50, 54), l(50, 54, 66, 66, 68, 88), l(50, 54, 36, 72, 22, 66), l(50, 32, 50, 52)),
            bench(4, 70, 26, 70), db(50, 56),
        ),
        "rdl_bb" to art(
            "legs", pt(28, 28),
            listOf(l(34, 36, 62, 50), l(62, 50, 60, 70, 58, 88), l(34, 38, 44, 62)),
            plate(44, 66, 10),
        ),
        "leg_press" to art(
            "legs", pt(22, 42),
            listOf(l(26, 48, 40, 70), l(40, 70, 60, 56, 74, 40)),
            bench(20, 76, 50, 76), bench(72, 26, 84, 54),
        ),
        "lunges_db" to art(
            "legs", pt(50, 18),
            listOf(l(50, 28, 50, 54), l(50, 54, 64, 66, 66, 88), l(50, 54, 40, 76, 26, 86), l(50, 32, 50, 52)),
            db(50, 56),
        ),
        "leg_curl" to art(
            "legs", pt(14, 56),
            listOf(l(22, 60, 50, 60), l(50, 60, 74, 60, 68, 36)),
            bench(10, 66, 80, 66),
        ),
        "hip_thrust" to art(
            "legs", pt(22, 44),
            listOf(l(30, 50, 56, 48), l(56, 48, 70, 48, 66, 86)),
            bench(8, 58, 34, 58), plate(56, 42, 8),
        ),
        "calf_raise" to art(
            "legs", pt(50, 16),
            listOf(l(50, 26, 50, 52), l(50, 52, 50, 76, 56, 84)),
            bar(42, 26, 60, 26), bench(44, 88, 66, 88),
        ),
        "hanging_leg_raise" to art(
            "core", pt(44, 20),
            listOf(l(56, 6, 50, 32), l(48, 32, 48, 58), l(48, 58, 66, 58, 84, 58)),
            bar(40, 6, 74, 6),
        ),
        "plank" to art(
            "core", pt(80, 48),
            listOf(l(68, 56, 12, 76), l(68, 56, 68, 78, 80, 78)),
        ),
        "side_plank" to art(
            "core", pt(74, 38),
            listOf(l(66, 50, 14, 84), l(66, 50, 66, 78, 80, 78), l(66, 52, 70, 30)),
        ),
        "wall_sit" to art(
            "legs", pt(32, 18),
            listOf(l(32, 28, 32, 62), l(32, 62, 62, 62, 62, 88), l(32, 34, 48, 52)),
            Prop.Wall(24f),
        ),
        "hamstring_stretch" to art(
            "stretch", pt(62, 54),
            listOf(l(26, 82, 82, 82), l(26, 82, 48, 58), l(48, 58, 66, 72, 78, 80)),
        ),
        "curl_cable" to standingSide("pull", l(50, 30, 50, 48, 62, 34), pulley(84, 86), cable(84, 86, 62, 34)),
        "hammer_curl_cable" to standingSide(
            "pull", l(50, 30, 50, 48, 62, 34),
            pulley(84, 86), cable(84, 86, 62, 34), cable(60, 28, 60, 40), cable(64, 28, 64, 40),
        ),
        "lateral_raise_cable" to standingFront(
            "push",
            listOf(l(54, 29, 70, 31, 80, 36), l(46, 29, 44, 44)),
            pulley(10, 86), cable(10, 86, 80, 36), db(80, 38, vertical = true),
        ),
    )
}

object ExerciseArt {
    /** Colour family (a key of [ExerciseImagePresets]) of the drawing referenced by [uri], if any. */
    fun tintOf(uri: String?): String? = keyOf(uri)?.let { Arts.all[it]?.tint }

    fun has(uri: String?): Boolean = keyOf(uri)?.let { it in Arts.all } == true

    private fun keyOf(uri: String?) = uri?.takeIf { it.startsWith(PRESET_SCHEME) }?.removePrefix(PRESET_SCHEME)
}

@Composable
fun ExerciseArtwork(uri: String, modifier: Modifier = Modifier) {
    val art = Arts.all[uri.removePrefix(PRESET_SCHEME)] ?: return
    val colors = TrainingSplitColors[art.tint]
        ?: (ExerciseImagePresets.all.firstOrNull { it.key == art.tint } ?: ExerciseImagePresets.fallback).colors
    Box(modifier.background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawArt(art) }
    }
}

private fun DrawScope.drawArt(art: Art) {
    val unit = size.minDimension / 100f * 0.72f
    val origin = Offset(size.width * 0.14f + (size.width - size.minDimension) / 2f, size.height * 0.14f + (size.height - size.minDimension) / 2f)
    fun o(p: Pt) = Offset(origin.x + p.first * unit, origin.y + p.second * unit)
    val ink = Color.White.copy(alpha = 0.94f)
    val gear = Color.White.copy(alpha = 0.62f)
    fun line(a: Pt, b: Pt, color: Color, width: Float) = drawLine(color, o(a), o(b), width * unit, StrokeCap.Round)

    line(pt(8, 90), pt(92, 90), Color.White.copy(alpha = 0.25f), 1.5f)
    art.props.forEach { prop ->
        when (prop) {
            is Prop.Bar -> {
                line(prop.a, prop.b, gear, 2.6f)
                listOf(prop.a, prop.b).forEach { end -> line(end.first to end.second - 7f, end.first to end.second + 7f, gear, 4f) }
            }
            is Prop.Plate -> drawCircle(gear, prop.r * unit, o(prop.c), style = Stroke(3.4f * unit))
            is Prop.Dumbbell -> {
                val (cx, cy) = prop.c
                val half = 7f
                val a = if (prop.vertical) cx to cy - half else cx - half to cy
                val b = if (prop.vertical) cx to cy + half else cx + half to cy
                line(a, b, gear, 2.6f)
                listOf(a, b).forEach { end ->
                    if (prop.vertical) line(end.first - 5f to end.second, end.first + 5f to end.second, gear, 4.2f)
                    else line(end.first to end.second - 5f, end.first to end.second + 5f, gear, 4.2f)
                }
            }
            is Prop.Line -> line(prop.a, prop.b, gear.copy(alpha = 0.5f), 1.4f)
            is Prop.Pulley -> drawCircle(gear, 3.4f * unit, o(prop.c), style = Stroke(1.8f * unit))
            is Prop.Bench -> {
                line(prop.a, prop.b, gear, 4.6f)
                if (prop.legs) listOf(prop.a, prop.b).forEach { end -> if (end.second < 86f) line(end, end.first to 90f, gear.copy(alpha = 0.5f), 2.4f) }
            }
            is Prop.Wall -> line(prop.x to 6f, prop.x to 90f, gear, 4.6f)
        }
    }
    val stroke = Stroke(width = 5.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
    art.limbs.forEach { points ->
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(o(points.first()).x, o(points.first()).y)
            points.drop(1).forEach { lineTo(o(it).x, o(it).y) }
        }
        drawPath(path, ink, style = stroke)
    }
    drawCircle(ink, 6.6f * unit, o(art.head))
}
