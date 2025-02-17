package io.ssafy.openticon.domain

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import io.ssafy.openticon.MainActivity
import io.ssafy.openticon.R
import io.ssafy.openticon.data.model.Emoticon
import io.ssafy.openticon.data.model.EmoticonPackWithEmotions
import io.ssafy.openticon.data.model.LikeEmoticon
import io.ssafy.openticon.data.model.LikeEmoticonPack
import io.ssafy.openticon.ui.component.EmoticonPackView
import io.ssafy.openticon.ui.component.LikeEmoticonPackView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var secondFloatingView: View
    private var isSecondViewVisible = false  // 두 번째 뷰의 표시 상태
    private lateinit var secondLayoutParams: WindowManager.LayoutParams

    private lateinit var likeView: LikeEmoticonPackView

    private var isCloseViewVisible = false  // 두 번째 뷰의 표시 상태
    private lateinit var closeFloatingView: View
    private lateinit var closeLayoutParams: WindowManager.LayoutParams


    private var initialTouchX = 0
    private var initialTouchY = 0
    private var initialX = 0
    private var initialY = 0

    private var selectedEmoticonPackView: EmoticonPackView? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("FloatingService", "onCreate called")
        changeIslaunched(true)
        startForegroundServiceWithNotification()
        setupFloatingView()
        //setupSecondFloatingView()  // 두 번째 플로팅 뷰 초기화
        loadInitialData()
        CoroutineScope(Dispatchers.Main).launch {
            loadLikeDate(autoClick = false)
            startCloseFloatingView()
        }
