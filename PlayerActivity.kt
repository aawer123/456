package com.example.thetestMusic10.ui

import android.animation.*
import android.annotation.SuppressLint
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.thetestMusic10.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.MediaMetadataRetriever
import android.util.DisplayMetrics
import android.util.Log
import android.view.animation.LinearInterpolator
import com.example.myappnamespace.R
import kotlinx.coroutines.delay

class PlayerActivity : AppCompatActivity() {

    private var titleAnimator: ObjectAnimator? = null
    private var miniTitleAnimator: ObjectAnimator? = null

    private lateinit var ivAlbumArt: ImageView
    private lateinit var pbCoverLoading: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var tvDuration: TextView

    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var miniPlayer: View
    private lateinit var miniIv: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniBtn: ImageButton

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // —— 主播放器视图绑定 ——
        ivAlbumArt     = findViewById(R.id.ivAlbumArt)
        pbCoverLoading = findViewById(R.id.pbCoverLoading)
        tvTitle        = findViewById(R.id.tvTitle)
        tvDuration     = findViewById(R.id.tvDuration)

        // —— Mini Player 视图绑定 ——
        btnPrev    = findViewById(R.id.btnPrev)
        btnNext    = findViewById(R.id.btnNext)
        miniPlayer = findViewById(R.id.miniPlayer)
        miniIv     = findViewById(R.id.miniIv)
        miniTitle  = findViewById(R.id.miniTitle)
        miniBtn    = findViewById(R.id.miniBtnPlayPause)

        // 从 Intent 拿到初始数据
        val audioId  = intent.getLongExtra(MainActivity.EXTRA_ID, -1L)
        val titleStr = intent.getStringExtra(MainActivity.EXTRA_TITLE) ?: "未知曲目"
        val duration = intent.getLongExtra(MainActivity.EXTRA_DURATION, 0L)

        // 设置文字和时间
        tvTitle.text   = titleStr
        miniTitle.text = titleStr
        tvDuration.text = String.format(
            "%02d:%02d",
            duration / 1000 / 60,
            duration / 1000 % 60
        )

        // Play/Pause 图标
        updateMiniBtnIcon()
        miniBtn.setOnClickListener { togglePlayPause() }

        // 上一首/下一首
        btnPrev.setOnClickListener {
            AudioPlayerManager.playPrevious(this)
            updateMiniInfo()
            restartMarquee()
        }
        btnNext.setOnClickListener {
            AudioPlayerManager.playNext(this)
            updateMiniInfo()
            restartMarquee()
        }

        // 载入封面
        loadEmbeddedAlbumArt(audioId)

