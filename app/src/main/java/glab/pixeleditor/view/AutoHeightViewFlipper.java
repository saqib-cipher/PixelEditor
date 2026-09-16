package glab.pixeleditor.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ViewFlipper;

/**
 * A ViewFlipper that dynamically calculates its height based solely on the
 * currently displayed child view, rather than taking the maximum height of all children.
 * This prevents blank empty gaps at the bottom of shorter panels.
 */
public class AutoHeightViewFlipper extends ViewFlipper {

    public AutoHeightViewFlipper(Context context) {
        super(context);
    }

    public AutoHeightViewFlipper(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setDisplayedChild(int whichChild) {
        super.setDisplayedChild(whichChild);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int displayedChild = getDisplayedChild();
        View child = getChildAt(displayedChild);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (heightMode == MeasureSpec.EXACTLY) {
            // When parent imposes an exact height (e.g. expanded dock taking 58% screen),
            // pass the exact child height so lists and dynamic panels can expand to fill the full area.
            if (child != null && child.getVisibility() != GONE) {
                int childHeightSpec = MeasureSpec.makeMeasureSpec(
                        Math.max(0, heightSize - getPaddingTop() - getPaddingBottom()),
                        MeasureSpec.EXACTLY
                );
                measureChildWithMargins(child, widthMeasureSpec, 0, childHeightSpec, 0);
            }
            int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, heightSize);
        } else if (child != null && child.getVisibility() != GONE) {
            measureChildWithMargins(child, widthMeasureSpec, 0, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
            int childHeight = child.getMeasuredHeight();
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp instanceof MarginLayoutParams) {
                MarginLayoutParams mlp = (MarginLayoutParams) lp;
                childHeight += mlp.topMargin + mlp.bottomMargin;
            }
            int height = childHeight + getPaddingTop() + getPaddingBottom();
            int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, height);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
