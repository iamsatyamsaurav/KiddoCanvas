package com.example.kidsdrawingapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

//we have to set up DrawingView of type View so that we can use it in out main_activity to draw
class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var mDrawPath: CustomPath?=null


    // a bitmap is like a digital canvas made up of tiny squares called pixels.
    //when you create a bitmap in programming (like in Android), you're creating this canvas where you can draw or paint your images.
    // Each pixel in the bitmap can store information about its color and transparency, allowing you to create detailed images or graphics.

    private var mCanvasBitmap : Bitmap?=null
    private var mDrawPaint: Paint?=null
    private var mCanvasPaint : Paint?=null
    private var mBrushSize: Float=0.toFloat()
    private var color= Color.BLACK
    private var canvas: Canvas?=null

    private val mPaths=ArrayList<CustomPath>() //to store all the mDrawPath so that we can use it to make the lines persistent
    private val mUndoPaths=ArrayList<CustomPath>()  //to store the undo paths

    init {
        setUpDrawing()
    }
    private fun setUpDrawing(){
        mDrawPath=CustomPath(color,mBrushSize)
        mDrawPaint= Paint()
        mDrawPaint!!.color=color
        mDrawPaint!!.style= Paint.Style.STROKE  //This means that the paint will draw only the outline (stroke) of the shapes, not the inside (fill).
        mDrawPaint!!.strokeJoin= Paint.Join.ROUND  //means that the joins between lines will be rounded.
        mDrawPaint!!.strokeCap= Paint.Cap.ROUND //means that the ends of the lines will be rounded rather than flat or squared off.
        mCanvasPaint= Paint(Paint.DITHER_FLAG)  //DITHER_FLAG is used to enable dithering, which is a technique to smooth out color transitions on the canvas.
//        mBrushSize=20.toFloat()
    }

    //onSizeChanged is the name of the function. It's called whenever the view's size changes
    //This might happen when the view is first created, when the device's orientation changes, or if the view is resized for any reason.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        //w and h are the new width and height of the view.
        super.onSizeChanged(w, h, oldw, oldh)

        //Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) creates a blank bitmap with the new width w and height h.
        //Bitmap.Config.ARGB_8888 means each pixel in the bitmap has four channels: Alpha (transparency), Red, Green, and Blue, with 8 bits per channel. This gives a high-quality image with full transparency support.
        mCanvasBitmap= Bitmap.createBitmap(w,h, Bitmap.Config.ARGB_8888)

        //Canvas is the drawing surface, and by linking it to mCanvasBitmap, anything drawn on the canvas will be drawn onto mCanvasBitmap.
        canvas= Canvas(mCanvasBitmap!!)
    }

    //onDraw handles the rendering of the bitmap and the current drawing path onto the canvas.
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        //Draws the bitmap (mCanvasBitmap) onto the canvas starting at the top-left corner (0f, 0f).
        //0f, 0f: These are the x and y coordinates where the bitmap should be placed on the canvas.
        //mCanvasPaint: Paint object that could apply effects (like dithering) while drawing the bitmap.
        canvas.drawBitmap(mCanvasBitmap!!,0f,0f,mCanvasPaint)


        //for persistent nature
        for(path in mPaths){
            mDrawPaint!!.strokeWidth=path.brushThickness
            mDrawPaint!!.color=path.color
            canvas.drawPath(path,mDrawPaint!!)
        }


        //Checks if the current path (mDrawPath) is not empty (meaning there is something to draw
        if(!mDrawPath!!.isEmpty){
            mDrawPaint!!.strokeWidth=mDrawPath!!.brushThickness
            mDrawPaint!!.color=mDrawPath!!.color
            canvas.drawPath(mDrawPath!!,mDrawPaint!!)  // Draws the current path (mDrawPath) onto the canvas using the specified paint (mDrawPaint).
        }
    }

//    mDrawPath: This is an instance of CustomPath, which extends Path. It represents the path the user is currently drawing, including
//    the points they have touched on the screen. It holds information about the brush's color and thickness.
//
//    mDrawPaint: This is an instance of the Paint class, which defines how the path (mDrawPath) should be drawn on the canvas.
//    It holds styling information like color, stroke width, stroke join, etc.
    override fun onTouchEvent(event: MotionEvent?): Boolean {
//      This method is called whenever a touch event (like pressing, moving, or lifting a finger) occurs on the view.
        val touchX=event?.x
        val touchY=event?.y

        when(event?.action){
            MotionEvent.ACTION_DOWN->{  //pressing
                mDrawPath!!.color=color
                mDrawPath!!.brushThickness=mBrushSize

                mDrawPath!!.reset() //Clears the current path, preparing it for a new stroke.
                if (touchX != null) {
                    if (touchY != null) {
                        //Moves the starting point of the path to the touch position (x, y) without drawing anything yet.
                        mDrawPath!!.moveTo(touchX,touchY)
                    }
                }
            }
            MotionEvent.ACTION_MOVE->{  //moving
                if (touchX != null) {
                    if (touchY != null) {
                        //Draws a line from the last position to the new touch position (x, y).
                        mDrawPath!!.lineTo(touchX,touchY)
                    }
                }
            }
            MotionEvent.ACTION_UP->{  //lifting
                mPaths.add(mDrawPath!!) //storing the mDrawPath for showing persistent lines

                //Creates a new CustomPath instance for the next stroke, using the current color and brush size.
                mDrawPath=CustomPath(color,mBrushSize)
            }
            else->{
                return false
            }
        }
    //invalidate() is like telling the app, "Hey, something has changed, please redraw the screen!"

    //In your drawing app, every time the user touches or moves their finger on the screen, invalidate() is called
    // to make sure the drawing gets updated right away, so they can see the new lines or shapes as they draw.

    // This tells the system that the view needs to be redrawn, triggering a call to onDraw.
        invalidate()
        return true
    }


//    TypedValue.applyDimension: This method is used to convert a value in a specific unit (like dp) into pixels, taking into account the screen's density.
//    TypedValue.COMPLEX_UNIT_DIP: This specifies that the unit of the newSize parameter is in dp (density-independent pixels).
//    newSize: The size of the brush in dp that you want to convert to pixels.
//    resources.displayMetrics: This provides information about the screen's display, like its density. It's used to correctly scale the dp value to pixels based on the current screen's resolution

    //The setSizeForBrush function sets the size of the brush that the user will draw with, based on a value provided in density-independent pixels (dp).
    // It converts this value to the appropriate size in pixels for the current screen and then applies it to the paint object used for drawing.

    fun setSizeForBrush(newSize:Float){
        //we have to take the size of the screen(dimension) into consideration
        mBrushSize= TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    newSize,resources.displayMetrics)
        mDrawPaint!!.strokeWidth=mBrushSize
    }
     fun setColor(newColor:String){
         color=Color.parseColor(newColor) //to parse the string to color
         mDrawPaint!!.color=color
     }

    fun onClickUndo(){
        if(mPaths.size>0){
            mUndoPaths.add(mPaths.removeAt(mPaths.size-1))
            invalidate() //it will internally call the onDraw() method
        }
    }
    fun onClickRedo(){
        if(mUndoPaths.size>0){
            mPaths.add(mUndoPaths.removeAt(mUndoPaths.size-1))
            invalidate() //it will internally call the onDraw() method
        }
    }


    //internal to be used within DrawingView only
    //inner to access even the private members of upper class
    internal inner class CustomPath(var color:Int,
                                    var brushThickness:Float): Path(){

    }
}