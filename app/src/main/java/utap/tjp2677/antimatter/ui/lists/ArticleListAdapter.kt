package utap.tjp2677.antimatter.ui.lists

import android.graphics.*
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import utap.tjp2677.antimatter.R
import utap.tjp2677.antimatter.authentication.models.Article
import utap.tjp2677.antimatter.databinding.FeedItemBinding
import utap.tjp2677.antimatter.utils.toPx
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


open class ArticleListAdapter(val onClickCallback: (Int) -> Unit)
    : ListAdapter<Article, ArticleListAdapter.ViewHolder>(Diff()) {

    inner class ViewHolder(val articleBinding: FeedItemBinding) :
        RecyclerView.ViewHolder(articleBinding.root) {
            init {
                articleBinding.root.setOnClickListener {
                    onClickCallback(absoluteAdapterPosition)
                }
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val rowBinding = FeedItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false)
        return ViewHolder(rowBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.articleBinding

        binding.publication.text = item.publicationName
        binding.title.text = item.title

        when (item.author.isNullOrBlank()) {
            true -> {  // no author
                binding.author.isGone = true
                binding.separator.isGone = true
            }
            false -> {  // Populate author
                binding.author.isGone = false
                binding.separator.isGone = false
                binding.author.text = item.author
            }
        }

        when (item.image.isNullOrBlank()) {
            true -> {  // No image, hide
                binding.heroImage.isGone = true
            }
            false -> {
                binding.heroImage.isGone = false
                Glide.with(binding.root.context)
                    .asBitmap() // Try to display animated Gifs and video still
                    .load(item.image)
                    .fitCenter()
                    .into(binding.heroImage)
            }
        }

        /*  Handle Read Status  */
        // Dealing with colors in Kotlin:  https://material.io/blog/android-material-theme-color#:~:text=MaterialColors%C2%A0utility%20class
        val titleTextColor = when (item.isRead) {
            true -> MaterialColors.getColor(binding.root, android.R.attr.textColorHint)
            false -> MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
        }
        binding.title.setTextColor(titleTextColor)

        val secondaryTextColor = when (item.isRead) {
            true -> MaterialColors.getColor(binding.root, android.R.attr.textColorHint)
            false -> MaterialColors.getColor(binding.root, android.R.attr.textColorSecondary)
        }
        // Todo:  combine author + publication
        binding.author.setTextColor(secondaryTextColor)
        binding.publication.setTextColor(secondaryTextColor)
    }

    public override fun getItem(position: Int): Article { // Expose the internal items
        return super.getItem(position)
    }

    class Diff : DiffUtil.ItemCallback<Article>() {
        override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }

        override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean {
            return oldItem.title == newItem.title
        }
    }
}

