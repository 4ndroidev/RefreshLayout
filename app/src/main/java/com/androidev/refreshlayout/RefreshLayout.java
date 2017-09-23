package com.androidev.refreshlayout;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.OverScroller;

/**
 * refer to {@link android.support.v4.widget.SwipeRefreshLayout}
 */

public class RefreshLayout extends FrameLayout {

    private static final float DRAGGING_RESISTANCE = 2.5f;
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final float RATIO_OF_HEADER_HEIGHT_TO_REFRESH = 1.2f;
    private static final int DIRECTION_POSITIVE = 1;
    private static final int DIRECTION_NEGATIVE = -1;

    private static final Interpolator sQuinticInterpolator = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private VelocityTracker mVelocityTracker;
    private int mMaximumVelocity;
    private int mMinimumVelocity;
    private ViewFlinger mViewFlinger;

    private View mHeader;
    private View mContent;

    private int mFrom;
    private int mTouchSlop;
    private int mHeaderHeight;
    private int mRefreshThreshold;

    private int mActivePointerId;

    private int mTotalOffset;
    private float mLastMotionX;
    private float mLastMotionY;

    private boolean canRefresh;
    private boolean isRefreshing;
    private boolean isBeingDragged;
    private boolean isHandler;

    private OnRefreshListener mListener;
    private RefreshUIHandler mUIHandler;

