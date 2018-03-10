package com.gamee.facesmash;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.hardware.Camera.Size;
import android.view.View;
import android.widget.RelativeLayout;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

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
    String videoOutputPath;

    private InterstitialAd mInterstitialAd;
    private AtomicBoolean mInterstitialLoaded;
    private AdView mAdView;

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

        videoOutputPath = getExternalFilesDir(null) + File.separator + "temp.mp4";
        copyAssets();
        InitCamera();
        InitVisage();
        InitAds();
    }

    private void InitAds() {
        MobileAds.initialize(this, "ca-app-pub-3134955856541949~5951417069");
        mAdView = new AdView( this );
        mAdView.setAdUnitId("ca-app-pub-3940256099942544/6300978111");
        mAdView.setAdSize(AdSize.BANNER);
        mAdView.setBackgroundColor(Color.TRANSPARENT);

        mInterstitialLoaded = new AtomicBoolean(false);
        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-3940256099942544/1033173712");
        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(int errorCode) {}

            @Override
            public void onAdOpened() {}

            @Override
            public void onAdLeftApplication() {}

            @Override
            public void onAdClosed() {
                mInterstitialLoaded.set(false);
            }

            @Override
            public void onAdLoaded() {
                mInterstitialLoaded.set(true);
            }
        });
    }

    public void AdsInterstitialLoad() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mInterstitialAd.loadAd(new AdRequest.Builder().build());
            }
        });
    }

    public boolean AdsIsInterstitialLoaded() {
        return mInterstitialLoaded.get();
    }

    public void AdsInterstitialShow() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mInterstitialAd.setImmersiveMode(true);
                mInterstitialAd.show();
            }
        });
    }

    public void AdsBannerShow() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.addRule(RelativeLayout.CENTER_HORIZONTAL);
                mLayout.addView(mAdView, params);
                AdRequest adRequest = new AdRequest.Builder().build();
                mAdView.loadAd(adRequest);
            }
        });

        File dir = new File(videoOutputPath).getParentFile();
        if (!dir.exists()) dir.mkdirs();
        WriteVideoOutputPath(videoOutputPath);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAdView.resume();

        if(cam == null)
            InitCamera();
        if(isPreviewOn)
            StartCamera();
    }

    @Override
    public void onPause() {
        mAdView.pause();
        ReleaseCamera();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mAdView.destroy();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        nativeOnActivityResult(this, requestCode, resultCode, data);

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
        setPreviewSize(parameters, 720);

        bitsPerPixel = ImageFormat.getBitsPerPixel(cam.getParameters().getPreviewFormat());
        int dataBufferSize = (previewSize.height * previewSize.width * bitsPerPixel) / 8;
        for (int i = 0; i < 2; i++) {
            cam.addCallbackBuffer(new byte[dataBufferSize]);
        }
        cam.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
            public void onPreviewFrame(byte[] data, Camera camera) {
                WriteFrameCamera(data);
                camera.addCallbackBuffer(data);
            }
        });
        tex = new SurfaceTexture(0);
        try {
            cam.setPreviewTexture(tex);
        } catch (IOException e) {
            e.printStackTrace();
        }

        WriteCameraParams(previewSize.width, previewSize.height);
    }

    private void ReleaseCamera() {
        if (cam != null) {
            cam.stopPreview();
            cam.release();
            cam = null;
        }
    }

    public void StartCamera() {
        if(cam != null) {
            cam.startPreview();
            isPreviewOn = true;
        }
    }

    public void StopCamera() {
        if(cam != null)
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

    static public void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
        out.close();

        in.close();
    }

    /** Utility method called to copy required file to trackerdata folder.
     *
     * @param rootDir absolute path to directory where files should be copied.
     * @param filename name of file that will be copied.
     */
    private void copyFile(String rootDir, String filename) {
        AssetManager assetManager = this.getAssets();
        String newFileName = rootDir + File.separator + filename;
        File file = new File(newFileName);
        if(!file.exists()){
            try {
                copyStream(assetManager.open(filename), new FileOutputStream(newFileName));
            } catch (IOException e) {
                Log.e(TAG, String.format("Error in copying: %s", e.getMessage()));
            }
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
                "visage/813-721-696-114-345-714-410-682-472-210-486.vlc",
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


    public void galleryAddVideo() {
        new AddVideoToGallery().execute();
    }


    private class AddVideoToGallery extends AsyncTask<Void, Void, File> {
        protected File doInBackground(Void... voids) {
            DateFormat s = new SimpleDateFormat("yyyyMMddhhmmss", Locale.US);
            String datetime = s.format(new Date()); // TODO: change format
            String dstPathName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                    + File.separator + "FaceSmash_" + datetime + ".mp4";
            File src = new File(FaceSmashActivity.this.videoOutputPath);
            File dst = new File(dstPathName);
            cp(src, dst);
            return dst;
        }

        protected void onPostExecute(File f) {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(f);
            mediaScanIntent.setData(contentUri);
            FaceSmashActivity.this.sendBroadcast(mediaScanIntent);
        }

        private void cp(File src, File dst) {
            if(!dst.exists()) {
                try {
                    FaceSmashActivity.copyStream(new FileInputStream(src), new FileOutputStream(dst));
                } catch (IOException e) {
                    Log.e(TAG, String.format("Error in copying: %s", e.getMessage()));
                }
            }
        }
    }

    /**
     * called from native code
     */
    public void showOssLicenses() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(FaceSmashActivity.this, OssLicensesMenuActivity.class));
            }
        });
    }


    // Interfaces to native methods used to pass stuff to C++
    public native void WriteFrameCamera(byte[] frame);
    public native void WriteCameraParams(int width, int height);
    public native void InitVisage();
    public native void WriteVideoOutputPath(String path);
    // needed by gpg
    public native void nativeOnActivityResult(Activity activity, int requestCode, int resultCode, Intent data);
}
