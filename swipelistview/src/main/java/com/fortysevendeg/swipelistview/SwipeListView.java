/*
 * Copyright (C) 2013 47 Degrees, LLC
 * http://47deg.com
 * hello@47deg.com
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
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.util.List;

/**
 * ListView subclass that provides the swipe functionality
 */
public class SwipeListView extends ListView {

    /**
     * Used when user want change swipe list mode on some rows
     */
    public final static int SWIPE_MODE_DEFAULT = -1;

    /**
     * Disables all swipes
     */
    public final static int SWIPE_MODE_NONE = 0;

    /**
     * Enables both left and right swipe
     */
    public final static int SWIPE_MODE_BOTH = 1;

    /**
     * Enables right swipe
     */
    public final static int SWIPE_MODE_RIGHT = 2;

    /**
     * Enables left swipe
     */
    public final static int SWIPE_MODE_LEFT = 3;

    /**
     * Enables left long swipe.
     */
    public final static int LONG_SWIPE_MODE_LEFT = 1;

    /**
     * Enables right long swipe.
     */
    public final static int LONG_SWIPE_MODE_RIGHT = 2;

    /**
     * Enables both left and right long swipe.
     */
    public final static int LONG_SWIPE_MODE_BOTH = 3;

    /**
     * Binds the swipe gesture to reveal a view behind the row (Drawer style)
     */
    public final static int SWIPE_ACTION_REVEAL = 0;

    /**
     * Dismisses the cell when swiped over
     */
    public final static int SWIPE_ACTION_DISMISS = 1;

    /**
     * No action when swiped
     */
    public final static int SWIPE_ACTION_NONE = 2;

    /**
     * Binds the swipe gesture to reveal a view behind the row (Drawer style)
     */
    public final static int LONG_SWIPE_ACTION_REVEAL = 0;

    /**
     * Dismisses the cell when swiped over
     */
    public final static int LONG_SWIPE_ACTION_DISMISS = 1;

    /**
     * No action when long swiped
     */
    public final static int LONG_SWIPE_ACTION_NONE = 2;

    /**
     * Default ids for front view
     */
    public final static String SWIPE_DEFAULT_FRONT_VIEW = "swipelist_frontview";

    /**
     * Default id for back view
     */
    public final static String SWIPE_DEFAULT_BACK_VIEW = "swipelist_backview";

    /**
     * Default id for back view icon
     */
    public final static String SWIPE_DEFAULT_BACK_VIEW_ICON = "swipelist_backview_icon";

    int swipeFrontView = 0;
    int swipeBackView = 0;
    int swipeBackIconLeft = 0;
    int swipeBackIconRight = 0;

    /**
     * Internal listener for common swipe events
     */
    private SwipeListViewListener swipeListViewListener;

    /**
     * Internal touch listener
     */
    private SwipeListViewTouchListener touchListener;

    /**
     * ViewPager reference to handle touch conflicts.
     */
    private DynamicViewPager mViewPager;

    /**
     * Controls swiping state.
     */
    protected boolean isSwiping;

    /**
     * If you create a View programmatically you need send back and front identifier
     *
     * @param context        Context
     * @param swipeBackView  Back Identifier
     * @param swipeFrontView Front Identifier
     */
    public SwipeListView(Context context, int swipeBackView, int swipeFrontView, int swipeBackIconLeft, int swipeBackIconRight) {
        super(context);
        this.swipeFrontView = swipeFrontView;
        this.swipeBackView = swipeBackView;
        this.swipeBackIconLeft = swipeBackIconLeft;
        this.swipeBackIconRight = swipeBackIconRight;
        init(null);
    }