    private final Interpolator mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

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
        setWillNotDraw(false);
        mViewFlinger = new ViewFlinger();
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mActivePointerId = MotionEvent.INVALID_POINTER_ID;
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mAnimateToStartPosition.setInterpolator(mDecelerateInterpolator);
        mAnimateToRefreshPosition.setInterpolator(mDecelerateInterpolator);
    }

    @Override
    protected void onFinishInflate() {
        final int childCount = getChildCount();
        if (childCount == 2) {
            if (mContent == null || mHeader == null) {
                View child1 = getChildAt(0);
                View child2 = getChildAt(1);
                if (child1 instanceof RefreshUIHandler) {
                    mHeader = child1;
                    mContent = child2;
                    setUIHandler((RefreshUIHandler) mHeader);
                } else if (child2 instanceof RefreshUIHandler) {
                    mHeader = child2;
                    mContent = child1;

                } else {
                    if (mContent == null && mHeader == null) {
                        mHeader = child1;
                        mContent = child2;
                    } else {
                        if (mHeader == null) {
                            mHeader = mContent == child1 ? child2 : child1;
                        } else {
                            mContent = mHeader == child1 ? child2 : child1;
                        }
                    }
                }
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
        int top = getPaddingTop() + mTotalOffset;
        if (mHeader != null) {
            mHeader.layout(left, top - mHeaderHeight, left + mHeader.getMeasuredWidth(), top);
        }
        if (mContent != null) {
            mContent.layout(left, top, left + mContent.getMeasuredWidth(), top + mContent.getMeasuredHeight());
        }
    }

    private void resetMember() {
        isHandler = true;
        mLastMotionX = 0;
        mLastMotionY = 0;
        isBeingDragged = false;
        mActivePointerId = MotionEvent.INVALID_POINTER_ID;
        initVelocityTrackIfNeeded();
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
    public boolean dispatchTouchEvent(MotionEvent ev) {

        int action = ev.getActionMasked();
        switch (action) {

            case MotionEvent.ACTION_DOWN:
                return handleDownEvent(ev);

            case MotionEvent.ACTION_MOVE:
                if (handleMoveEvent(ev)) return true;
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (handleUpEvent(ev)) return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean handleDownEvent(MotionEvent ev) {
        resetMember();
        mVelocityTracker.addMovement(ev);
        mActivePointerId = ev.getPointerId(0);
        int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex < 0) return false;
        mLastMotionX = ev.getX(pointerIndex);
        mLastMotionY = ev.getY(pointerIndex);
        isBeingDragged = !mViewFlinger.isFinished();
        mViewFlinger.stop();
        super.dispatchTouchEvent(ev);
        return true;
    }

    private boolean handleMoveEvent(MotionEvent ev) {
        int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex < 0) return false;

        float x = ev.getX(pointerIndex);
        float y = ev.getY(pointerIndex);
        float xDiff = x - mLastMotionX;
        float yDiff = y - mLastMotionY;

        int offset = (int) yDiff;

        if (!isBeingDragged) {
            isBeingDragged = Math.abs(xDiff) < Math.abs(yDiff) && Math.abs(yDiff) >= mTouchSlop;
            offset = 0;
        }

        if (!isBeingDragged) return false;

        mVelocityTracker.addMovement(ev);

        mLastMotionX = x;
        mLastMotionY = y;

        if (yDiff < 0) { // 上拉
            if (mTotalOffset > 0) {
                isHandler = true;
                int targetOffset = mTotalOffset + offset;
                if (targetOffset < 0) {
                    offsetChildren(-mTotalOffset);
                    mContent.scrollBy(0, -targetOffset);
                } else {
                    offsetChildren(offset);
                }
                if (mUIHandler != null) {
                    mUIHandler.onPull(mTotalOffset >= mRefreshThreshold, mTotalOffset);
                }
                return true;
            } else {
                if (isHandler && mContent.canScrollVertically(DIRECTION_POSITIVE)) {
                    isHandler = false;
                    MotionEvent down = MotionEvent.obtain(
                            ev.getDownTime(),
                            ev.getEventTime(),
                            MotionEvent.ACTION_DOWN,
                            x,
                            y + mTouchSlop,
                            ev.getMetaState()
                    );
                    super.dispatchTouchEvent(down);
                    down.recycle();
                    super.dispatchTouchEvent(ev);
                    return true;
                }
            }
        } else { // 下拉
            if (!mContent.canScrollVertically(DIRECTION_NEGATIVE)) {
                if (!isHandler || mTotalOffset != 0) {
                    offset = (int) (offset / DRAGGING_RESISTANCE);
                    if (!isHandler) {
                        isHandler = true;
                        offset = Math.max(offset, 1);
                        if (mUIHandler != null) {
                            mUIHandler.onPrepare();
                        }
                    }
                }
                if (isHandler && mTotalOffset == 0) {
                    if (mUIHandler != null) {
                        mUIHandler.onPrepare();
                    }
                }
                offsetChildren(offset);
                if (mUIHandler != null) {
                    mUIHandler.onPull(mTotalOffset >= mRefreshThreshold, mTotalOffset);
                }
                return true;
            } else {
                if (isHandler) {
                    isHandler = false;
                    MotionEvent down = MotionEvent.obtain(
                            ev.getDownTime(),
                            ev.getEventTime(),
                            MotionEvent.ACTION_DOWN,
                            x,
                            y - mTouchSlop,
                            ev.getMetaState()
                    );
                    super.dispatchTouchEvent(down);
                    down.recycle();
                    super.dispatchTouchEvent(ev);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleUpEvent(MotionEvent ev) {
        boolean handled = false;
        mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
        int velocity = (int) mVelocityTracker.getYVelocity();
        if (isBeingDragged) {

            if (Math.abs(velocity) > mMinimumVelocity) {
                mViewFlinger.fling(velocity);
            }
            MotionEvent cancel = MotionEvent.obtain(
                    ev.getDownTime(),
                    ev.getEventTime(),
                    MotionEvent.ACTION_CANCEL,
                    mLastMotionX,
                    mLastMotionY,
                    ev.getMetaState()
            );
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            handled = true;
        }
        recycleVelocityTrack();
        return handled;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    private void offsetChildren(int offset) {
        if (offset == 0) return;
        mTotalOffset += offset;
        ViewCompat.offsetTopAndBottom(mHeader, offset);
        ViewCompat.offsetTopAndBottom(mContent, offset);
    }

    private void smoothScrollBy(int dy) {
        if (dy == 0) return;
        if (dy < 0) {
            if (mTotalOffset > 0) {
                if (-dy > mTotalOffset) {
                    offsetChildren(-mTotalOffset);
                    mContent.scrollBy(0, -dy - mTotalOffset);
                } else {
                    offsetChildren(dy);
                }
            } else {
                mContent.scrollBy(0, -dy);
            }
        } else {
            if (mTotalOffset > 0) {
                if (mTotalOffset + dy < mHeaderHeight) {
                    offsetChildren(dy);
                } else {
                    offsetChildren(mHeaderHeight - mTotalOffset);
                }
            } else {
                if (mContent.canScrollVertically(DIRECTION_NEGATIVE)) {
                    mContent.scrollBy(0, -dy);
                } else {
                    offsetChildren(dy);
                }
            }
        }
    }

    private boolean reachEnd() {
        return !mContent.canScrollVertically(DIRECTION_NEGATIVE) && mTotalOffset >= mHeaderHeight
                || !mContent.canScrollVertically(DIRECTION_POSITIVE);
    }

    private void animateOffsetToStartPosition(int from) {
        mFrom = from;
        long duration = (long) (250.0f * Math.abs(mFrom) / mHeaderHeight);
        mAnimateToStartPosition.setDuration(duration);
        clearAnimation();
        startAnimation(mAnimateToStartPosition);
    }

    private void animateOffsetToRefreshPosition(int from) {
        mFrom = from;
        int distance = Math.abs(mFrom - mHeaderHeight);
        long duration = (long) (150.0f * distance / mHeaderHeight);
        mAnimateToRefreshPosition.setDuration(duration);
        clearAnimation();
        startAnimation(mAnimateToRefreshPosition);
    }

    private void moveToStart(float interpolatedTime) {
        int targetTop = (mFrom + (int) (-mFrom * interpolatedTime));
        int offset = targetTop - mContent.getTop();
        offsetChildren(offset);
    }

    private void moveToRefresh(float interpolatedTime) {
        int targetTop = (mFrom + (int) ((mHeaderHeight - mFrom) * interpolatedTime));
        int offset = targetTop - mContent.getTop();
        offsetChildren(offset);
    }

    private boolean hasChild(View child) {
        return indexOfChild(child) != -1;
    }

    public void setHeaderView(View header) {
        if (mHeader == header) return;
        if (this.mHeader != null && hasChild(header)) {
            removeView(mHeader);
        }
        this.mHeader = header;
        if (header == null) {
            setUIHandler(null);
            return;
        }
        if (header instanceof RefreshUIHandler)
            setUIHandler((RefreshUIHandler) header);
        else
            throw new IllegalArgumentException("Header View must implements RefreshUIHandler!");
        if (header.getLayoutParams() == null)
            header.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(header);
    }

    public void setContentView(View content) {
        if (mContent == content) return;
        if (this.mContent != null && hasChild(mContent)) {
            removeView(mContent);
        }
        this.mContent = content;
        if (content == null) return;
        content.setOverScrollMode(OVER_SCROLL_NEVER);
        content.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(content);
    }

    public boolean isRefreshing() {
        return isRefreshing;
    }

    public void setRefreshing(final boolean refreshing) {
        if (!isRefreshing && refreshing) {
            animateOffsetToRefreshPosition((int) mTotalOffset);
            isRefreshing = true;
            if (mUIHandler != null) mUIHandler.onStart();
        } else if (isRefreshing && !refreshing) {
            if (mUIHandler != null) mUIHandler.onComplete();
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isBeingDragged) animateOffsetToStartPosition((int) mTotalOffset);
                    isRefreshing = false;
                }
            }, 500);
        }

    }

    public void setUIHandler(RefreshUIHandler handler) {
        this.mUIHandler = handler;
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.mListener = listener;
    }

    public interface OnRefreshListener {
        void onRefresh();
    }

    public interface RefreshUIHandler {

        void onPrepare();

        void onStart();

        void onComplete();

        void onPull(boolean willRefresh, int offset);

    }

    private class ViewFlinger implements Runnable {

        private int mLastFlingY;
        private OverScroller mScroller;

        private ViewFlinger() {
            this.mScroller = new OverScroller(getContext(), sQuinticInterpolator);
        }

        @Override
        public void run() {
            if (reachEnd()) {
                mScroller.forceFinished(true);
            } else if (mScroller.computeScrollOffset()) {
                int currY = mScroller.getCurrY();
                smoothScrollBy(currY - mLastFlingY);
                mLastFlingY = currY;
                postOnAnimation(this);
            }
        }

        private boolean isFinished() {
            return mScroller.isFinished();
        }

        private void stop() {
            mScroller.forceFinished(true);
        }

        private void fling(int velocity) {
            mLastFlingY = 0;
            mScroller.fling(0, mLastFlingY, 0, velocity, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
            postOnAnimation(this);
        }
    }
}
