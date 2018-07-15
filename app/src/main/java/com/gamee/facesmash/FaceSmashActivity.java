package com.gamee.facesmash;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.hardware.Camera.Size;
import android.view.View;
import android.widget.RelativeLayout;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

/**
 * A sample wrapper class that just calls SDLActivity
 */
public class FaceSmashActivity extends SDLActivity {

    static final String TAG = "FaceSmashActivity";
    static final int PERMISSION_REQUEST_CAMERA = 0;
    static final int PERMISSION_DENIED = 0;
    static final int PERMISSION_GRANTED = 1;
    static final int PERMISSION_SHOW_RATIONALE = 2;
    static final int REMOVE_ADS_CODE = 0;

    /**
     * Path (relative) to the final, audio-video muxed, media file to be shared
     */
    static final String OUTPUT_VIDEO_NAME = "video" + File.separator + "output.mp4";
    /**
     * Path (relative) to the captured video stream, written down to file
     */
    static final String VIDEO_STREAM_MP4 = "video_stream.mp4";
    /**
     * Path (relative) to the audio asset used as a soundtrack for our gameplay videos
     */
    static final String MUSIC_VIDEO_AAC = "audio" + File.separator + "music_video.aac";

    Camera cam;
    SurfaceTexture tex;
    Size previewSize;
    int bitsPerPixel;
    boolean isPreviewOn = false;
    String outputVideoPath;
    String videoStreamPath;
    ByteBuffer extractorBuffer;

