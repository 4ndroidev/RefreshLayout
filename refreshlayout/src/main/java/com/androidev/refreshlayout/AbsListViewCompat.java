package com.androidev.refreshlayout;


import android.os.Build;
import android.widget.AbsListView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

class AbsListViewCompat {

    private Constructor mFlingRunnableConstructor;

    private Field mFlingRunnable;
    private Field mPerformClick;
    private Field mPendingCheckForTap;
    private Field mPendingCheckForLongPress;
    private Field mPendingCheckForKeyLongPress;

    private Method mStartMethod;
    private Method mReportScrollStateChangeMethod;

    AbsListViewCompat() {
        try {
            Class<?> absListViewClass = AbsListView.class;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                mFlingRunnable = absListViewClass.getDeclaredField("mFlingRunnable");
                mFlingRunnable.setAccessible(true);
                mReportScrollStateChangeMethod = absListViewClass.getDeclaredMethod("reportScrollStateChange", Integer.TYPE);
                mReportScrollStateChangeMethod.setAccessible(true);
                Class<?> flingRunnableClass = mFlingRunnable.getType();
                mFlingRunnableConstructor = flingRunnableClass.getDeclaredConstructor(AbsListView.class);
                mFlingRunnableConstructor.setAccessible(true);
                mStartMethod = flingRunnableClass.getDeclaredMethod("start", Integer.TYPE);
                mStartMethod.setAccessible(true);
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                mPerformClick = absListViewClass.getDeclaredField("mPerformClick");
                mPendingCheckForTap = absListViewClass.getDeclaredField("mPendingCheckForTap");
                mPendingCheckForLongPress = absListViewClass.getDeclaredField("mPendingCheckForLongPress");
                mPendingCheckForKeyLongPress = absListViewClass.getDeclaredField("mPendingCheckForKeyLongPress");
                mPerformClick.setAccessible(true);
                mPendingCheckForTap.setAccessible(true);
                mPendingCheckForLongPress.setAccessible(true);
                mPendingCheckForKeyLongPress.setAccessible(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void fling(AbsListView absListView, int velocity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            absListView.fling(velocity);
        } else {
            try {
                Object flingRunnable = mFlingRunnable.get(absListView);
                if (flingRunnable == null) {
                    flingRunnable = mFlingRunnableConstructor.newInstance(absListView);
                    mFlingRunnable.set(absListView, flingRunnable);
                }
                mReportScrollStateChangeMethod.invoke(absListView, AbsListView.OnScrollListener.SCROLL_STATE_FLING);
                mStartMethod.invoke(flingRunnable, velocity);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void cancelClickEvent(AbsListView absListView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            absListView.onCancelPendingInputEvents();
        } else {
            try {
                Runnable performClick = (Runnable) mPerformClick.get(absListView);
                if (performClick != null)
                    absListView.removeCallbacks(performClick);
                Runnable pendingCheckForTap = (Runnable) mPendingCheckForTap.get(absListView);
                if (pendingCheckForTap != null)
                    absListView.removeCallbacks(pendingCheckForTap);
                Runnable pendingCheckForLongPress = (Runnable) mPendingCheckForLongPress.get(absListView);
                if (pendingCheckForLongPress != null)
                    absListView.removeCallbacks(pendingCheckForLongPress);
                Runnable pendingCheckForKeyLongPress = (Runnable) mPendingCheckForKeyLongPress.get(absListView);
                if (pendingCheckForKeyLongPress != null)
                    absListView.removeCallbacks(pendingCheckForKeyLongPress);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}