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
    private boolean isAnimating;
    private boolean isRefreshing;
    private boolean isBeingDragged;
    private boolean isClickCanceled;

    private OverScroller mScroller;
    private FlingHelper mFlingHelper;
    private Method mResetTouchMethod;
    private VelocityTracker mVelocityTracker;
    private AbsListViewCompat mAbsListViewCompat;

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
            isAnimating = true;
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            isAnimating = false;
            canRefresh = !isRefreshing && mCurrentOffset == 0;
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
        try {
            if (mContent instanceof RecyclerView) {
                mScroller = new OverScroller(getContext(), sQuinticInterpolator);
                mResetTouchMethod = RecyclerView.class.getDeclaredMethod("resetTouch");
            } else if (mContent instanceof ScrollView) {
                mScroller = new OverScroller(getContext());
                mResetTouchMethod = ScrollView.class.getDeclaredMethod("endDrag");
            } else if (mContent instanceof NestedScrollView) {
                mScroller = new OverScroller(getContext());
                mResetTouchMethod = NestedScrollView.class.getDeclaredMethod("endDrag");
            } else if (mContent instanceof AbsListView) {
                mScroller = new OverScroller(getContext());
                mResetTouchMethod = AbsListView.class.getDeclaredMethod("recycleVelocityTracker");
                mAbsListViewCompat = new AbsListViewCompat();
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

    // call content view reset touch method after intercepting touch up event to fling
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

    private void cancelClickEvent(MotionEvent ev) {
        if (isClickCanceled) return;
        isClickCanceled = true;
        if (mContent instanceof AbsListView) {
            mAbsListViewCompat.cancelClickEvent((AbsListView) mContent);
        } else if (mContent instanceof ScrollView) {
            super.dispatchTouchEvent(ev);
        }
    }

    /**
     * nested-scroll supported view list:
     * {@link android.support.v7.widget.RecyclerView}
     * {@link android.support.v4.widget.NestedScrollView}
     * <p>
     * only care about touch up event, intercept it to perform fling, when nested scroll is enabled
     * because we can make header visible and invisible by {@link #onNestedScroll} and {@link #onNestedPreScroll}
     */
    private boolean dispatchNestedTouchEvent(MotionEvent ev) {
        boolean handled = false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initVelocityTrackIfNeeded();
                break;
            case MotionEvent.ACTION_UP:
                mVelocityTracker.addMovement(ev);
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityY = (int) mVelocityTracker.getYVelocity();
                recycleVelocityTrack();
                handled = mFlingHelper.fling(velocityY);
                if (handled) resetTouch();
        }
        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }
        return handled || super.dispatchTouchEvent(ev);
    }

    /**
     * nested-scroll not supported view list:
     * {@link android.widget.ScrollView} SDK_INT < LOLLIPOP
     * {@link android.widget.ListView}
     * {@link android.widget.GridView}
     * <p>
     * intercept move event to make header visible and invisible when content view can't scroll up
     */
    private boolean dispatchRawTouchEvent(MotionEvent ev) {
        int pointerIndex;
        boolean handled = false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initVelocityTrackIfNeeded();
                isOffset = false;
                isClickCanceled = false;
                isBeingDragged = isAnimating || !mScroller.isFinished();
                if (isBeingDragged) {
                    clearAnimation();
                }
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
                if (!isBeingDragged && Math.abs(yDiff) >= mTouchSlop) {
                    isBeingDragged = true;
                    yDiff += yDiff > 0 ? -mTouchSlop : mTouchSlop;
                }
                if (!isBeingDragged) break;
                mLastMotionY = y;
                // intercept move event to offset header when content view can't scroll up
                if (!mContent.canScrollVertically(DIRECTION_NEGATIVE)) {
                    // pulling down
                    if (yDiff > 0) {
                        if (mCurrentOffset == 0 && canRefresh && mRefreshHeader != null) {
                            mRefreshHeader.onPrepare();
                        }
                        // make header visible
                        offsetChildren((int) (yDiff * DRAGGING_RATE));
                        if (canRefresh && mRefreshHeader != null) {
                            mRefreshHeader.onPull(mCurrentOffset >= mRefreshThreshold, mCurrentOffset);
                        }

                        if (!isOffset) {
                            isOffset = true;
                            cancelClickEvent(ev);
                        }
                        handled = true;
                    } else if (mCurrentOffset > 0) {
                        // pulling up
                        // make header invisible
                        if (-yDiff > mCurrentOffset) {
                            mContent.scrollBy(0, -yDiff - mCurrentOffset);
                            offsetChildren(-mCurrentOffset);
                        } else {
                            offsetChildren(yDiff);
                        }
                        if (canRefresh && mRefreshHeader != null) {
                            mRefreshHeader.onPull(mCurrentOffset >= mRefreshThreshold, mCurrentOffset);
                        }
                        cancelClickEvent(ev);
                        handled = true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                mVelocityTracker.addMovement(ev);
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
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
                        handled = true;
                    } else if (!isRefreshing && mCurrentOffset > 0) {
                        animateOffsetToStartPosition();
                        handled = true;
                    } else {
                        canRefresh = !isRefreshing && mCurrentOffset == 0;
                        handled = mFlingHelper.fling(velocityY);
                    }
                }
                if (handled) resetTouch();
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
        mHeader.bringToFront();
    }

    private void dispatchFling(int velocityY) {
        if (mContent instanceof RecyclerView) {
            ((RecyclerView) mContent).fling(0, -velocityY);
        } else if (mContent instanceof ScrollView) {
            ((ScrollView) mContent).fling(-velocityY);
        } else if (mContent instanceof NestedScrollView) {
            ((NestedScrollView) mContent).fling(-velocityY);
        } else if (mContent instanceof AbsListView) {
            mAbsListViewCompat.fling((AbsListView) mContent, -velocityY);
        }
    }

    private void animateOffsetToStartPosition() {
        mStartOffset = mCurrentOffset;
        long duration = (long) Math.min((250.0f * Math.abs(mStartOffset) / mHeaderHeight), 250);
        if (duration == 0) {
            canRefresh = !isRefreshing && mCurrentOffset == 0;
            return;
        }
        mAnimateToStartPosition.setDuration(duration);
        clearAnimation();
        startAnimation(mAnimateToStartPosition);
    }

    private void animateOffsetToRefreshPosition() {
        mStartOffset = mCurrentOffset;
        int distance = Math.abs(mStartOffset - mHeaderHeight);
        long duration = (long) Math.min((150.0f * distance / mHeaderHeight), 250);
        if (duration == 0) return;
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

    public void setHeader(View header) {
        if (mHeader == header) return;
        if (this.mHeader != null && indexOfChild(mHeader) != -1) {
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
        // set it to current offset
        // because maybe content view will get a touch down event while flinging hasn't been completed
        mTotalUnconsumed = mCurrentOffset;
        isNestedScrolling = true;
        isOffset = mCurrentOffset > 0;
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (dy > 0 && mTotalUnconsumed > 0) {
            // make header invisible
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
            // make header visible
            offsetChildren(offset);
            if (!isOffset) {
                isOffset = true;
            }
            if (canRefresh && mRefreshHeader != null) {
                mRefreshHeader.onPull(mCurrentOffset >= mRefreshThreshold, mCurrentOffset);
            }
        }
    }

    @Override
    public void onStopNestedScroll(View target) {
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        isNestedScrolling = false;
        isOffset = false;
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
            if (mCurrentOffset > 0)
                animateOffsetToStartPosition();
            else if (mCurrentOffset == 0) {
                canRefresh = true;
            }
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

        private int mVelocity;
        private int mLastFlingY;

        private FlingHelper() {
        }

        @Override
        public void run() {
            // scroll down, dispatch fling after offsetting header to zero
            if (mVelocity < 0) {
                if (mScroller.computeScrollOffset() && mCurrentOffset > 0) {
                    int offset = Math.max(mScroller.getCurrY() - mLastFlingY, -mCurrentOffset);
                    offsetChildren(offset);
                    mLastFlingY = mScroller.getCurrY();
                    postOnAnimation(this);
                } else if (!mScroller.isFinished()) {
                    dispatchFling((int) -mScroller.getCurrVelocity());
                    mScroller.forceFinished(true);
                }
            } else {
                // scroll up, if it is refreshing, make header visible after content view can't scroll up
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

        private boolean fling(int velocity) {
            mVelocity = velocity;
            mLastFlingY = 0;
            mScroller.fling(0, 0, 0, mVelocity, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
            int distance = Math.abs(mScroller.getFinalY());
            if (mVelocity < -mMinimumVelocity) {
                if (mCurrentOffset > 0) {
                    if (distance > mCurrentOffset || isRefreshing) {
                        postOnAnimation(this);
                    } else {
                        mScroller.forceFinished(true);
                        animateOffsetToStartPosition();
                    }
                    return true;
                } else if (!isNestedEnabled && isOffset && mCurrentOffset == 0) {
                    mScroller.forceFinished(true);
                    dispatchFling(mVelocity);
                    return true;
                }
            } else if (mVelocity > mMinimumVelocity) {
                if (isRefreshing && distance > mContent.getScrollY()) {
                    dispatchFling(mVelocity);
                    postOnAnimation(this);
                    return true;
                }
            }
            mScroller.forceFinished(true);
            return false;
        }
    }

}
