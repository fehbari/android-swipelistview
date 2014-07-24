/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fortysevendeg.swipelistview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * The dynamic listview is an extension of SwipeListView that supports
 * cell dragging, swapping and swiping.
 * <p/>
 * This layout is in charge of positioning the hover cell in the correct location
 * on the screen in response to user touch events. It uses the position of the
 * hover cell to determine when two cells should be swapped. If two cells should
 * be swapped, all the corresponding data set and layout changes are handled here.
 * <p/>
 * If no cell is selected, all the touch events are passed down to the listview
 * and behave normally. If one of the items in the listview experiences a
 * long press event, the contents of its current visible state are captured as
 * a bitmap and its visibility is set to INVISIBLE. A hover cell is then created and
 * added to this layout as an overlaying BitmapDrawable above the listview. Once the
 * hover cell is translated some distance to signify an item swap, a data set change
 * accompanied by animation takes place. When the user releases the hover cell,
 * it animates into its corresponding position in the listview.
 * <p/>
 * When the hover cell is either above or below the bounds of the listview, this
 * listview also scrolls on its own so as to reveal additional content.
 */
public class DynamicListView extends SwipeListView {

    private WeakReference<Context> mContext;

    private final int SMOOTH_SCROLL_AMOUNT_AT_EDGE = 15;
    private final int MOVE_DURATION = 150;
    private final int LINE_THICKNESS = 15;
    private final float BITMAP_SCALE = 0.9f;

    public List mContentList;

    private int mLastEventY = -1;

    private int mDownY = -1;
    private int mDownX = -1;

    private int mPointerIndex;

    private int mTotalOffset = 0;

    private boolean mCellIsMobile = false;
    private boolean mIsMobileScrolling = false;
    private int mSmoothScrollAmountAtEdge = 0;

    private final int INVALID_ID = -1;
    private long mAboveItemId = INVALID_ID;
    private long mMobileItemId = INVALID_ID;
    private long mBelowItemId = INVALID_ID;

    private BitmapDrawable mHoverCell;
    private Rect mHoverCellCurrentBounds;
    private Rect mHoverCellOriginalBounds;
    private View mMobileView;

    private final int INVALID_POINTER_ID = -1;
    private int mActivePointerId = INVALID_POINTER_ID;

    private boolean mIsWaitingForScrollFinish = false;
    private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;

    private boolean mDragAndDropEnabled = false;
    private boolean mIsDragAndDropping = false;

    private ListOrderListener mListOrderListener;

    private boolean mHasPerformedLongPress;
    private Runnable mPendingCheckForLongPress;

    public DynamicListView(Context context, int swipeBackView, int swipeFrontView, int swipeBackIconLeft, int swipeBackIconRight) {
        super(context, swipeBackView, swipeFrontView, swipeBackIconLeft, swipeBackIconRight);
        init(context);
    }

    public DynamicListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public DynamicListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void init(Context context) {
        setOnScrollListener(mScrollListener);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mSmoothScrollAmountAtEdge = (int) (SMOOTH_SCROLL_AMOUNT_AT_EDGE / metrics.density);
        mContext = new WeakReference<Context>(context);
    }

    /**
     * Sets background colors for swipe gestures.
     *
     * @param rightColor Right swipe gesture color.
     * @param leftColor  Left swipe gesture color.
     */
    public void setSwipeBackgroundColors(int rightColor, int leftColor, int neutralColor) {
        getTouchListener().setRightBackgroundColor(rightColor);
        getTouchListener().setLeftBackgroundColor(leftColor);
        getTouchListener().setNeutralBackgroundColor(neutralColor);
    }

    /**
     * Sets background colors for long swipe gestures.
     *
     * @param rightLongColor Long right swipe gesture color.
     * @param leftLongColor  Long left swipe gesture color.
     */
    public void setLongSwipeBackgroundColors(int rightLongColor, int leftLongColor) {
        getTouchListener().setLongRightBackgroundColor(rightLongColor);
        getTouchListener().setLongLeftBackgroundColor(leftLongColor);
    }

    /**
     * Sets the icon font text resources for the back icon when short swiping.
     *
     * @param rightText Resource for the right swipe icon.
     * @param leftText  Resource for the left swipe icon.
     */
    public void setBackIconText(int rightText, int leftText) {
        getTouchListener().setBackIconRightText(rightText);
        getTouchListener().setBackIconLeftText(leftText);
    }

