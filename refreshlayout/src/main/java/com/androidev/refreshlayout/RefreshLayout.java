package com.androidev.refreshlayout;

import android.content.Context;
import android.os.Build;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.OverScroller;
import android.widget.ScrollView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class RefreshLayout extends FrameLayout implements NestedScrollingParent, NestedScrollingChild {

    private static final float DRAGGING_RATE = .5f;
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final float RATIO_OF_HEADER_HEIGHT_TO_REFRESH = 1.2f;
    private static final int DIRECTION_NEGATIVE = -1;

    private static final Interpolator sQuinticInterpolator = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private static final Interpolator sDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

    private View mHeader;
    private View mContent;

    private int mStartOffset;
    private int mCurrentOffset;

    private int mMinimumVelocity;
    private int mMaximumVelocity;

    private int mTouchSlop;
    private int mHeaderHeight;
    private int mRefreshThreshold;
    private int mActivePointerId;

    private float mLastMotionY;

    private boolean canRefresh;
    private boolean isOffset;
    private boolean isRefreshing;
    private boolean isBeingDragged;
    private boolean isPressedCanceled;

    private OverScroller mScroller;
    private FlingHelper mFlingHelper;
    private Method mResetTouchMethod;
    private VelocityTracker mVelocityTracker;
    private AbsListViewFlingCompat mALVFlingCompat;

    private OnRefreshListener mListener;
    private RefreshHeader mRefreshHeader;

    private int mTotalUnconsumed;
    private boolean isNestedEnabled;
    private boolean isNestedScrolling;
    private int[] mParentScrollConsumed = new int[2];
    private int[] mParentOffsetInWindow = new int[2];
    private NestedScrollingParentHelper mNestedScrollingParentHelper;
    private NestedScrollingChildHelper mNestedScrollingChildHelper;

    private Animation mAnimateToStartPosition = new Animation() {
        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            moveToStart(interpolatedTime);
        }
    };

    private Animation mAnimateToRefreshPosition = new Animation() {
        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            moveToRefresh(interpolatedTime);
        }
    };

    private Animation.AnimationListener mAnimationListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            canRefresh = !isRefreshing;
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    };

    public RefreshLayout(Context context) {
        this(context, null);
    }

    public RefreshLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        canRefresh = true;
        mFlingHelper = new FlingHelper();
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mActivePointerId = MotionEvent.INVALID_POINTER_ID;
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mAnimateToStartPosition.setInterpolator(sDecelerateInterpolator);
        mAnimateToStartPosition.setAnimationListener(mAnimationListener);
        mAnimateToRefreshPosition.setInterpolator(sDecelerateInterpolator);
        mAnimateToRefreshPosition.setAnimationListener(mAnimationListener);
        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        setHeader(new DefaultHeader(context));
    }

    private void postInit() {
        if (mContent == null) return;
        isNestedEnabled = ViewCompat.isNestedScrollingEnabled(mContent);
        mALVFlingCompat = new AbsListViewFlingCompat();
        try {
            Class<?> clazz = mContent.getClass();
            if (mContent instanceof RecyclerView) {
                mScroller = new OverScroller(getContext(), sQuinticInterpolator);
                mResetTouchMethod = clazz.getDeclaredMethod("resetTouch");
            } else if (mContent instanceof ScrollView || mContent instanceof NestedScrollView) {
                mScroller = new OverScroller(getContext());
                mResetTouchMethod = clazz.getDeclaredMethod("endDrag");
            } else if (mContent instanceof AbsListView) {
                mScroller = new OverScroller(getContext());
                mResetTouchMethod = clazz.getDeclaredMethod("recycleVelocityTracker");
            }
            mResetTouchMethod.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mContent.setOverScrollMode(OVER_SCROLL_NEVER);
        mContent.setVerticalScrollBarEnabled(false);
        ViewCompat.setNestedScrollingEnabled(mContent, isNestedEnabled);
        ViewCompat.setNestedScrollingEnabled(this, isNestedEnabled);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postInit();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFlingHelper.stop();
    }

    @Override
    protected void onFinishInflate() {
        for (int i = 0, count = getChildCount(); i < count; i++) {
            View child = getChildAt(i);
            if (!(child instanceof RefreshHeader)) {
                mContent = child;
                break;
            }
        }
        super.onFinishInflate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        if (mHeader != null) {
            mHeaderHeight = mHeader.getMeasuredHeight();
            mRefreshThreshold = (int) (mHeaderHeight * RATIO_OF_HEADER_HEIGHT_TO_REFRESH);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = getPaddingLeft();
        int top = getPaddingTop() + mCurrentOffset;
        if (mHeader != null) {
            mHeader.layout(left, top - mHeaderHeight, left + mHeader.getMeasuredWidth(), top);
        }
        if (mContent != null) {
            mContent.layout(left, top, left + mContent.getMeasuredWidth(), top + mContent.getMeasuredHeight());
        }
    }

    private void initVelocityTrackIfNeeded() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void recycleVelocityTrack() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP || !(mContent instanceof AbsListView))
                && ViewCompat.isNestedScrollingEnabled(mContent)) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private void resetTouch() {
        if (!(mContent instanceof RecyclerView)) {
            ViewCompat.stopNestedScroll(mContent);
        }
        if (mResetTouchMethod != null) {
            try {
                mResetTouchMethod.invoke(mContent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean dispatchNestedTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initVelocityTrackIfNeeded();
                break;
            case MotionEvent.ACTION_UP:
                mVelocityTracker.addMovement(ev);
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityX = (int) mVelocityTracker.getXVelocity();
                int velocityY = (int) mVelocityTracker.getYVelocity();
                recycleVelocityTrack();
                boolean handled = mFlingHelper.fling(velocityX, velocityY);
                if (handled) resetTouch();
                return handled || super.dispatchTouchEvent(ev);
        }
        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean dispatchRawTouchEvent(MotionEvent ev) {
        int pointerIndex;
        boolean handled = false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initVelocityTrackIfNeeded();
                isOffset = false;
                isPressedCanceled = false;
                isBeingDragged = !mScroller.isFinished();
                mActivePointerId = ev.getPointerId(0);
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) break;
                mLastMotionY = ev.getY(pointerIndex);
                break;
            case MotionEvent.ACTION_MOVE:
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) break;
                float y = ev.getY(pointerIndex);
                int yDiff = (int) (y - mLastMotionY);
                if (!isBeingDragged) {
                    isBeingDragged = Math.abs(yDiff) >= mTouchSlop;
                    yDiff += yDiff > 0 ? -mTouchSlop : mTouchSlop;
                }
                if (!isBeingDragged) break;
                mLastMotionY = y;
                if (!mContent.canScrollVertically(DIRECTION_NEGATIVE)) {
                    if (yDiff > 0) {
                        if (mCurrentOffset == 0 && canRefresh && mRefreshHeader != null) {
                            mRefreshHeader.onPrepare();
                        }
                        offsetChildren((int) (yDiff * DRAGGING_RATE));
                        if (canRefresh && mRefreshHeader != null) {
                            mRefreshHeader.onPull(mCurrentOffset >= mRefreshThreshold, mCurrentOffset);
                        }
                        if (!isOffset) {
                            isOffset = true;
                            isPressedCanceled = true;
                            MotionEvent cancel = MotionEvent.obtain(
                                    ev.getDownTime(),
                                    ev.getEventTime(),
                                    MotionEvent.ACTION_CANCEL,
                                    ev.getX(),
                                    ev.getY(),
                                    ev.getMetaState());
                            mContent.dispatchTouchEvent(cancel);
                            cancel.recycle();
                        }
                        handled = true;
                    } else if (mCurrentOffset > 0) {
                        if (-yDiff > mCurrentOffset) {
                            mContent.scrollBy(0, -yDiff - mCurrentOffset);
                            offsetChildren(-mCurrentOffset);
                        } else {
                            offsetChildren(yDiff);
                        }
                        if (canRefresh && mRefreshHeader != null) {
                            mRefreshHeader.onPull(mCurrentOffset >= mRefreshThreshold, mCurrentOffset);
                        }
                        if (!isPressedCanceled) {
                            isPressedCanceled = true;
                            MotionEvent cancel = MotionEvent.obtain(
                                    ev.getDownTime(),
                                    ev.getEventTime(),
                                    MotionEvent.ACTION_CANCEL,
                                    ev.getX(),
                                    ev.getY(),
                                    ev.getMetaState());
                            mContent.dispatchTouchEvent(cancel);
                            cancel.recycle();
                        }
                        handled = true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                mVelocityTracker.addMovement(ev);
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityX = (int) mVelocityTracker.getXVelocity();
                int velocityY = (int) mVelocityTracker.getYVelocity();
                recycleVelocityTrack();
                if (isBeingDragged) {
                    if (mCurrentOffset >= mRefreshThreshold) {
                        if (canRefresh) {
                            isRefreshing = true;
                            canRefresh = false;
                            if (mRefreshHeader != null) mRefreshHeader.onStart();
                            if (mListener != null) mListener.onRefresh();
                        }
                        if (isRefreshing) animateOffsetToRefreshPosition();
                        else animateOffsetToStartPosition();
                    } else if (mCurrentOffset > 0 && !isRefreshing) {
                        animateOffsetToStartPosition();
                    } else {
                        if (handled = mFlingHelper.fling(velocityX, velocityY)) {
                            resetTouch();
                        }
                    }
                }
                isBeingDragged = false;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                pointerIndex = ev.getActionIndex();
                if (pointerIndex < 0) {
                    return false;
                }
                mActivePointerId = ev.getPointerId(pointerIndex);
                mLastMotionY = ev.getY(pointerIndex);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                pointerIndex = ev.getActionIndex();
                int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mActivePointerId = ev.getPointerId(newPointerIndex);
                    mLastMotionY = ev.getY(newPointerIndex);
                }
                break;
        }
        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }
        return handled || super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isNestedEnabled) {
            return dispatchNestedTouchEvent(ev);
        } else {
            return dispatchRawTouchEvent(ev);
        }
    }

    private void offsetChildren(int offset) {
        if (offset == 0) return;
        mHeader.offsetTopAndBottom(offset);
        mContent.offsetTopAndBottom(offset);
        mCurrentOffset = mContent.getTop();
    }

    private void dispatchFling(int velocityX, int velocityY) {
        if (mContent instanceof RecyclerView) {
            ((RecyclerView) mContent).fling(-velocityX, -velocityY);
        } else if (mContent instanceof ScrollView) {
            ((ScrollView) mContent).fling(-velocityY);
        } else if (mContent instanceof NestedScrollView) {
            ((NestedScrollView) mContent).fling(-velocityY);
        } else if (mContent instanceof AbsListView) {
            mALVFlingCompat.fling((AbsListView) mContent, -velocityY);
        }
    }

    private void animateOffsetToStartPosition() {
        mStartOffset = mCurrentOffset;
        long duration = (long) Math.min((250.0f * Math.abs(mStartOffset) / mHeaderHeight), 250);
        mAnimateToStartPosition.setDuration(duration);
        clearAnimation();
        startAnimation(mAnimateToStartPosition);
    }

    private void animateOffsetToRefreshPosition() {
        mStartOffset = mCurrentOffset;
        int distance = Math.abs(mStartOffset - mHeaderHeight);
        long duration = (long) Math.min((150.0f * distance / mHeaderHeight), 250);
        mAnimateToRefreshPosition.setDuration(duration);
        clearAnimation();
        startAnimation(mAnimateToRefreshPosition);
    }

    private void moveToStart(float interpolatedTime) {
        offsetChildren((int) (mStartOffset * (1 - interpolatedTime)) - mCurrentOffset);
    }

    private void moveToRefresh(float interpolatedTime) {
        offsetChildren(mStartOffset + (int) ((mHeaderHeight - mStartOffset) * interpolatedTime) - mCurrentOffset);
    }

    private boolean hasChild(View child) {
        return indexOfChild(child) != -1;
    }

    public void setHeader(View header) {
        if (mHeader == header) return;
        if (this.mHeader != null && hasChild(mHeader)) {
            removeView(mHeader);
        }
        this.mHeader = header;
        if (mHeader == null) {
            mRefreshHeader = null;
            return;
        }
        if (header instanceof RefreshHeader)
            mRefreshHeader = (RefreshHeader) header;
        else
            throw new IllegalArgumentException("Header View must implements RefreshHeader!");
        if (header.getLayoutParams() == null)
            header.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(header);
    }

    public void addView(View child, ViewGroup.LayoutParams params) {
        if (getChildCount() > 1) {
            throw new IllegalStateException("RefreshLayout can host only one direct child excluding header");
        }
        super.addView(child, params);
    }

    @SuppressWarnings(value = "unused")
    public boolean isRefreshing() {
        return isRefreshing;
    }

    @SuppressWarnings(value = "unused")
    public void setRefreshing(final boolean refreshing) {
        if (!isRefreshing && refreshing) {
            animateOffsetToRefreshPosition();
            isRefreshing = true;
            if (mRefreshHeader != null) mRefreshHeader.onStart();
        } else if (isRefreshing && !refreshing) {
            if (mRefreshHeader != null) mRefreshHeader.onComplete();
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isBeingDragged && !isNestedScrolling) animateOffsetToStartPosition();
                    isRefreshing = false;
                }
            }, 800);
        }

    }

    @SuppressWarnings(value = "unused")
    public void setOnRefreshListener(OnRefreshListener listener) {
        this.mListener = listener;
    }

    // NestedScrollingParent

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return isEnabled() && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL);
        mTotalUnconsumed = mCurrentOffset;
        isNestedScrolling = true;
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (dy > 0 && mTotalUnconsumed > 0) {
            offsetChildren(-Math.min(mTotalUnconsumed, dy));
            if (canRefresh && mRefreshHeader != null) {
                mRefreshHeader.onPull(mCurrentOffset >= mRefreshThreshold, mCurrentOffset);
            }
            if (dy > mTotalUnconsumed) {
                consumed[1] = dy - mTotalUnconsumed;
                mTotalUnconsumed = 0;
            } else {
                mTotalUnconsumed -= dy;
                consumed[1] = dy;
            }
        }
        final int[] parentConsumed = mParentScrollConsumed;
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, mParentOffsetInWindow);
        int dy = dyUnconsumed + mParentOffsetInWindow[1];
        if (dy < 0 && !mContent.canScrollVertically(DIRECTION_NEGATIVE)) {
            if (mCurrentOffset == 0 && canRefresh && mRefreshHeader != null) {
                mRefreshHeader.onPrepare();
            }
            int offset = (int) (Math.abs(dy) * DRAGGING_RATE);
            mTotalUnconsumed += offset;
            offsetChildren(offset);
            if (canRefresh && mRefreshHeader != null) {
                mRefreshHeader.onPull(mCurrentOffset >= mRefreshThreshold, mCurrentOffset);
            }
        }
    }

    @Override
    public void onStopNestedScroll(View target) {
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        isNestedScrolling = false;
        if (mCurrentOffset >= mRefreshThreshold) {
            if (canRefresh) {
                isRefreshing = true;
                canRefresh = false;
                if (mRefreshHeader != null) mRefreshHeader.onStart();
                if (mListener != null) mListener.onRefresh();
            }
            if (isRefreshing) animateOffsetToRefreshPosition();
            else animateOffsetToStartPosition();
        } else if (!isRefreshing) {
            animateOffsetToStartPosition();
        }
        stopNestedScroll();
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    // NestedScrollingChild

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    @SuppressWarnings(value = "all")
    public interface OnRefreshListener {
        void onRefresh();
    }

    @SuppressWarnings(value = "all")
    public interface RefreshHeader {

        void onPrepare();

        void onStart();

        void onComplete();

        void onPull(boolean willRefresh, int offset);

    }

    private class FlingHelper implements Runnable {

        private int mVelocityX;
        private int mVelocityY;
        private int mLastFlingY;

        private FlingHelper() {
        }

        @Override
        public void run() {
            if (mVelocityY < 0) {
                if (mScroller.computeScrollOffset() && mCurrentOffset > 0) {
                    int offset = Math.max(mScroller.getCurrY() - mLastFlingY, -mCurrentOffset);
                    offsetChildren(offset);
                    mLastFlingY = mScroller.getCurrY();
                    postOnAnimation(this);
                } else if (!mScroller.isFinished()) {
                    dispatchFling(mVelocityX, mVelocityY);
                }
            } else {
                if (mCurrentOffset >= mHeaderHeight || mScroller.isFinished()) {
                    mScroller.forceFinished(true);
                } else if (isRefreshing && !mScroller.isFinished() && mScroller.computeScrollOffset()) {
                    if (!mContent.canScrollVertically(DIRECTION_NEGATIVE)) {
                        offsetChildren(Math.min(mHeaderHeight - mCurrentOffset, mScroller.getCurrY() - mLastFlingY));
                    }
                    mLastFlingY = mScroller.getCurrY();
                    postOnAnimation(this);
                }
            }
        }

        private void stop() {
            if (mScroller != null) mScroller.forceFinished(true);
            removeCallbacks(this);
        }

        private boolean fling(int velocityX, int velocityY) {
            mVelocityX = velocityX;
            mVelocityY = velocityY;
            mLastFlingY = 0;
            mScroller.fling(0, 0, mVelocityX, mVelocityY, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
            int distance = Math.abs(mScroller.getFinalY());
            if (mVelocityY < -mMinimumVelocity) {
                if (mCurrentOffset > 0) {
                    if (distance > mCurrentOffset || isRefreshing) {
                        postOnAnimation(this);
                    } else {
                        mScroller.forceFinished(true);
                        animateOffsetToStartPosition();
                    }
                    return true;
                } else if (!isNestedEnabled && isOffset && mCurrentOffset == 0) {
                    dispatchFling(mVelocityX, mVelocityY);
                    return true;
                }
            } else if (mVelocityY > mMinimumVelocity) {
                if (isRefreshing && distance > mContent.getScrollY()) {
                    dispatchFling(mVelocityX, mVelocityY);
                    postOnAnimation(this);
                    return true;
                }
            }
            return false;
        }
    }

    private class AbsListViewFlingCompat {

        private Field mFlingRunnableField;
        private Constructor mFlingRunnableConstructor;
        private Method mReportScrollStateChangeMethod;
        private Method mStartMethod;

        private AbsListViewFlingCompat() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) return;
            try {
                Class<?> absListViewClass = AbsListView.class;
                mFlingRunnableField = absListViewClass.getDeclaredField("mFlingRunnable");
                mFlingRunnableField.setAccessible(true);
                mReportScrollStateChangeMethod = absListViewClass.getDeclaredMethod("reportScrollStateChange", Integer.TYPE);
                mReportScrollStateChangeMethod.setAccessible(true);
                Class<?> flingRunnableClass = mFlingRunnableField.getType();
                mFlingRunnableConstructor = flingRunnableClass.getDeclaredConstructor(AbsListView.class);
                mFlingRunnableConstructor.setAccessible(true);
                mStartMethod = flingRunnableClass.getDeclaredMethod("start", Integer.TYPE);
                mStartMethod.setAccessible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void fling(AbsListView absListView, int velocity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                absListView.fling(velocity);
            } else {
                try {
                    Object flingRunnable = mFlingRunnableField.get(absListView);
                    if (flingRunnable == null) {
                        flingRunnable = mFlingRunnableConstructor.newInstance(absListView);
                        mFlingRunnableField.set(absListView, flingRunnable);
                    }
                    mReportScrollStateChangeMethod.invoke(absListView, AbsListView.OnScrollListener.SCROLL_STATE_FLING);
                    mStartMethod.invoke(flingRunnable, velocity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
