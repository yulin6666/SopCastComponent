package com.laifeng.sopcastsdk.camera;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.widget.ResourceCursorTreeAdapter;

import com.baidu.paddle.lite.demo.ocr.Predictor;
import com.laifeng.sopcastsdk.camera.exception.CameraHardwareException;
import com.laifeng.sopcastsdk.camera.exception.CameraNotSupportException;
import com.laifeng.sopcastsdk.configuration.CameraConfiguration;
import com.laifeng.sopcastsdk.utils.SopCastLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.sfsu.cs.orange.ocr.PlanarYUVLuminanceSource;

/**
 * @Title: CameraHolder
 * @Package com.youku.crazytogether.app.modules.sopCastV2
 * @Description:
 * @Author Jim
 * @Date 16/3/23
 * @Time 上午11:57
 * @Version
 */
@TargetApi(14)
public class CameraHolder {
    private static final String TAG = "CameraHolder";
    private final static int FOCUS_WIDTH = 80;
    private final static int FOCUS_HEIGHT = 80;

    private List<CameraData> mCameraDatas;
    private Camera mCameraDevice;
    private CameraData mCameraData;
    private State mState;
    private SurfaceTexture mTexture;
    private boolean isTouchMode = false;
    private boolean isOpenBackFirst = false;
    private CameraConfiguration mConfiguration = CameraConfiguration.createDefault();
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

    public enum State {
        INIT,
        OPENED,
        PREVIEW
    }

    private static CameraHolder sHolder;

    public static synchronized CameraHolder instance() {
        if (sHolder == null) {
            sHolder = new CameraHolder();
        }
        return sHolder;
    }

    private CameraHolder() {
        mState = State.INIT;

        boolean initResult =  predictor.init(getApplicationContext(), modelPath, labelPath, cpuThreadNum,
                cpuPowerMode,
                inputColorFormat,
                inputShape, inputMean,
                inputStd, scoreThreshold);
    }

    public int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    public CameraData getCameraData() {
        return mCameraData;
    }

    public boolean isLandscape() {
        return (mConfiguration.orientation != CameraConfiguration.Orientation.PORTRAIT);
    }

    public synchronized Camera openCamera()
            throws CameraHardwareException, CameraNotSupportException {
        if (mCameraDatas == null || mCameraDatas.size() == 0) {
            mCameraDatas = CameraUtils.getAllCamerasData(isOpenBackFirst);
        }
        CameraData cameraData = mCameraDatas.get(0);
        if (mCameraDevice != null && mCameraData == cameraData) {
            return mCameraDevice;
        }
        if (mCameraDevice != null) {
            releaseCamera();
        }
        try {
            SopCastLog.d(TAG, "open camera " + cameraData.cameraID);
            mCameraDevice = Camera.open(cameraData.cameraID);
        } catch (RuntimeException e) {
            SopCastLog.e(TAG, "fail to connect Camera");
            throw new CameraHardwareException(e);
        }
        if (mCameraDevice == null) {
            throw new CameraNotSupportException();
        }
        try {
            CameraUtils.initCameraParams(mCameraDevice, cameraData, isTouchMode, mConfiguration);
        } catch (Exception e) {
            e.printStackTrace();
            mCameraDevice.release();
            mCameraDevice = null;
            throw new CameraNotSupportException();
        }
        mCameraData = cameraData;
        mState = State.OPENED;
        return mCameraDevice;
    }

