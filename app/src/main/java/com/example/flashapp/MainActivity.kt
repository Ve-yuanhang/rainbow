package com.example.flashapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.flashapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaPlayer: MediaPlayer
    private var originalBrightness: Float = 0f
    private var isActive = false
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var colorChangeRunnable: Runnable? = null

    // 颜色渐变相关变量
    private var currentHue = 0f
    private val saturation = 1f
    private val value = 1f
    private val hueStep = 1f

    // 音量相关变量
    private lateinit var audioManager: AudioManager
    private var originalVolume = 0
    private var maxVolume = 0

    // 权限请求码
    private val cameraPermissionRequestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化音频管理器
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 保存原始音量
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        // 隐藏状态栏
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN

        // 保存当前窗口的原始亮度
        val layoutParams = window.attributes
        originalBrightness = layoutParams.screenBrightness

        // 初始化相机管理器
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        // 设置触摸监听器
        binding.root.setOnClickListener {
            stopAndExit()
        }

        // 检查并请求权限
        checkPermissions()
    }

    private fun checkPermissions() {
        // 检查摄像头权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 请求权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequestCode
            )
        } else {
            // 已有权限，启动应用功能
            startAppFunctions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予
                startAppFunctions()
            } else {
                // 权限被拒绝
                showPermissionDeniedDialog()
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage("需要摄像头权限才能使用手电筒功能。")
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
                startAppFunctionsWithoutFlash()
            }
            .setCancelable(false)
            .show()
    }

    private fun startAppFunctionsWithoutFlash() {
        binding.statusText.text = "状态: 特效运行中 (无手电筒)"
        startAppFunctions(false)
    }

    private fun startAppFunctions(withFlash: Boolean = true) {
        // 设置最大亮度
        setMaxBrightness()

        // 播放音乐（设置音量）
        playMusic()

        // 打开手电筒（如果允许）
        if (withFlash) {
            turnOnFlashlight()
        }

        // 开始颜色渐变效果
        startColorGradient()

        // 开始音量动画
        startVolumeAnimation()

        isActive = true
    }

    private fun setMaxBrightness() {
        // 设置当前窗口亮度为最大值（不影响系统设置）
        val layoutParams = window.attributes
        layoutParams.screenBrightness = 1.0f // 1.0为最大亮度
        window.attributes = layoutParams
    }

    private fun playMusic() {
        try {
            // 设置媒体音量到最大音量的一半
            setMediaVolumeToHalf()

            // 创建并播放音乐
            mediaPlayer = MediaPlayer.create(this, R.raw.beep)
            mediaPlayer.isLooping = true
            mediaPlayer.start()

            // 更新音量指示器
            updateVolumeIndicator()
        } catch (e: Exception) {
            e.printStackTrace()
            binding.statusText.text = "状态: 特效运行中 (无音乐)"
        }
    }

    private fun setMediaVolumeToHalf() {
        // 计算目标音量（最大音量的一半）
        val targetVolume = maxVolume / 2

        // 设置媒体音量
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            targetVolume,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun updateVolumeIndicator() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.volumeText.text = "音量: $currentVolume/$maxVolume"
    }

    private fun restoreOriginalVolume() {
        try {
            // 恢复原始音量
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                originalVolume,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun turnOnFlashlight() {
        try {
            // 查找支持闪光灯的相机
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (hasFlash == true) {
                    cameraId = id
                    break
                }
            }

            cameraId?.let {
                cameraManager.setTorchMode(it, true)
            } ?: run {
                binding.statusText.text = "状态: 特效运行中 (无手电筒)"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.statusText.text = "状态: 特效运行中 (无手电筒)"
        }
    }

    private fun startColorGradient() {
        // 停止之前的颜色变化
        colorChangeRunnable?.let { handler.removeCallbacks(it) }

        // 创建新的颜色渐变效果
        colorChangeRunnable = object : Runnable {
            override fun run() {
                if (isActive) {
                    // 使用HSV颜色空间实现平滑渐变
                    currentHue = (currentHue + hueStep) % 360
                    val color = Color.HSVToColor(floatArrayOf(currentHue, saturation, value))
                    binding.colorBackground.setBackgroundColor(color)

                    // 更新颜色信息
                    binding.colorInfo.text = "色相: ${currentHue.toInt()}°"

                    // 16ms后再次运行（约60FPS）
                    handler.postDelayed(this, 16)
                }
            }
        }

        // 开始渐变
        colorChangeRunnable?.let { handler.post(it) }
    }

    private fun startVolumeAnimation() {
        // 创建音量动画效果
        val volumeRunnable = object : Runnable {
            private var scale = 1.0f
            private var growing = false

            override fun run() {
                if (isActive) {
                    // 更新音量指示器大小
                    if (growing) {
                        scale += 0.05f
                        if (scale >= 1.2f) growing = false
                    } else {
                        scale -= 0.05f
                        if (scale <= 1.0f) growing = true
                    }

                    binding.volumeText.scaleX = scale
                    binding.volumeText.scaleY = scale

                    // 200ms后再次运行
                    handler.postDelayed(this, 200)
                }
            }
        }

        // 开始动画
        handler.post(volumeRunnable)
    }

    private fun stopAndExit() {
        if (!isActive) return
        isActive = false

        // 停止颜色渐变
        colorChangeRunnable?.let { handler.removeCallbacks(it) }

        // 停止音乐
        try {
            if (::mediaPlayer.isInitialized) {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 关闭手电筒
        try {
            cameraId?.let {
                cameraManager.setTorchMode(it, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 恢复亮度
        val layoutParams = window.attributes
        layoutParams.screenBrightness = originalBrightness
        window.attributes = layoutParams

        // 恢复原始音量
        restoreOriginalVolume()

        // 退出应用
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        stopAndExit()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isActive) {
            stopAndExit()
        }
    }
}