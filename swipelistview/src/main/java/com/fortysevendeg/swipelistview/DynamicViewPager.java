package com.fortysevendeg.swipelistview;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * Custom ViewPager that handles conflict with swiping cells.
 *
 * @author Felipe Bari
 */
public class DynamicViewPager extends ViewPager {

    private boolean mSwipeable = true;

    public DynamicViewPager(Context context) {
        super(context);
    }

    public DynamicViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent arg0) {
        return mSwipeable && super.onInterceptTouchEvent(arg0);
    }

    public void setSwipeable(boolean swipeable) {
        mSwipeable = swipeable;
    }

}
