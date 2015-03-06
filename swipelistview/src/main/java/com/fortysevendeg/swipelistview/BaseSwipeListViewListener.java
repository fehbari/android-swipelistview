package com.fortysevendeg.swipelistview;

import android.view.View;

public class BaseSwipeListViewListener implements SwipeListViewListener {
    @Override
    public void onOpened(int position, boolean toRight) {
    }

    @Override
    public void onClosed(int position, boolean fromRight) {
    }

    @Override
    public void onListChanged() {
    }

    @Override
    public void onMove(int position) {
    }

    @Override
    public void onMoveEnded(int position) {
    }

    @Override
    public void onStartOpen(int position, int action, boolean right) {
    }

    @Override
    public void onStartClose(int position, boolean right) {
    }

    @Override
    public void onClickFrontView(View view, int position) {
    }

    @Override
    public void onClickCheckbox(View view, int position) {
    }

    @Override
    public void onClickNumber(View view, int position) {
    }

    @Override
    public void onClickBackView(View view, int position) {
    }

    @Override
    public void onDismiss(int[] reverseSortedPositions) {
    }

    @Override
    public int onChangeSwipeMode(int position) {
        return SwipeListView.SWIPE_MODE_DEFAULT;
    }

    @Override
    public void onChoiceChanged(int position, boolean selected) {
    }

    @Override
    public void onChoiceStarted() {
    }

    @Override
    public void onChoiceEnded() {
    }

    @Override
    public void onFirstListItem() {
    }

    @Override
    public void onLastListItem() {
    }

    @Override
    public void onFinishedSwipeRight(int position) {
    }

    @Override
    public void onFinishedSwipeLeft(int position) {
    }

    @Override
    public void onFinishedLongSwipeRight(int position) {
    }

    @Override
    public void onFinishedLongSwipeLeft(int position) {
    }
}