    public void setSurfaceTexture(SurfaceTexture texture) {
        mTexture = texture;
        if (mState == State.PREVIEW && mCameraDevice != null && mTexture != null) {
            try {
                Log.e(TAG, "setSurfaceTexture setPreviewTexture ..");
                mCameraDevice.setPreviewTexture(mTexture);
                mCameraDevice.setPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        Log.e(TAG, "onPreviewFrame ..");

//                        Rect rect = getFramingRectInPreview();
//                        if (rect == null) {
//                            return null;
//                        }
//                        // Go ahead and assume it's YUV rather than die.
//                        return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
//                                rect.width(), rect.height(), reverseImage);
                    }
                });
            } catch (IOException e) {
                releaseCamera();
            }
        }
    }

    public State getState() {
        return mState;
    }

    public void setConfiguration(CameraConfiguration configuration) {
        isTouchMode = (configuration.focusMode != CameraConfiguration.FocusMode.AUTO);
        isOpenBackFirst = (configuration.facing != CameraConfiguration.Facing.FRONT);
        mConfiguration = configuration;
    }

    public synchronized void startPreview() {
        if (mState != State.OPENED) {
            return;
        }
        if (mCameraDevice == null) {
            return;
        }
        if (mTexture == null) {
            return;
        }
        try {
            Log.e(TAG, "setPreviewTexture ..");
            mCameraDevice.setPreviewTexture(mTexture);
            mCameraDevice.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    Log.e(TAG, "onPreviewFrame ..,width:"+mCameraData.cameraWidth+",height:"+mCameraData.cameraHeight+"+landscape:%d"+isLandscape());

                    //拿到当前帧区域
                    Point screenResolution = new Point();
                    if(isLandscape()){
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
                    cameraResolution.x = mCameraData.cameraWidth;
                    cameraResolution.y = mCameraData.cameraHeight;
                    rect.left = rect.left * cameraResolution.x / screenResolution.x;
                    rect.right = rect.right * cameraResolution.x / screenResolution.x;
                    rect.top = rect.top * cameraResolution.y / screenResolution.y;
                    rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;

                    PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, cameraResolution.x, cameraResolution.y, rect.left, rect.top,
                            rect.width(), rect.height(), false);

                    Bitmap bitmap = source.renderCroppedGreyscaleBitmap();
                }
            });
            mCameraDevice.startPreview();
            mState = State.PREVIEW;
        } catch (Exception e) {
            releaseCamera();
            e.printStackTrace();
        }
    }

    public synchronized void stopPreview() {
        if (mState != State.PREVIEW) {
            return;
        }
        if (mCameraDevice == null) {
            return;
        }
        mCameraDevice.setPreviewCallback(null);
        Camera.Parameters cameraParameters = mCameraDevice.getParameters();
        if (cameraParameters != null && cameraParameters.getFlashMode() != null
                && !cameraParameters.getFlashMode().equals(Camera.Parameters.FLASH_MODE_OFF)) {
            cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }
        mCameraDevice.setParameters(cameraParameters);
        mCameraDevice.stopPreview();
        mState = State.OPENED;
    }

    public synchronized void releaseCamera() {
        if (mState == State.PREVIEW) {
            stopPreview();
        }
        if (mState != State.OPENED) {
            return;
        }
        if (mCameraDevice == null) {
            return;
        }
        mCameraDevice.release();
        mCameraDevice = null;
        mCameraData = null;
        mState = State.INIT;
    }

    public void release() {
        mCameraDatas = null;
        mTexture = null;
        isTouchMode = false;
        isOpenBackFirst = false;
        mConfiguration = CameraConfiguration.createDefault();
    }

    public void setFocusPoint(int x, int y) {
        if (mState != State.PREVIEW || mCameraDevice == null) {
            return;
        }
        if (x < -1000 || x > 1000 || y < -1000 || y > 1000) {
            SopCastLog.w(TAG, "setFocusPoint: values are not ideal " + "x= " + x + " y= " + y);
            return;
        }
        Camera.Parameters params = mCameraDevice.getParameters();

        if (params != null && params.getMaxNumFocusAreas() > 0) {
            List<Camera.Area> focusArea = new ArrayList<Camera.Area>();
            focusArea.add(new Camera.Area(new Rect(x, y, x + FOCUS_WIDTH, y + FOCUS_HEIGHT), 1000));

            params.setFocusAreas(focusArea);

            try {
                mCameraDevice.setParameters(params);
            } catch (Exception e) {
                // Ignore, we might be setting it too
                // fast since previous attempt
            }
        } else {
            SopCastLog.w(TAG, "Not support Touch focus mode");
        }
    }

    public boolean doAutofocus(Camera.AutoFocusCallback focusCallback) {
        if (mState != State.PREVIEW || mCameraDevice == null) {
            return false;
        }
        // Make sure our auto settings aren't locked
        Camera.Parameters params = mCameraDevice.getParameters();
        if (params.isAutoExposureLockSupported()) {
            params.setAutoExposureLock(false);
        }

        if (params.isAutoWhiteBalanceLockSupported()) {
            params.setAutoWhiteBalanceLock(false);
        }

        mCameraDevice.setParameters(params);
        mCameraDevice.cancelAutoFocus();
        mCameraDevice.autoFocus(focusCallback);
        return true;
    }

    public void changeFocusMode(boolean touchMode) {
        if (mState != State.PREVIEW || mCameraDevice == null || mCameraData == null) {
            return;
        }
        isTouchMode = touchMode;
        mCameraData.touchFocusMode = touchMode;
        if (touchMode) {
            CameraUtils.setTouchFocusMode(mCameraDevice);
        } else {
            CameraUtils.setAutoFocusMode(mCameraDevice);
        }
    }

    public void switchFocusMode() {
        changeFocusMode(!isTouchMode);
    }

    public float cameraZoom(boolean isBig) {
        if (mState != State.PREVIEW || mCameraDevice == null || mCameraData == null) {
            return -1;
        }
        Camera.Parameters params = mCameraDevice.getParameters();
        if (isBig) {
            params.setZoom(Math.min(params.getZoom() + 1, params.getMaxZoom()));
        } else {
            params.setZoom(Math.max(params.getZoom() - 1, 0));
        }
        mCameraDevice.setParameters(params);
        return (float) params.getZoom() / params.getMaxZoom();
    }

    public boolean switchCamera() {
        if (mState != State.PREVIEW) {
            return false;
        }
        try {
            CameraData camera = mCameraDatas.remove(1);
            mCameraDatas.add(0, camera);
            openCamera();
            startPreview();
            return true;
        } catch (Exception e) {
            CameraData camera = mCameraDatas.remove(1);
            mCameraDatas.add(0, camera);
            try {
                openCamera();
                startPreview();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
            return false;
        }
    }

    public boolean switchLight() {
        if (mState != State.PREVIEW || mCameraDevice == null || mCameraData == null) {
            return false;
        }
        if (!mCameraData.hasLight) {
            return false;
        }
        Camera.Parameters cameraParameters = mCameraDevice.getParameters();
        if (cameraParameters.getFlashMode().equals(Camera.Parameters.FLASH_MODE_OFF)) {
            cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        } else {
            cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }
        try {
            mCameraDevice.setParameters(cameraParameters);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
