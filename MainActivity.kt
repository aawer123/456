package com.example.thetestMusic10.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myappnamespace.R
import com.example.thetestMusic10.AudioPlayerManager
import com.example.thetestMusic10.ui.AudioItem
import com.example.thetestMusic10.ui.PrefsManager

class MainActivity : AppCompatActivity() {

    // ----------------------------------------
    // 常量區：SharedPreferences 名稱、鍵值與 Intent 額外參數
    // ----------------------------------------
    companion object {
        private const val PREFS_NAME     = "audio_permission_prefs"
        private const val KEY_ASK_COUNT  = "ask_count"
        private const val MAX_NATIVE_ASK = 2

        const val EXTRA_ID       = "EXTRA_ID"
        const val EXTRA_TITLE    = "EXTRA_TITLE"
        const val EXTRA_DURATION = "EXTRA_DURATION"
    }

    // ----------------------------------------
    // 儲存與取回權限詢問次數
    // ----------------------------------------
    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private var askCount: Int
        get() = prefs.getInt(KEY_ASK_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_ASK_COUNT, value).apply()

    // ----------------------------------------
    // UI 元件宣告
    // ----------------------------------------
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRescan: Button
    private lateinit var miniPlayer: View
    private lateinit var miniTitle: TextView
    private lateinit var miniBtn: ImageButton