    /**
     * @see android.widget.ListView#ListView(android.content.Context, android.util.AttributeSet)
     */
    public SwipeListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    /**
     * @see android.widget.ListView#ListView(android.content.Context, android.util.AttributeSet, int)
     */
    public SwipeListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    /**
     * Init ListView
     *
     * @param attrs AttributeSet
     */
    private void init(AttributeSet attrs) {

        int swipeMode = SWIPE_MODE_BOTH;
        boolean swipeCloseAllItemsWhenMoveList = true;
        long swipeAnimationTime = 0;
        float swipeOffsetLeft = 0;
        float swipeOffsetRight = 0;
        int swipeDrawableChecked = 0;
        int swipeDrawableUnchecked = 0;

        int swipeActionLeft = SWIPE_ACTION_REVEAL;
        int swipeActionRight = SWIPE_ACTION_REVEAL;

        if (attrs != null) {
            TypedArray styled = getContext().obtainStyledAttributes(attrs, R.styleable.SwipeListView);
            swipeMode = styled.getInt(R.styleable.SwipeListView_swipeMode, SWIPE_MODE_BOTH);
            swipeActionLeft = styled.getInt(R.styleable.SwipeListView_swipeActionLeft, SWIPE_ACTION_REVEAL);
            swipeActionRight = styled.getInt(R.styleable.SwipeListView_swipeActionRight, SWIPE_ACTION_REVEAL);
            swipeOffsetLeft = styled.getDimension(R.styleable.SwipeListView_swipeOffsetLeft, 0);
            swipeOffsetRight = styled.getDimension(R.styleable.SwipeListView_swipeOffsetRight, 0);
            swipeAnimationTime = styled.getInteger(R.styleable.SwipeListView_swipeAnimationTime, 0);
            swipeCloseAllItemsWhenMoveList = styled.getBoolean(R.styleable.SwipeListView_swipeCloseAllItemsWhenMoveList, true);
            swipeDrawableChecked = styled.getResourceId(R.styleable.SwipeListView_swipeDrawableChecked, 0);
            swipeDrawableUnchecked = styled.getResourceId(R.styleable.SwipeListView_swipeDrawableUnchecked, 0);
            swipeFrontView = styled.getResourceId(R.styleable.SwipeListView_swipeFrontView, 0);
            swipeBackView = styled.getResourceId(R.styleable.SwipeListView_swipeBackView, 0);
            swipeBackIconLeft = styled.getResourceId(R.styleable.SwipeListView_swipeBackIconLeft, 0);
            swipeBackIconRight = styled.getResourceId(R.styleable.SwipeListView_swipeBackIconRight, 0);
        }

        if (swipeFrontView == 0 || swipeBackView == 0) {
            swipeFrontView = getContext().getResources().getIdentifier(SWIPE_DEFAULT_FRONT_VIEW, "id", getContext().getPackageName());
            swipeBackView = getContext().getResources().getIdentifier(SWIPE_DEFAULT_BACK_VIEW, "id", getContext().getPackageName());
            swipeBackIconLeft = getContext().getResources().getIdentifier(SWIPE_DEFAULT_BACK_VIEW_ICON, "id", getContext().getPackageName());

            if (swipeFrontView == 0 || swipeBackView == 0 || swipeBackIconLeft == 0) {
                throw new RuntimeException(String.format("You forgot the attributes swipeFrontView, swipeBackView or swipeBackIconLeft. You can add this attributes or use '%s' and '%s' identifiers", SWIPE_DEFAULT_FRONT_VIEW, SWIPE_DEFAULT_BACK_VIEW));
            }
        }

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        touchListener = new SwipeListViewTouchListener(this, swipeFrontView, swipeBackView, swipeBackIconLeft, swipeBackIconRight);
        if (swipeAnimationTime > 0) {
            touchListener.setAnimationTime(swipeAnimationTime);
        }
        touchListener.setRightOffset(swipeOffsetRight);
        touchListener.setLeftOffset(swipeOffsetLeft);
        touchListener.setSwipeActionLeft(swipeActionLeft);
        touchListener.setSwipeActionRight(swipeActionRight);
        touchListener.setSwipeMode(swipeMode);
        touchListener.setSwipeClosesAllItemsWhenListMoves(swipeCloseAllItemsWhenMoveList);
        setOnTouchListener(touchListener);
        setOnScrollListener(touchListener.makeScrollListener());
    }

    /**
     * Get positions selected
     *
     * @return
     */
    public List<Integer> getPositionsSelected() {
        return touchListener.getPositionsSelected();
    }

    /**
     * Count selected
     *
     * @return
     */
    public int getCountSelected() {
        return touchListener.getCountSelected();
    }

