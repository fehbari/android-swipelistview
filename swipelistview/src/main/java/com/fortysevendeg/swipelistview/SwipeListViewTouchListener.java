/*
 * Copyright (C) 2013 47 Degrees, LLC
 * http://47deg.com
 * hello@47deg.com
 *
 * Copyright 2012 Roman Nurik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fortysevendeg.swipelistview;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.support.v4.view.MotionEventCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.ValueAnimator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.nineoldandroids.view.ViewHelper.setAlpha;
import static com.nineoldandroids.view.ViewHelper.setTranslationX;
import static com.nineoldandroids.view.ViewPropertyAnimator.animate;

/**
 * Touch listener impl for the SwipeListView
 */
public class SwipeListViewTouchListener implements View.OnTouchListener {

    private static String LOG_TAG = SwipeListViewTouchListener.class.getCanonicalName();

    private Context context;

    private static final int DISPLACE_CHOICE = 80;

    private int swipeMode = SwipeListView.SWIPE_MODE_BOTH;
    private int longSwipeMode = SwipeListView.LONG_SWIPE_MODE_BOTH;
    private boolean swipeClosesAllItemsWhenListMoves = true;

    private int swipeFrontView = 0;
    private int swipeBackView = 0;
    private int swipeBackIconLeft = 0;
    private int swipeBackIconRight = 0;
    private int swipeFrontIcon = 0;

    private Rect rect = new Rect();

    // Cached ViewConfiguration and system-wide constant values
    private int slop;
    private long configShortAnimationTime;
    private long animationTime;

    private float leftOffset = 0;
    private float rightOffset = 0;

    private int swipeThreshold;
    private int longSwipeThreshold;
    private int minSwipeThreshold;

    // Fixed properties
    private SwipeListView swipeListView;
    private int viewWidth = 1; // 1 and not 0 to prevent dividing by zero

    private List<PendingDismissData> pendingDismisses = new ArrayList<PendingDismissData>();
    private int dismissAnimationRefCount = 0;

    private float downX;
    private float previousRawX;

    private boolean swiping;
    private boolean swipingRight;
    private boolean swipingLongRight;
    private boolean swipingLeft;
    private boolean swipingLongLeft;

    private int downPosition = -1;
    private View parentView;
    private View frontView;
    private View backView;
    private View backIconLeft;
    private View backIconRight;
    private View checkbox;
    private boolean paused;

    private int swipeCurrentAction = SwipeListView.SWIPE_ACTION_NONE;
    private int longSwipeCurrentAction = SwipeListView.LONG_SWIPE_ACTION_NONE;

    private int swipeActionLeft = SwipeListView.SWIPE_ACTION_REVEAL;
    private int swipeActionRight = SwipeListView.SWIPE_ACTION_REVEAL;

    private int longSwipeActionLeft = SwipeListView.LONG_SWIPE_ACTION_DISMISS;
    private int longSwipeActionRight = SwipeListView.LONG_SWIPE_ACTION_DISMISS;

    private List<Boolean> opened = new ArrayList<Boolean>();
    private List<Boolean> openedRight = new ArrayList<Boolean>();
    private boolean listViewMoving;
    private List<Boolean> checked = new ArrayList<Boolean>();

    private int rightBackgroundColor;
    private int longRightBackgroundColor;
    private int leftBackgroundColor;
    private int longLeftBackgroundColor;
    private int neutralBackgroundColor;

    private int frontIconBackground;
    private int frontIconRightBackground;
    private int frontIconLongRightBackground;
    private int frontIconLeftBackground;
    private int frontIconLongLeftBackground;

    private int backIconRightText;
    private int backIconLongRightText;
    private int backIconLeftText;
    private int backIconLongLeftText;

    private boolean longSwipeEnabled;
    private boolean swipeEnabled = true;

    private int animationMoveTo;

    private SwipeDirections initialSwipeDirection;
    private SwipeDirections currentSwipeDirection;

    private int hitX;
    private int hitY;

    private enum SwipeDirections {
        LEFT,
        RIGHT;
    }

    /**
     * Constructor
     *
     * @param swipeListView  SwipeListView
     * @param swipeFrontView front view Identifier
     * @param swipeBackView  back view Identifier
     */
    public SwipeListViewTouchListener(SwipeListView swipeListView, int swipeFrontView, int swipeBackView, int swipeBackIconLeft, int swipeBackIconRight) {
        context = swipeListView.getContext();
        this.swipeFrontView = swipeFrontView;
        this.swipeBackView = swipeBackView;
        this.swipeBackIconLeft = swipeBackIconLeft;
        this.swipeBackIconRight = swipeBackIconRight;
        ViewConfiguration vc = ViewConfiguration.get(swipeListView.getContext());
        slop = vc.getScaledTouchSlop();
        configShortAnimationTime = swipeListView.getContext().getResources().getInteger(android.R.integer.config_shortAnimTime);
        animationTime = configShortAnimationTime;
        this.swipeListView = swipeListView;
        swipeThreshold = convertDpiToPixel(60);
        longSwipeThreshold = convertDpiToPixel(180);
        minSwipeThreshold = convertDpiToPixel(20);
    }

    /**
     * Sets current item's parent view
     *
     * @param parentView Parent view
     */
    private void setParentView(View parentView) {
        this.parentView = parentView;
    }

