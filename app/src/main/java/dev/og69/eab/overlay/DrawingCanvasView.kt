package dev.og69.eab.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import org.json.JSONObject

class DrawingCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    sealed class OverlayItem {
        data class Stroke(val path: Path, val color: Int) : OverlayItem()
        data class Emoji(val text: String, val x: Float, val y: Float) : OverlayItem()
        data class Text(val text: String, val x: Float, val y: Float, val color: Int) : OverlayItem()
    }

    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        textSize = 64f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    private val emojiPaint = Paint().apply {
        textSize = 96f
        isAntiAlias = true
    }

    private val items = mutableListOf<OverlayItem>()
    private var currentStroke: OverlayItem.Stroke? = null

    fun handleCommand(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val type = json.optString("type")
            
            when (type) {
                "draw" -> {
                    val action = json.optString("action")
                    val x = json.optDouble("x").toFloat() * width
                    val y = json.optDouble("y").toFloat() * height
                    val color = json.optInt("color", Color.RED)
                    
                    when (action) {
                        "down" -> {
                            val path = Path().apply { moveTo(x, y) }
                            val stroke = OverlayItem.Stroke(path, color)
                            currentStroke = stroke
                            items.add(stroke)
                        }
                        "move" -> {
                            currentStroke?.path?.lineTo(x, y)
                        }
                        "up" -> {
                            currentStroke = null
                        }
                    }
                }
                "emoji" -> {
                    val emoji = json.optString("emoji", "❤️")
                    val x = json.optDouble("x").toFloat() * width
                    val y = json.optDouble("y").toFloat() * height
                    items.add(OverlayItem.Emoji(emoji, x, y))
                }
                "text" -> {
                    val text = json.optString("text", "")
                    val x = json.optDouble("x").toFloat() * width
                    val y = json.optDouble("y").toFloat() * height
                    val color = json.optInt("color", Color.WHITE)
                    items.add(OverlayItem.Text(text, x, y, color))
                }
                "clear" -> {
                    items.clear()
                    currentStroke = null
                }
            }
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (item in items) {
            when (item) {
                is OverlayItem.Stroke -> {
                    strokePaint.color = item.color
                    canvas.drawPath(item.path, strokePaint)
                }
                is OverlayItem.Emoji -> {
                    canvas.drawText(item.text, item.x, item.y, emojiPaint)
                }
                is OverlayItem.Text -> {
                    textPaint.color = item.color
                    canvas.drawText(item.text, item.x, item.y, textPaint)
                }
            }
        }
    }
    
    fun clear() {
        items.clear()
        currentStroke = null
        invalidate()
    }
}
