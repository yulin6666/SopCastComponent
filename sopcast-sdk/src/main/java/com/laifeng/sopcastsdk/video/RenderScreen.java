package com.laifeng.sopcastsdk.video;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import com.laifeng.sopcastsdk.camera.CameraData;
import com.laifeng.sopcastsdk.camera.CameraHolder;
import com.laifeng.sopcastsdk.entity.Watermark;
import com.laifeng.sopcastsdk.entity.WatermarkPosition;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * @Title: RenderScreen
 * @Package com.laifeng.sopcastsdk.video
 * @Description:
 * @Author Jim
 * @Date 16/9/14
 * @Time 下午2:15
 * @Version
 */
public class RenderScreen {
    private final FloatBuffer mNormalVtxBuf = GlUtil.createVertexBuffer();
    private final FloatBuffer mNormalTexCoordBuf = GlUtil.createTexCoordBuffer();
    private final float[] mPosMtx = GlUtil.createIdentityMtx();

    private int mFboTexId;

    private int mProgram = -1;
    private int maPositionHandle = -1;
    private int maTexCoordHandle = -1;
    private int muPosMtxHandle = -1;
    private int muSamplerHandle = -1;

    private int mScreenW = -1;
    private int mScreenH = -1;

    private FloatBuffer mCameraTexCoordBuffer;

    private Bitmap mWatermarkImg;
    private FloatBuffer mWatermarkVertexBuffer;
    private int mWatermarkTextureId = -1;
    private float mWatermarkRatio = 1.0f;

    private boolean mDrawBoundingBox;
    private Bitmap mimgBitmap;
    private int mimgWidth;
    private int mimgHeight;
    private int morientation;
    private int mimgVmargin;
    private int mimgHmargin;

    public RenderScreen(int id) {
        mFboTexId = id;
        initGL();
    }

    public void setSreenSize(int width, int height) {
        mScreenW = width;
        mScreenH = height;

        initCameraTexCoordBuffer();
    }

    public void setTextureId(int textureId) {
        mFboTexId = textureId;
    }

    public void setVideoSize(int width, int height) {
        mWatermarkRatio = mScreenW / ((float)width);
    }

    private void initCameraTexCoordBuffer() {
        int cameraWidth, cameraHeight;
        CameraData cameraData = CameraHolder.instance().getCameraData();
        int width;
        int height;
        if(cameraData!=null){
            width = cameraData.cameraWidth;
            height = cameraData.cameraHeight;
        }else{
            width = 960;
            height = 720;
        }

        if(CameraHolder.instance().isLandscape()) {
            cameraWidth = Math.max(width, height);
            cameraHeight = Math.min(width, height);
        } else {
            cameraWidth = Math.min(width, height);
            cameraHeight = Math.max(width, height);
        }

        float hRatio = mScreenW / ((float)cameraWidth);
        float vRatio = mScreenH / ((float)cameraHeight);

        float ratio;
        if(hRatio > vRatio) {
            ratio = mScreenH / (cameraHeight * hRatio);
            final float vtx[] = {
                    //UV
                    0f, 0.5f + ratio/2,
                    0f, 0.5f - ratio/2,
                    1f, 0.5f + ratio/2,
                    1f, 0.5f - ratio/2,
            };
            ByteBuffer bb = ByteBuffer.allocateDirect(4 * vtx.length);
            bb.order(ByteOrder.nativeOrder());
            mCameraTexCoordBuffer = bb.asFloatBuffer();
            mCameraTexCoordBuffer.put(vtx);
            mCameraTexCoordBuffer.position(0);
        } else {
            ratio = mScreenW/ (cameraWidth * vRatio);
            final float vtx[] = {
                    //UV
                    0.5f - ratio/2, 1f,
                    0.5f - ratio/2, 0f,
                    0.5f + ratio/2, 1f,
                    0.5f + ratio/2, 0f,
            };
            ByteBuffer bb = ByteBuffer.allocateDirect(4 * vtx.length);
            bb.order(ByteOrder.nativeOrder());
            mCameraTexCoordBuffer = bb.asFloatBuffer();
            mCameraTexCoordBuffer.put(vtx);
            mCameraTexCoordBuffer.position(0);
        }

    }

    public void setWatermark(Watermark watermark) {
    }

    public void setOneBoundBox(boolean open,Bitmap img, int imgWidth, int imgHeight, int orientation, int imgVmargin, int imgHmargin) {
        mDrawBoundingBox = open;
        mimgBitmap = img;
        mimgHeight = imgHeight;
        mimgWidth = imgWidth;
        morientation = orientation;
        mimgVmargin = imgVmargin;
        mimgHmargin = imgHmargin;
    }

