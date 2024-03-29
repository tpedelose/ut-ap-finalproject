package utap.tjp2677.antimatter.ui.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import jp.wasabeef.glide.transformations.BlurTransformation
import jp.wasabeef.glide.transformations.gpu.BrightnessFilterTransformation
import utap.tjp2677.antimatter.MainViewModel
import utap.tjp2677.antimatter.R
import utap.tjp2677.antimatter.databinding.FragmentPlayerBinding
import utap.tjp2677.antimatter.utils.toDp
import kotlin.math.abs
import kotlin.math.sign


class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!  // This property is only valid between onCreateView and onDestroyView.
    private val viewModel: MainViewModel by activityViewModels()

    private val multiTransform = MultiTransformation<Bitmap>(
        BlurTransformation(30, 5),
        BrightnessFilterTransformation(0.3F)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.observeNowPlaying().observe(viewLifecycleOwner) {
            if (it == null) { return@observe }

            binding.title.text = it.title
            binding.title.isSelected = true  // To make the marquee move
            binding.publication.text = it.publicationName

            // Attempt transition between backgrounds
            val transformationFactory = DrawableCrossFadeFactory.Builder()
                .setCrossFadeEnabled(true).build()

            it.image?.let { imagePath ->
                Glide.with(binding.root.context)
                    .load(imagePath)
                    .transition(withCrossFade(transformationFactory))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .apply(RequestOptions.bitmapTransform(multiTransform))
                    .into(binding.backgroundImage)
            } ?: binding.backgroundImage.setImageDrawable(null)  // Clear background image

            when (it.publicationIconLink.isNullOrBlank()) {
                true -> { // Reset image
                    binding.foregroundImage.setImageResource(R.drawable.ic_outline_album_24)
                }
                false -> { // Set album image
                    Glide.with(binding.root.context)
                        .load(it.publicationIconLink)
                        .transition(withCrossFade(transformationFactory))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .transform(RoundedCorners (20.toDp.toInt()))
                        .into(binding.foregroundImage)
                }
            }

        }


        viewModel.observeIsPlayingStatus().observe(viewLifecycleOwner) { isPlaying ->
            if (isPlaying) {
                binding.playbackButton.setIconResource(R.drawable.ic_outline_pause_24)
                binding.playbackButton.tooltipText = "Pause"
            } else {
                binding.playbackButton.setIconResource(R.drawable.ic_outline_play_arrow_24)
                binding.playbackButton.tooltipText = "Play"
            }
        }

        binding.playbackButton.setOnClickListener {
            viewModel.getIsPlayingStatus().let {
                when (it) {
                    true -> viewModel.stopPlaying()
                    false -> viewModel.playArticle()
                }
            }
        }

        // Would have been any click on the player, but there is some funkiness with the
        // click listener when using the PlayerTouchHandler implementation
        binding.openArticleButton.setOnClickListener {
            val noNavigateClass = "utap.tjp2677.antimatter:id/article_view"
            val fragClass = findNavController().currentDestination?.displayName

            if (!viewModel.openArticleIsLoadedToPlayer()) {
                viewModel.setNowPlayingArticleAsOpenArticle()
            }

            if (fragClass != noNavigateClass) {  // TODO:  There must be a better way...
                // Navigate to article
                viewModel.setNowPlayingArticleAsOpenArticle()
                val navOps = NavOptions.Builder()
                    .setEnterAnim(androidx.navigation.ui.R.anim.nav_default_enter_anim)
                    .setExitAnim(androidx.navigation.ui.R.anim.nav_default_exit_anim)
                    .setPopEnterAnim(androidx.navigation.ui.R.anim.nav_default_pop_enter_anim)
                    .setPopExitAnim(androidx.navigation.ui.R.anim.nav_default_pop_exit_anim)
                    .build()
                findNavController().navigate(R.id.article_view, null, navOps)
            }
        }


        // Must include this to keep click-through to behind view
        // and to capture motion events other than ACTION_DOWN
        binding.root.setOnClickListener {

        }

        // Handle swipe gestures on root view
        binding.root.setOnTouchListener(
            PlayerTouchHandler {
                viewModel.stopPlaying()
                val frag = parentFragmentManager.findFragmentById(R.id.fragment_player) as? PlayerFragment
                if (frag != null) {
                    parentFragmentManager.commit {
                        remove(frag)
                        setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                        viewModel.setPlayerIsActive(false)
                    }
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

enum class SwipeDirection(i: Int) {
    START(-1),
    END(1)
}

class PlayerTouchHandler(val onSwipeCallback: ((direction: SwipeDirection) -> Unit)? = null)
    : View.OnTouchListener {
    // Similar to touch listener in recycler view
    // https://developer.android.com/reference/android/view/View.OnTouchListener

    val TAG = "PlayerTouchListener"

    private var x0: Float = 0f
    private var y0: Float = 0f
    private var viewX0: Float = 0f
    private var viewY0: Float = 0f

    private var swipeThreshold: Float = 0.45f
    private var isSwiped: Boolean = false
    private var isSwipedDirection: SwipeDirection = SwipeDirection.START
    private var hapticsTriggered = false

    private var xDisplacementBuffer = 24.toDp
    private var yDisplacementBuffer = 24.toDp

    private var parentWidth: Float = 0f

    // TODO: Check out MotionEvent to see if we can use other things like history:
    // https://developer.android.com/reference/android/view/MotionEvent

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        Log.d(TAG, "$event ; ${event.action}")

        var dX = 0f
        var dY = 0f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Set initial position for displacement
                x0 = event.rawX
                y0 = event.rawY
                viewX0 = view.x
                viewY0 = view.y
                parentWidth = (view.parent as View).width.toFloat()
            }

            MotionEvent.ACTION_UP -> {

                // Reset initial position
                x0 = 0f
                y0 = 0f
                viewX0 = 0f
                viewY0 = 0f

                var xTarget = 0f  // Return to start
                if (isSwiped) {
                    // Exit in direction of swipe
                    xTarget = when (isSwipedDirection) {
                        SwipeDirection.START -> -parentWidth
                        SwipeDirection.END -> parentWidth
                    }
                }
                else if (abs(dX) < xDisplacementBuffer){
                    // Consider it a click
                    view.performClick()
                }

                view.animate()
                    .translationX(xTarget)
                    .withEndAction {
                        // Callback on swipe out
                        if (isSwiped) {
                            onSwipeCallback?.let { it(isSwipedDirection) }
                        }
                    }
                    .start()
            }

            MotionEvent.ACTION_MOVE -> {

                dX = event.rawX - x0
                if (abs(dX) > xDisplacementBuffer) {
                    view.x = viewX0 + dX - sign(dX) * xDisplacementBuffer
                }

                // Enable for up/down
//                dY = event.rawY - y0!!
//                view.y = viewY0?.plus(dY)!!

                Log.v(TAG, "dX: $dX; dY: $dY")

                // Check for swipe threshold
                if (abs(dX) >= view.width * swipeThreshold) {
                    // User has swiped past threshold to dismiss
                    isSwiped = true
                    isSwipedDirection = when (dX < 0) {
                        true -> SwipeDirection.START
                        false -> SwipeDirection.END
                    }
                    Log.d(TAG, "$dX >= ${view.width} * $swipeThreshold :: ${isSwipedDirection.name}")

                    if (view.isHapticFeedbackEnabled && !hapticsTriggered) {
                        // Let user know that it dismiss will trigger
                        view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                        hapticsTriggered = true
                    }
                } else if (isSwiped) {
                    // User has swiped back to under threshold
                    isSwiped = false
                    isSwipedDirection = SwipeDirection.START
                    if (view.isHapticFeedbackEnabled && hapticsTriggered) {
                        // Let user know that dismiss won't trigger
                        view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                    }
                    hapticsTriggered = false
                }
            }
        }

        return false
    }

    fun getSwipeThreshold () {
        TODO()
    }

}
