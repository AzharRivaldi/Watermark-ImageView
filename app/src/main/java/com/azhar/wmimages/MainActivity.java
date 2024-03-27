package com.azhar.wmimages;

import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.azhar.wmimages.BuildConfig;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    GPSTracker gpsTracker;
    TextView tvLocation, tvDateTime, tvImageName, tvDevice;
    ImageView imagePreview;
    Button btnCapture, btnWatermark;
    String imageFilePath, encodedImage, timeStamp, imageName, imageSize;
    File fileDirectoty, fileName;
    NumberFormat numberFormat;
    Uri uriImage;
    int REQ_CAMERA = 100;
    int fileSize;
    byte[] imageBytes;
    private static final int REQUEST_PICK_PHOTO = 1;

    @SuppressLint("SimpleDateFormat")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
            if (checkIfAlreadyhavePermission()) {
                requestForSpecificPermission();
            }
        }

        imagePreview = findViewById(R.id.imagePreview);
        btnCapture = findViewById(R.id.btnCapture);
        btnWatermark = findViewById(R.id.btnWatermark);
        tvLocation = findViewById(R.id.tvLocation);
        tvDateTime = findViewById(R.id.tvDateTime);
        tvImageName = findViewById(R.id.tvImageName);
        tvDevice = findViewById(R.id.tvDevice);

        timeStamp = new SimpleDateFormat("dd MMMM yyyy HH:mm:ss").format(new Date());

        btnCapture.setOnClickListener(view -> showPictureDialog());

        btnWatermark.setOnClickListener(view -> {
            if (imageFilePath == null) {
                Toast.makeText(MainActivity.this, "Ups, tidak ada foto!",
                        Toast.LENGTH_SHORT).show();
            } else {
                savePhotoToExternalStorage();
            }
        });
    }

    //dialog pilihan
    private void showPictureDialog() {
        AlertDialog.Builder pictureDialog = new AlertDialog.Builder(this);
        pictureDialog.setTitle("Pilih:");
        String[] pictureDialogItems = {
                "Pilih foto dari galeri",
                "Ambil foto dari kamera"};
        pictureDialog.setItems(pictureDialogItems,
                (dialog, which) -> {
                    switch (which) {
                        case 0:
                            UploadImage();
                            break;
                        case 1:
                            takeCameraImage();
                            break;
                    }
                });
        pictureDialog.show();
    }

    //ambil gambar dari kamera
    private void takeCameraImage() {
        Dexter.withContext(this)
                .withPermissions(android.Manifest.permission.CAMERA,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            try {
                                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                intent.putExtra(MediaStore.EXTRA_OUTPUT,
                                        FileProvider.getUriForFile(MainActivity.this,
                                                BuildConfig.APPLICATION_ID + ".provider",
                                                createImageFile()));
                                startActivityForResult(intent, REQ_CAMERA);
                            } catch (IOException ex) {
                                Toast.makeText(MainActivity.this,
                                        "Ups, kamera bermasalah!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest>
                                                                           permissions,
                                                                   PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    //ambil gambar dari galeri
    private void UploadImage() {
        Dexter.withContext(this)
                .withPermissions(android.Manifest.permission.CAMERA,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            startActivityForResult(galleryIntent, REQUEST_PICK_PHOTO);
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest>
                                                                           permissions,
                                                                   PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    //create image file
    private File createImageFile() throws IOException {
        imageName = "JPEG_";
        fileDirectoty = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), "");
        fileName = File.createTempFile(imageName, ".jpg", fileDirectoty);
        imageFilePath = fileName.getAbsolutePath();
        return fileName;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA && resultCode == RESULT_OK) {
            convertImage(imageFilePath);
        } else if (requestCode == REQUEST_PICK_PHOTO && resultCode == RESULT_OK) {
            uriImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            assert uriImage != null;
            Cursor cursor = getContentResolver().query(uriImage,
                    filePathColumn, null, null, null);
            assert cursor != null;
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String mediaPath = cursor.getString(columnIndex);
            cursor.close();
            imageFilePath = mediaPath;
            convertImage(mediaPath);
        }
    }

    @SuppressLint("SetTextI18n")
    private void convertImage(String urlImg) {
        File imgFile = new File(urlImg);
        if (imgFile.exists()) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            Bitmap bitmap = BitmapFactory.decodeFile(imageFilePath, options);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);

            ExifInterface exif;
            try {
                exif = new ExifInterface(imgFile.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);

            Matrix matrix = new Matrix();
            if (orientation == 6) {
                matrix.postRotate(90);
            } else if (orientation == 3) {
                matrix.postRotate(180);
            } else if (orientation == 8) {
                matrix.postRotate(270);
            }

            bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            imagePreview.setImageBitmap(bitmap);

            imageBytes = baos.toByteArray();
            encodedImage = Base64.encodeToString(imageBytes, Base64.DEFAULT);

            numberFormat = new DecimalFormat();
            numberFormat.setMaximumFractionDigits(2);
            fileSize = Integer.parseInt(String.valueOf(imgFile.length() / 1024));
            imageSize = numberFormat.format(fileSize);

            gpsTracker = new GPSTracker(MainActivity.this);
            if (gpsTracker.getIsGPSTrackingEnabled()) {
                double latitude = gpsTracker.getLatitude();
                double longitude = gpsTracker.getLongitude();
                String lokasiGambar = gpsTracker.getAddressLine(this);
                String modelHP = Build.MODEL;
                String brandHP = Build.BRAND;

                tvLocation.setText(lokasiGambar + "\n\n" + latitude + ", " + longitude);
                tvDateTime.setText(timeStamp);
                tvImageName.setText(imageFilePath + "\n\n" + imageSize + " MB");
                tvDevice.setText(brandHP + " " + modelHP);
            } else {
                Toast.makeText(MainActivity.this,
                        "Ups, gagal mendapatkan lokasi. Silakan periksa GPS Anda!",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    //save image with watermark
    private void savePhotoToExternalStorage() {
        ContentResolver contentResolver = getContentResolver();
        Uri imageCollection;
        if (SDK_INT >= Build.VERSION_CODES.Q){
            imageCollection = MediaStore.Images.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY);
        }
        else {
            imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        try {
            Bitmap bitmap;
            if (SDK_INT >= Build.VERSION_CODES.P) {
                bitmap = getMarkBitmap(ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(contentResolver, uriImage)), getMetaData());
            } else {
                bitmap = getMarkBitmap(MediaStore.Images.Media.getBitmap(
                        contentResolver, uriImage), getMetaData());
            }

            imagePreview.setImageURI(null);
            imagePreview.setImageBitmap(bitmap);

            ContentValues contentValues = new ContentValues();
            contentValues.put(
                    MediaStore.Images.Media.DISPLAY_NAME, "IMG_" + new Date().getTime() + ".jpg");
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.Images.Media.WIDTH, bitmap.getWidth());
            contentValues.put(MediaStore.Images.Media.HEIGHT, bitmap.getHeight());

            Uri uri = contentResolver.insert(imageCollection, contentValues);
            OutputStream stream = contentResolver.openOutputStream(uri);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            stream.flush();
            stream.close();
            Toast.makeText(MainActivity.this, "Yeay! Watermark berhasil dibuat!",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    //create watermark
    private Bitmap getMarkBitmap(Bitmap hardwareBitmap, ArrayList<String> metaData) {
        byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0,
                decodedString.length);

        ExifInterface exif;
        try {
            exif = new ExifInterface(getContentResolver().openInputStream(uriImage));;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);

        Matrix matrix = new Matrix();
        if (orientation == 6) {
            matrix.postRotate(90);
        } else if (orientation == 3) {
            matrix.postRotate(180);
        } else if (orientation == 8) {
            matrix.postRotate(270);
        }

        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);

        imagePreview.getDrawingCache();

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap,
                hardwareBitmap.getWidth(), hardwareBitmap.getHeight(), false);
        Canvas canvas = new Canvas(scaledBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setAlpha(80);
        paint.setTextSize(65);
        paint.setAntiAlias(true);
        paint.setColor(ContextCompat.getColor(this, R.color.colorPrimary));
        canvas.drawRect(120, canvas.getHeight() - (100 * metaData.size()),
                1200, canvas.getHeight() - 20, paint);
        paint.setColor(ContextCompat.getColor(this, R.color.colorAccent));
        int height = canvas.getHeight() - (90 * metaData.size()) + 20;
        for (String value : metaData) {
            canvas.drawText(value, 130, height, paint);
            height += 90;
        }
        return scaledBitmap;
    }

    //detail watermark
    private ArrayList<String> getMetaData() {
        ArrayList<String> metaData = new ArrayList<>();
        try {
            ExifInterface exif = new ExifInterface(getContentResolver().openInputStream(uriImage));
            metaData.add("Lat: " + gpsTracker.getLatitude());
            metaData.add("Long: " + gpsTracker.getLongitude());
            String orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
            metaData.add("Orientation: " + orientation);
            metaData.add("Date: " + timeStamp);
        } catch (Exception e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
        }
        return metaData;
    }

    private boolean checkIfAlreadyhavePermission() {
        int result = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestForSpecificPermission() {
        ActivityCompat.requestPermissions(this, new String[]{
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION}, 101);
    }

}