open class ArticleItemTouchHelper(
    private val adapter: ArticleListAdapter,
    val onSwipeToStart: (Int) -> Unit,
    val onSwipeToEnd: (Int) -> Unit) : ItemTouchHelper.Callback()
{
    /* Resources:
        - https://stackoverflow.com/questions/44965278/recyclerview-itemtouchhelper-buttons-on-swipe
        - https://developer.android.com/reference/kotlin/androidx/recyclerview/widget/ItemTouchHelper.Callback#onSwiped(androidx.recyclerview.widget.RecyclerView.ViewHolder,int)
        - https://www.digitalocean.com/community/tutorials/android-recyclerview-swipe-to-delete-undo
        - https://developer.android.com/reference/androidx/constraintlayout/motion/widget/OnSwipe#getAutoCompleteMode()
        - https://codeburst.io/android-swipe-menu-with-recyclerview-8f28a235ff28
     */

    private var hapticsTriggered = false
    private var reachedMax = false
    private var activated = false

    private val buttonWidth = 80.toPx // itemView.width * 1/5f
    private val buttonBaseColor = com.google.android.material.R.attr.colorSurfaceVariant //com.google.android.material.R.attr.colorOnSurfaceVariant
    private val textBaseColor = com.google.android.material.R.attr.colorOnSurfaceVariant // Color.WHITE

    private val iconStart = mapOf(
        "add" to R.drawable.ic_twotone_bookmark_add_24, //R.drawable.ic_outline_bookmark_add_24,
        "saved" to R.drawable.ic_twotone_bookmark_added_24, //R.drawable.ic_outline_bookmark_added_24,
        "remove" to R.drawable.ic_twotone_bookmark_remove_24, //R.drawable.ic_outline_bookmark_remove_24,
    )
    private val iconEnd = R.drawable.ic_outline_done_24  // Todo: Switch to "archive"?

    private val buttonEndActivatedColor = Color.HSVToColor(floatArrayOf(92f, 0.18f, 0.88f))
    private val buttonStartActivatedColor = Color.HSVToColor(floatArrayOf(260f, 0.13f, 0.88f))
    private val textActivatedColor = com.google.android.material.R.attr.colorOnSurfaceInverse

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: ViewHolder
    ): Int {
        val swipeFlags: Int = ItemTouchHelper.START or ItemTouchHelper.END
        return makeMovementFlags(0, swipeFlags)
    }

    override fun getSwipeThreshold(viewHolder: ViewHolder): Float {
        return (buttonWidth/viewHolder.itemView.width).toFloat()
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
        Log.d("SWIPED YA!", "$direction")

        when (direction) {
            ItemTouchHelper.START -> {
                onSwipeToStart(viewHolder.absoluteAdapterPosition)
            }
            ItemTouchHelper.END -> {
                onSwipeToEnd(viewHolder.absoluteAdapterPosition)
            }
        }

        /*  Animation debugging:
        *   - https://stackoverflow.com/questions/42379660/how-to-prevent-recyclerview-item-from-blinking-after-notifyitemchangedpos
        *   - Try this?  https://stackoverflow.com/a/71153427/17370202
        *   - https://stackoverflow.com/questions/31787272/android-recyclerview-itemtouchhelper-revert-swipe-and-restore-view-holder/
        *   Maybe do this in onDraw? onDrawOver?
        */

        // start the inverse animation and reset the internal swipe state AFTERWARDS
        viewHolder.itemView
            .animate()
            .translationX(0f)
            .withEndAction {
//                 adapter.notifyItemChanged(viewHolder.absoluteAdapterPosition)  // Plays animations...
                adapter.notifyDataSetChanged()  // Inefficient, but no animation
            }
            .start()
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return defaultValue
    }

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
        return defaultValue
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val view = viewHolder.itemView
        val swipeThreshold = this.getSwipeThreshold(viewHolder)
        val maxDX = view.width * swipeThreshold

        if ((!isCurrentlyActive || hapticsTriggered) && abs(dX) < maxDX) {
            hapticsTriggered = false
            reachedMax = false
        }

        var xD = dX

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {

            xD = when(dX >= 0) {
                true -> min(dX, maxDX)
                false -> max(dX, -maxDX)
            }

//            Log.d("Displacement Magnitude", "${abs(dX)} (max: $maxDX)")

            if (abs(dX) >= maxDX && view.isHapticFeedbackEnabled && !hapticsTriggered) {
                // TODO: Try/catch
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                hapticsTriggered = true
                reachedMax = true
            }
        }

        when {
            dX < 0 -> {
                Log.v("SWIPE TO START", dX.toString())
                drawIndicator(c, viewHolder, ItemTouchHelper.START, iconEnd)
            }
            dX > 0 -> {
                Log.v("SWIPE TO END", dX.toString())
                val item = adapter.getItem(viewHolder.absoluteAdapterPosition)
                val icon = when {
                    item.isQueued && reachedMax -> iconStart["remove"]  // bookmark-
                    !item.isQueued && reachedMax -> iconStart["saved"]  // bookmark_check
                    item.isQueued && !reachedMax -> iconStart["saved"]  // bookmark_check
                    else -> iconStart["add"]  // bookmark+
                }
                drawIndicator(c, viewHolder, ItemTouchHelper.END, icon!!)
            }
            else -> c.restore()
        }

        super.onChildDraw(c, recyclerView, viewHolder, xD, dY, actionState, isCurrentlyActive)
    }

    private fun drawIndicator(c: Canvas, viewHolder: ViewHolder, direction: Int, icon: Int) {

        val itemView = viewHolder.itemView

        /*  Background  */
        val indicatorPaint = Paint()
        indicatorPaint.color = MaterialColors.getColor(viewHolder.itemView, buttonBaseColor)

        val indicator: RectF? = when (direction) {
            ItemTouchHelper.START -> { // Draw indicator at END
                if (reachedMax) { indicatorPaint.color = buttonEndActivatedColor }
                RectF(
                    (itemView.right - buttonWidth).toFloat(),
                    itemView.top.toFloat(),
                    itemView.right.toFloat(),
                    itemView.bottom.toFloat()
                )

            }
            ItemTouchHelper.END -> { // Draw indicator at START
                if (reachedMax) { indicatorPaint.color = buttonStartActivatedColor }
                RectF(
                    itemView.left.toFloat(),
                    itemView.top.toFloat(),
                    (itemView.left + buttonWidth).toFloat(),
                    itemView.bottom.toFloat()
                )
            }
            else -> null
        }
        indicator?.let { c.drawRect(indicator, indicatorPaint) }

        /*  Foreground  */
        val iconDrawable = ResourcesCompat.getDrawable(itemView.context.resources, icon, null)

        iconDrawable?.let {
            val iconColor = when (reachedMax) {
                false -> MaterialColors.getColor(viewHolder.itemView, textBaseColor)
                true -> MaterialColors.getColor(viewHolder.itemView, textActivatedColor)
            }
            it.colorFilter = BlendModeColorFilterCompat
                .createBlendModeColorFilterCompat(iconColor, BlendModeCompat.SRC_ATOP)

            val buttonHeight = itemView.bottom - itemView.top
            val iconHeight = it.intrinsicHeight*1.3
            val iconWidth = it.intrinsicWidth*1.3

            when (direction) {
                ItemTouchHelper.START -> { // Draw indicator at END
                    val iconT = (itemView.top + (buttonHeight/2 - iconHeight/2)).toInt()
                    val iconR = (itemView.right - (buttonWidth/2 - iconWidth/2)).toInt()
                    val iconB = (iconT + iconHeight).toInt()
                    val iconL = (iconR - iconWidth).toInt()
                    it.setBounds(iconL, iconT, iconR, iconB)
                }
                ItemTouchHelper.END -> { // Draw indicator at START
                    val iconL = (itemView.left + (buttonWidth/2 - iconWidth/2)).toInt()
                    val iconT = (itemView.top + (buttonHeight/2 - iconHeight/2)).toInt()
                    val iconR = (iconL + iconWidth).toInt()
                    val iconB = (iconT + iconHeight).toInt()
                    it.setBounds(iconL, iconT, iconR, iconB)
                }
            }
            it.draw(c)
        }
    }
}