    /**
     * Sets current item's front view
     *
     * @param frontView Front view
     */
    private void setFrontView(View frontView) {
        this.frontView = frontView;
        // HACK: Without setting the listener to null here, kinetic scrolling misbehaves and drag
        // and drop crashes. This needs to be further investigated and improved later.
        frontView.setOnClickListener(null);
    }

    /**
     * Set current item's back view
     *
     * @param backView Back view
     */
    private void setBackView(View backView) {
        this.backView = backView;
    }

    /**
     * Set current item's left back view icon
     *
     * @param backIconLeft Left back view icon
     */
    private void setBackIconLeft(View backIconLeft) {
        this.backIconLeft = backIconLeft;
    }

    /**
     * Set current item's right back view icon
     *
     * @param backIconRight Right back view icon
     */
    private void setBackIconRight(View backIconRight) {
        this.backIconRight = backIconRight;
    }

    /**
     * Set the front view icon resource.
     *
     * @param swipeFrontIcon Icon resource to set.
     */
    public void setSwipeFrontIcon(int swipeFrontIcon) {
        this.swipeFrontIcon = swipeFrontIcon;
    }

    /**
     * Set the front view checkbox.
     *
     * @param checkbox View to set.
     */
    public void setCheckbox(View checkbox) {
        this.checkbox = checkbox;
    }

    /**
     * @return true if the list is in motion
     */
    public boolean isListViewMoving() {
        return listViewMoving;
    }

    /**
     * Sets animation time when the user drops the cell
     *
     * @param animationTime milliseconds
     */
    public void setAnimationTime(long animationTime) {
        if (animationTime > 0) {
            this.animationTime = animationTime;
        } else {
            this.animationTime = configShortAnimationTime;
        }
    }

    /**
     * Sets the right offset
     *
     * @param rightOffset Offset
     */
    public void setRightOffset(float rightOffset) {
        this.rightOffset = rightOffset;
    }

    /**
     * Set the left offset
     *
     * @param leftOffset Offset
     */
    public void setLeftOffset(float leftOffset) {
        this.leftOffset = leftOffset;
    }

    /**
     * Set if all item opened will be close when the user move ListView
     *
     * @param swipeClosesAllItemsWhenListMoves
     */
    public void setSwipeClosesAllItemsWhenListMoves(boolean swipeClosesAllItemsWhenListMoves) {
        this.swipeClosesAllItemsWhenListMoves = swipeClosesAllItemsWhenListMoves;
    }

    /**
     * Sets the swipe mode
     *
     * @param swipeMode
     */
    public void setSwipeMode(int swipeMode) {
        this.swipeMode = swipeMode;
    }

    /**
     * Sets the long swipe mode
     *
     * @param longSwipeMode
     */
    public void setLongSwipeMode(int longSwipeMode) {
        this.longSwipeMode = longSwipeMode;
    }

    /**
     * Check is swiping is enabled
     *
     * @return
     */
    protected boolean isSwipeEnabled() {
        return swipeEnabled && swipeMode != SwipeListView.SWIPE_MODE_NONE;
    }

    /**
     * Return action on left
     *
     * @return Action
     */
    public int getSwipeActionLeft() {
        return swipeActionLeft;
    }

    /**
     * Set action on left
     *
     * @param swipeActionLeft Action
     */
    public void setSwipeActionLeft(int swipeActionLeft) {
        this.swipeActionLeft = swipeActionLeft;
    }

    /**
     * Return action on right
     *
     * @return Action
     */
    public int getSwipeActionRight() {
        return swipeActionRight;
    }

    /**
     * Set action on right
     *
     * @param swipeActionRight Action
     */
    public void setSwipeActionRight(int swipeActionRight) {
        this.swipeActionRight = swipeActionRight;
    }

    /**
     * Set long action on left
     *
     * @param longSwipeActionLeft Action
     */
    public void setLongSwipeActionLeft(int longSwipeActionLeft) {
        this.longSwipeActionLeft = longSwipeActionLeft;
    }

    /**
     * Set long action on right
     *
     * @param longSwipeActionRight Action
     */
    public void setLongSwipeActionRight(int longSwipeActionRight) {
        this.longSwipeActionRight = longSwipeActionRight;
    }

    /**
     * Set the color for the right swipe background.
     *
     * @param backgroundColor Color to set.
     */
    public void setRightBackgroundColor(int backgroundColor) {
        rightBackgroundColor = backgroundColor;
    }

    /**
     * Set the color for the left swipe background.
     *
     * @param backgroundColor Color to set.
     */
    public void setLeftBackgroundColor(int backgroundColor) {
        leftBackgroundColor = backgroundColor;
    }

    /**
     * Set the color for the long right swipe background.
     *
     * @param backgroundColor Color to set.
     */
    public void setLongRightBackgroundColor(int backgroundColor) {
        longRightBackgroundColor = backgroundColor;
    }

    /**
     * Set the color for the long left swipe background.
     *
     * @param backgroundColor Color to set.
     */
    public void setLongLeftBackgroundColor(int backgroundColor) {
        longLeftBackgroundColor = backgroundColor;
    }