    private void initWatermarkVertexBuffer() {
        if (mScreenW <= 0 || mScreenH <= 0) {
            return;
        }

        int width = (int) (mimgWidth *mWatermarkRatio);
        int height = (int) (mimgHeight *mWatermarkRatio);
        int vMargin = (int) (mimgVmargin *mWatermarkRatio);
        int hMargin = (int) (mimgHmargin *mWatermarkRatio);

        boolean isTop, isRight;
        if(morientation == WatermarkPosition.WATERMARK_ORIENTATION_TOP_LEFT
                || morientation == WatermarkPosition.WATERMARK_ORIENTATION_TOP_RIGHT) {
            isTop = true;
        } else {
            isTop = false;
        }

        if(morientation == WatermarkPosition.WATERMARK_ORIENTATION_TOP_RIGHT
                || morientation == WatermarkPosition.WATERMARK_ORIENTATION_BOTTOM_RIGHT) {
            isRight = true;
        } else {
            isRight = false;
        }

        float leftX = (mScreenW/2.0f - hMargin - width)/(mScreenW/2.0f);
        float rightX = (mScreenW/2.0f - hMargin)/(mScreenW/2.0f);

        float topY = (mScreenH/2.0f - vMargin)/(mScreenH/2.0f);
        float bottomY = (mScreenH/2.0f - vMargin - height)/(mScreenH/2.0f);

        float temp;

        if(!isRight) {
            temp = leftX;
            leftX = -rightX;
            rightX = -temp;
        }
        if(!isTop) {
            temp = topY;
            topY = -bottomY;
            bottomY = -temp;
        }
        final float watermarkCoords[]= {
                leftX,  bottomY, 0.0f,
                leftX, topY, 0.0f,
                rightX, topY, 0.0f,
                rightX,  bottomY, 0.0f
        };
        ByteBuffer bb = ByteBuffer.allocateDirect(watermarkCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        mWatermarkVertexBuffer = bb.asFloatBuffer();
        mWatermarkVertexBuffer.put(watermarkCoords);
        mWatermarkVertexBuffer.position(0);
    }

    public void draw() {
        if (mScreenW <= 0 || mScreenH <= 0) {
            return;
        }
        GlUtil.checkGlError("draw_S");

        GLES20.glViewport(0, 0, mScreenW, mScreenH);

        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mProgram);

        mNormalVtxBuf.position(0);
        GLES20.glVertexAttribPointer(maPositionHandle,
                3, GLES20.GL_FLOAT, false, 4 * 3, mNormalVtxBuf);
        GLES20.glEnableVertexAttribArray(maPositionHandle);

        mCameraTexCoordBuffer.position(0);
        GLES20.glVertexAttribPointer(maTexCoordHandle,
                2, GLES20.GL_FLOAT, false, 4 * 2, mCameraTexCoordBuffer);
        GLES20.glEnableVertexAttribArray(maTexCoordHandle);

        GLES20.glUniformMatrix4fv(muPosMtxHandle, 1, false, mPosMtx, 0);
        GLES20.glUniform1i(muSamplerHandle, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFboTexId);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        if(mDrawBoundingBox){
            drawBoundingBox();
        }
        GlUtil.checkGlError("draw_E");
    }


    public void drawBoundingBox() {

        initWatermarkVertexBuffer();

        mWatermarkVertexBuffer.position(0);
        GLES20.glVertexAttribPointer(maPositionHandle,
                3, GLES20.GL_FLOAT, false, 4 * 3, mWatermarkVertexBuffer);
        GLES20.glEnableVertexAttribArray(maPositionHandle);

        mNormalTexCoordBuf.position(0);
        GLES20.glVertexAttribPointer(maTexCoordHandle,
                2, GLES20.GL_FLOAT, false, 4 * 2, mNormalTexCoordBuf);
        GLES20.glEnableVertexAttribArray(maTexCoordHandle);

        if(mWatermarkTextureId == -1) {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mimgBitmap, 0);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            mWatermarkTextureId = textures[0];
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mWatermarkTextureId);

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable(GLES20.GL_BLEND);

        GLES20.glLineWidth(10);
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void initGL() {
        GlUtil.checkGlError("initGL_S");

        final String vertexShader =
                //
                "attribute vec4 position;\n" +
                        "attribute vec4 inputTextureCoordinate;\n" +
                        "uniform   mat4 uPosMtx;\n" +
                        "varying   vec2 textureCoordinate;\n" +
                        "void main() {\n" +
                        "  gl_Position = uPosMtx * position;\n" +
                        "  textureCoordinate   = inputTextureCoordinate.xy;\n" +
                        "}\n";
        final String fragmentShader =
                //
                "precision mediump float;\n" +
                        "uniform sampler2D uSampler;\n" +
                        "varying vec2  textureCoordinate;\n" +
                        "void main() {\n" +
                        "  gl_FragColor = texture2D(uSampler, textureCoordinate);\n" +
                        "}\n";
        mProgram = GlUtil.createProgram(vertexShader, fragmentShader);
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "position");
        maTexCoordHandle = GLES20.glGetAttribLocation(mProgram, "inputTextureCoordinate");
        muPosMtxHandle = GLES20.glGetUniformLocation(mProgram, "uPosMtx");
        muSamplerHandle = GLES20.glGetUniformLocation(mProgram, "uSampler");

        GlUtil.checkGlError("initGL_E");
    }
}
