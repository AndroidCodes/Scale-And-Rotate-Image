package com.example.scaleandrotateimage;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.io.FileDescriptor;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener {

    static final int NONE = 0, DRAG = 1, ZOOM = 2;

    private static final float MAX_ZOOM = 5.0f, MIN_ZOOM = 0.15f;

    Matrix matrix = new Matrix();
    Matrix savedMatrix = new Matrix();

    int mode = NONE;
    float[] lastEvent = null;
    float d = 0f, newRot = 0f;

    PointF start = new PointF();
    PointF mid = new PointF();
    double oldDist = 1f;

    private ImageView iv_image;

    public static Bitmap getImageFromKitkat(Activity activity, Intent data) {

        ParcelFileDescriptor parcelFileDescriptor = null;
        try {
            parcelFileDescriptor = activity.getContentResolver().
                    openFileDescriptor(data.getData(), "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
            final int REQUIRED_SIZE = 700;
            int scale = 1;
            while (options.outWidth / scale / 2 >= REQUIRED_SIZE
                    && options.outHeight / scale / 2 >= REQUIRED_SIZE)
                scale *= 2;
            options.inSampleSize = scale;
            options.inJustDecodeBounds = false;

            Bitmap bmp = BitmapFactory.decodeFileDescriptor(fileDescriptor,
                    null, options);

            parcelFileDescriptor.close();

            return bmp;

        } catch (Exception e) {

            System.out.print("KitkatGalleryException ...>>>..." + e.getMessage());

            return null;

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        iv_image = (ImageView) findViewById(R.id.iv_image);
        iv_image.setOnTouchListener(this);

        findViewById(R.id.btn_gallery).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Intent intent;

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {

                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    startActivityForResult(intent, 100);

                } else {

                    intent = new Intent(Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.setType("image/*");
                    startActivityForResult(Intent.createChooser(intent, "Select File"), 100);

                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Bitmap bitmap = null;

        if (resultCode == Activity.RESULT_OK) {

            if (requestCode == 100) {

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {

                    bitmap = getImageFromKitkat(MainActivity.this, data);

                } else {

                    bitmap = onSelectFromGalleryResult(data);

                }
            } else {
            }

            iv_image.setImageBitmap(bitmap);

        }
    }

    private Bitmap onSelectFromGalleryResult(Intent data) {

        Uri selectedImageUri = data.getData();
        String[] projection = {MediaStore.MediaColumns.DATA};
        Cursor cursor = getContentResolver().query(selectedImageUri, projection, null, null,
                null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
        cursor.moveToFirst();

        String selectedImagePath = cursor.getString(column_index);

        System.out.println("\n\n\n selectedImagePath --> " + selectedImagePath + "\n\n\n");

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(selectedImagePath, options);
        final int REQUIRED_SIZE = 1080;
        int scale = 1;
        while (options.outWidth / scale / 2 >= REQUIRED_SIZE
                && options.outHeight / scale / 2 >= REQUIRED_SIZE)
            scale *= 2;
        options.inSampleSize = scale;
        options.inJustDecodeBounds = false;

        Bitmap bitmap = BitmapFactory.decodeFile(selectedImagePath, options);

        return bitmap;

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        ImageView view = (ImageView) v;

        // Handle touch events here...
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:

                savedMatrix.set(matrix);

                start.set(event.getX(), event.getY());

                mode = DRAG;

                lastEvent = null;

                break;

            case MotionEvent.ACTION_POINTER_DOWN:

                oldDist = spacing(event);

                if (oldDist > 10f) {

                    savedMatrix.set(matrix);

                    midPoint(mid, event);

                    mode = ZOOM;

                }

                lastEvent = new float[4];
                lastEvent[0] = event.getX(0);
                lastEvent[1] = event.getX(1);
                lastEvent[2] = event.getY(0);
                lastEvent[3] = event.getY(1);

                d = rotation(event);

                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:

                mode = NONE;

                lastEvent = null;

                break;

            case MotionEvent.ACTION_MOVE:

                if (mode == DRAG) {

                    matrix.set(savedMatrix);
                    matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);

                } else if (mode == ZOOM && event.getPointerCount() == 2) {

                    float newDist = spacing(event);

                    matrix.set(savedMatrix);

                    if (newDist > 10f) {

                        float scale = (float) (newDist / oldDist);

                        matrix.postScale(scale, scale, mid.x, mid.y);

                    }

                    if (lastEvent != null) {

                        newRot = rotation(event);

                        float r = newRot - d;

                        matrix.postRotate(r, view.getMeasuredWidth() / 2,
                                view.getMeasuredHeight() / 2);

                    }
                }
                break;
        }

        view.setImageMatrix(matrix);

        return true;
    }

    private float rotation(MotionEvent event) {
        double delta_x = (event.getX(0) - event.getX(1));
        double delta_y = (event.getY(0) - event.getY(1));
        double radians = Math.atan2(delta_y, delta_x);

        return (float) Math.toDegrees(radians);
    }

    /**
     * Determine the space between the first two fingers
     */
    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * Calculate the mid point of the first two fingers
     */
    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }
}
