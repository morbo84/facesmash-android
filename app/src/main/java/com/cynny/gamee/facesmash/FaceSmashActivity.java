package com.cynny.gamee.facesmash;

import android.content.res.AssetManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.hardware.Camera.Size;
import android.view.View;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * A sample wrapper class that just calls SDLActivity
 */
public class FaceSmashActivity extends SDLActivity {

    static final String TAG = "FaceSmashActivity";

    Camera cam;
    SurfaceTexture tex;
    Size previewSize;
    int bitsPerPixel;
    boolean isPreviewOn = false;

    /**
     * This method is called by SDL before loading the native shared libraries.
     * It can be overridden to provide names of shared libraries to be loaded.
     * The default implementation returns the defaults. It never returns null.
     * An array returned by a new implementation must at least contain "SDL2".
     * Also keep in mind that the order the libraries are loaded may matter.
     *
     * @return names of shared libraries to be loaded (e.g. "SDL2", "main").
     */
    @Override
    protected String[] getLibraries() {
        return new String[]{
                "SDL2",
                "SDL2_image",
                "SDL2_mixer",
                // "SDL2_net",
                "SDL2_ttf",
                "VisageVision",
                "VisageAnalyser",
                "facesmash"
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        copyAssets();
        InitCamera();
        InitVisage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(cam == null)
            InitCamera();
        if(isPreviewOn)
            StartCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        ReleaseCamera();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void InitCamera() {
        int cameraId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            cameraId = i;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                break;
            }
        }

        try {
            cam = Camera.open(cameraId);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open camera");
            return;
        }
        // cam.setDisplayOrientation(90);
        Camera.Parameters parameters = cam.getParameters();
        setPreviewSize(parameters, 700);

        bitsPerPixel = ImageFormat.getBitsPerPixel(cam.getParameters().getPreviewFormat());
        int dataBufferSize = (previewSize.height * previewSize.width * bitsPerPixel) / 8;
        for (int i = 0; i < 10; i++) {
            cam.addCallbackBuffer(new byte[dataBufferSize]);
        }
        cam.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
            public void onPreviewFrame(byte[] data, Camera camera) {
                camera.addCallbackBuffer(data);
                WriteFrameCamera(data);
            }
        });
        tex = new SurfaceTexture(0);
        try {
            cam.setPreviewTexture(tex);
        } catch (IOException e) {
            e.printStackTrace();
        }

        WriteCameraParams(previewSize.width, previewSize.height, bitsPerPixel);
    }

    private void ReleaseCamera() {
        if (cam != null) {
            cam.stopPreview();
            cam.release();
            cam = null;
        }
    }

    public void StartCamera() {
        cam.startPreview();
        isPreviewOn = true;
    }

    public void StopCamera() {
        cam.stopPreview();
        isPreviewOn = false;
    }

    /**
     * Sets preview size so that width is closest to param width
     *
     * @param parameters
     * @param width
     */
    private void setPreviewSize(Camera.Parameters parameters, int width) {
        int idx = 0, dist = 100000;
        List<Size> sizes = parameters.getSupportedPreviewSizes();
        for (int i = 0; i < sizes.size(); i++) {
            if (Math.abs(sizes.get(i).width - width) < dist) {
                idx = i;
                dist = Math.abs(sizes.get(i).width - width);
            }
        }
        parameters.setPreviewSize(sizes.get(idx).width, sizes.get(idx).height);
        parameters.setPreviewFormat(ImageFormat.NV21);
        cam.setParameters(parameters);

        previewSize = cam.getParameters().getPreviewSize();
        Log.i(TAG, "Current preview size is " + previewSize.width + ", " + previewSize.height);
    }

    /** Utility method called to copy required file to trackerdata folder.
     *
     * @param rootDir absolute path to directory where files should be copied.
     * @param filename name of file that will be copied.
     */
    private void copyFile(String rootDir, String filename) {
        AssetManager assetManager = this.getAssets();

        InputStream in;
        OutputStream out;
        try {
            String newFileName = rootDir + File.separator + filename;
            File file = new File(newFileName);

            if(!file.exists()) {
                in = assetManager.open(filename);
                out = new FileOutputStream(newFileName);

                byte[] buffer = new byte[4*1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                out.close();

                in.close();
            }
        } catch (Exception e) {
            Log.e(TAG, String.format("Error in copying: %s", e.getMessage()));
        }
    }

    /** Utility method called to create required directories and initiate copy of all assets required for tracking.
     *
     * @param rootDir absolute path to root directory used for storing assets required for tracking.
     */
    public void copyAssets() {
        // create dirs
        final String[] dirs = {
                "visage",
                "visage/bdtsdata",
                "visage/bdtsdata/FF",
                "visage/bdtsdata/LBF",
                "visage/bdtsdata/NN",
                "visage/bdtsdata/LBF/pe",
                "visage/bdtsdata/LBF/vfadata",
                "visage/bdtsdata/LBF/ye",
                "visage/bdtsdata/LBF/vfadata/ad",
                "visage/bdtsdata/LBF/vfadata/ed",
                "visage/bdtsdata/LBF/vfadata/gd"
        };

        String rootDir = getFilesDir().getAbsolutePath();
        for (String dirname : dirs) {
            try {
                File dir = new File(rootDir + File.separator + dirname);
                if (!dir.exists()) dir.mkdir();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        // copy files
        final String[] files = {
                "visage/578-496-411-691-522-273-235-359-916-935-253.vlc",
                "visage/Facial Features Tracker - High.cfg",
                "visage/bdtsdata/FF/ff.dat",
                "visage/bdtsdata/LBF/lv",
                "visage/bdtsdata/LBF/vfadata/ad/ad0.lbf",
                "visage/bdtsdata/LBF/vfadata/ad/ad1.lbf",
                "visage/bdtsdata/LBF/vfadata/ad/ad2.lbf",
                "visage/bdtsdata/LBF/vfadata/ad/ad3.lbf",
                "visage/bdtsdata/LBF/vfadata/ad/ad4.lbf",
                "visage/bdtsdata/LBF/vfadata/ad/regressor.lbf",
                "visage/bdtsdata/LBF/vfadata/ed/ed0.lbf",
                "visage/bdtsdata/LBF/vfadata/ed/ed1.lbf",
                "visage/bdtsdata/LBF/vfadata/ed/ed2.lbf",
                "visage/bdtsdata/LBF/vfadata/ed/ed3.lbf",
                "visage/bdtsdata/LBF/vfadata/ed/ed4.lbf",
                "visage/bdtsdata/LBF/vfadata/ed/ed5.lbf",
                "visage/bdtsdata/LBF/vfadata/ed/ed6.lbf",
                "visage/bdtsdata/LBF/vfadata/gd/gd.lbf",
                "visage/bdtsdata/LBF/ye/landmarks.txt",
                "visage/bdtsdata/LBF/ye/lp11.bdf",
                "visage/bdtsdata/LBF/ye/W",
                "visage/bdtsdata/NN/fa.lbf",
                "visage/bdtsdata/NN/fc.lbf",
                "visage/bdtsdata/NN/fr.bin",
                "visage/bdtsdata/NN/pr.bin",
                "visage/candide3.fdp",
                "visage/candide3.wfm",
                "visage/jk_300.fdp",
                "visage/jk_300.wfm"
        };

        for (String filename : files) {
            try {
                Log.i(TAG, rootDir + File.separator + filename);
                copyFile(rootDir, filename);
            } catch(Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    /**
     * Interface to native method used for passing raw pixel data to FaceSmashActivity.
     *
     * @param frame  raw pixel data of image used for tracking.
     */
    public native void WriteFrameCamera(byte[] frame);
    public native void WriteCameraParams(int width, int height, int bitsPerPixel);
    public native void InitVisage();
}