    /**
     * Set the color for the neutral background.
     *
     * @param backgroundColor Color to set.
     */
    public void setNeutralBackgroundColor(int backgroundColor) {
        neutralBackgroundColor = backgroundColor;
    }

    /**
     * Set the resource for the front view icon background.
     *
     * @param frontIconBackground Resource to set.
     */
    public void setFrontIconBackground(int frontIconBackground) {
        this.frontIconBackground = frontIconBackground;
    }

    /**
     * Set the resource for the front view icon background when swiping right.
     *
     * @param frontIconRightBackground Resource to set.
     */
    public void setFrontIconRightBackground(int frontIconRightBackground) {
        this.frontIconRightBackground = frontIconRightBackground;
    }

    /**
     * Set the resource for the front view icon background when long swiping right.
     *
     * @param frontIconLongRightBackground Resource to set.
     */
    public void setFrontIconLongRightBackground(int frontIconLongRightBackground) {
        this.frontIconLongRightBackground = frontIconLongRightBackground;
    }

    /**
     * Set the resource for the front view icon background when swiping left.
     *
     * @param frontIconLeftBackground Resource to set.
     */
    public void setFrontIconLeftBackground(int frontIconLeftBackground) {
        this.frontIconLeftBackground = frontIconLeftBackground;
    }

    /**
     * Set the resource for the front view icon background when long swiping left.
     *
     * @param frontIconLongLeftBackground Resource to set.
     */
    public void setFrontIconLongLeftBackground(int frontIconLongLeftBackground) {
        this.frontIconLongLeftBackground = frontIconLongLeftBackground;
    }

    /**
     * Set the icon font text for the back icon when swiping to the right.
     *
     * @param backIconRightText Text to set.
     */
    public void setBackIconRightText(int backIconRightText) {
        this.backIconRightText = backIconRightText;
    }

    /**
     * Set the icon font text for the back icon when long swiping to the right.
     *
     * @param backIconLongRightText Text to set.
     */
    public void setBackIconLongRightText(int backIconLongRightText) {
        this.backIconLongRightText = backIconLongRightText;
    }

    /**
     * Set the icon font text for the back icon when swiping to the left.
     *
     * @param backIconLeftText Text to set.
     */
    public void setBackIconLeftText(int backIconLeftText) {
        this.backIconLeftText = backIconLeftText;
    }

    /**
     * Set the icon font text for the back icon when long swiping to the left.
     *
     * @param backIconLongLeftText Text to set.
     */
    public void setBackIconLongLeftText(int backIconLongLeftText) {
        this.backIconLongLeftText = backIconLongLeftText;
    }

    /**
     * Enables or disables long swipes in the list.
     *
     * @param enabled True to enable, false otherwise.
     */
    public void setLongSwipeEnabled(boolean enabled) {
        longSwipeEnabled = enabled;
    }

    /**
     * Enables or disables swiping in the list.
     *
     * @param enabled True to enable, false otherwise.
     */
    public void setSwipeEnabled(boolean enabled) {
        swipeEnabled = enabled;
    }

    /**
     * Gets the position touched on the list.
     *
     * @return Position.
     */
    public int getDownPosition() {
        return downPosition;
    }

    /**
     * Determines if cell is being swiped.
     *
     * @return True if it's swiping.
     */
    public boolean isSwiping() {
        return swiping;
    }

    /**
     * Adds new items when adapter is modified
     */
    public void resetItems() {
        if (swipeListView.getAdapter() != null) {
            int count = swipeListView.getAdapter().getCount();
            for (int i = opened.size(); i <= count; i++) {
                opened.add(false);
                openedRight.add(false);
                checked.add(false);
            }
        }
    }

    /**
     * Open item
     *
     * @param position Position of list
     */
    protected void openAnimate(int position) {
        openAnimate(swipeListView.getChildAt(position - swipeListView.getFirstVisiblePosition()).findViewById(swipeFrontView), position);
    }

    /**
     * Close item
     *
     * @param position Position of list
     */
    protected void closeAnimate(int position) {
        closeAnimate(swipeListView.getChildAt(position - swipeListView.getFirstVisiblePosition()).findViewById(swipeFrontView), position);
    }

    /**
     * Unselected choice state in item
     */
    protected int dismiss(int position) {
        int start = swipeListView.getFirstVisiblePosition();
        int end = swipeListView.getLastVisiblePosition();
        View view = swipeListView.getChildAt(position - start);
        ++dismissAnimationRefCount;
        if (position >= start && position <= end) {
            performDismiss(view, position, false);
            return view.getHeight();
        } else {
            pendingDismisses.add(new PendingDismissData(position, null));
            return 0;
        }
    }

    /**
     * Get if item is selected
     *
     * @param position position in list
     * @return
     */
    protected boolean isChecked(int position) {
        return position < checked.size() && checked.get(position);
    }

    /**
     * Count selected
     *
     * @return
     */
    protected int getCountSelected() {
        int count = 0;
        for (int i = 0; i < checked.size(); i++) {
            if (checked.get(i)) {
                count++;
            }
        }
        Log.d("SwipeListView", "selected: " + count);
        return count;
    }

