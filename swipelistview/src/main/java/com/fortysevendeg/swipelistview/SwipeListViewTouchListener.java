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

    private Context context;

    private static final int DISPLACE_CHOICE = 80;

    private int swipeMode = SwipeListView.SWIPE_MODE_BOTH;
    private int longSwipeMode = SwipeListView.LONG_SWIPE_MODE_BOTH;
    private boolean swipeClosesAllItemsWhenListMoves = true;

    private int swipeFrontView = 0;
    private int swipeBackView = 0;

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

    private int downPosition;
    private View parentView;
    private View frontView;
    private View backView;
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

    private boolean longSwipeEnabled;

    private int animationMoveTo;

    private SwipeDirections initialSwipeDirection;
    private SwipeDirections currentSwipeDirection;

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
    public SwipeListViewTouchListener(SwipeListView swipeListView, int swipeFrontView, int swipeBackView) {
        context = swipeListView.getContext();
        this.swipeFrontView = swipeFrontView;
        this.swipeBackView = swipeBackView;
        ViewConfiguration vc = ViewConfiguration.get(swipeListView.getContext());
        slop = vc.getScaledTouchSlop();
        configShortAnimationTime = swipeListView.getContext().getResources().getInteger(android.R.integer.config_shortAnimTime);
        animationTime = configShortAnimationTime;
        this.swipeListView = swipeListView;
        swipeThreshold = convertDpiToPixel(60);
        longSwipeThreshold = convertDpiToPixel(240);
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
        frontView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swipeListView.onClickFrontView(downPosition);
            }
        });
    }

    /**
     * Set current item's back view
     *
     * @param backView
     */
    private void setBackView(View backView) {
        this.backView = backView;
        backView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swipeListView.onClickBackView(downPosition);
            }
        });
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
        return swipeMode != SwipeListView.SWIPE_MODE_NONE;
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
     * Enables or disables long swipes in the list.
     *
     * @param enabled True to enable, false otherwise.
     */
    public void setLongSwipeEnabled(boolean enabled) {
        longSwipeEnabled = enabled;
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
        if (swipingRight || swipingLeft) {
            if (swipeCurrentAction == SwipeListView.SWIPE_ACTION_REVEAL) {
                generateRevealAnimate(view, swap, swapRight, position);
            }
            if (swipeCurrentAction == SwipeListView.SWIPE_ACTION_DISMISS) {
                generateDismissAnimate(frontView, swap, swapRight, position);
            }
            if (swipeCurrentAction == SwipeListView.SWIPE_ACTION_NONE) {
                generateNoActionAnimate(view, position);
            }
        } else if (swipingLongRight || swipingLongLeft) {
            if (longSwipeCurrentAction == SwipeListView.LONG_SWIPE_ACTION_REVEAL) {
                generateRevealAnimate(view, swap, swapRight, position);
            }
            if (longSwipeCurrentAction == SwipeListView.LONG_SWIPE_ACTION_DISMISS) {
                generateDismissAnimate(frontView, swap, swapRight, position);
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
                        swipeListView.resetScrolling();
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
                        resetCell();
                    }
                });

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
                        swipeListView.resetScrolling();
                        if (swap) {
                            boolean aux = !opened.get(position);
                            opened.set(position, aux);
                            if (aux) {
                                swipeListView.onOpened(position, swapRight);
                                openedRight.set(position, swapRight);
                            } else {
                                swipeListView.onClosed(position, openedRight.get(position));
                            }
                        }
                        resetCell();
                    }
                });
    }

    private void resetCell() {
        if (downPosition != ListView.INVALID_POSITION) {
            frontView.setClickable(opened.get(downPosition));
            frontView = null;
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
                    swipeListView.resetScrolling();
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

                        frontView.setClickable(!opened.get(downPosition));

                        if (swipeBackView > 0) {
                            setBackView(child.findViewById(swipeBackView));
                        }
                        break;
                    }
                }
                view.onTouchEvent(motionEvent);
                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (!swiping || downPosition == ListView.INVALID_POSITION) {
                    break;
                }

                float deltaX = previousRawX - downX;
                boolean swap = false;
                boolean swapRight = false;

                if (didRegretSwipe()) {
                    swap = false;
                } else if (Math.abs(deltaX) > swipeThreshold) {
                    swap = true;
                    swapRight = deltaX > 0;
                } else if (swapRight != swipingRight && swipeActionLeft != swipeActionRight) {
                    swap = false;
                } else if (opened.get(downPosition) && openedRight.get(downPosition) && swapRight) {
                    swap = false;
                } else if (opened.get(downPosition) && !openedRight.get(downPosition) && !swapRight) {
                    swap = false;
                } else if (swipeMode == SwipeListView.SWIPE_MODE_LEFT && deltaX > swipeThreshold) {
                    swap = false;
                } else if (swipeMode == SwipeListView.SWIPE_MODE_RIGHT && deltaX < swipeThreshold) {
                    swap = false;
                }

                generateAnimate(frontView, swap, swapRight, downPosition);

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

                // Interaction is done, reset state variables.
                downX = 0;
                previousRawX = 0;
                swiping = false;
                currentSwipeDirection = null;
                initialSwipeDirection = null;

                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (paused || downPosition == ListView.INVALID_POSITION) {
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

                    if (longSwipeEnabled) {
                        swipingRight = deltaX > swipeThreshold && deltaX < longSwipeThreshold;
                        swipingLeft = deltaX < -swipeThreshold && deltaX > -longSwipeThreshold;
                    } else {
                        swipingRight = deltaX > swipeThreshold;
                        swipingLeft = deltaX < -swipeThreshold;
                    }

                    swipingLongRight = deltaX > longSwipeThreshold;
                    swipingLongLeft = deltaX < -longSwipeThreshold;

                    // Changes colors and actions based on swipe direction and length.
                    if (swipingRight && swipeEnabledForDirection(SwipeDirections.RIGHT)) {
                        backView.setBackgroundColor(rightBackgroundColor);

                        if (swipeActionRight == SwipeListView.SWIPE_ACTION_DISMISS) {
                            swipeCurrentAction = SwipeListView.SWIPE_ACTION_DISMISS;
                        } else if (swipeActionRight == SwipeListView.SWIPE_ACTION_REVEAL) {
                            swipeCurrentAction = SwipeListView.SWIPE_ACTION_REVEAL;
                        }
                    } else if (swipingLeft && swipeEnabledForDirection(SwipeDirections.LEFT)) {
                        backView.setBackgroundColor(leftBackgroundColor);

                        if (swipeActionLeft == SwipeListView.SWIPE_ACTION_DISMISS) {
                            swipeCurrentAction = SwipeListView.SWIPE_ACTION_DISMISS;
                        } else if (swipeActionLeft == SwipeListView.SWIPE_ACTION_REVEAL) {
                            swipeCurrentAction = SwipeListView.SWIPE_ACTION_REVEAL;
                        }
                    } else if (swipingLongRight) {
                        if (longSwipeEnabledForDirection(SwipeDirections.RIGHT)) {
                            backView.setBackgroundColor(longRightBackgroundColor);
                        } else {
                            backView.setBackgroundColor(rightBackgroundColor);
                        }

                        if (longSwipeActionRight == SwipeListView.LONG_SWIPE_ACTION_DISMISS) {
                            longSwipeCurrentAction = SwipeListView.LONG_SWIPE_ACTION_DISMISS;
                        } else if (longSwipeActionRight == SwipeListView.LONG_SWIPE_ACTION_REVEAL) {
                            longSwipeCurrentAction = SwipeListView.SWIPE_ACTION_REVEAL;
                        }
                    } else if (swipingLongLeft) {
                        if (longSwipeEnabledForDirection(SwipeDirections.LEFT)) {
                            backView.setBackgroundColor(longLeftBackgroundColor);
                        } else {
                            backView.setBackgroundColor(leftBackgroundColor);
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

                        // Changes colors based on swipe direction change (i.e. "regret").
                        if (didRegretSwipe()) {
                            backView.setBackgroundColor(neutralBackgroundColor);
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

        // TODO: Find out why a thin line of the dismissed view stays on screen.
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
                if (longSwipeMode == SwipeListView.SWIPE_MODE_RIGHT || longSwipeMode == SwipeListView.LONG_SWIPE_MODE_BOTH) {
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

}