    /**
     * Sets the icon font text resources for the back icon when long swiping.
     *
     * @param longRightText Resource for the long right swipe icon.
     * @param longLeftText  Resource for the long left swipe icon.
     */
    public void setLongSwipeBackIconText(int longRightText, int longLeftText) {
        getTouchListener().setBackIconLongRightText(longRightText);
        getTouchListener().setBackIconLongLeftText(longLeftText);
    }

    /**
     * Set the front view icon.
     *
     * @param frontIcon Icon to set.
     */
    public void setFrontIcon(int frontIcon) {
        getTouchListener().setSwipeFrontIcon(frontIcon);
    }

    /**
     * Sets background resources for front icon during swipe gestures.
     *
     * @param rightBackground   Right swipe gesture background.
     * @param leftBackground    Left swipe gesture background.
     * @param defaultBackground Default background.
     */
    public void setFrontIconBackgrounds(int rightBackground, int leftBackground, int defaultBackground) {
        getTouchListener().setFrontIconRightBackground(rightBackground);
        getTouchListener().setFrontIconLeftBackground(leftBackground);
        getTouchListener().setFrontIconBackground(defaultBackground);
    }

    /**
     * Sets background resources for front icon during long swipe gestures.
     *
     * @param longRightBackground Long right swipe gesture background.
     * @param longLeftBackground  Long left swipe gesture background.
     */
    public void setFrontIconLongSwipeBackgrounds(int longRightBackground, int longLeftBackground) {
        getTouchListener().setFrontIconLongRightBackground(longRightBackground);
        getTouchListener().setFrontIconLongLeftBackground(longLeftBackground);
    }

    /**
     * Enables or disables long swipes in the list.
     *
     * @param enabled True to enable, false otherwise.
     */
    public void setLongSwipeEnabled(boolean enabled) {
        getTouchListener().setLongSwipeEnabled(enabled);
    }

    /**
     * Enables or disables drag and drop in the list.
     *
     * @param enabled True to enable, false otherwise.
     */
    public void setDragAndDropEnabled(boolean enabled) {
        mDragAndDropEnabled = enabled;
    }

    /**
     * Sets the listener for list reordering.
     *
     * @param listener Listener to set.
     */
    public void setListOrderListener(ListOrderListener listener) {
        mListOrderListener = listener;
    }

    /**
     * Determines if a long press has been performed.
     *
     * @return True if it has been.
     */
    public boolean hasPerformedLongPress() {
        return mHasPerformedLongPress;
    }

    /**
     * Starts the drag and drop flow. When a cell has
     * been selected, the hover cell is created and set up.
     */
    private void startDragAndDrop() {
        if (mDragAndDropEnabled) {
            mIsDragAndDropping = true;

            mTotalOffset = 0;

            int position = pointToPosition(mDownX, mDownY);
            int itemNum = position - getFirstVisiblePosition();

            View selectedView = getChildAt(itemNum);
            mMobileItemId = getAdapter().getItemId(position);
            mMobileView = getViewForID(mMobileItemId);
            mHoverCell = getAndAddScaledHoverView(selectedView);
            selectedView.setVisibility(INVISIBLE);

            mCellIsMobile = true;

            updateNeighborViewsForID(mMobileItemId);
        }
    }

    /**
     * Creates the scaled hover cell with the appropriate bitmap and of appropriate
     * size. The hover cell's BitmapDrawable is drawn on top of the scaled bitmap every
     * single time an invalidate call is made.
     */
    private BitmapDrawable getAndAddScaledHoverView(View v) {

        int w = v.getWidth();
        int h = v.getHeight();
        int top = v.getTop();
        int left = v.getLeft();

        // TODO: Figure out why we need an arbitrary value of 0.9f (could be a miscalculation of bounds. The original code was never prepared for scaled bitmaps).
        Bitmap b = getScaledBitmapFromView(v, BITMAP_SCALE);

        BitmapDrawable drawable = new BitmapDrawable(getResources(), b);

        mHoverCellOriginalBounds = new Rect(left, top, left + w, top + h);
        mHoverCellCurrentBounds = new Rect(mHoverCellOriginalBounds);

        drawable.setBounds(mHoverCellCurrentBounds);

        return drawable;
    }

    /**
     * Returns a scaled bitmap showing a screenshot of the view passed in.
     */
    private Bitmap getScaledBitmapFromView(View v, float scale) {
        int width = Math.round(v.getWidth() * scale);
        int height = Math.round(v.getHeight() * scale);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        v.draw(canvas);

        return bitmap;
    }

    /**
     * Stores a reference to the views above and below the item currently
     * corresponding to the hover cell. It is important to note that if this
     * item is either at the top or bottom of the list, mAboveItemId or mBelowItemId
     * may be invalid.
     */
    private void updateNeighborViewsForID(long itemID) {
        int position = getPositionForID(itemID);
        mAboveItemId = getAdapter().getItemId(position - 1);
        mBelowItemId = getAdapter().getItemId(position + 1);
    }

