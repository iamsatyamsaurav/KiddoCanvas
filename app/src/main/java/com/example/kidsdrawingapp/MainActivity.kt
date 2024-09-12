package com.example.kidsdrawingapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.media.tv.TvContract.AUTHORITY
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yuku.ambilwarna.AmbilWarnaDialog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView:DrawingView?=null
    private var mImageButtonCurrentPaint: ImageButton?=null //to get the image button using index from the linear layout of color pallet acting as an array of image buttons
    private var customProgressDialog:Dialog?=null

    var mSelectedColor = Color.GRAY
    private lateinit var color_picker : ImageButton

    //openGalleryLauncher is a variable of type ActivityResultLauncher<Intent>. It's used to launch an activity (like the gallery) and handle the result (the image the user selects).
    val openGalleryLauncher:ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()  // is the contract that defines how to start an activity and get a result back. In this case, it's used to start the gallery and get the selected image.
    ){
        result-> // what happens after the user selects an image and the gallery returns a result.

        //result.resultCode == RESULT_OK: This checks if the result from the gallery was successful (the user picked an image).
        //result.data != null: This ensures that the result data is not null, meaning the user actually selected something.
        if(result.resultCode== RESULT_OK && result.data!=null){
            val imageBackground=findViewById<ImageView>(R.id.iv_background)

            //The URI in this context points to the location of the image on the device. It’s a reference that tells the ImageView where to find the image file to display.
            imageBackground.setImageURI(result.data?.data)  //result.data?.data: This extracts the URI of the selected image from the result. The ?. is a safe call operator, which means "get the URI if data is not null."
        }
    }


