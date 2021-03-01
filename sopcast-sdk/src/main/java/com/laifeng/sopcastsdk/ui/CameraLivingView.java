package com.laifeng.sopcastsdk.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.laifeng.sopcastsdk.R;
import com.laifeng.sopcastsdk.audio.AudioUtils;
import com.laifeng.sopcastsdk.camera.CameraData;
import com.laifeng.sopcastsdk.camera.CameraHolder;
import com.laifeng.sopcastsdk.camera.CameraListener;
import com.laifeng.sopcastsdk.camera.ImageUtils;
import com.laifeng.sopcastsdk.configuration.AudioConfiguration;
import com.laifeng.sopcastsdk.configuration.CameraConfiguration;
import com.laifeng.sopcastsdk.configuration.VideoConfiguration;
import com.laifeng.sopcastsdk.constant.SopCastConstant;
import com.laifeng.sopcastsdk.controller.StreamController;
import com.laifeng.sopcastsdk.controller.audio.NormalAudioController;
import com.laifeng.sopcastsdk.controller.video.CameraVideoController;
import com.laifeng.sopcastsdk.entity.Watermark;
import com.laifeng.sopcastsdk.entity.WatermarkPosition;
import com.laifeng.sopcastsdk.mediacodec.AudioMediaCodec;
import com.laifeng.sopcastsdk.mediacodec.MediaCodecHelper;
import com.laifeng.sopcastsdk.mediacodec.VideoMediaCodec;
import com.laifeng.sopcastsdk.stream.packer.Packer;
import com.laifeng.sopcastsdk.stream.sender.Sender;
import com.laifeng.sopcastsdk.utils.SopCastLog;
import com.laifeng.sopcastsdk.utils.SopCastUtils;
import com.laifeng.sopcastsdk.utils.WeakHandler;
import com.laifeng.sopcastsdk.video.effect.Effect;

import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @Title: CameraLivingView
 * @Package com.laifeng.sopcastsdk.ui
 * @Description:
 * @Author Jim
 * @Date 16/9/18
 * @Time 下午5:41
 * @Version
 */
public class CameraLivingView extends CameraView {
    public static final int NO_ERROR = 0;
    public static final int VIDEO_TYPE_ERROR = 1;
    public static final int AUDIO_TYPE_ERROR = 2;
    public static final int VIDEO_CONFIGURATION_ERROR = 3;
    public static final int AUDIO_CONFIGURATION_ERROR = 4;
    public static final int CAMERA_ERROR = 5;
    public static final int AUDIO_ERROR = 6;
    public static final int AUDIO_AEC_ERROR = 7;
    public static final int SDK_VERSION_ERROR = 8;

    private static final String TAG = SopCastConstant.TAG;
    private StreamController mStreamController;
    private Context mContext;
    private PowerManager.WakeLock mWakeLock;
    private VideoConfiguration mVideoConfiguration = VideoConfiguration.createDefault();
    private AudioConfiguration mAudioConfiguration = AudioConfiguration.createDefault();
    private CameraListener mOutCameraOpenListener;
    private LivingStartListener mLivingStartListener;
    private WeakHandler mHandler = new WeakHandler();

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.7f;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private int[] rgbBytes = null;
    private Detector detector;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private Handler handler;
    private HandlerThread handlerThread;

    private Bitmap boundingboxColorimg;

    private Thread mUiThread;

    public interface LivingStartListener {
        void startError(int error);

        void startSuccess();
    }

