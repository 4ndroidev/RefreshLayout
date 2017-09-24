package com.androidev.refreshlayout;

import android.content.Context;
import android.support.v4.view.ViewCompat;
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
import android.widget.FrameLayout;
import android.widget.OverScroller;

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

    private static final Interpolator sDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

    private int mMaximumVelocity;
    private int mMinimumVelocity;
    private ViewFlinger mViewFlinger;
    private VelocityTracker mVelocityTracker;

    private View mHeader;
    private View mContent;

    private int mStartOffset;
    private int mTotalOffset;
    private int mTouchSlop;
    private int mHeaderHeight;
    private int mRefreshThreshold;
    private int mActivePointerId;

    private float mLastMotionX;
    private float mLastMotionY;

    private boolean canRefresh;
    private boolean isRefreshing;
    private boolean isBeingDragged;
    private boolean isHandler;

    private OnRefreshListener mListener;
    private RefreshHeader mRefreshHeader;

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
            canRefresh = true;
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
        mViewFlinger = new ViewFlinger();
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mActivePointerId = MotionEvent.INVALID_POINTER_ID;
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mAnimateToStartPosition.setAnimationListener(mAnimationListener);
        mAnimateToStartPosition.setInterpolator(sDecelerateInterpolator);
        mAnimateToRefreshPosition.setInterpolator(sDecelerateInterpolator);
        setHeader(new DefaultHeader(context));
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
        canRefresh &= !isRefreshing;
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
        if (!isEnabled() || mContent == null)
            return super.dispatchTouchEvent(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return handleDownEvent(ev);
            case MotionEvent.ACTION_MOVE:
                return handleMoveEvent(ev) || super.dispatchTouchEvent(ev);
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                return handleUpEvent(ev) || super.dispatchTouchEvent(ev);
            case MotionEvent.ACTION_POINTER_DOWN:
                return handlePointerDownEvent(ev) || super.dispatchTouchEvent(ev);
            case MotionEvent.ACTION_POINTER_UP:
                return handlePointerUpEvent(ev) || super.dispatchTouchEvent(ev);
            default:
                return super.dispatchTouchEvent(ev);
        }
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
            offset = 0; // first offset set to zero, so that it will not cause a little jump
        }

        if (!isBeingDragged) return false;

        mVelocityTracker.addMovement(ev);

        mLastMotionX = x;
        mLastMotionY = y;

        // divide to two parts, one is pulling up, the other is pulling down
        // pulling up
        if (yDiff < 0) {
            // content view has offset, so handle the move event by own
            if (mTotalOffset > 0) {
                isHandler = true;
                // make sure `mTotalOffset==0` in the end
                int targetOffset = mTotalOffset + offset;
                if (targetOffset < 0) {
                    offsetChildren(-mTotalOffset);
                    mContent.scrollBy(0, -targetOffset);
                } else {
                    offsetChildren(offset);
                }
                if (canRefresh && mRefreshHeader != null) {
                    mRefreshHeader.onPull(mTotalOffset >= mRefreshThreshold, mTotalOffset);
                }
                return true;
            } else {
                // content view hasn't offset, so dispatch the move event to the content view
                if (isHandler && mContent.canScrollVertically(DIRECTION_POSITIVE)) {
                    isHandler = false;
                    // by sending down event, make sure content view can handle the next move event to scroll
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
        } else {
            // pulling down
            // content view can't scroll up, so it will offset content view
            if (!mContent.canScrollVertically(DIRECTION_NEGATIVE)) {
                // `!isHandler` means content just scroll to the top, `mTotalOffset==0` means the initial time
                if ((!isHandler || mTotalOffset == 0) && canRefresh && mRefreshHeader != null) {
                    mRefreshHeader.onPrepare();
                }
                // transfer processing right
                if (!isHandler) {
                    isHandler = true;
                }
                offsetChildren((int) (offset / DRAGGING_RESISTANCE));
                if (canRefresh && mRefreshHeader != null) {
                    mRefreshHeader.onPull(mTotalOffset >= mRefreshThreshold, mTotalOffset);
                }
                return true;
            } else {
                // content view can scroll up, so dispatch the move event to the content view
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
            handled = true;
            if (mTotalOffset >= mRefreshThreshold) {
                if (canRefresh) {
                    canRefresh = false;
                    isRefreshing = true;
                    animateOffsetToRefreshPosition(mTotalOffset);
                    if (mRefreshHeader != null) mRefreshHeader.onStart();
                    if (mListener != null) mListener.onRefresh();
                } else {
                    animateOffsetToStartPosition(mTotalOffset);
                }
            } else if (mTotalOffset > 0 && !isRefreshing) {
                animateOffsetToStartPosition(mTotalOffset);
            } else if ((isRefreshing || isHandler) && Math.abs(velocity) > mMinimumVelocity) {
                mViewFlinger.fling(velocity);
            } else {
                handled = false;
            }
            if (handled) {
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
            }
        }
        recycleVelocityTrack();
        isBeingDragged = false;
        return handled;
    }

    private boolean handlePointerDownEvent(MotionEvent ev) {
        int pointerIndex = ev.getActionIndex();
        if (pointerIndex < 0) {
            return false;
        }
        mActivePointerId = ev.getPointerId(pointerIndex);
        mLastMotionX = ev.getX(pointerIndex);
        mLastMotionY = ev.getY(pointerIndex);
        return false;
    }

    private boolean handlePointerUpEvent(MotionEvent ev) {
        int pointerIndex = ev.getActionIndex();
        int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            mLastMotionX = ev.getX(newPointerIndex);
            mLastMotionY = ev.getY(newPointerIndex);
        }
        return false;
    }

    private void offsetChildren(int offset) {
        if (offset == 0) return;
        mTotalOffset += offset;
        ViewCompat.offsetTopAndBottom(mHeader, offset);
        ViewCompat.offsetTopAndBottom(mContent, offset);
    }

    private void smoothScrollBy(int dy) {
        if (dy == 0) return;
        if (dy < 0) { // scroll down after pulling up
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
        } else { // scroll up after puling down
            if (mTotalOffset > 0) {
                if (mTotalOffset + dy < mHeaderHeight) {
                    offsetChildren(dy);
                } else {
                    offsetChildren(mHeaderHeight - mTotalOffset);
                }
            } else {
                // will cause a deviation because we didn't get the scrollable range
                // but doesn't mater the fling effect, so ignore it
                if (mContent.canScrollVertically(DIRECTION_NEGATIVE)) {
                    mContent.scrollBy(0, -dy);
                } else if (isRefreshing) {
                    if (mTotalOffset + dy <= mHeaderHeight)
                        offsetChildren(dy);
                    else
                        offsetChildren(mHeaderHeight - mTotalOffset);
                }
            }
        }
    }

    private boolean reachEnd() {
        return isRefreshing && !mContent.canScrollVertically(DIRECTION_NEGATIVE) && mTotalOffset >= mHeaderHeight
                || !isRefreshing && !mContent.canScrollVertically(DIRECTION_NEGATIVE)
                || !mContent.canScrollVertically(DIRECTION_POSITIVE);
    }

    private void animateOffsetToStartPosition(int startOffset) {
        mStartOffset = startOffset;
        long duration = (long) (250.0f * Math.abs(mStartOffset) / mHeaderHeight);
        mAnimateToStartPosition.setDuration(duration);
        clearAnimation();
        startAnimation(mAnimateToStartPosition);
    }

    private void animateOffsetToRefreshPosition(int startOffset) {
        mStartOffset = startOffset;
        int distance = Math.abs(mStartOffset - mHeaderHeight);
        long duration = (long) (150.0f * distance / mHeaderHeight);
        mAnimateToRefreshPosition.setDuration(duration);
        clearAnimation();
        startAnimation(mAnimateToRefreshPosition);
    }

    private void moveToStart(float interpolatedTime) {
        int targetTop = (mStartOffset + (int) (-mStartOffset * interpolatedTime));
        int offset = targetTop - mContent.getTop();
        offsetChildren(offset);
    }

    private void moveToRefresh(float interpolatedTime) {
        int targetTop = (mStartOffset + (int) ((mHeaderHeight - mStartOffset) * interpolatedTime));
        int offset = targetTop - mContent.getTop();
        offsetChildren(offset);
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

    public void setRefreshing(final boolean refreshing) {
        if (!isRefreshing && refreshing) {
            animateOffsetToRefreshPosition(mTotalOffset);
            isRefreshing = true;
            if (mRefreshHeader != null) mRefreshHeader.onStart();
        } else if (isRefreshing && !refreshing) {
            if (mRefreshHeader != null) mRefreshHeader.onComplete();
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isBeingDragged) animateOffsetToStartPosition(mTotalOffset);
                    isRefreshing = false;
                }
            }, 500);
        }

    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.mListener = listener;
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
