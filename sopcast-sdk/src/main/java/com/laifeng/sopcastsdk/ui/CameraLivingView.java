package com.laifeng.sopcastsdk.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.util.Log;

import com.baidu.paddle.lite.demo.ocr.Predictor;
import com.laifeng.sopcastsdk.audio.AudioUtils;
import com.laifeng.sopcastsdk.camera.CameraData;
import com.laifeng.sopcastsdk.camera.CameraHolder;
import com.laifeng.sopcastsdk.camera.CameraListener;
import com.laifeng.sopcastsdk.configuration.AudioConfiguration;
import com.laifeng.sopcastsdk.configuration.CameraConfiguration;
import com.laifeng.sopcastsdk.configuration.VideoConfiguration;
import com.laifeng.sopcastsdk.constant.SopCastConstant;
import com.laifeng.sopcastsdk.controller.StreamController;
import com.laifeng.sopcastsdk.controller.audio.NormalAudioController;
import com.laifeng.sopcastsdk.controller.video.CameraVideoController;
import com.laifeng.sopcastsdk.entity.Watermark;
import com.laifeng.sopcastsdk.mediacodec.AudioMediaCodec;
import com.laifeng.sopcastsdk.mediacodec.MediaCodecHelper;
import com.laifeng.sopcastsdk.mediacodec.VideoMediaCodec;
import com.laifeng.sopcastsdk.stream.packer.Packer;
import com.laifeng.sopcastsdk.stream.sender.Sender;
import com.laifeng.sopcastsdk.utils.SopCastLog;
import com.laifeng.sopcastsdk.utils.SopCastUtils;
import com.laifeng.sopcastsdk.utils.WeakHandler;
import com.laifeng.sopcastsdk.video.effect.Effect;

import edu.sfsu.cs.orange.ocr.PlanarYUVLuminanceSource;

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

    protected Predictor predictor = new Predictor();

    //模型需要信息
    protected String modelPath = "models/ocr_v1.1";
    protected String labelPath = "labels/ppocr_keys_v1.txt";
    protected String imagePath = "images/5.jpg";
    protected int cpuThreadNum = 4;
    protected String cpuPowerMode = "LITE_POWER_HIGH";
    protected String inputColorFormat = "BGR";
    protected long[] inputShape = new long[]{1,3,960};
    protected float[] inputMean = new float[]{(float)0.485,(float) 0.456, (float)0.406};
    protected float[] inputStd = new float[]{(float)0.229,(float)0.224,(float)0.225};
    protected float scoreThreshold = 0.1f;

    public interface LivingStartListener {
        void startError(int error);

        void startSuccess();
    }

    public CameraLivingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();

        boolean initResult =  predictor.init( context, modelPath, labelPath, cpuThreadNum,
                cpuPowerMode,
                inputColorFormat,
                inputShape, inputMean,
                inputStd, scoreThreshold);
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

    public void ocrDetect(byte[] data, Camera camera){
        //拿到当前帧区域
        Point screenResolution = new Point();
        if(CameraHolder.instance().isLandscape()){
            screenResolution.x= 1470;
            screenResolution.y = 672;
        }else{
            screenResolution.x= 720;
            screenResolution.y = 1470;
        }
        int frameWidth = screenResolution.x * 1/4;
        int frameHeight = screenResolution.y * 1/4;
        int leftOffset = (screenResolution.x - frameWidth) / 2;
        int topOffset = (screenResolution.y - frameHeight) / 2;
        Rect rect = new Rect(leftOffset, topOffset, leftOffset + frameWidth, topOffset + frameHeight);
        //帧等比例兑换
        Point cameraResolution = new Point();
        cameraResolution.x = CameraHolder.instance().getCameraData().cameraWidth;
        cameraResolution.y = CameraHolder.instance().getCameraData().cameraHeight;
        rect.left = rect.left * cameraResolution.x / screenResolution.x;
        rect.right = rect.right * cameraResolution.x / screenResolution.x;
        rect.top = rect.top * cameraResolution.y / screenResolution.y;
        rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;

        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, cameraResolution.x, cameraResolution.y, rect.left, rect.top,
                rect.width(), rect.height(), false);

        Bitmap image = source.renderCroppedGreyscaleBitmap();

        predictor.setInputImage(image);
        predictor.runModel();

        Log.d(TAG, "ocrDetect: "+predictor.outputResult());
    }

    private void initView() {
        CameraVideoController videoController = new CameraVideoController(mRenderer);
        NormalAudioController audioController = new NormalAudioController();
        mStreamController = new StreamController(videoController, audioController);
        mRenderer.setCameraOpenListener(mCameraOpenListener);
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
                ocrDetect(data,camera);
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
    }

    public void resume() {
        mStreamController.resume();
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