    // ----------------------------------------
    // 權限請求 Launcher：Android 13+ 用 READ_MEDIA_AUDIO，舊版用 READ_EXTERNAL_STORAGE
    // ----------------------------------------
    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // 使用者同意後重置詢問次數，並首次掃描音樂
                askCount = 0
                if (!PrefsManager.hasScanned(this)) {
                    PrefsManager.setHasScanned(this, true)
                    loadAndShowList()
                }
            } else {
                // 拒絕時重新檢查流程，可能顯示理由或引導設定
                checkPermissionFlow()
            }
        }

    // ----------------------------------------
    // 設定畫面返回 Launcher：返回後再次檢查權限流程
    // ----------------------------------------
    private val goSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkPermissionFlow()
        }

    // ----------------------------------------
    // Activity 生命週期：onCreate
    // ----------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化 RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 2. 進度條與重掃按鈕
        progressBar = findViewById(R.id.progressBar)
        btnRescan   = findViewById(R.id.btnRescan)

        // 3. 迷你播放器元件
        miniPlayer = findViewById(R.id.miniPlayer)
        miniTitle  = findViewById(R.id.miniTitle)
        miniBtn    = findViewById(R.id.miniBtnPlayPause)

        // 4. 重新掃描按鈕點擊：清除掃描標記並重新載入清單
        btnRescan.setOnClickListener {
            PrefsManager.setHasScanned(this, false)
            askCount = 0
            progressBar.visibility = View.VISIBLE
            loadAndShowList()
        }

        // 5. 點擊迷你播放器區塊：開啟播放頁面
        miniPlayer.setOnClickListener {
            val uri = AudioPlayerManager.currentUri ?: return@setOnClickListener
            val audioId = uri.lastPathSegment?.toLongOrNull() ?: return@setOnClickListener
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra(EXTRA_ID, audioId)
                putExtra(EXTRA_TITLE, AudioPlayerManager.currentTitle)
                putExtra(EXTRA_DURATION, 0L)
            })
        }

        // 6. 迷你播放器播放/暫停按鈕
        miniBtn.setOnClickListener {
            if (AudioPlayerManager.isPlaying()) {
                AudioPlayerManager.pause()
                miniBtn.setImageResource(android.R.drawable.ic_media_play)
            } else {
                AudioPlayerManager.play()
                miniBtn.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        // 7. 啟動權限檢查流程
        checkPermissionFlow()
    }

    // ----------------------------------------
    // Activity 生命週期：onResume
    // ----------------------------------------
    override fun onResume() {
        super.onResume()
        refreshMiniPlayer()
    }

    // ----------------------------------------
    // 更新迷你播放器狀態：顯示或隱藏、更新標題與圖示
    // ----------------------------------------
    private fun refreshMiniPlayer() {
        if (AudioPlayerManager.currentUri != null) {
            miniPlayer.visibility = View.VISIBLE
            miniTitle.text = AudioPlayerManager.currentTitle
            miniBtn.setImageResource(
                if (AudioPlayerManager.isPlaying())
                    android.R.drawable.ic_media_pause
                else
                    android.R.drawable.ic_media_play
            )
        } else {
            miniPlayer.visibility = View.GONE
        }
    }

    // ----------------------------------------
    // 權限檢查與流程（詢問、顯示理由、引導設定）
    // ----------------------------------------
    private fun checkPermissionFlow() {
        // 根據 Android 版本選擇權限
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            android.Manifest.permission.READ_MEDIA_AUDIO
        else
            android.Manifest.permission.READ_EXTERNAL_STORAGE

        // 權限已授予
        if (ContextCompat.checkSelfPermission(this, perm) ==
            PackageManager.PERMISSION_GRANTED) {

            // 若尚未掃描，執行載入並顯示
            if (!PrefsManager.hasScanned(this)) {
                PrefsManager.setHasScanned(this, true)
                loadAndShowList()
            } else {
                // 已掃描：從快取讀取並過濾已刪除檔案
                val cached = PrefsManager.loadAudioList(this)
                val filtered = cached.filter { existsInMediaStore(it.id) }
                if (filtered.size != cached.size) {
                    PrefsManager.saveAudioList(this, filtered)
                }
                showList(filtered)
            }

        } else {
            // 權限未授予：依詢問次數決定行動
            when {
                askCount < MAX_NATIVE_ASK ->
                    if (askCount == 1) {
                        // 第二次詢問前，先顯示理由
                        showRationale(perm)
                    } else {
                        // 直接發起系統詢問
                        requestAudioPermission.launch(perm)
                        askCount++
                    }
                else ->
                    // 超過重試次數，引導至設定頁
                    showGoToSettings()
            }
        }
    }

    // ----------------------------------------
    // 顯示權限請求理由對話框
    // ----------------------------------------
    private fun showRationale(perm: String) {
        AlertDialog.Builder(this)
            .setTitle("請允許讀取權限")
            .setMessage("本應用僅用於掃描並播放您的本機音樂檔案")
            .setPositiveButton("好") { _, _ ->
                requestAudioPermission.launch(perm)
                askCount++
            }
            .setNegativeButton("取消") { _, _ ->
                finishAffinity() // 完全關閉 App
            }
            .setCancelable(false)
            .show()
    }

    // ----------------------------------------
    // 顯示引導前往設定的對話框
    // ----------------------------------------
    private fun showGoToSettings() {
        AlertDialog.Builder(this)
            .setTitle("需要手動授權")
            .setMessage("請至「設定」→「應用程式」→本應用→權限→允許 存取音樂檔案")
            .setPositiveButton("前往設定") { _, _ ->
                goSettingsLauncher.launch(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                )
            }
            .setNegativeButton("取消") { _, _ ->
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    // ----------------------------------------
    // 掃描 MediaStore 並儲存、顯示音樂清單
    // ----------------------------------------
    private fun loadAndShowList() {
        val list = mutableListOf<AudioItem>()

        // 從 MediaStore 查詢音樂檔
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION
            ),
            "${MediaStore.Audio.Media.IS_MUSIC}!=0",
            null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nmCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val duCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                list += AudioItem(
                    id          = cursor.getLong(idCol),
                    displayName = cursor.getString(nmCol),
                    duration    = cursor.getLong(duCol)
                )
            }
        }

        // 快取並顯示列表
        PrefsManager.saveAudioList(this, list)
        showList(list)
    }

    // ----------------------------------------
    // 使用 RecyclerView 顯示音樂清單
    // ----------------------------------------
    private fun showList(list: List<AudioItem>) {
        recyclerView.adapter = AudioAdapter(list) { item ->
            try {
                // 初始化並播放音樂
                AudioPlayerManager.initAndPlay(
                    this,
                    ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        item.id
                    ),
                    item.displayName
                )
                // 啟動播放頁面
                startActivity(
                    Intent(this, PlayerActivity::class.java).apply {
                        putExtra(EXTRA_ID, item.id)
                        putExtra(EXTRA_TITLE, item.displayName)
                        putExtra(EXTRA_DURATION, item.duration)
                    }
                )
            } catch (e: Exception) {
                // 若檔案不存在或播放失敗，提示重新掃描
                showRescanDialog()
            }
        }
        progressBar.visibility = View.GONE
    }

    // ----------------------------------------
    // 顯示「檔案不存在」對話框，提供重新掃描操作
    // ----------------------------------------
    private fun showRescanDialog() {
        AlertDialog.Builder(this)
            .setTitle("檔案不存在")
            .setMessage("部分音樂檔案已被刪除，請重新掃描清單。")
            .setPositiveButton("重新掃描") { _, _ ->
                btnRescan.performClick()
            }
            .setCancelable(true)
            .show()
    }

    // ----------------------------------------
    // 檢查單筆音樂是否仍存在 MediaStore
    // ----------------------------------------
    private fun existsInMediaStore(id: Long): Boolean {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            id
        )
        contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use {
            return it.moveToFirst()
        }
        return false
    }

    // ----------------------------------------
    // RecyclerView Adapter：音樂列表
    // ----------------------------------------
    inner class AudioAdapter(
        private val items: List<AudioItem>,
        private val onClick: (AudioItem) -> Unit
    ) : RecyclerView.Adapter<AudioAdapter.VH>() {

        // ViewHolder：綁定名稱與時長
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName     : TextView = view.findViewById(R.id.tvName)
            val tvDuration : TextView = view.findViewById(R.id.tvDuration)

            init {
                view.setOnClickListener {
                    onClick(items[bindingAdapterPosition])
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_audio, parent, false)
            )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.displayName
            // 時長格式化為 mm:ss
            holder.tvDuration.text =
                "%02d:%02d".format(
                    item.duration / 1000 / 60,
                    item.duration / 1000 % 60
                )
        }

        override fun getItemCount() = items.size
    }
}