    public CameraLivingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
        mUiThread = Thread.currentThread();
        mContext = context;
    }

    public CameraLivingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
        mContext = context;
    }

    public CameraLivingView(Context context) {
        super(context);
        initView();
        mContext = context;
    }

    private void initView() {
        CameraVideoController videoController = new CameraVideoController(mRenderer);
        NormalAudioController audioController = new NormalAudioController();
        mStreamController = new StreamController(videoController, audioController);
        mRenderer.setCameraOpenListener(mCameraOpenListener);

        initObjectDetect();
    }

    public void initObjectDetect(){
        int cropSize = TF_OD_API_INPUT_SIZE;
        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getContext(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public void doObjectDetect(byte[] data,Camera camera){
        //1：转化为RBG bitmap数据
        Camera.Size size = camera.getParameters().getPreviewSize();
        if(rgbBytes == null) {//初始化rgb缓冲区
            rgbBytes = new int[size.width*size.height];
            rgbFrameBitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888);
            croppedBitmap = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888);
            //对图片进行旋转
            Integer sensorOrientation;
            sensorOrientation = 90 - getScreenOrientation();
            frameToCropTransform =
                    ImageUtils.getTransformationMatrix(
                            size.width, size.height,
                            TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                            sensorOrientation, false);
            cropToFrameTransform = new Matrix();

             Matrix screenToCropTransform = ImageUtils.getTransformationMatrix(
                     1470, 672,
                     TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                     sensorOrientation, false);

            screenToCropTransform.invert(cropToFrameTransform);


        }
        ImageUtils.convertYUV420SPToARGB8888(data, size.width, size.height, rgbBytes);
        rgbFrameBitmap.setPixels(rgbBytes, 0, size.width, 0, 0, size.width, size.height);

        //2：裁剪
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        //3：识别
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);


                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        boolean find = false;
                        for (final Detector.Recognition result : results) {
                            if(result.getTitle().equals("person") && result.getConfidence() >= minimumConfidence ) {
                                SopCastLog.d(TAG, "person : " + result.getTitle());
                                final RectF location = result.getLocation();
                                if (location != null && result.getConfidence() >= minimumConfidence) {
                                    cropToFrameTransform.mapRect(location);
                                    drawBoundingBox(true, location.left, location.right, location.bottom, location.top);
                                }
                                find = true;
                            }
                        }

                        if(!find){
                            drawBoundingBox(false, 0,0, 0, 0);
                        }
                    }
                }
        );


