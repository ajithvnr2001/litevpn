package com.example.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight custom vector icons to avoid bundling the ~10MB material-icons-extended library,
 * keeping the APK strictly under 5MB while retaining sharp Material iconography.
 */
object AppIcons {

    val Shield: ImageVector by lazy {
        ImageVector.Builder("Shield", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(4f, 5f)
                verticalLineTo(11.09f)
                curveTo(4f, 16.14f, 7.41f, 20.85f, 12f, 22f)
                curveTo(16.59f, 20.85f, 20f, 16.14f, 20f, 11.09f)
                verticalLineTo(5f)
                lineTo(12f, 2f)
                close()
                moveTo(12f, 4.18f)
                lineTo(18f, 6.43f)
                verticalLineTo(11.09f)
                curveTo(18f, 15.05f, 15.44f, 18.78f, 12f, 19.93f)
                curveTo(8.56f, 18.78f, 6f, 15.05f, 6f, 11.09f)
                verticalLineTo(6.43f)
                lineTo(12f, 4.18f)
                close()
            }
        }.build()
    }

    val ShieldCheck: ImageVector by lazy {
        ImageVector.Builder("ShieldCheck", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(4f, 5f)
                verticalLineTo(11.09f)
                curveTo(4f, 16.14f, 7.41f, 20.85f, 12f, 22f)
                curveTo(16.59f, 20.85f, 20f, 16.14f, 20f, 11.09f)
                verticalLineTo(5f)
                lineTo(12f, 2f)
                close()
                moveTo(10.5f, 16.5f)
                lineTo(6.75f, 12.75f)
                lineTo(8.16f, 11.34f)
                lineTo(10.5f, 13.67f)
                lineTo(15.84f, 8.33f)
                lineTo(17.25f, 9.75f)
                lineTo(10.5f, 16.5f)
                close()
            }
        }.build()
    }

    val Power: ImageVector by lazy {
        ImageVector.Builder("Power", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 3f)
                verticalLineTo(11f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(17.66f, 6.34f)
                arcTo(8f, 8f, 0f, true, true, 6.34f, 6.34f)
            }
        }.build()
    }

    val Globe: ImageVector by lazy {
        ImageVector.Builder("Globe", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveTo(6.48f, 22f, 12f, 22f)
                reflectiveCurveTo(22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(11f, 19.93f)
                curveTo(7.05f, 19.44f, 4f, 16.08f, 4f, 12f)
                curveTo(4f, 11.38f, 4.08f, 10.79f, 4.21f, 10.21f)
                lineTo(9f, 15f)
                verticalLineTo(16f)
                curveTo(9f, 17.1f, 9.9f, 18f, 11f, 18f)
                verticalLineTo(19.93f)
                close()
                moveTo(17.9f, 17.39f)
                curveTo(17.64f, 16.58f, 16.9f, 16f, 16f, 16f)
                horizontalLineTo(15f)
                verticalLineTo(13f)
                curveTo(15f, 12.45f, 14.55f, 12f, 14f, 12f)
                horizontalLineTo(8f)
                verticalLineTo(10f)
                horizontalLineTo(10f)
                curveTo(10.55f, 10f, 11f, 9.55f, 11f, 9f)
                verticalLineTo(7f)
                horizontalLineTo(13f)
                curveTo(14.1f, 7f, 15f, 6.1f, 15f, 5f)
                verticalLineTo(4.59f)
                curveTo(17.93f, 5.78f, 20f, 8.65f, 20f, 12f)
                curveTo(20f, 14.08f, 19.2f, 15.97f, 17.9f, 17.39f)
                close()
            }
        }.build()
    }

    val Dns: ImageVector by lazy {
        ImageVector.Builder("Dns", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 13f)
                horizontalLineTo(4f)
                curveTo(3.45f, 13f, 3f, 13.45f, 3f, 14f)
                verticalLineTo(20f)
                curveTo(3f, 20.55f, 3.45f, 21f, 4f, 21f)
                horizontalLineTo(20f)
                curveTo(20.55f, 21f, 21f, 20.55f, 21f, 20f)
                verticalLineTo(14f)
                curveTo(21f, 13.45f, 20.55f, 13f, 20f, 13f)
                close()
                moveTo(7f, 19f)
                curveTo(5.9f, 19f, 5f, 18.1f, 5f, 17f)
                reflectiveCurveTo(5.9f, 15f, 7f, 15f)
                reflectiveCurveTo(9f, 15.9f, 9f, 17f)
                reflectiveCurveTo(8.1f, 19f, 7f, 19f)
                close()
                moveTo(20f, 3f)
                horizontalLineTo(4f)
                curveTo(3.45f, 3f, 3f, 3.45f, 3f, 4f)
                verticalLineTo(10f)
                curveTo(3f, 10.55f, 3.45f, 11f, 4f, 11f)
                horizontalLineTo(20f)
                curveTo(20.55f, 11f, 21f, 10.55f, 21f, 10f)
                verticalLineTo(4f)
                curveTo(21f, 3.45f, 20.55f, 3f, 20f, 3f)
                close()
                moveTo(7f, 9f)
                curveTo(5.9f, 9f, 5f, 8.1f, 5f, 7f)
                reflectiveCurveTo(5.9f, 5f, 7f, 5f)
                reflectiveCurveTo(9f, 5.9f, 9f, 7f)
                reflectiveCurveTo(8.1f, 9f, 7f, 9f)
                close()
            }
        }.build()
    }

    val SplitApps: ImageVector by lazy {
        ImageVector.Builder("SplitApps", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 8f)
                horizontalLineTo(8f)
                verticalLineTo(4f)
                horizontalLineTo(4f)
                verticalLineTo(8f)
                close()
                moveTo(10f, 20f)
                horizontalLineTo(14f)
                verticalLineTo(16f)
                horizontalLineTo(10f)
                verticalLineTo(20f)
                close()
                moveTo(4f, 20f)
                horizontalLineTo(8f)
                verticalLineTo(16f)
                horizontalLineTo(4f)
                verticalLineTo(20f)
                close()
                moveTo(4f, 14f)
                horizontalLineTo(8f)
                verticalLineTo(10f)
                horizontalLineTo(4f)
                verticalLineTo(14f)
                close()
                moveTo(10f, 14f)
                horizontalLineTo(14f)
                verticalLineTo(10f)
                horizontalLineTo(10f)
                verticalLineTo(14f)
                close()
                moveTo(16f, 4f)
                verticalLineTo(8f)
                horizontalLineTo(20f)
                verticalLineTo(4f)
                horizontalLineTo(16f)
                close()
                moveTo(10f, 8f)
                horizontalLineTo(14f)
                verticalLineTo(4f)
                horizontalLineTo(10f)
                verticalLineTo(8f)
                close()
                moveTo(16f, 14f)
                horizontalLineTo(20f)
                verticalLineTo(10f)
                horizontalLineTo(16f)
                verticalLineTo(14f)
                close()
                moveTo(16f, 20f)
                horizontalLineTo(20f)
                verticalLineTo(16f)
                horizontalLineTo(16f)
                verticalLineTo(20f)
                close()
            }
        }.build()
    }

    val Bolt: ImageVector by lazy {
        ImageVector.Builder("Bolt", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11f, 21f)
                horizontalLineTo(10f)
                lineTo(11f, 14f)
                horizontalLineTo(7.5f)
                curveTo(6.92f, 14f, 6.67f, 13.68f, 6.96f, 13.18f)
                lineTo(13f, 3f)
                horizontalLineTo(14f)
                lineTo(13f, 10f)
                horizontalLineTo(16.51f)
                curveTo(17f, 10f, 17.28f, 10.33f, 17.07f, 10.66f)
                lineTo(11f, 21f)
                close()
            }
        }.build()
    }

    val Key: ImageVector by lazy {
        ImageVector.Builder("Key", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12.65f, 10f)
                curveTo(11.83f, 7.67f, 9.61f, 6f, 7f, 6f)
                curveTo(3.69f, 6f, 1f, 8.69f, 1f, 12f)
                reflectiveCurveTo(3.69f, 18f, 7f, 18f)
                curveTo(9.61f, 18f, 11.83f, 16.33f, 12.65f, 14f)
                horizontalLineTo(17f)
                verticalLineTo(18f)
                horizontalLineTo(21f)
                verticalLineTo(14f)
                horizontalLineTo(23f)
                verticalLineTo(10f)
                horizontalLineTo(12.65f)
                close()
                moveTo(7f, 14f)
                curveTo(5.9f, 14f, 5f, 13.1f, 5f, 12f)
                reflectiveCurveTo(5.9f, 10f, 7f, 10f)
                reflectiveCurveTo(9f, 10.9f, 9f, 12f)
                reflectiveCurveTo(8.1f, 14f, 7f, 14f)
                close()
            }
        }.build()
    }

    val Copy: ImageVector by lazy {
        ImageVector.Builder("Copy", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 1f)
                horizontalLineTo(4f)
                curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
                verticalLineTo(17f)
                horizontalLineTo(4f)
                verticalLineTo(3f)
                horizontalLineTo(16f)
                verticalLineTo(1f)
                close()
                moveTo(19f, 5f)
                horizontalLineTo(8f)
                curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
                verticalLineTo(21f)
                curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
                horizontalLineTo(19f)
                curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
                verticalLineTo(7f)
                curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
                close()
                moveTo(19f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(7f)
                horizontalLineTo(19f)
                verticalLineTo(21f)
                close()
            }
        }.build()
    }

    val StarOutline: ImageVector by lazy {
        ImageVector.Builder("StarOutline", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2.5f)
                lineTo(14.9f, 8.6f)
                lineTo(21.5f, 9.4f)
                lineTo(16.6f, 14f)
                lineTo(17.9f, 20.5f)
                lineTo(12f, 17.3f)
                lineTo(6.1f, 20.5f)
                lineTo(7.4f, 14f)
                lineTo(2.5f, 9.4f)
                lineTo(9.1f, 8.6f)
                close()
            }
        }.build()
    }

    val SplitRoute: ImageVector by lazy {
        ImageVector.Builder("SplitRoute", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 20f)
                verticalLineTo(12f)
                lineTo(6f, 6f)
                moveTo(12f, 12f)
                lineTo(18f, 6f)
                moveTo(6f, 10f)
                verticalLineTo(6f)
                horizontalLineTo(10f)
                moveTo(14f, 6f)
                horizontalLineTo(18f)
                verticalLineTo(10f)
            }
        }.build()
    }

    val Sliders: ImageVector by lazy {
        ImageVector.Builder("Sliders", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 6f)
                horizontalLineTo(20f)
                moveTo(4f, 12f)
                horizontalLineTo(20f)
                moveTo(4f, 18f)
                horizontalLineTo(20f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 4f)
                arcTo(2f, 2f, 0f, true, true, 9f, 8f)
                arcTo(2f, 2f, 0f, true, true, 9f, 4f)
                close()
                moveTo(15f, 10f)
                arcTo(2f, 2f, 0f, true, true, 15f, 14f)
                arcTo(2f, 2f, 0f, true, true, 15f, 10f)
                close()
                moveTo(10f, 16f)
                arcTo(2f, 2f, 0f, true, true, 10f, 20f)
                arcTo(2f, 2f, 0f, true, true, 10f, 16f)
                close()
            }
        }.build()
    }
}
