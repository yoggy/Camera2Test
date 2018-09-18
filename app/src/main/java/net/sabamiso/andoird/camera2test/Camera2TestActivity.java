package net.sabamiso.andoird.camera2test;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Camera2TestActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    public static final String TAG = "Camera2TestActivity";

    CameraCharacteristics cameraCharacteristics;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    CaptureRequest.Builder builder;

    ImageReader imageReader;

    Button buttonCapture;
    ImageView imageView;

    Bitmap captureResultBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2_test_activity);

        buttonCapture = (Button)findViewById(R.id.buttonCapture);
        buttonCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickCaptureButton();
            }
        });
        imageView = (ImageView)findViewById(R.id.imageView);

        // ImageReaderをCameraDeviceの書き込み先Surfaceとして使用する
        imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG
                , 2);
        imageReader.setOnImageAvailableListener(imageReaderOnImageAvailableListener, new Handler(this.getMainLooper()));
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();

        //
        // ここを通過する際、アプリのpermissionの状態は、次の3種類
        //    1. permissionダイアログでユーザが拒否した場合
        //    2. permissionが未確認の状態
        //    3. permissionダイアログでユーザが許可した場合
        //
        if (isIgnorePermission() == true) {
            // 1. permissionダイアログでユーザが拒否した場合
            // とりあえずToastを表示して、カメラの権限を許可してもらうように促す...
            Toast.makeText(this, "アプリ情報から、このアプリのカメラのパーミッションを許可してください…", Toast.LENGTH_LONG).show();
        }
        else if (hasPermission() == false) {
            // 2. permissionが未確認の状態
            // 未確認なのでパーミッションダイアログを表示
            requestPermission();
        }
        else {
            // 3. permissionダイアログでユーザが許可した場合
            // カメラプレビューを開始する
            startCamera();

            // この例の場合は、SurfaceViewを使用しないためいきなりonResume()で開始してもOK
            // SurfaceViewを使用する場合は、SurfaceHolder.Callback.surfaceCreated()が呼び出された後で初期化する必要があるので要注意…
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        stopCamera();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    boolean hasPermission() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return true;
    }

    boolean isIgnorePermission() {
        return ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA);
    }

    protected final int MY_PERMISSIONS_REQUEST_CAMERA = 1234;

    void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                MY_PERMISSIONS_REQUEST_CAMERA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult()");
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                if (grantResults.length > 0  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "GRANTED Camera permission");
                } else {
                    Log.d(TAG, "IGNORE Camera permission");
                }

                return;
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    String getBackfaceCameraId() {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);

        try {
            for (String cameraId : manager.getCameraIdList()) {
                cameraCharacteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    @SuppressLint("MissingPermission")
    void startCamera() {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);

        try {
            String cameraId = getBackfaceCameraId();
            manager.openCamera(cameraId, stateCallback, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void stopCamera() {
        if (cameraCaptureSession != null) {
            try {
                cameraCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if(cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    int getOrientation(int rotation) {
        // see also...https://github.com/googlesamples/android-Camera2Basic/blob/d1a4f53338b76c7aaa2579adbc16ef5a553a5462/Application/src/main/java/com/example/android/camera2basic/Camera2BasicFragment.java#L857

        final SparseIntArray ORIENTATIONS = new SparseIntArray();
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);

        int displayRotation = this.getWindowManager().getDefaultDisplay().getRotation();
        int cameraOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        return (ORIENTATIONS.get(rotation) + cameraOrientation + 270) % 360;
    }

    final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, " CameraDevice.StateCallback.onOpend()");

            Camera2TestActivity.this.cameraDevice = cameraDevice;

            List<Surface> surfacesList = new ArrayList<Surface>();

            Surface surface_image_reader = imageReader.getSurface();
            surfacesList.add(surface_image_reader);

            try {
                builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                builder.addTarget(surface_image_reader);
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                builder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
                cameraDevice.createCaptureSession(surfacesList, captureSessionCallback, null);

                // addTarget(), createCaptureSession()に使用するsurfaceは複数指定可能
                // SurfaceView, TextureView, ImageReaderなどが使用可能
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, " CameraDevice.StateCallback.onDisconnected()");
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Log.d(TAG, " CameraDevice.StateCallback.onError()");
        }
    };

    final CameraCaptureSession.StateCallback captureSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            Log.d(TAG, "CameraCaptureSession.StateCallback.onConfigured()");

            Camera2TestActivity.this.cameraCaptureSession = session;

            try {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START); // オートフォーカスのトリガー

                session.setRepeatingRequest(builder.build(), captureCallback, null);

            } catch (CameraAccessException e) {
                Log.e(TAG, e.toString());
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Log.d(TAG, "CameraCaptureSession.StateCallback.onConfigureFailed()");
        }
    };

    final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            //キャプチャ完了時に呼び出されるコールバック関数
            //Log.d(TAG, "CameraCaptureSession.StateCallback.onCaptureCompleted()");
        }
    };

    final ImageReader.OnImageAvailableListener imageReaderOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader imageReader) {
            //ImageReaderの内容が更新されたら呼び出されるコールバック
            //Log.d(TAG, "ImageReader.OnImageAvailableListener.onImageAvailable()");

            Image img = imageReader.acquireLatestImage();
            if (img == null) {
                return;
            }

            // planeからjpeg_dataを取り出す
            Image.Plane[] planes = img.getPlanes();
            if (planes == null) {
                // DeviceSessionを閉じた後に空のplanesが取れることがあるので要注意…
                img.close();
                return;
            }

            Image.Plane p = img.getPlanes()[0];
            ByteBuffer buffer = p.getBuffer();
            byte[] jpeg_data = new byte[buffer.remaining()];
            buffer.get(jpeg_data);
            int width = img.getWidth();
            int height = img.getHeight();

            // ここでjpeg_dataからbitmapを生成する
            BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
            bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
            captureResultBitmap = BitmapFactory.decodeByteArray(jpeg_data, 0, jpeg_data.length, bitmapFatoryOptions);

            img.close();
        }
    };

    void onClickCaptureButton() {
        imageView.setImageBitmap(captureResultBitmap);
    }
}