    private InterstitialAd mInterstitialAd;
    private AtomicBoolean mInterstitialLoaded;
    private AdView mAdView;
    private BillingManager mBillingManager;

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
        outputVideoPath = getFilesDir().getAbsolutePath() + File.separator + OUTPUT_VIDEO_NAME;
        videoStreamPath = getFilesDir().getAbsolutePath() + File.separator + VIDEO_STREAM_MP4;
        extractorBuffer = ByteBuffer.allocate(256 * 1024);
        copyAssets();
        InitVisage();
        InitAds();
        initStorage();
        InitBillings();
        InitCamera();
    }

    private void InitBillings() {
        mBillingManager = new BillingManager(this, new BillingManager.BillingUpdatesListener() {
            @Override
            public void onBillingClientSetupFinished() {}

            @Override
            public void onConsumeFinished(String token, int result) {}

            @Override
            public void onPurchasesUpdated(@BillingClient.BillingResponse int resultCode, List<Purchase> purchases) {
                int result;
                switch (resultCode) {
                    case BillingClient.BillingResponse.OK:
                        result = 0;
                        break;
                    case BillingClient.BillingResponse.USER_CANCELED:
                        result = 1;
                        break;
                    default:
                        result = 2; // PURCHASE_ERROR in native code
                        break;
                }

                for(Purchase p : purchases) {
                    if(p.getSku().equals("remove_ads"))
                        purchaseUpdated(REMOVE_ADS_CODE, result);
                }
            }
        });
    }

    static private String skuFromCode(int productId) {
        String ret = "";
        switch (productId) {
            case REMOVE_ADS_CODE:
                ret = "remove_ads";
                break;
                default:
                    break;
        }

        return ret;
    }

    public void initiatePurchaseFlow(int productId) {
        final String skuId = FaceSmashActivity.skuFromCode(productId);
        mBillingManager.initiatePurchaseFlow(skuId, BillingClient.SkuType.INAPP);
    }

    public void queryPurchases() {
        mBillingManager.queryPurchases();
    }

    private void initStorage() {
        File dir = new File(videoStreamPath).getParentFile();
        if (!dir.exists()) dir.mkdirs();
        WriteVideoOutputPath(videoStreamPath);

        // create output file directory
        File outDir = new File(outputVideoPath).getParentFile();
        if (!outDir.exists()) outDir.mkdirs();
    }

    private void InitAds() {
        if(mAdView != null) return;
        MobileAds.initialize(this, "ca-app-pub-5570185472343522~6969327132");
        mAdView = new AdView( this );
        mAdView.setAdUnitId("ca-app-pub-5570185472343522/9261266791");
        mAdView.setAdSize(AdSize.BANNER);
        mAdView.setBackgroundColor(Color.TRANSPARENT);

        mInterstitialLoaded = new AtomicBoolean(false);
        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-5570185472343522/3283015951");
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

    private void destroyAds() {
        if(mAdView == null) return;
        mAdView.destroy();
        mAdView = null;
    }

    public void AdsInterstitialLoad() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mInterstitialAd.loadAd(buildAdRequest());
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
                InitAds();
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.addRule(RelativeLayout.CENTER_HORIZONTAL);
                mLayout.addView(mAdView, params);
                mAdView.loadAd(buildAdRequest());
            }
        });
    }

    @NonNull
    private static AdRequest buildAdRequest() {
        return new AdRequest.Builder()
                .addTestDevice("D254B27DC2B97617242FD155AC7B7D30")
                .addTestDevice("B1BA8FBC0401428CA17088B5D3C481F0")
                .build();
    }

    public void AdsBannerHide() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(mLayout != null)
                    mLayout.removeView(mAdView);
                destroyAds();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(mAdView != null)
            mAdView.resume();

        if(cam == null) {
            InitCamera();
        }

        if(cam != null && isPreviewOn) {
            InternalStartCamera();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if(mAdView != null)
            mAdView.pause();

        if(cam != null) {
            if (isPreviewOn) {
                InternalStopCamera();
            }

            cam.release();
            cam = null;
        }
    }

    @Override
    public void onDestroy() {
        mBillingManager.destroy();
        destroyAds();
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
        if(CheckPermissionStatus(PERMISSION_REQUEST_CAMERA) == PERMISSION_GRANTED) {
            InternalInitCamera();
        }
    }

    private void InternalInitCamera() {
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

        Camera.Parameters parameters = cam.getParameters();
        setPreviewSize(parameters, 720);

        bitsPerPixel = ImageFormat.getBitsPerPixel(cam.getParameters().getPreviewFormat());
        int dataBufferSize = (previewSize.height * previewSize.width * bitsPerPixel) / 8;
        for (int i = 0; i < 2; i++) {
            cam.addCallbackBuffer(new byte[dataBufferSize]);
        }

        tex = new SurfaceTexture(0);
        try {
            cam.setPreviewTexture(tex);
        } catch (IOException e) {
            e.printStackTrace();
        }

        WriteCameraParams(previewSize.width, previewSize.height);
    }

    private void InternalStartCamera() {
        cam.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
            public void onPreviewFrame(byte[] data, Camera camera) {
            WriteFrameCamera(data);
            camera.addCallbackBuffer(data);
            }
        });
        cam.startPreview();
    }

    private void InternalStopCamera() {
        cam.stopPreview();
    }

    public void StartCamera() {
        if(cam != null && !isPreviewOn) {
            InternalStartCamera();
            isPreviewOn = true;
        }
    }

    public void StopCamera() {
        if(cam != null && isPreviewOn) {
            InternalStopCamera();
            isPreviewOn = false;
        }
    }

    public int CheckPermissionStatus(int permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // transform the numerical code in a Manifest permission string
            String permissionStr = permissionCodeToString(permission);

            if(checkSelfPermission(permissionStr) == PackageManager.PERMISSION_GRANTED) {
                return PERMISSION_GRANTED;
            } else {
                if(shouldShowRequestPermissionRationale(permissionStr)) {
                    return PERMISSION_SHOW_RATIONALE;
                }

                return PERMISSION_DENIED;
            }
        } else
            return PERMISSION_GRANTED;
    }

    private String permissionCodeToString(int permission) {
        String permissionStr;
        switch (permission) {
            case PERMISSION_REQUEST_CAMERA:
                permissionStr = Manifest.permission.CAMERA;
                break;
            default:
                permissionStr = "error";
                break;
        }

        return permissionStr;
    }

    public void RequestPermission(int permission) {
        String permissionStr = permissionCodeToString(permission);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{permissionStr}, permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        int result = PERMISSION_DENIED;
        if(grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            result = PERMISSION_GRANTED;
        else if(ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0]))
            result = PERMISSION_SHOW_RATIONALE;

        if(requestCode == PERMISSION_REQUEST_CAMERA && result == PERMISSION_GRANTED) {
            InitCamera();
        }

        EnqueuePermissionResult(requestCode, result);
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
                "visage/bdtsdata/LBF/vfadata/gd",
                "audio"
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
                "visage/504-932-294-611-606-835-011-303-246-311-003.vlc",
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
                "visage/jk_300.wfm",
                MUSIC_VIDEO_AAC
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


    public void startShareActivity() {
        String authority = getApplicationContext().getPackageName() + ".provider";
        Uri uri = GameeFileProvider.getUriForFile(this, authority, new File(outputVideoPath));
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setType("video/mp4");
        // intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share via"));
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


    public void muxAudioVideo() {
        try {
            final String audioInputPath = getFilesDir().getAbsolutePath() + File.separator + MUSIC_VIDEO_AAC;

            MediaExtractor extractorV = new MediaExtractor();
            extractorV.setDataSource(videoStreamPath);
            MediaFormat videoFormat = extractorV.getTrackFormat(0);
            extractorV.selectTrack(0);

            MediaExtractor extractorA = new MediaExtractor();
            extractorA.setDataSource(audioInputPath);
            MediaFormat audioFormat = extractorA.getTrackFormat(0);
            extractorA.selectTrack(0);

            MediaMuxer muxer = new MediaMuxer(outputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            int videoTrack = muxer.addTrack(videoFormat);
            int audioTrack = muxer.addTrack(audioFormat);

            muxer.start();

            MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
            // extractorV.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while((videoInfo.size = extractorV.readSampleData(extractorBuffer, 0)) >= 0) {
                long ts = extractorV.getSampleTime();
                videoInfo.flags = extractorV.getSampleFlags();
                videoInfo.presentationTimeUs = Math.max(videoInfo.presentationTimeUs, ts);
                muxer.writeSampleData(videoTrack, extractorBuffer, videoInfo);
                extractorV.advance();
            }

            MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
            // extractorA.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while((audioInfo.size = extractorA.readSampleData(extractorBuffer, 0)) >= 0) {
                audioInfo.flags = extractorA.getSampleFlags();
                audioInfo.presentationTimeUs = extractorA.getSampleTime();
                muxer.writeSampleData(audioTrack, extractorBuffer, audioInfo);
                extractorA.advance();
            }

            muxer.stop();
            muxer.release();
            extractorV.release();
            extractorA.release();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Interfaces to native methods used to pass stuff to C++
    public native void WriteFrameCamera(byte[] frame);
    public native void WriteCameraParams(int width, int height);
    public native void InitVisage();
    public native void WriteVideoOutputPath(String path);
    // permissions management
    public native void EnqueuePermissionResult(int permission, int result);
    // needed by gpg
    public native void nativeOnActivityResult(Activity activity, int requestCode, int resultCode, Intent data);
    // billing service
    public native void purchaseUpdated(int product, int result);
}