//In Android versions before Android 10 (API level 29), granting the READ_EXTERNAL_STORAGE permission implicitly allow writing to external storage as well, due to how the storage permissions were designed
    private val requestPermission : ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        permissions ->
        permissions.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value
            if (isGranted) {
                Toast.makeText(
                    this,
                    "Permission granted , now you can read the storage",
                    Toast.LENGTH_LONG
                ).show()

                //go to gallery to pick image
                //we can also use intent to navigate(move) to different application
                //Intent.ACTION_PICK is a specific action that allows the user to select something (in this case, an image).
                //MediaStore.Images.Media.EXTERNAL_CONTENT_URI tells the intent where to look for the images (in the device's external storage, like the gallery).
                val pickIntent=Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                openGalleryLauncher.launch(pickIntent)   //This line launches the gallery or another app that can handle image selection, allowing the user to choose an image from their device.

            } else {
                if (permissionName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                    Toast.makeText(
                        this,
                        "Oops! You just denied the permission!",
                        Toast.LENGTH_LONG
                    ).show()

                }
            }
        }
    }
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView=findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(10.toFloat())

        val linearLayoutPaintColors=findViewById<LinearLayout>(R.id.ll_paint_colors) //so that we can use linear layout as an array of image buttons
        mImageButtonCurrentPaint=linearLayoutPaintColors[0] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
        )

        val ib_brush=findViewById<ImageButton>(R.id.ib_brush)
        ib_brush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val ib_gallery=findViewById<ImageButton>(R.id.ib_gallery)
        ib_gallery.setOnClickListener {
                requestStoragePermission()
        }

        val ib_undo=findViewById<ImageButton>(R.id.ib_undo)
        ib_undo.setOnClickListener {
            //call a method to perform undo action
            drawingView?.onClickUndo()
        }

        val ib_redo=findViewById<ImageButton>(R.id.ib_redo)
        ib_redo.setOnClickListener {
            //call a method to perform redo action
            drawingView?.onClickRedo()
        }

        val ib_save=findViewById<ImageButton>(R.id.ib_save)
        ib_save.setOnClickListener {
            if(isReadStorageAllowed()){
                showProgressDialog()
                lifecycleScope.launch {
                    val fldrawingView=findViewById<FrameLayout>(R.id.fl_drawing_view_container)
                    saveBitmapFile(getBitmapFromView(fldrawingView))
                }
            }
        }

        val ib_back_bg=findViewById<ImageButton>(R.id.ib_back_bg)
        ib_back_bg.setOnClickListener {
            val imageBackground=findViewById<ImageView>(R.id.iv_background)
            imageBackground.setImageResource(R.drawable.backgroud_drawing_view_layout)



        }

        color_picker  = findViewById(R.id.color_picker)
        color_picker.setOnClickListener {
            paintClicked(it)
            paintDialog()
        }


    }


    private fun paintDialog(){
        val dialog = AmbilWarnaDialog(this,mSelectedColor,object: AmbilWarnaDialog.OnAmbilWarnaListener{
            override fun onCancel(dialog: AmbilWarnaDialog?) {

            }

            override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
                mSelectedColor = color
                drawingView?.setColor("#${Integer.toHexString(color)}")

                color_picker.setBackgroundColor(color)

            }
        }).show()
    }

    private fun showBrushSizeChooserDialog(){
        val brushDialog=Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size) //set the content of the dialog to be the dialog_brush_size.xml
        brushDialog.setTitle("Brush size :")
        val smallBtn=brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        val mediumBtn=brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        val largeBtn=brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)


        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    fun paintClicked(view: View){
        if(view!== mImageButtonCurrentPaint){
            val imageButton=view as ImageButton
            val colorTag=imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
            )
            mImageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal)
            )

            mImageButtonCurrentPaint=view

        }

    }

    private fun isReadStorageAllowed():Boolean{
        val result=ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE)  //checks if the read external storage permission is allowed
                                                        //returns 0 if allowed and -1 if not allowed
        return result==PackageManager.PERMISSION_GRANTED   //PackageManager.PERMISSION_GRANTED is 0
    }

    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_EXTERNAL_STORAGE)){
            showRationaleDialog("KiddoCanvas", "KiddoCanvas " + "needs to access your external storage")
        }
        else{
            requestPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                                             Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun showRationaleDialog(
        title:String,
        message:String
    ){
        val builder: AlertDialog.Builder= AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){
                    dialog,_->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmapFromView(view:View): Bitmap {
        val returnedBitmap= Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)  //create bitmap so that it can be returned as image
        val canvas=Canvas(returnedBitmap)  //bind returned bitmap to canvas

        val bgDrawable=view.background
        if(bgDrawable!=null){
            //draw background on the canvas
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)  //draw the view on the canvas
        //The line view.draw(canvas) takes everything that is currently visible in the view (including any strokes or drawings you've made) and draws it onto the canvas.
        // Since the canvas is linked to the Bitmap, this effectively captures the entire view, including all the strokes, as an image.

        return returnedBitmap
        //When the function returns returnedBitmap, it gives you that image with everything that was drawn on the view.

    }

    //to store returned bitmap (an image) as a file on your device
    private suspend fun saveBitmapFile(mBitmap: Bitmap?):String{
        var result=""
        withContext(Dispatchers.IO){   //run the operation on IO thread as we are outputting the image
            if(mBitmap!=null){
                try {
                    val bytes= ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90,bytes) //The Bitmap is compressed into a PNG format (with 90% quality) and written into a ByteArrayOutputStream, which stores the image data in memory.

                    // A new File object is created, with a unique name based on the current time. The image will be stored in the device's external cache directory.
                    val f= File(externalCacheDir?.absoluteFile.toString()  //file to be stored at this location with unique name obtained from "System.currentTimeMillis()/1000.png"
                    + File.separator + "KiddoCanvas_"+ System.currentTimeMillis()/1000+".png"
                    )

                    //The image data from the ByteArrayOutputStream is written to the file using a FileOutputStream.
                    val fo=FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()


                    // The absolute path of the saved file is stored in the result variable.
                    result=f.absolutePath

                    runOnUiThread{
                        cancelProgressDialog()
                        if(result.isNotEmpty()){
                            Toast.makeText(this@MainActivity,
                                "File saved successfully :$result",
                                Toast.LENGTH_LONG).show()

                            shareImage(result) //for sharing the image
                        }
                        else{
                            Toast.makeText(this@MainActivity,
                                "Something went wrong while saving the file!",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
                catch (e: Exception){
                    result=""
                    e.printStackTrace()
                }
            }
        }
        return result  // the function returns the file path where the image was saved. If something went wrong, it returns an empty string.
    }

    /**
     * Method is used to show the Custom Progress Dialog.
     */
    private fun showProgressDialog() {
        customProgressDialog = Dialog(this@MainActivity)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        customProgressDialog?.show()
    }

    /*
    * This function is used to dismiss the progress dialog if it is visible to user.
    */
    private fun cancelProgressDialog() {
        if (customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1
        private const val GALLERY = 2
        private const val AUTHORITY = "com.example.kidsdrawingapp.fileprovider"
    }

    private fun shareImage(result:String) {

////        MediaScannerConnection.scanFile makes sure that the image file is recognized by the system and available for sharing. It updates the media library with the new file.
//        MediaScannerConnection.scanFile(this, arrayOf(result), null) { path, uri ->


            //CHATPGT
        val file = File(result)  //create a File object using the result path.

        if (!file.exists()) {
            Toast.makeText(this, "File does not exist", Toast.LENGTH_LONG).show()
            return
        }


            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(this, "com.example.kidsdrawingapp.fileprovider", file)  //If your app is running on Android 7.0 (Nougat) or later, you use FileProvider to get this URI. This is required because newer versions of Android don’t let apps directly access files from other apps.
            } else {
                Uri.fromFile(file)  //If your app is running on an older version of Android, you get the URI directly from the file.
            }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/png"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)  // to make sure the app you're sharing the image with can access the file.
        }
            //CHATGPT


//            val shareIntent = Intent() //An Intent is created to handle sharing the file
//            shareIntent.action =
//                Intent.ACTION_SEND  //The ACTION_SEND action tells Android that you want to send something (in this case, an image).
//            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
//            //The uri (a link to the image file) is added to the intent so that other apps can access the image.
//            //Intent.EXTRA_STREAM is a key used in an Intent to specify that you want to include a stream of data (like a file) in the intent.
//
//            shareIntent.type =
//                "image/png" // The type of file being shared is set to "image/png" to indicate that it's an image in PNG format
//



            if (uri != null) {
                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        "Share"
                    )
                ) //Intent.createChooser opens a menu allowing the user to choose which app they want to use to share the image (like email, messaging apps, etc.).
            } else {

                Toast.makeText(this, "Oops! can't share image", Toast.LENGTH_LONG).show()
            }


        }




}




