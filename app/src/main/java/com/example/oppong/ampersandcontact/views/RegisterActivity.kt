package com.example.oppong.ampersandcontact.views

import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProviders
import com.example.oppong.ampersandcontact.HomeQRActivity
import com.example.oppong.ampersandcontact.R
import com.example.oppong.ampersandcontact.viewmodels.AuthViewModel
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_register.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class RegisterActivity : AppCompatActivity() {


    private val viewModel: AuthViewModel by lazy {
        ViewModelProviders.of(this).get(AuthViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
    }

    val CAMERA = 1
    val GALLERY = 0

    companion object {
        val IMAGE_DIRECTORY = "/ampersand"
    }


    fun onRegisterButtonClick(view: View) {
        if (areInputsValid()) {
            val fullNameSplit = fullNameEditText.text!!.split(" ")
            val firstName = fullNameSplit[0]
            val lastName = fullNameSplit[1]

            val (message, userObject) = viewModel.registerUser(
                mFirstName = firstName,
                mLastName = lastName,
                mEmail = emailEditText.text.toString(),
                mPassword = passwordEditText.text.toString(),
                mPhone = phoneEditText.text.toString(),
                mRole = roleEditText.text.toString(),
                mTwitter = twitterEditText.text.toString(),
                mLinkedIn = linkedInEditText.text.toString(),
                mPhoto = encodeImageFileToBase64(perfectBitmap)
            )

            when (message) {
                "registration successful" -> {
                    val intent = Intent(this, HomeQRActivity::class.java)
                    intent.putExtra("user", userObject)
                    startActivity(intent)
                }
                "network error" -> Toast.makeText(
                    this,
                    "Network error. Check your internet connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
                "email exists" -> Toast.makeText(this, "Email already exists. Go to login page.", Toast.LENGTH_LONG).show()
            }

        } else Toast.makeText(this, "Invalid inputs", Toast.LENGTH_LONG).show()
    }

    private fun areInputsValid(): Boolean {
        var valid = true
        if (emailEditText.text.isNullOrBlank() || !Patterns.EMAIL_ADDRESS.matcher(emailEditText.text).matches()) {
            valid = false
            emailEditText.error = "Enter valid email address"
        }

        if (fullNameEditText.text.isNullOrBlank()) {
            valid = false
            fullNameEditText.error = "Full Name cannot be blank"
        }

        if (passwordEditText.text.isNullOrBlank() || passwordEditText.text!!.length < 8) {
            valid = false
            passwordEditText.error = "Password must be 8 characters or more"
        }

        if (phoneEditText.text.isNullOrBlank() || !Patterns.PHONE.matcher(phoneEditText.text).matches()) {
            valid = false
            phoneEditText.error = "Enter a valid phone number"
        }

        if (!twitterEditText.text.isNullOrBlank() && !twitterEditText.text!!.startsWith("@")) {
            valid = false
            twitterEditText.error = "Twitter handle must begin with @"
        }

        return valid
    }

    fun showChooserDialog(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            requestMultiplePerms()
        }
        val chooserDialog = AlertDialog.Builder(this)
        chooserDialog.setTitle("Select Action")
        val chooserDialogItems = arrayOf("Select a photo from gallery", "Capture photo from camera")
        chooserDialog.setItems(chooserDialogItems) { _, which ->
            when (which) {
                0 -> chooseImageFromGallery()
                1 -> takeImageFromCamera()
            }
        }
        chooserDialog.show()
    }

    private fun chooseImageFromGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent, GALLERY)
    }

    lateinit var photoURI: Uri
    private fun takeImageFromCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    //TODO: HANDLE ERROR HERE
                    null
                }
                photoFile?.also {
                    photoURI = FileProvider.getUriForFile(
                        this,
                        "com.example.android.fileprovider",
                        it
                    )
                }
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, CAMERA)
            }
        }
    }

    lateinit var perfectBitmap: Bitmap

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GALLERY) {
                if (data != null) {
                    val contentUri = data.data
                    try {
                        perfectBitmap =
                            MediaStore.Images.Media.getBitmap(this.contentResolver, contentUri)
                        saveImage(perfectBitmap)
                        profileImageView.setImageBitmap(perfectBitmap)
                        makeProfileImageVisible()

                    } catch (e: IOException) {
                    }
                }
            } else if (requestCode == CAMERA) {
//                val thumbnail = data!!.extras!!.get("data") as Bitmap
                setPic()
//                saveImage(thumbnail)
            }
        }
    }

    fun setPic() {

        val bitmap = BitmapFactory.decodeFile(currentPhotoPath)
        perfectBitmap = checkIfRotationIsRequired(bitmap)
        profileImageView.setImageBitmap(perfectBitmap)
        makeProfileImageVisible()
    }

    private fun makeProfileImageVisible() {
        profilePictureContainer.visibility = View.VISIBLE
        addProfilePictureBtn.visibility = View.GONE
        profilePhotoLabel.visibility = View.GONE
    }

    private fun saveImage(mBitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream()
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
        val profilePictureDir =
            File(Environment.getExternalStorageDirectory().toString() + IMAGE_DIRECTORY)

        if (!profilePictureDir.exists()) {
            profilePictureDir.mkdirs()
        }

        try {
            val f =
                File(profilePictureDir, ((Calendar.getInstance().timeInMillis.toString() + ".jpg")))
            f.createNewFile()
            val fo = FileOutputStream(f)
            fo.write(bytes.toByteArray())

            MediaScannerConnection.scanFile(this, arrayOf(f.path), arrayOf("image/jpeg"), null)
            fo.close()

            return f.absolutePath
        } catch (ex: IOException) {
            Toast.makeText(this, "Error occurred while saving image", Toast.LENGTH_LONG).show()
        }

        return ""
    }


    private var currentPhotoPath: String = ""

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }

    }

    fun checkIfRotationIsRequired(bitmap: Bitmap): Bitmap {
        val exifInterface = ExifInterface(currentPhotoPath)
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
            else -> bitmap
        }
    }

    private fun encodeImageFileToBase64(bitmap: Bitmap?): String {
        var encodedString = ""
        val stream = ByteArrayOutputStream()

        if (bitmap != null) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
            val bytes = stream.toByteArray()
            encodedString = Base64.encodeToString(bytes, Base64.DEFAULT)
        }
        return encodedString

    }

    fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun requestMultiplePerms() {
        Dexter.withActivity(this)
            .withPermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).withListener(
                object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        if (report.isAnyPermissionPermanentlyDenied) {
                            //open device settings.
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        token?.continuePermissionRequest()
                    }

                }
            ).withErrorListener {
                Toast.makeText(
                    this,
                    "An error occurred when requesting permission. Try again.",
                    Toast.LENGTH_LONG
                ).show()
            }.onSameThread().check()
    }
}