    /**
     * Retrieves the view in the list corresponding to itemID
     */
    public View getViewForID(long itemID) {
        int firstVisiblePosition = getFirstVisiblePosition();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int position = firstVisiblePosition + i;
            long id = getAdapter().getItemId(position);
            if (id == itemID) {
                return v;
            }
        }
        return null;
    }

    /**
     * Retrieves the position in the list corresponding to itemID
     */
    public int getPositionForID(long itemID) {
        View v = getViewForID(itemID);
        if (v == null) {
            return -1;
        } else {
            return getPositionForView(v);
        }
    }

    /**
     * dispatchDraw gets invoked when all the child views are about to be drawn.
     * By overriding this method, the hover cell (BitmapDrawable) can be drawn
     * over the listview's items whenever the listview is redrawn.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mHoverCell != null) {
            mHoverCell.draw(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                mDownX = (int) event.getX();
                mDownY = (int) event.getY();
                mActivePointerId = event.getPointerId(0);

                if (mPendingCheckForLongPress == null && mDragAndDropEnabled) {
                    mPendingCheckForLongPress = new Runnable() {
                        public void run() {
                            if (!getTouchListener().isSwiping()) {
                                mHasPerformedLongPress = true;
                                startDragAndDrop();
                            }
                        }
                    };
                }

                mHasPerformedLongPress = false;
                postDelayed(mPendingCheckForLongPress, ViewConfiguration.getLongPressTimeout());
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                float deltaModeX = Math.abs(event.getX() - mDownX);
                float deltaModeY = Math.abs(event.getY() - mDownY);

                // Be lenient about moving finger.
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (deltaModeX > slop || deltaModeY > slop) {
                    if (mPendingCheckForLongPress != null) {
                        removeCallbacks(mPendingCheckForLongPress);
                    }
                }

                if (mActivePointerId == INVALID_POINTER_ID) {
                    break;
                }

                mPointerIndex = event.findPointerIndex(mActivePointerId);

                mLastEventY = (int) event.getY(mPointerIndex);
                int deltaY = mLastEventY - mDownY;

                if (mCellIsMobile) {
                    mHoverCellCurrentBounds.offsetTo(mHoverCellOriginalBounds.left,
                            mHoverCellOriginalBounds.top + deltaY + mTotalOffset);
                    mHoverCell.setBounds(mHoverCellCurrentBounds);
                    invalidate();

                    handleCellSwitch();

                    mIsMobileScrolling = false;
                    handleMobileCellScroll();

                    return false;
                }
                break;
            }

            case MotionEvent.ACTION_UP: {
                touchEventsEnded();
                if (mIsDragAndDropping) {
                    mListOrderListener.listReordered(mContentList);
                }
                mIsDragAndDropping = false;

                if (!mHasPerformedLongPress) {
                    // This is a tap, so remove the long press check.
                    if (mPendingCheckForLongPress != null) {
                        removeCallbacks(mPendingCheckForLongPress);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                touchEventsCancelled();
                mIsDragAndDropping = false;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                /* If a multitouch event took place and the original touch dictating
                 * the movement of the hover cell has ended, then the dragging event
                 * ends and the hover cell is animated to its corresponding position
                 * in the listview. */
                mPointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                        MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = event.getPointerId(mPointerIndex);
                if (pointerId == mActivePointerId) {
                    touchEventsEnded();
                }
                mIsDragAndDropping = false;
                break;
            }
        }
        return super.onTouchEvent(event);
    }

    /**
     * This method determines whether the hover cell has been shifted far enough
     * to invoke a cell swap. If so, then the respective cell swap candidate is
     * determined and the data set is changed. Upon posting a notification of the
     * data set change, a layout is invoked to place the cells in the right place.
     * Using a ViewTreeObserver and a corresponding OnPreDrawListener, we can
     * offset the cell being swapped to where it previously was and then animate it to
     * its new position.
     */
    private void handleCellSwitch() {
        final int deltaY = mLastEventY - mDownY;
        int deltaYTotal = mHoverCellOriginalBounds.top + mTotalOffset + deltaY;

        View belowView = getViewForID(mBelowItemId);
        mMobileView = getViewForID(mMobileItemId);
        View aboveView = getViewForID(mAboveItemId);

        boolean isBelow = (belowView != null) && (deltaYTotal > belowView.getTop());
        boolean isAbove = (aboveView != null) && (deltaYTotal < aboveView.getTop());

        if (isBelow || isAbove) {

            final long switchItemID = isBelow ? mBelowItemId : mAboveItemId;
            View switchView = isBelow ? belowView : aboveView;
            final int originalItem = getPositionForView(mMobileView);

            if (switchView == null) {
                updateNeighborViewsForID(mMobileItemId);
                return;
            }

            swapElements(mContentList, originalItem, getPositionForView(switchView));

            ((BaseAdapter) getAdapter()).notifyDataSetChanged();

            mDownY = mLastEventY;

            final int switchViewStartTop = switchView.getTop();

            mMobileView.setVisibility(View.VISIBLE);
            switchView.setVisibility(View.INVISIBLE);

            updateNeighborViewsForID(mMobileItemId);

            final ViewTreeObserver observer = getViewTreeObserver();
            observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() {
                    observer.removeOnPreDrawListener(this);

                    View switchView = getViewForID(switchItemID);

                    mTotalOffset += deltaY;

                    int switchViewNewTop = switchView.getTop();
                    int delta = switchViewStartTop - switchViewNewTop;

                    switchView.setTranslationY(delta);

                    ObjectAnimator animator = ObjectAnimator.ofFloat(switchView,
                            View.TRANSLATION_Y, 0);
                    animator.setDuration(MOVE_DURATION);
                    animator.start();

                    return true;
                }
            });
        }
    }

    private void swapElements(List list, int indexOne, int indexTwo) {
        Object temp = list.get(indexOne);
        list.set(indexOne, list.get(indexTwo));
        list.set(indexTwo, temp);
    }

    /**
     * Resets all the appropriate fields to a default state while also animating
     * the hover cell back to its correct location.
     */
    private void touchEventsEnded() {
        if (mCellIsMobile || mIsWaitingForScrollFinish) {
            mCellIsMobile = false;
            mIsWaitingForScrollFinish = false;
            mIsMobileScrolling = false;
            mActivePointerId = INVALID_POINTER_ID;

            // If the autoscroller has not completed scrolling, we need to wait for it to
            // finish in order to determine the final location of where the hover cell
            // should be animated to.
            if (mScrollState != OnScrollListener.SCROLL_STATE_IDLE) {
                // TODO: Check if this flag is causing the "frozen" cell bug.
                mIsWaitingForScrollFinish = true;
                return;
            }

            mHoverCellCurrentBounds.offsetTo(mHoverCellOriginalBounds.left, mMobileView.getTop());

            ObjectAnimator hoverViewAnimator = ObjectAnimator.ofObject(mHoverCell, "bounds",
                    sBoundEvaluator, mHoverCellCurrentBounds);
            hoverViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    invalidate();
                }
            });
            hoverViewAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    setEnabled(false);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mAboveItemId = INVALID_ID;
                    mMobileItemId = INVALID_ID;
                    mBelowItemId = INVALID_ID;
                    mMobileView.setVisibility(VISIBLE);
                    mHoverCell = null;
                    setEnabled(true);
                    invalidate();
                }
            });
            hoverViewAnimator.start();
        } else {
            touchEventsCancelled();
        }
    }

    /**
     * Resets all the appropriate fields to a default state.
     */
    private void touchEventsCancelled() {
        if (mCellIsMobile) {
            mAboveItemId = INVALID_ID;
            mMobileItemId = INVALID_ID;
            mBelowItemId = INVALID_ID;
            mMobileView.setVisibility(VISIBLE);
            mHoverCell = null;
            invalidate();
        }
        mCellIsMobile = false;
        mIsMobileScrolling = false;
        mActivePointerId = INVALID_POINTER_ID;
    }

    /**
     * This TypeEvaluator is used to animate the BitmapDrawable back to its
     * final location when the user lifts his finger by modifying the
     * BitmapDrawable's bounds.
     */
    private final static TypeEvaluator<Rect> sBoundEvaluator = new TypeEvaluator<Rect>() {
        public Rect evaluate(float fraction, Rect startValue, Rect endValue) {
            return new Rect(interpolate(startValue.left, endValue.left, fraction),
                    interpolate(startValue.top, endValue.top, fraction),
                    interpolate(startValue.right, endValue.right, fraction),
                    interpolate(startValue.bottom, endValue.bottom, fraction));
        }

        public int interpolate(int start, int end, float fraction) {
            return (int) (start + fraction * (end - start));
        }
    };

    /**
     * Determines whether this listview is in a scrolling state invoked
     * by the fact that the hover cell is out of the bounds of the listview;
     */
    private void handleMobileCellScroll() {
        mIsMobileScrolling = handleMobileCellScroll(mHoverCellCurrentBounds);
    }

    /**
     * This method is in charge of determining if the hover cell is above
     * or below the bounds of the listview. If so, the listview does an appropriate
     * upward or downward smooth scroll so as to reveal new items.
     */
    public boolean handleMobileCellScroll(Rect r) {
        int offset = computeVerticalScrollOffset();
        int height = getHeight();
        int extent = computeVerticalScrollExtent();
        int range = computeVerticalScrollRange();
        int hoverViewTop = r.top;
        int hoverHeight = r.height();

        if (hoverViewTop <= 0 && offset > 0) {
            smoothScrollBy(-mSmoothScrollAmountAtEdge, 0);
            return true;
        }

        if (hoverViewTop + hoverHeight >= height && (offset + extent) < range) {
            smoothScrollBy(mSmoothScrollAmountAtEdge, 0);
            return true;
        }

        return false;
    }

    public void setContentList(List contentList) {
        mContentList = contentList;
    }

    /**
     * This scroll listener is added to the listview in order to handle cell swapping
     * when the cell is either at the top or bottom edge of the listview. If the hover
     * cell is at either edge of the listview, the listview will begin scrolling. As
     * scrolling takes place, the listview continuously checks if new cells became visible
     * and determines whether they are potential candidates for a cell swap.
     */
    private AbsListView.OnScrollListener mScrollListener = new AbsListView.OnScrollListener() {

        private int mPreviousFirstVisibleItem = -1;
        private int mPreviousVisibleItemCount = -1;
        private int mCurrentFirstVisibleItem;
        private int mCurrentVisibleItemCount;
        private int mCurrentScrollState;

        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                             int totalItemCount) {
            mCurrentFirstVisibleItem = firstVisibleItem;
            mCurrentVisibleItemCount = visibleItemCount;

            mPreviousFirstVisibleItem = (mPreviousFirstVisibleItem == -1) ? mCurrentFirstVisibleItem
                    : mPreviousFirstVisibleItem;
            mPreviousVisibleItemCount = (mPreviousVisibleItemCount == -1) ? mCurrentVisibleItemCount
                    : mPreviousVisibleItemCount;

            checkAndHandleFirstVisibleCellChange();
            checkAndHandleLastVisibleCellChange();

            mPreviousFirstVisibleItem = mCurrentFirstVisibleItem;
            mPreviousVisibleItemCount = mCurrentVisibleItemCount;
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            mCurrentScrollState = scrollState;
            mScrollState = scrollState;
            isScrollCompleted();
        }

        /**
         * This method is in charge of invoking 1 of 2 actions. Firstly, if the listview
         * is in a state of scrolling invoked by the hover cell being outside the bounds
         * of the listview, then this scrolling event is continued. Secondly, if the hover
         * cell has already been released, this invokes the animation for the hover cell
         * to return to its correct position after the listview has entered an idle scroll
         * state.
         */
        private void isScrollCompleted() {
            if (mCurrentVisibleItemCount > 0 && mCurrentScrollState == SCROLL_STATE_IDLE) {
                if (mCellIsMobile && mIsMobileScrolling) {
                    handleMobileCellScroll();
                } else if (mIsWaitingForScrollFinish) {
                    touchEventsEnded();
                }
            }
        }

        /**
         * Determines if the listview scrolled up enough to reveal a new cell at the
         * top of the list. If so, then the appropriate parameters are updated.
         */
        public void checkAndHandleFirstVisibleCellChange() {
            if (mCurrentFirstVisibleItem != mPreviousFirstVisibleItem) {
                if (mCellIsMobile && mMobileItemId != INVALID_ID) {
                    updateNeighborViewsForID(mMobileItemId);
                    handleCellSwitch();
                }
            }
        }

        /**
         * Determines if the listview scrolled down enough to reveal a new cell at the
         * bottom of the list. If so, then the appropriate parameters are updated.
         */
        public void checkAndHandleLastVisibleCellChange() {
            int currentLastVisibleItem = mCurrentFirstVisibleItem + mCurrentVisibleItemCount;
            int previousLastVisibleItem = mPreviousFirstVisibleItem + mPreviousVisibleItemCount;
            if (currentLastVisibleItem != previousLastVisibleItem) {
                if (mCellIsMobile && mMobileItemId != INVALID_ID) {
                    updateNeighborViewsForID(mMobileItemId);
                    handleCellSwitch();
                }
            }
        }
    };

    /**
     * Interface for list reordering events.
     */
    public interface ListOrderListener {
        /**
         * List has been reordered.
         *
         * @param list Reordered list.
         */
        public void listReordered(List list);
    }

}