    /**
     * @see android.widget.ListView#setAdapter(android.widget.ListAdapter)
     */
    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
        touchListener.resetItems();
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                onListChanged();
                touchListener.resetItems();
            }
        });
    }

    /**
     * Dismiss item
     *
     * @param position Position that you want open
     */
    public void dismiss(int position) {
        int height = touchListener.dismiss(position);
        if (height > 0) {
            touchListener.handlerPendingDismisses(height);
        } else {
            int[] dismissPositions = new int[1];
            dismissPositions[0] = position;
            onDismiss(dismissPositions);
            touchListener.resetPendingDismisses();
        }
    }

    /**
     * Dismiss items selected
     */
    public void dismissSelected() {
        List<Integer> list = touchListener.getPositionsSelected();
        int[] dismissPositions = new int[list.size()];
        int height = 0;
        for (int i = 0; i < list.size(); i++) {
            int position = list.get(i);
            dismissPositions[i] = position;
            int auxHeight = touchListener.dismiss(position);
            if (auxHeight > 0) {
                height = auxHeight;
            }
        }
        if (height > 0) {
            touchListener.handlerPendingDismisses(height);
        } else {
            onDismiss(dismissPositions);
            touchListener.resetPendingDismisses();
        }
    }

    /**
     * Open ListView's item
     *
     * @param position Position that you want open
     */
    public void openAnimate(int position) {
        touchListener.openAnimate(position);
    }

    /**
     * Close ListView's item
     *
     * @param position Position that you want open
     */
    public void closeAnimate(int position) {
        touchListener.closeAnimate(position);
    }

    /**
     * Notifies onDismiss
     *
     * @param reverseSortedPositions All dismissed positions
     */
    protected void onDismiss(int[] reverseSortedPositions) {
        if (swipeListViewListener != null) {
            swipeListViewListener.onDismiss(reverseSortedPositions);
        }
    }

    /**
     * Start open item
     *
     * @param position list item
     * @param action   current action
     * @param right    to right
     */
    protected void onStartOpen(int position, int action, boolean right) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onStartOpen(position, action, right);
        }
    }

    /**
     * Start close item
     *
     * @param position list item
     * @param right
     */
    protected void onStartClose(int position, boolean right) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onStartClose(position, right);
        }
    }

    /**
     * Notifies onClickFrontView
     *
     * @param view     view clicked
     * @param position item clicked
     */
    protected void onClickFrontView(View view, int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onClickFrontView(view, position);
        }
    }

    /**
     * Notifies onClickCheckbox
     *
     * @param view     view clicked
     * @param position item clicked
     */
    protected void onClickCheckbox(View view, int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onClickCheckbox(view, position);
        }
    }

    /**
     * Notifies onClickNumber
     *
     * @param view     view clicked
     * @param position item clicked
     */
    protected void onClickNumber(View view, int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onClickNumber(view, position);
        }
    }

    /**
     * Notifies onClickBackView
     *
     * @param view     view clicked
     * @param position item clicked
     */
    protected void onClickBackView(View view, int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onClickBackView(view, position);
        }
    }

    /**
     * Notifies onOpened
     *
     * @param position Item opened
     * @param toRight  If should be opened toward the right
     */
    protected void onOpened(int position, boolean toRight) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onOpened(position, toRight);
        }
    }

    /**
     * Notifies onClosed
     *
     * @param position  Item closed
     * @param fromRight If open from right
     */
    protected void onClosed(int position, boolean fromRight) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onClosed(position, fromRight);
        }
    }

    /**
     * Notifies onChoiceChanged
     *
     * @param position position that choice
     * @param selected if item is selected or not
     */
    protected void onChoiceChanged(int position, boolean selected) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onChoiceChanged(position, selected);
        }
    }

    /**
     * User start choice items
     */
    protected void onChoiceStarted() {
        if (swipeListViewListener != null) {
            swipeListViewListener.onChoiceStarted();
        }
    }

    /**
     * User end choice items
     */
    protected void onChoiceEnded() {
        if (swipeListViewListener != null) {
            swipeListViewListener.onChoiceEnded();
        }
    }

    /**
     * User is in first item of list
     */
    protected void onFirstListItem() {
        if (swipeListViewListener != null) {
            swipeListViewListener.onFirstListItem();
        }
    }

    /**
     * User is in last item of list
     */
    protected void onLastListItem() {
        if (swipeListViewListener != null) {
            swipeListViewListener.onLastListItem();
        }
    }

    /**
     * Notifies onListChanged
     */
    protected void onListChanged() {
        if (swipeListViewListener != null) {
            swipeListViewListener.onListChanged();
        }
    }

    /**
     * Notifies onMove
     *
     * @param position Item moving
     */
    protected void onMove(int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onMove(position);
            isSwiping = true;
        }
    }

    /**
     * Notifies onMoveEnded
     *
     * @param position Item moving
     */
    protected void onMoveEnded(int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onMoveEnded(position);
            isSwiping = false;
        }
    }

    /**
     * Called when an item has been swiped to the right.
     *
     * @param position Position of the item.
     */
    protected void onFinishedSwipeRight(int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onFinishedSwipeRight(position);
        }
    }

    /**
     * Called when an item has been swiped to the left.
     *
     * @param position Position of the item.
     */
    protected void onFinishedSwipeLeft(int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onFinishedSwipeLeft(position);
        }
    }

    /**
     * Called when an item has been long swiped to the right.
     *
     * @param position Position of the item.
     */
    protected void onFinishedLongSwipeRight(int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onFinishedLongSwipeRight(position);
        }
    }

    /**
     * Called when an item has been long swiped to the left.
     *
     * @param position Position of the item.
     */
    protected void onFinishedLongSwipeLeft(int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onFinishedLongSwipeLeft(position);
        }
    }

    protected int changeSwipeMode(int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            return swipeListViewListener.onChangeSwipeMode(position);
        }
        return SWIPE_MODE_DEFAULT;
    }

    /**
     * Sets the Listener
     *
     * @param swipeListViewListener Listener
     */
    public void setSwipeListViewListener(SwipeListViewListener swipeListViewListener) {
        this.swipeListViewListener = swipeListViewListener;
    }

    /**
     * Set offset on right
     *
     * @param offsetRight Offset
     */
    public void setOffsetRight(float offsetRight) {
        touchListener.setRightOffset(offsetRight);
    }

    /**
     * Set offset on left
     *
     * @param offsetLeft Offset
     */
    public void setOffsetLeft(float offsetLeft) {
        touchListener.setLeftOffset(offsetLeft);
    }

    /**
     * Set if all items opened will be closed when the user moves the ListView
     *
     * @param swipeCloseAllItemsWhenMoveList
     */
    public void setSwipeCloseAllItemsWhenMoveList(boolean swipeCloseAllItemsWhenMoveList) {
        touchListener.setSwipeClosesAllItemsWhenListMoves(swipeCloseAllItemsWhenMoveList);
    }

    /**
     * Set swipe mode
     *
     * @param swipeMode
     */
    public void setSwipeMode(int swipeMode) {
        touchListener.setSwipeMode(swipeMode);
    }

    /**
     * Set long swipe mode
     *
     * @param longSwipeMode
     */
    public void setLongSwipeMode(int longSwipeMode) {
        touchListener.setLongSwipeMode(longSwipeMode);
    }

    /**
     * Return action on left
     *
     * @return Action
     */
    public int getSwipeActionLeft() {
        return touchListener.getSwipeActionLeft();
    }

    /**
     * Set action on left
     *
     * @param swipeActionLeft Action
     */
    public void setSwipeActionLeft(int swipeActionLeft) {
        touchListener.setSwipeActionLeft(swipeActionLeft);
    }

    /**
     * Return action on right
     *
     * @return Action
     */
    public int getSwipeActionRight() {
        return touchListener.getSwipeActionRight();
    }

    /**
     * Set action on right
     *
     * @param swipeActionRight Action
     */
    public void setSwipeActionRight(int swipeActionRight) {
        touchListener.setSwipeActionRight(swipeActionRight);
    }

    /**
     * Set long action on left
     *
     * @param longSwipeActionLeft Action
     */
    public void setLongSwipeActionLeft(int longSwipeActionLeft) {
        touchListener.setLongSwipeActionLeft(longSwipeActionLeft);
    }

    /**
     * Set long action on right
     *
     * @param longSwipeActionRight Action
     */
    public void setLongSwipeActionRight(int longSwipeActionRight) {
        touchListener.setLongSwipeActionRight(longSwipeActionRight);
    }

    /**
     * Sets animation time when user drops cell
     *
     * @param animationTime milliseconds
     */
    public void setAnimationTime(long animationTime) {
        touchListener.setAnimationTime(animationTime);
    }

    /**
     * Determines the swiping state.
     *
     * @return True if the list is swiping.
     */
    public boolean isSwiping() {
        return isSwiping;
    }

    /**
     * @return Swipe touch listener.
     */
    protected SwipeListViewTouchListener getTouchListener() {
        return touchListener;
    }

    /**
     * @see android.widget.ListView#onInterceptTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = MotionEventCompat.getActionMasked(ev);

        if (isEnabled() && touchListener.isSwipeEnabled()) {
            switch (action) {
                case MotionEvent.ACTION_MOVE:
                    return getTouchListener().getDownPosition() != -1;
                case MotionEvent.ACTION_DOWN:
                    super.onInterceptTouchEvent(ev);
                    touchListener.onTouch(this, ev);
                    return false;
                case MotionEvent.ACTION_UP:
                    touchListener.onTouch(this, ev);
                    return false;
            }
        }

        return super.onInterceptTouchEvent(ev);
    }

    /**
     * Close all opened items
     */
    public void closeOpenedItems() {
        touchListener.closeOpenedItems();
    }

    /**
     * Sets ViewPager reference to handle touch conflicts.
     *
     * @param viewPager ViewPager being used.
     */
    public void setViewPager(DynamicViewPager viewPager) {
        mViewPager = viewPager;
    }

    /**
     * @return ViewPager reference to handle touch conflicts.
     */
    public DynamicViewPager getViewPager() {
        return mViewPager;
    }

}