    /**
     * Get positions selected
     *
     * @return
     */
    protected List<Integer> getPositionsSelected() {
        List<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.get(i)) {
                list.add(i);
            }
        }
        return list;
    }

    /**
     * Open item
     *
     * @param view     affected view
     * @param position Position of list
     */
    private void openAnimate(View view, int position) {
        if (!opened.get(position)) {
            generateRevealAnimate(view, true, false, position);
        }
    }

    /**
     * Close item
     *
     * @param view     affected view
     * @param position Position of list
     */
    private void closeAnimate(View view, int position) {
        if (opened.get(position)) {
            generateRevealAnimate(view, true, false, position);
        }
    }

    /**
     * Create animation
     *
     * @param view      affected view
     * @param swap      If state should change. If "false" returns to the original position
     * @param swapRight If swap is true, this parameter tells if move is to the right or left
     * @param position  Position of list
     */
    private void generateAnimate(final View view, final boolean swap, final boolean swapRight, final int position) {
        Log.d("SwipeListView", "swap: " + swap + " - swapRight: " + swapRight + " - position: " + position);

        // Determines the icon to animate alongside the front view.
        View icon = swapRight ? backIconLeft : backIconRight;

        if (swipingRight || swipingLeft) {
            if (swipeCurrentAction == SwipeListView.SWIPE_ACTION_REVEAL) {
                generateRevealAnimate(view, swap, swapRight, position);
                animateIconReveal(icon, swapRight);
            }
            if (swipeCurrentAction == SwipeListView.SWIPE_ACTION_DISMISS) {
                generateDismissAnimate(frontView, swap, swapRight, position);
                animateIconDismiss(icon, swapRight);
            }
            if (swipeCurrentAction == SwipeListView.SWIPE_ACTION_NONE) {
                generateNoActionAnimate(view, position);
            }
        } else if (swipingLongRight || swipingLongLeft) {
            if (longSwipeCurrentAction == SwipeListView.LONG_SWIPE_ACTION_REVEAL) {
                generateRevealAnimate(view, swap, swapRight, position);
                animateIconReveal(icon, swapRight);
            }
            if (longSwipeCurrentAction == SwipeListView.LONG_SWIPE_ACTION_DISMISS) {
                generateDismissAnimate(frontView, swap, swapRight, position);
                animateIconDismiss(icon, swapRight);
            }
        } else {
            generateNoActionAnimate(view, position);
        }
    }

    /**
     * Create no action animation
     *
     * @param view     affected view
     * @param position list position
     */
    private void generateNoActionAnimate(final View view, final int position) {
        animate(view)
                .translationX(0)
                .setDuration(animationTime)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        resetCell();
                    }
                });
    }

    /**
     * Create dismiss animation
     *
     * @param view      affected view
     * @param swap      If will change state. If is "false" returns to the original position
     * @param swapRight If swap is true, this parameter tells if move is to the right or left
     * @param position  Position of list
     */
    private void generateDismissAnimate(final View view, final boolean swap, final boolean swapRight, final int position) {
        animationMoveTo = 0;
        if (opened.get(position)) {
            if (!swap) {
                animationMoveTo = openedRight.get(position) ? (int) (viewWidth - rightOffset) : (int) (-viewWidth + leftOffset);
            }
        } else {
            if (swap) {
                animationMoveTo = swapRight ? (int) (viewWidth - rightOffset) : (int) (-viewWidth + leftOffset);
            }
        }

        animate(view)
                .translationX(animationMoveTo)
                .setDuration(animationTime)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (swap) {
                            closeOpenedItems();
                            performDismiss(parentView, position, true);
                        }
                    }
                });

    }

    private void animateIconDismiss(View view, boolean swapRight) {
        int moveTo = swapRight ? (int) (viewWidth - rightOffset) : (int) (-viewWidth + leftOffset);

        animate(view)
                .translationX(animationMoveTo)
                .setDuration(animationTime)
                .setListener(null);
    }

    /**
     * Create reveal animation
     *
     * @param view      affected view
     * @param swap      If will change state. If "false" returns to the original position
     * @param swapRight If swap is true, this parameter tells if movement is toward right or left
     * @param position  list position
     */
    private void generateRevealAnimate(final View view, final boolean swap, final boolean swapRight, final int position) {
        int moveTo = 0;
        if (opened.get(position)) {
            if (!swap) {
                moveTo = openedRight.get(position) ? (int) (viewWidth - rightOffset) : (int) (-viewWidth + leftOffset);
            }
        } else {
            if (swap) {
                moveTo = swapRight ? (int) (viewWidth - rightOffset) : (int) (-viewWidth + leftOffset);
            }
        }

        animate(view)
                .translationX(moveTo)
                .setDuration(animationTime)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (swap) {
                            boolean aux = !opened.get(position);
                            if (aux) {
                                swipeListView.onOpened(position, swapRight);
                            } else {
                                swipeListView.onClosed(position, openedRight.get(position));
                            }
                        }
                        triggerAction();
                        resetCell();
                    }
                });
    }

    private void animateIconReveal(View view, boolean swapRight) {
        int moveTo = swapRight ? (int) (viewWidth - rightOffset) : (int) (-viewWidth + leftOffset);

        animate(view)
                .translationX(moveTo)
                .setDuration(animationTime)
                .setListener(null);
    }

    private void resetCell() {
        if (downPosition != ListView.INVALID_POSITION) {
            frontView = null;
            backView = null;
            backView = null;
            downPosition = ListView.INVALID_POSITION;
        }
    }

    /**
     * Set enabled
     *
     * @param enabled
     */
    public void setEnabled(boolean enabled) {
        paused = !enabled;
    }

    /**
     * Return ScrollListener for ListView
     *
     * @return OnScrollListener
     */
    public AbsListView.OnScrollListener makeScrollListener() {
        return new AbsListView.OnScrollListener() {

            private boolean isFirstItem = false;
            private boolean isLastItem = false;

            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                setEnabled(scrollState != AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                if (swipeClosesAllItemsWhenListMoves && scrollState == SCROLL_STATE_TOUCH_SCROLL) {
                    closeOpenedItems();
                }
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
                    listViewMoving = true;
                    setEnabled(false);
                }
                if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_FLING && scrollState != SCROLL_STATE_TOUCH_SCROLL) {
                    listViewMoving = false;
                    downPosition = ListView.INVALID_POSITION;
                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            setEnabled(true);
                        }
                    }, 500);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (isFirstItem) {
                    boolean onSecondItemList = firstVisibleItem == 1;
                    if (onSecondItemList) {
                        isFirstItem = false;
                    }
                } else {
                    boolean onFirstItemList = firstVisibleItem == 0;
                    if (onFirstItemList) {
                        isFirstItem = true;
                        swipeListView.onFirstListItem();
                    }
                }
                if (isLastItem) {
                    boolean onBeforeLastItemList = firstVisibleItem + visibleItemCount == totalItemCount - 1;
                    if (onBeforeLastItemList) {
                        isLastItem = false;
                    }
                } else {
                    boolean onLastItemList = firstVisibleItem + visibleItemCount >= totalItemCount;
                    if (onLastItemList) {
                        isLastItem = true;
                        swipeListView.onLastListItem();
                    }
                }
            }
        };
    }

    /**
     * Close all opened items
     */
    void closeOpenedItems() {
        if (opened != null) {
            int start = swipeListView.getFirstVisiblePosition();
            int end = swipeListView.getLastVisiblePosition();
            for (int i = start; i <= end; i++) {
                if (opened.get(i)) {
                    closeAnimate(swipeListView.getChildAt(i - start).findViewById(swipeFrontView), i);
                }
            }
        }

    }

    /**
     * @see View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
     */
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (!isSwipeEnabled()) {
            return false;
        }

        if (!(view instanceof DynamicListView)) {
            Log.e(LOG_TAG, "Invalid list type. Please use DynamicListView instead.");
            return false;
        }

        if (viewWidth < 2) {
            viewWidth = swipeListView.getWidth();
        }

        switch (MotionEventCompat.getActionMasked(motionEvent)) {
            case MotionEvent.ACTION_DOWN: {
                if (paused && downPosition != ListView.INVALID_POSITION) {
                    return false;
                }

                swipeCurrentAction = SwipeListView.SWIPE_ACTION_NONE;
                longSwipeCurrentAction = SwipeListView.LONG_SWIPE_ACTION_NONE;

                int childCount = swipeListView.getChildCount();
                int[] listViewCoords = new int[2];
                swipeListView.getLocationOnScreen(listViewCoords);
                int x = (int) motionEvent.getRawX() - listViewCoords[0];
                int y = (int) motionEvent.getRawY() - listViewCoords[1];
                View child;
                for (int i = 0; i < childCount; i++) {
                    child = swipeListView.getChildAt(i);
                    child.getHitRect(rect);

                    int childPosition = swipeListView.getPositionForView(child);

                    // dont allow swiping if this is on the header or footer or IGNORE_ITEM_VIEW_TYPE or enabled is false on the adapter
                    boolean allowSwipe = swipeListView.getAdapter().isEnabled(childPosition) && swipeListView.getAdapter().getItemViewType(childPosition) >= 0;

                    if (allowSwipe && rect.contains(x, y)) {
                        setParentView(child);
                        setFrontView(child.findViewById(swipeFrontView));

                        downX = motionEvent.getRawX();
                        downPosition = childPosition;

                        hitX = (int) motionEvent.getRawX();
                        hitY = (int) motionEvent.getRawY();

                        if (swipeBackView > 0) {
                            setBackView(child.findViewById(swipeBackView));
                        }

                        if (swipeBackIconLeft > 0) {
                            setBackIconLeft(child.findViewById(swipeBackIconLeft));
                        }

                        if (swipeBackIconRight > 0) {
                            setBackIconRight(child.findViewById(swipeBackIconRight));
                        }

                        if (swipeFrontIcon > 0) {
                            setCheckbox(child.findViewById(swipeFrontIcon));
                        }

                        if (swipeListView.getViewPager() != null) {
                            swipeListView.getViewPager().setSwipeable(false);
                        }

                        break;
                    }
                }
                view.onTouchEvent(motionEvent);
                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (downPosition == ListView.INVALID_POSITION || frontView == null) {
                    break;
                }

                if (!swiping) {
                    // Detect single tap.
                    if (!((DynamicListView) view).isScrollingY()) {
                        // Detect if touch was on the checkbox.
                        if (didTouchView((View) checkbox.getParent(), hitX, hitY)) {
                            swipeListView.onClickCheckbox(checkbox, downPosition);
                        } else {
                            swipeListView.onClickFrontView(frontView, downPosition);
                        }
                    }

                    view.onTouchEvent(motionEvent);
                }

                float deltaX = previousRawX - downX;
                boolean swap = false;
                boolean swapRight = false;

                if (didRegretSwipe()) {
                    swap = false;
                } else if (Math.abs(deltaX) > swipeThreshold) {
                    swap = true;
                    swapRight = deltaX > 0;
                } else if (swipingRight && swipeActionLeft != swipeActionRight) {
                    swap = false;
                } else if (opened.get(downPosition) && !openedRight.get(downPosition)) {
                    swap = false;
                } else if (swipeMode == SwipeListView.SWIPE_MODE_LEFT && deltaX > swipeThreshold) {
                    swap = false;
                } else if (swipeMode == SwipeListView.SWIPE_MODE_RIGHT && deltaX < swipeThreshold) {
                    swap = false;
                }

                generateAnimate(frontView, swap, swapRight, downPosition);

                if (swipeListView.getViewPager() != null) {
                    swipeListView.getViewPager().setSwipeable(true);
                }

                // Interaction is done, reset state variables.
                downX = 0;
                previousRawX = 0;
                swiping = false;
                currentSwipeDirection = null;
                initialSwipeDirection = null;

                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (paused || downPosition == ListView.INVALID_POSITION || areViewsNull() ||
                        ((DynamicListView) view).hasPerformedLongPress() || ((DynamicListView) view).isScrollingY()) {
                    break;
                }

                float rawX = motionEvent.getRawX();
                float deltaX = rawX - downX;
                float deltaMode = Math.abs(deltaX);
                // Delta for the current position X - the previous one.
                float histDeltaX = rawX - previousRawX;

                // Only process swipe changes when touch has moved far enough.
                if (Math.abs(histDeltaX) > minSwipeThreshold) {
                    int swipeMode = this.swipeMode;
                    int changeSwipeMode = swipeListView.changeSwipeMode(downPosition);
                    if (changeSwipeMode >= 0) {
                        swipeMode = changeSwipeMode;
                    }

                    if (swipeMode == SwipeListView.SWIPE_MODE_NONE && !longSwipeEnabled) {
                        deltaMode = 0;
                    } else if (swipeMode != SwipeListView.SWIPE_MODE_BOTH) {
                        if (opened.get(downPosition)) {
                            if (swipeMode == SwipeListView.SWIPE_MODE_LEFT && deltaX < 0) {
                                deltaMode = 0;
                            } else if (swipeMode == SwipeListView.SWIPE_MODE_RIGHT && deltaX > 0) {
                                deltaMode = 0;
                            }
                        } else {
                            if (swipeMode == SwipeListView.SWIPE_MODE_LEFT && deltaX > swipeThreshold) {
                                break;
                            } else if (swipeMode == SwipeListView.SWIPE_MODE_RIGHT && deltaX < -swipeThreshold) {
                                break;
                            }
                        }
                    }

                    // Reset current actions.
                    swipeCurrentAction = SwipeListView.SWIPE_ACTION_NONE;
                    longSwipeCurrentAction = SwipeListView.LONG_SWIPE_ACTION_NONE;

                    // Reset back icons.
                    backIconLeft.setVisibility(View.VISIBLE);
                    backIconRight.setVisibility(View.VISIBLE);
                    ((TextView) backIconLeft).setText(null);
                    ((TextView) backIconRight).setText(null);

                    if (longSwipeEnabled) {
                        swipingRight = deltaX > swipeThreshold && deltaX < longSwipeThreshold;
                        swipingLeft = deltaX < -swipeThreshold && deltaX > -longSwipeThreshold;
                    } else {
                        swipingRight = deltaX > swipeThreshold;
                        swipingLeft = deltaX < -swipeThreshold;
                    }

                    if (longSwipeEnabledForDirection(SwipeDirections.RIGHT)) {
                        swipingLongRight = deltaX > longSwipeThreshold;
                    }

                    if (longSwipeEnabledForDirection(SwipeDirections.LEFT)) {
                        swipingLongLeft = deltaX < -longSwipeThreshold;
                    }

                    // Changes colors and actions based on swipe direction and length.
                    if (swipingRight && swipeEnabledForDirection(SwipeDirections.RIGHT)) {
                        backView.setBackgroundColor(rightBackgroundColor);
                        checkbox.setBackgroundResource(frontIconRightBackground);
                        ((TextView) backIconLeft).setText(context.getString(backIconRightText));

                        if (swipeActionRight == SwipeListView.SWIPE_ACTION_DISMISS) {
                            swipeCurrentAction = SwipeListView.SWIPE_ACTION_DISMISS;
                        } else if (swipeActionRight == SwipeListView.SWIPE_ACTION_REVEAL) {
                            swipeCurrentAction = SwipeListView.SWIPE_ACTION_REVEAL;
                        }
                    } else if (swipingLeft && swipeEnabledForDirection(SwipeDirections.LEFT)) {
                        backView.setBackgroundColor(leftBackgroundColor);
                        checkbox.setBackgroundResource(frontIconLeftBackground);
                        ((TextView) backIconRight).setText(context.getString(backIconLeftText));

                        if (swipeActionLeft == SwipeListView.SWIPE_ACTION_DISMISS) {
                            swipeCurrentAction = SwipeListView.SWIPE_ACTION_DISMISS;
                        } else if (swipeActionLeft == SwipeListView.SWIPE_ACTION_REVEAL) {
                            swipeCurrentAction = SwipeListView.SWIPE_ACTION_REVEAL;
                        }
                    } else if (swipingLongRight) {
                        if (longSwipeEnabledForDirection(SwipeDirections.RIGHT)) {
                            backView.setBackgroundColor(longRightBackgroundColor);
                            checkbox.setBackgroundResource(frontIconLongRightBackground);
                            ((TextView) backIconLeft).setText(context.getString(backIconLongRightText));
                        } else {
                            backView.setBackgroundColor(rightBackgroundColor);
                            checkbox.setBackgroundResource(frontIconRightBackground);
                            ((TextView) backIconLeft).setText(context.getString(backIconRightText));
                        }

                        if (longSwipeActionRight == SwipeListView.LONG_SWIPE_ACTION_DISMISS) {
                            longSwipeCurrentAction = SwipeListView.LONG_SWIPE_ACTION_DISMISS;
                        } else if (longSwipeActionRight == SwipeListView.LONG_SWIPE_ACTION_REVEAL) {
                            longSwipeCurrentAction = SwipeListView.SWIPE_ACTION_REVEAL;
                        }
                    } else if (swipingLongLeft) {
                        if (longSwipeEnabledForDirection(SwipeDirections.LEFT)) {
                            backView.setBackgroundColor(longLeftBackgroundColor);
                            checkbox.setBackgroundResource(frontIconLongLeftBackground);
                            ((TextView) backIconRight).setText(context.getString(backIconLongLeftText));
                        } else {
                            backView.setBackgroundColor(leftBackgroundColor);
                            checkbox.setBackgroundResource(frontIconLeftBackground);
                            ((TextView) backIconRight).setText(context.getString(backIconLeftText));
                        }

                        if (longSwipeActionLeft == SwipeListView.LONG_SWIPE_ACTION_DISMISS) {
                            longSwipeCurrentAction = SwipeListView.LONG_SWIPE_ACTION_DISMISS;
                        } else if (longSwipeActionLeft == SwipeListView.LONG_SWIPE_ACTION_REVEAL) {
                            longSwipeCurrentAction = SwipeListView.SWIPE_ACTION_REVEAL;
                        }
                    } else {
                        backView.setBackgroundColor(neutralBackgroundColor);
                    }

                    swipeListView.onStartOpen(downPosition, swipeCurrentAction, swipingRight);

                    if (previousRawX > 0) {
                        if (histDeltaX > 0) {
                            currentSwipeDirection = SwipeDirections.RIGHT;
                        } else {
                            currentSwipeDirection = SwipeDirections.LEFT;
                        }

                        if (initialSwipeDirection == null) {
                            initialSwipeDirection = currentSwipeDirection;
                        }

                        // Changes back view based on swipe direction change (i.e. "regret").
                        if (didRegretSwipe()) {
                            backView.setBackgroundColor(neutralBackgroundColor);
                            checkbox.setBackgroundResource(frontIconBackground);
                            backIconLeft.setVisibility(View.GONE);
                            backIconRight.setVisibility(View.GONE);
                            swipeCurrentAction = SwipeListView.SWIPE_ACTION_NONE;
                            longSwipeCurrentAction = SwipeListView.LONG_SWIPE_ACTION_NONE;
                        }
                    }

                    if (deltaMode > slop) {
                        swiping = true;
                        Log.d("SwipeListView", "deltaX: " + deltaX + " - swipingRight: " + swipingRight);

                        if (opened.get(downPosition)) {
                            swipeListView.onStartClose(downPosition, swipingRight);
                            swipeCurrentAction = SwipeListView.SWIPE_ACTION_REVEAL;
                        }

                        swipeListView.requestDisallowInterceptTouchEvent(true);
                        MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
                        cancelEvent.setAction(MotionEvent.ACTION_CANCEL |
                                (MotionEventCompat.getActionIndex(motionEvent) << MotionEventCompat.ACTION_POINTER_INDEX_SHIFT));
                        swipeListView.onTouchEvent(cancelEvent);
                    }

                    previousRawX = motionEvent.getRawX();
                }

                if (swiping && downPosition != ListView.INVALID_POSITION) {
                    if (opened.get(downPosition)) {
                        deltaX += openedRight.get(downPosition) ? viewWidth - rightOffset : -viewWidth + leftOffset;
                    }
                    move(deltaX);
                    return true;
                }
                break;
            }
        }
        return false;
    }

    /**
     * Moves the view
     *
     * @param deltaX delta
     */
    public void move(float deltaX) {
        swipeListView.onMove(downPosition, deltaX);
        backView.setVisibility(View.VISIBLE);
        setTranslationX(frontView, deltaX);
        setTranslationX(backIconLeft, deltaX);
        setTranslationX(backIconRight, deltaX);
    }

    /**
     * Class that saves pending dismiss data
     */
    class PendingDismissData implements Comparable<PendingDismissData> {
        public int position;
        public View view;

        public PendingDismissData(int position, View view) {
            this.position = position;
            this.view = view;
        }

        @Override
        public int compareTo(PendingDismissData other) {
            // Sort by descending position
            return other.position - position;
        }
    }

    /**
     * Perform dismiss action
     *
     * @param dismissView     View
     * @param dismissPosition Position of list
     */
    protected void performDismiss(final View dismissView, final int dismissPosition, boolean doPendingDismiss) {
        final ViewGroup.LayoutParams lp = dismissView.getLayoutParams();
        final int originalHeight = dismissView.getHeight();

        ValueAnimator animator = ValueAnimator.ofInt(originalHeight, 1).setDuration(animationTime);

        if (doPendingDismiss) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    --dismissAnimationRefCount;
                    if (dismissAnimationRefCount == 0) {
                        removePendingDismisses(originalHeight);
                    }
                    dismissView.setVisibility(View.GONE);
                    triggerAction();
                    resetCell();
                }
            });
        }

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                lp.height = (Integer) valueAnimator.getAnimatedValue();
                dismissView.setLayoutParams(lp);
            }
        });

        pendingDismisses.add(new PendingDismissData(dismissPosition, dismissView));
        animator.start();
    }

    protected void resetPendingDismisses() {
        pendingDismisses.clear();
    }

    protected void handlerPendingDismisses(final int originalHeight) {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                removePendingDismisses(originalHeight);
            }
        }, animationTime + 100);
    }

    private void removePendingDismisses(int originalHeight) {
        // No active animations, process all pending dismisses.
        // Sort by descending position
        Collections.sort(pendingDismisses);

        int[] dismissPositions = new int[pendingDismisses.size()];
        for (int i = pendingDismisses.size() - 1; i >= 0; i--) {
            dismissPositions[i] = pendingDismisses.get(i).position;
        }
        swipeListView.onDismiss(dismissPositions);

        ViewGroup.LayoutParams lp;
        for (PendingDismissData pendingDismiss : pendingDismisses) {
            // Reset view presentation
            if (pendingDismiss.view != null) {
                setAlpha(pendingDismiss.view, 1f);
                setTranslationX(pendingDismiss.view, 0);
                lp = pendingDismiss.view.getLayoutParams();
                lp.height = originalHeight;
                pendingDismiss.view.setLayoutParams(lp);
            }
        }

        resetPendingDismisses();
    }

    private void triggerAction() {
        // Trigger actions for each swiping state.
        if (swipeCurrentAction != SwipeListView.SWIPE_ACTION_NONE) {
            if (swipingRight && swipeEnabledForDirection(SwipeDirections.RIGHT)) {
                swipeListView.onFinishedSwipeRight(downPosition);
            } else if (swipingLeft && swipeEnabledForDirection(SwipeDirections.LEFT)) {
                swipeListView.onFinishedSwipeLeft(downPosition);
            }
        } else if (longSwipeCurrentAction != SwipeListView.LONG_SWIPE_ACTION_NONE) {
            if (swipingLongRight && longSwipeEnabledForDirection(SwipeDirections.RIGHT)) {
                swipeListView.onFinishedLongSwipeRight(downPosition);
            } else if (swipingLongLeft && longSwipeEnabledForDirection(SwipeDirections.LEFT)) {
                swipeListView.onFinishedLongSwipeLeft(downPosition);
            }
        }
    }

    private boolean swipeEnabledForDirection(SwipeDirections direction) {
        switch (direction) {
            case RIGHT:
                if (swipeMode == SwipeListView.SWIPE_MODE_RIGHT || swipeMode == SwipeListView.SWIPE_MODE_BOTH) {
                    return true;
                }
                break;
            case LEFT:
                if (swipeMode == SwipeListView.SWIPE_MODE_LEFT || swipeMode == SwipeListView.SWIPE_MODE_BOTH) {
                    return true;
                }
                break;
        }
        return false;
    }

    private boolean longSwipeEnabledForDirection(SwipeDirections direction) {
        switch (direction) {
            case RIGHT:
                if (longSwipeMode == SwipeListView.LONG_SWIPE_MODE_RIGHT || longSwipeMode == SwipeListView.LONG_SWIPE_MODE_BOTH) {
                    return true;
                }
                break;
            case LEFT:
                if (longSwipeMode == SwipeListView.LONG_SWIPE_MODE_LEFT || longSwipeMode == SwipeListView.LONG_SWIPE_MODE_BOTH) {
                    return true;
                }
                break;
        }
        return false;
    }

    private boolean didRegretSwipe() {
        if (currentSwipeDirection != initialSwipeDirection) {
            return true;
        }
        return false;
    }

    public int convertDpiToPixel(float dpi) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int px = Math.round(dpi * metrics.density);
        return px;
    }

    private boolean areViewsNull() {
        boolean areNull = false;
        if (backIconLeft == null || backIconRight == null) {
            areNull = true;
        }
        return areNull;
    }

    private boolean didTouchView(View view, int x, int y) {
        Rect viewRect = new Rect();
        int[] location = new int[2];

        view.getDrawingRect(viewRect);
        view.getLocationOnScreen(location);
        viewRect.offset(location[0], location[1]);

        return viewRect.contains(x, y);
    }

}