        // 启动两个跑马灯动画，完全一致
        startMarqueeFromCenter(
            tv       = tvTitle,
            prevAnim = titleAnimator,
            setAnim  = { titleAnimator = it }
        )
        startMarqueeFromCenter(
            tv       = miniTitle,
            prevAnim = miniTitleAnimator,
            setAnim  = { miniTitleAnimator = it }
        )
    }

    override fun onDestroy() {
        titleAnimator?.cancel()
        miniTitleAnimator?.cancel()
        super.onDestroy()
    }

    private fun togglePlayPause() {
        if (AudioPlayerManager.isPlaying()) AudioPlayerManager.pause()
        else AudioPlayerManager.play()
        updateMiniBtnIcon()
    }

    private fun updateMiniBtnIcon() {
        miniBtn.setImageResource(
            if (AudioPlayerManager.isPlaying())
                android.R.drawable.ic_media_pause
            else
                android.R.drawable.ic_media_play
        )
    }

    private fun updateMiniInfo() {
        val current = AudioPlayerManager.currentTitle
        miniTitle.text = current
        tvTitle.text   = current
        updateMiniBtnIcon()

        AudioPlayerManager.currentUri
            ?.lastPathSegment
            ?.toLongOrNull()
            ?.let { loadEmbeddedAlbumArt(it) }

        restartMarquee()
    }

    private fun restartMarquee() {
        titleAnimator?.cancel()
        miniTitleAnimator?.cancel()
        tvTitle.clearAnimation()
        miniTitle.clearAnimation()

        // 重新启动，两个标题完全同速
        startMarqueeFromCenter(tvTitle,  titleAnimator,     { titleAnimator = it })
        startMarqueeFromCenter(miniTitle, miniTitleAnimator, { miniTitleAnimator = it })
    }

    private fun loadEmbeddedAlbumArt(audioId: Long) {
        pbCoverLoading.visibility = View.VISIBLE
        ivAlbumArt.visibility     = View.INVISIBLE
        miniIv.setImageResource(R.drawable.picsongbg)

        val contentUri: Uri = ContentUris.withAppendedId(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            audioId
        )

        lifecycleScope.launchWhenCreated {
            val embedded = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(this@PlayerActivity, contentUri)
                    retriever.embeddedPicture
                } catch (_: Exception) {
                    null
                } finally {
                    retriever.release()
                }
            }

            if (embedded != null) {
                val bmp = BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
                ivAlbumArt.setImageBitmap(bmp)
                miniIv.setImageBitmap(bmp)
                ivAlbumArt.visibility = View.VISIBLE
                pbCoverLoading.visibility = View.GONE
            } else {
                Glide.with(this@PlayerActivity)
                    .asBitmap()
                    .load(R.drawable.picsongbg)
                    .apply(RequestOptions().downsample(DownsampleStrategy.CENTER_INSIDE))
                    .into(ivAlbumArt)
                ivAlbumArt.visibility = View.VISIBLE
                pbCoverLoading.visibility = View.GONE

                Glide.with(this@PlayerActivity)
                    .asGif()
                    .load(R.drawable.picsongbg)
                    .into(miniIv)
            }
        }
    }

    /**
     * 跑马灯 + 淡入淡出 + 无缝循环
     *
     * @param tv       目标 TextView
     * @param prevAnim 上一轮 Animator 引用，用来 cancel
     * @param setAnim  将新 Animator 回写到对应变量
     */
    private fun startMarqueeFromCenter(
        tv: TextView,
        prevAnim: ObjectAnimator?,
        setAnim: (ObjectAnimator?) -> Unit,
        speedPxPerSec: Float = 80f,
        bias: Float = 0.05f
    ) {
        tv.post {
            val tvW    = tv.width.toFloat()
            val layout = tv.layout ?: return@post
            val textW  = layout.getLineWidth(0)
            val rawX   = (tvW - textW) * bias
            val startX = if (textW > tvW) 0f else rawX.coerceAtLeast(0f)
            val endX   = -textW - startX - 1f

            val distance  = startX - endX
            val duration  = (distance / speedPxPerSec * 1000).toLong()
            val fadeInMs  = 1600L
            val fadeOutMs = 1500L
            val tInFrac   = (fadeInMs.toFloat()  / duration).coerceIn(0f, 1f)
            val tOutFrac  = ((duration - fadeOutMs).toFloat() / duration).coerceIn(0f, 1f)

            prevAnim?.cancel()
            tv.translationX = startX
            tv.alpha        = 0f

            val pvhTrans = PropertyValuesHolder.ofFloat(View.TRANSLATION_X, startX, endX)
            val pvhAlpha = PropertyValuesHolder.ofKeyframe(
                View.ALPHA,
                Keyframe.ofFloat(0f,       0f),
                Keyframe.ofFloat(tInFrac,  1f),
                Keyframe.ofFloat(tOutFrac, 1f),
                Keyframe.ofFloat(1f,       0f)
            )

            val animator = ObjectAnimator.ofPropertyValuesHolder(tv, pvhTrans, pvhAlpha).apply {
                this.duration     = duration
                this.interpolator = LinearInterpolator()
                this.repeatCount  = ValueAnimator.INFINITE
                this.repeatMode   = ValueAnimator.RESTART
                start()
            }

            setAnim(animator)
        }
    }
}