package com.mobiledrivetech.recorder.core.ui.com.overlaycamera

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.camera.core.Preview
import com.mobiledrivetech.recorder.R
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class OverlayCameraView(context : Context, attributeSet : AttributeSet? = null) :
    GLSurfaceView(context, attributeSet) {

    private val renderer : CameraRenderer

    public val provider : Preview.SurfaceProvider
        get() = renderer

    init {
        val vertexShader : String = readShaderCode(context, R.raw.vertext)
        val fragmentShader : String = readShaderCode(context, R.raw.fragment)
        setEGLContextClientVersion(2)
        renderer = CameraRenderer(vertexShader, fragmentShader){
            requestRender()
        }

        setRenderer(renderer)

        renderMode = RENDERMODE_WHEN_DIRTY
    }

    private fun readShaderCode(context : Context, rawId : Int) : String {
        val `is` : InputStream = context.resources.openRawResource(rawId)
        val br = BufferedReader(InputStreamReader(`is`))
        var line : String?
        val sb = StringBuilder()
        try {
            while (br.readLine().also { line = it } != null) {
                sb.append(line)
                sb.append("\n")
            }
        } catch (e : Exception) {
            e.printStackTrace()
        }
        try {
            br.close()
        } catch (e : IOException) {
            e.printStackTrace()
        }
        return sb.toString()
    }

}