//        startCloseFloatingView()
    }

    private fun loadInitialData() {
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val jsonString = sharedPreferences.getString("emoticon_data", null)

        // jsonString이 null이 아니면 역직렬화하여 List<ImoticonPack>으로 변환
        val data = jsonString?.let {
            Json.decodeFromString<List<EmoticonPackWithEmotions>>(it)
        } ?: emptyList()

        updateFloatingView(data)
    }

    private fun loadLikeDate(autoClick: Boolean = false) {
        CoroutineScope(Dispatchers.Main).launch {
            val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val jsonString = sharedPreferences.getString("like_emoticon_data", null)

            val data = jsonString?.let {
                Json.decodeFromString<LikeEmoticonPack>(it)
            }

            likeView = secondFloatingView.findViewById(R.id.imageLike)
            val tableLayout = secondFloatingView.findViewById<TableLayout>(R.id.tableLayout)
            val titleText = secondFloatingView.findViewById<TextView>(R.id.floatingTextView)

            data?.let {
                likeView.removeAllViews()
                likeView.setupEmoticonPack(it) { images ->
                    likeView.displayImagesInTable(tableLayout, images,
                        onImageClick = { emoticon: LikeEmoticon ->
                            CoroutineScope(Dispatchers.Main).launch {
                                insertEmoticonIntoFocusedEditText(emoticon.filePath)
                            }
                        },
                        onImageLongClick = { emoticon: LikeEmoticon ->
                            deleteEmoticon(emoticon)
                        }
                    )

                    titleText.text = it.name

                    if (selectedEmoticonPackView != null) {
                        selectedEmoticonPackView!!.resetColor()
                        selectedEmoticonPackView = null
                        likeView.makeGray()
                    } else {
                        likeView.makeGray()
                    }
                }
            }
            // 모든 작업이 완료된 후 콜백 호출
            if (autoClick) {
                if (data != null) {
                    likeView.displayImagesInTable(tableLayout, data.emoticons,
                        onImageClick = { emoticon: LikeEmoticon ->
                            CoroutineScope(Dispatchers.Main).launch {
                                insertEmoticonIntoFocusedEditText(emoticon.filePath)
                            }
                        },
                        onImageLongClick = { emoticon: LikeEmoticon ->
                            deleteEmoticon(emoticon)
                        }
                    )
                }
                likeView.makeGray()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCloseFloatingView() {
        // WindowManager를 사용하여 floatingView 설정
        closeLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,  // 화면 너비에 맞춤
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        closeLayoutParams.gravity = Gravity.CENTER
        closeFloatingView = LayoutInflater.from(this).inflate(R.layout.exit_layout, null)
        // 두 번째 플로팅 뷰 설정
        // 터치 리스너 설정
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun updateFloatingView(data: List<EmoticonPackWithEmotions>) {
        // WindowManager를 사용하여 floatingView 설정
        secondLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,  // 화면 너비에 맞춤
            dpToPx(350),  // 300dp 높이
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        secondLayoutParams.gravity = Gravity.CENTER
        secondFloatingView =
            LayoutInflater.from(this).inflate(R.layout.new_compose_activity_layout, null)
        // 두 번째 플로팅 뷰 설정
        // 터치 리스너 설정
        secondFloatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX.toInt()
                    initialTouchY = event.rawY.toInt()
                    initialX = secondLayoutParams.x
                    initialY = secondLayoutParams.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    secondLayoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    secondLayoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(secondFloatingView, secondLayoutParams)
                    true
                }

                else -> false
            }
        }
        val horizontalScrollView =
            secondFloatingView.findViewById<LinearLayout>(R.id.horizontal_linear)
        val tableLayout = secondFloatingView.findViewById<TableLayout>(R.id.tableLayout)
        val closeButton = secondFloatingView.findViewById<ImageView>(R.id.closeButton)
        val settingButton = secondFloatingView.findViewById<FrameLayout>(R.id.homeBtn)
        val likeView = secondFloatingView.findViewById<LikeEmoticonPackView>(R.id.imageLike)
        val titleText = secondFloatingView.findViewById<TextView>(R.id.floatingTextView)

        closeButton.setOnClickListener {
            toggleSecondFloatingView()
        }
        settingButton.setOnClickListener {
            popupSetting()
        }

        // 데이터에 따라 UI 업데이트
        horizontalScrollView.removeAllViews()
        data.forEach { pack ->
            val emoticonPackView = EmoticonPackView(this)
            emoticonPackView.setupEmoticonPack(pack) { images ->
                emoticonPackView.displayImagesInTable(tableLayout, images,
                    onImageClick = { emoticon: Emoticon ->
                        CoroutineScope(Dispatchers.Main).launch {
                            insertEmoticonIntoFocusedEditText(emoticon.filePath)
                        }
                    },
                    onImageLongClick = { emoticon: Emoticon ->
                        lkeEmoticon(emoticon)
                    }
                )

                titleText.text = pack.emoticonPackEntity.title

                if (selectedEmoticonPackView != null) {
                    selectedEmoticonPackView!!.resetColor()
                    likeView.resetColor()
                    emoticonPackView.makeGray()
                    selectedEmoticonPackView = emoticonPackView
                } else {
                    emoticonPackView.makeGray()
                    likeView.resetColor()
                    selectedEmoticonPackView = emoticonPackView
                }
            }
            horizontalScrollView.addView(emoticonPackView)
        }
    }

    private fun popupSetting() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("isLaunched", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // FLAG_IMMUTABLE은 Android 12+에서 필요
        )
        stopSelf()
        pendingIntent.send()
    }

    private fun lkeEmoticon(emoticon: Emoticon) {
        CoroutineScope(Dispatchers.Main).launch {


            val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

            val new_jsonString = Json.encodeToString(
                LikeEmoticon(
                    filePath = emoticon.filePath,
                    title = emoticon.filePath,
                    packId = emoticon.packId
                )
            )
            val first_editor = sharedPreferences.edit()
            first_editor.putString("new_like_emoticon_data", new_jsonString)
            first_editor.apply()

//        val past_jsonString = sharedPreferences.getString("like_emoticon_data", null)
//
//        // jsonString이 null이 아니면 역직렬화하여 List<ImoticonPack>으로 변환
//        val likeEmoticonPack = past_jsonString?.let {
//            Json.decodeFromString<LikeEmoticonPack>(it)
//        }


//        likeEmoticonPack?.let {
//            val mutableImages = it.emoticons.toMutableList()  // MutableList로 변환
//            mutableImages.add(LikeEmoticon(filePath = emoticon.filePath, title = emoticon.filePath, packId = emoticon.packId))
//            it.emoticons = mutableImages.toList()  // 다시 List로 변환하여 할당
//        }

//        val editor = sharedPreferences.edit()
//        val jsonString = Json.encodeToString(likeEmoticonPack)
//        editor.putString("like_emoticon_data", jsonString)
//        editor.apply()
            Log.d("floating", "Maybe.... success,..?")
            delay(100L)
            loadLikeDate()
        }
    }


    private fun deleteEmoticon(emoticon: LikeEmoticon) {
        CoroutineScope(Dispatchers.Main).launch {
            val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

            val new_jsonString = Json.encodeToString(
                emoticon
            )
            val first_editor = sharedPreferences.edit()
            first_editor.putString("delete_like_data", new_jsonString)
            first_editor.apply()


            delay(100L)
            Log.d("floating", "Maybe.... success,..?")
            loadLikeDate(true)

        }
    }

    suspend fun insertEmoticonIntoFocusedEditText(resourceUri: String) {
        val drawable = loadImageFromUrl(resourceUri)

        if (drawable != null) {
            if (!resourceUri.endsWith(".gif")) {
                // 리소스가 정적 이미지일 경우
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)

                val uri = getImageUri(bitmap)
                if (uri != null) {
                    copyToClipboard(uri)
                    Toast.makeText(this, "이미지가 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "이미지 복사 실패", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 리소스가 GIF일 경우
                val gifUri = getGifUri(resourceUri)
                if (gifUri != null) {
                    copyToClipboard(gifUri)
                    Toast.makeText(this, "GIF가 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "GIF 복사 실패", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "이미지를 로드할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun loadImageFromUrl(resourceUri: String): Drawable? {
        return withContext(Dispatchers.IO) {
            val request =
                ImageRequest.Builder(this@FloatingService) // 서비스에서는 applicationContext를 사용
                    .data(resourceUri)
                    .allowHardware(false)
                    .build()

            val result =
                (applicationContext.imageLoader.execute(request) as? SuccessResult)?.drawable
            result
        }
    }

    suspend fun getGifUri(resourceUri: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(resourceUri) // 로컬 파일 경로
                if (!file.exists()) return@withContext null

                // FileProvider를 사용하여 URI 생성
                FileProvider.getUriForFile(this@FloatingService, "${packageName}.provider", file)
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }


    suspend fun getImageUri(bitmap: Bitmap): Uri? {
        return withContext(Dispatchers.IO) {
            val file = File(cacheDir, "emoticon.png")
            try {
                val stream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
                stream.close()
            } catch (e: IOException) {
                e.printStackTrace()
                return@withContext null
            }
            FileProvider.getUriForFile(this@FloatingService, "${packageName}.provider", file)
        }
    }

    fun copyToClipboard(uri: Uri) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(contentResolver, "Emoticon", uri)
        clipboard.setPrimaryClip(clip)
    }


    private fun startForegroundServiceWithNotification() {
        val channelId = "floating_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Floating Service Channel", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Floating Service")
            .setContentText("Floating window is running in the background")
            .setSmallIcon(R.drawable.ic_stat_emoji_emotions)
            .build()
        startForeground(1, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.LEFT

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null)


        val imageButton = floatingView.findViewById<ShapeableImageView>(R.id.imageButton1)
        imageButton.shapeAppearanceModel = imageButton.shapeAppearanceModel.toBuilder()
            .setAllCorners(CornerFamily.ROUNDED, 75f) // 원하는 크기의 반지름 설정
            .build()

        floatingView.findViewById<ShapeableImageView>(R.id.imageButton1)
            .setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isCloseViewVisible = true
                        windowManager.addView(closeFloatingView, closeLayoutParams)
                        initialTouchX = event.rawX.toInt()
                        initialTouchY = event.rawY.toInt()
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, layoutParams)
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val closeImg = closeFloatingView.findViewById<ImageView>(R.id.closeButton)

                        // 터치 움직임이 작으면 클릭으로 간주
                        if (Math.abs(event.rawX - initialTouchX) < 10 && Math.abs(event.rawY - initialTouchY) < 10) {
                            toggleSecondFloatingView()
                        } else {
                            val imageButtonRect = Rect()
                            val closeImgRect = Rect()

                            imageButton.post {
                                val locationImageButton = IntArray(2)
                                val locationCloseImg = IntArray(2)

                                imageButton.getLocationOnScreen(locationImageButton)
                                closeImg.getLocationOnScreen(locationCloseImg)

                                imageButtonRect.set(
                                    locationImageButton[0],
                                    locationImageButton[1],
                                    locationImageButton[0] + imageButton.width,
                                    locationImageButton[1] + imageButton.height
                                )

                                closeImgRect.set(
                                    locationCloseImg[0],
                                    locationCloseImg[1],
                                    locationCloseImg[0] + closeImg.width,
                                    locationCloseImg[1] + closeImg.height
                                )

                                // 충돌 여부 확인
                                val isColliding = Rect.intersects(imageButtonRect, closeImgRect)

                                if (isColliding) {
                                    changeIslaunched(false)
                                    stopSelf()
                                    Log.d("Collision", "imageButton이 closeImg와 겹칩니다.")
                                } else {
                                    Log.d("Collision", "겹치지 않습니다.")
                                }
                            }

                        }
                        isCloseViewVisible = false
                        windowManager.removeView(closeFloatingView)

                        true
                    }

                    else -> false
                }
            }


        windowManager.addView(floatingView, layoutParams)
        Log.d("FloatingService", "Floating view added successfully")
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun toggleSecondFloatingView() {
        if (isSecondViewVisible) {
            windowManager.removeView(secondFloatingView)
            Log.d("FloatingService", "Second Floating View Hidden")
        } else {
            windowManager.addView(secondFloatingView, secondLayoutParams)
            Log.d("FloatingService", "Second Floating View Shown")
        }
        isSecondViewVisible = !isSecondViewVisible
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized && floatingView.windowToken != null) {
            windowManager.removeView(floatingView)
        }
        if (isSecondViewVisible && ::secondFloatingView.isInitialized && secondFloatingView.windowToken != null) {
            windowManager.removeView(secondFloatingView)
        }

        val intent = Intent("io.ssafy.openticon.ACTION_STOP_FLOATING_SERVICE")
        sendBroadcast(intent)
        Log.d("FloatingService", "FloatingService stopped, broadcast sent")

        getSharedPreferences("ServicePrefs", Context.MODE_PRIVATE)
            .edit().putBoolean("isServiceRunning", false).apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun changeIslaunched(boolean: Boolean) {
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("is_visible", boolean) // Boolean 값으로 직접 저장
        editor.apply()
    }

}