//        drawBoundingBox(true,367,1102,168,504);

    }

    public final void runOnUiThread(Runnable action) {
        if (Thread.currentThread() != mUiThread) {
            mHandler.post(action);
        } else {
            action.run();
        }
    }


    protected int getScreenOrientation() {
        WindowManager manager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        switch (display.getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    @SuppressLint("InvalidWakeLockTag")
    public void init() {
        SopCastLog.d(TAG, "Version : " + SopCastConstant.VERSION);
        SopCastLog.d(TAG, "Branch : " + SopCastConstant.BRANCH);

        PowerManager mPowerManager = ((PowerManager) mContext.getSystemService(getContext().POWER_SERVICE));
        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.ON_AFTER_RELEASE, TAG);
    }

    public void setLivingStartListener(LivingStartListener listener) {
        mLivingStartListener = listener;
    }

    public void setPacker(Packer packer) {
        mStreamController.setPacker(packer);
    }

    public void setSender(Sender sender) {
        mStreamController.setSender(sender);
    }

    public void setVideoConfiguration(VideoConfiguration videoConfiguration) {
        mVideoConfiguration = videoConfiguration;
        mStreamController.setVideoConfiguration(videoConfiguration);
    }

    public void setCameraConfiguration(CameraConfiguration cameraConfiguration) {
        CameraHolder.instance().setConfiguration(cameraConfiguration);
        CameraHolder.instance().setPreviewCallBack(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Log.e(TAG, "onPreviewFrame ..");
                doObjectDetect(data,camera);
            }
        });
    }

    public void setAudioConfiguration(AudioConfiguration audioConfiguration) {
        mAudioConfiguration = audioConfiguration;
        mStreamController.setAudioConfiguration(audioConfiguration);
    }

    private int check() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            SopCastLog.w(TAG, "Android sdk version error");
            return SDK_VERSION_ERROR;
        }
        if (!checkAec()) {
            SopCastLog.w(TAG, "Doesn't support audio aec");
            return AUDIO_AEC_ERROR;
        }
        if (!isCameraOpen()) {
            SopCastLog.w(TAG, "The camera have not open");
            return CAMERA_ERROR;
        }
        MediaCodecInfo videoMediaCodecInfo = MediaCodecHelper.selectCodec(mVideoConfiguration.mime);
        if (videoMediaCodecInfo == null) {
            SopCastLog.w(TAG, "Video type error");
            return VIDEO_TYPE_ERROR;
        }
        MediaCodecInfo audioMediaCodecInfo = MediaCodecHelper.selectCodec(mAudioConfiguration.mime);
        if (audioMediaCodecInfo == null) {
            SopCastLog.w(TAG, "Audio type error");
            return AUDIO_TYPE_ERROR;
        }
        MediaCodec videoMediaCodec = VideoMediaCodec.getVideoMediaCodec(mVideoConfiguration);
        if (videoMediaCodec == null) {
            SopCastLog.w(TAG, "Video mediacodec configuration error");
            return VIDEO_CONFIGURATION_ERROR;
        }
        MediaCodec audioMediaCodec = AudioMediaCodec.getAudioMediaCodec(mAudioConfiguration);
        if (audioMediaCodec == null) {
            SopCastLog.w(TAG, "Audio mediacodec configuration error");
            return AUDIO_CONFIGURATION_ERROR;
        }
        if (!AudioUtils.checkMicSupport(mAudioConfiguration)) {
            SopCastLog.w(TAG, "Can not record the audio");
            return AUDIO_ERROR;
        }
        return NO_ERROR;
    }

    private boolean checkAec() {
        if (mAudioConfiguration.aec) {
            if (mAudioConfiguration.frequency == 8000 ||
                    mAudioConfiguration.frequency == 16000) {
                if (mAudioConfiguration.channelCount == 1) {
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    public void start() {
        SopCastUtils.processNotUI(new SopCastUtils.INotUIProcessor() {
            @Override
            public void process() {
                final int result = check();
                if (result == NO_ERROR) {
                    if (mLivingStartListener != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mLivingStartListener.startSuccess();
                            }
                        });
                    }
                    chooseVoiceMode();
                    screenOn();
                    mStreamController.start();
                } else {
                    if (mLivingStartListener != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mLivingStartListener.startError(result);
                            }
                        });
                    }
                }
            }
        });
    }

    private void chooseVoiceMode() {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (mAudioConfiguration.aec) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
        } else {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(false);
        }
    }

    public void stop() {
        screenOff();
        mStreamController.stop();
        setAudioNormal();
    }

    private void screenOn() {
        if (mWakeLock != null) {
            if (!mWakeLock.isHeld()) {
                mWakeLock.acquire();
            }
        }
    }

    private void screenOff() {
        if (mWakeLock != null) {
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
    }

    public void pause() {

        mStreamController.pause();

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void resume() {

        mStreamController.resume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    public void mute(boolean mute) {
        mStreamController.mute(mute);
    }

    public int getSessionId() {
        return mStreamController.getSessionId();
    }

    public void setEffect(Effect effect) {
        mRenderSurfaceView.setEffect(effect);
    }

    public void setWatermark(Watermark watermark) {
        mRenderer.setWatermark(watermark);
    }

    public void setBoundingboxColorTexture(Bitmap texture) {
        boundingboxColorimg = texture;
    }

    public void drawBoundingBox(boolean open ,float leftX, float rightX, float bottomY, float topY) {
        mRenderer.drawBoundingBox(true,boundingboxColorimg,leftX,rightX,bottomY,topY);
    }
    public boolean setVideoBps(int bps) {
        return mStreamController.setVideoBps(bps);
    }

    private boolean isCameraOpen() {
        return mRenderer.isCameraOpen();
    }

    public void setCameraOpenListener(CameraListener cameraOpenListener) {
        mOutCameraOpenListener = cameraOpenListener;
    }

    public void switchCamera() {
        boolean change = CameraHolder.instance().switchCamera();
        if (change) {
            changeFocusModeUI();
            if (mOutCameraOpenListener != null) {
                mOutCameraOpenListener.onCameraChange();
            }
        }
    }

    public CameraData getCameraData() {
        return CameraHolder.instance().getCameraData();
    }

    public void switchFocusMode() {
        CameraHolder.instance().switchFocusMode();
        changeFocusModeUI();
    }

    public void switchTorch() {
        CameraHolder.instance().switchLight();
    }

    public void release() {
        screenOff();
        try {
            mWakeLock.release();
            mWakeLock = null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        CameraHolder.instance().releaseCamera();
        CameraHolder.instance().release();
        setAudioNormal();
    }

    private CameraListener mCameraOpenListener = new CameraListener() {
        @Override
        public void onOpenSuccess() {
            changeFocusModeUI();
            if (mOutCameraOpenListener != null) {
                mOutCameraOpenListener.onOpenSuccess();
            }
        }

        @Override
        public void onOpenFail(int error) {
            if (mOutCameraOpenListener != null) {
                mOutCameraOpenListener.onOpenFail(error);
            }
        }

        @Override
        public void onCameraChange() {
            // Won't Happen
        }
    };

    private void setAudioNormal() {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setSpeakerphoneOn(false);
    }
}
