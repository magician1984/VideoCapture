package com.mobiledrivetech.recorder.core.ui.com.overlaycamera

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView.Renderer
import android.util.Log
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.properties.Delegates

internal class CameraRenderer(
    private val vertexShader : String,
    private val fragmentShader : String,
    private val onFrameAvailableCallback : () -> Unit
) : Renderer, OnFrameAvailableListener, Preview.SurfaceProvider {
    companion object {
        const val NUMBER_OF_TEXTURES = 2
        const val PREVIEW_IND = 0
        const val OVERLAY_IND = 1

        private val VERTEX_COORDINATE = floatArrayOf(
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
        )

        private val TEXTURE_COORDINATE = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
    }

    private var textures : IntArray = IntArray(NUMBER_OF_TEXTURES)

    private var previewTexture : SurfaceTexture? = null

    private var overlayTexture : SurfaceTexture? = null

    private val textureBuffer : FloatBuffer = ByteBuffer.allocateDirect(4 * 2 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val vertexBuffer : FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 2)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private var textureMatrix : FloatArray = FloatArray(16)

    private var program by Delegates.notNull<Int>()
    private var vPosition by Delegates.notNull<Int>()
    private var vCoord by Delegates.notNull<Int>()
    private var vTexture by Delegates.notNull<Int>()
    private var vMatrix by Delegates.notNull<Int>()

    init {
        vertexBuffer.clear()
        vertexBuffer.put(VERTEX_COORDINATE)

        textureBuffer.clear()
        textureBuffer.put(TEXTURE_COORDINATE)


    }

    override fun onSurfaceCreated(gl : GL10?, config : EGLConfig?) {
        gl?.let {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

            // Generate texture IDs
            GLES20.glGenTextures(NUMBER_OF_TEXTURES, textures, 0)

            // Bind the first texture
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[PREVIEW_IND])

            // Set texture parameters
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_NEAREST
            )

//                previewTexture = SurfaceTexture(textures[PREVIEW_IND])

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[OVERLAY_IND])

            // Set texture parameters
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_NEAREST
            )

//                previewTexture = SurfaceTexture(textures[OVERLAY_IND])

            program = loadProgram(vertexShader, fragmentShader)

            vPosition = GLES20.glGetAttribLocation(program, "vPosition")
            vCoord = GLES20.glGetAttribLocation(program, "vCoord")
            vTexture = GLES20.glGetUniformLocation(program, "vTexture")
            vMatrix = GLES20.glGetUniformLocation(program, "vMatrix")


        }
    }

    override fun onSurfaceChanged(gl : GL10?, width : Int, height : Int) {
        Log.d("Trace", "onSurfaceChanged: $width, $height")
        gl?.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl : GL10?) {

        val previewTexture = this.previewTexture ?: return

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        previewTexture.updateTexImage()
//            previewTexture.getTransformMatrix(textureMatrix)
        drawFrame(textures[PREVIEW_IND], vertexBuffer, textureBuffer)
    }

    override fun onSurfaceRequested(request : SurfaceRequest) {
        if (previewTexture == null) {
            previewTexture = SurfaceTexture(textures[PREVIEW_IND])
        }
        previewTexture?.setOnFrameAvailableListener(this)
        val surface = Surface(previewTexture!!)
        request.provideSurface(surface, Executors.newSingleThreadExecutor()) {
            surface.release()
            previewTexture?.release()
        }
    }

    override fun onFrameAvailable(surfaceTexture : SurfaceTexture?) {
        onFrameAvailableCallback.invoke()
    }

    private fun loadProgram(vSource : String, fSource : String) : Int {
        Log.d("Trace", "Load program: $vSource, $fSource")
        val vShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)

        GLES20.glShaderSource(vShader, vSource)

        GLES20.glCompileShader(vShader)

        val fShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)

        GLES20.glShaderSource(fShader, fSource)

        GLES20.glCompileShader(fShader)


        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vShader)
        GLES20.glAttachShader(program, fShader)
        GLES20.glLinkProgram(program)

        GLES20.glDeleteShader(vShader)
        GLES20.glDeleteShader(fShader)
        return program
    }

    private fun drawFrame(textureId : Int, vBuffer : FloatBuffer, fBuffer : FloatBuffer) {

        GLES20.glUseProgram(program)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, vBuffer)
        GLES20.glEnableVertexAttribArray(vPosition)

        textureBuffer.position(0)
        GLES20.glVertexAttribPointer(vCoord, 2, GLES20.GL_FLOAT, false, 0, fBuffer)
        GLES20.glEnableVertexAttribArray(vCoord)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_SAMPLER_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(vTexture, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }
}