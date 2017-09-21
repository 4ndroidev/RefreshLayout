package com.androidev.refreshlayout;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.FrameLayout;

/**
 * refer to {@link android.support.v4.widget.SwipeRefreshLayout}
 */

public class RefreshLayout extends FrameLayout {

    private static final float DRAGGING_RESISTANCE = 2f;
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final float RATIO_OF_HEADER_HEIGHT_TO_REFRESH = 1.2f;
    private static final int DIRECTION_POSITIVE = 1;
    private static final int DIRECTION_NEGATIVE = -1;

    private View mHeader;
    private View mContent;

    private int mFrom;
    private int mTouchSlop;
    private int mTotalOffset;
    private int mHeaderHeight;
    private int mRefreshThreshold;
    private int mActivePointerId = MotionEvent.INVALID_POINTER_ID;

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
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
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
        isHandler = false;
        mLastMotionX = 0;
        mLastMotionY = 0;
        isBeingDragged = false;
        mActivePointerId = MotionEvent.INVALID_POINTER_ID;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {

            case MotionEvent.ACTION_DOWN:
                return handleDownEvent(ev);

            case MotionEvent.ACTION_MOVE:
                handleMoveEvent(ev);
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean handleDownEvent(MotionEvent ev) {
        resetMember();
        mActivePointerId = ev.getPointerId(0);
        int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex < 0) return false;
        mLastMotionX = ev.getX(pointerIndex);
        mLastMotionY = ev.getY(pointerIndex);
        return true;
    }

    private void handleMoveEvent(MotionEvent ev) {
        int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex < 0) return;
        boolean hasOffset = mTotalOffset != 0;
        boolean canScrollUp = mContent != null && mContent.canScrollVertically(DIRECTION_NEGATIVE);
        float x = ev.getX(pointerIndex);
        float y = ev.getY(pointerIndex);
        float xDiff = x - mLastMotionX;
        float yDiff = y - mLastMotionY;

        if (yDiff < 0) { // 上拉

        } else { // 下拉
            if (!canScrollUp) { //子组件不能向上滚动
                if (!hasOffset && !isHandler) { // 没有偏移
                    if (yDiff < mTouchSlop || Math.abs(xDiff) > yDiff) return;
                    offsetChildren((int) (mTouchSlop / DRAGGING_RESISTANCE));
                    isBeingDragged = true;
                    mLastMotionX = ev.getX(pointerIndex);
                    mLastMotionY = ev.getY(pointerIndex);
                    isHandler = true;
                } else if (isHandler) { // 发生了偏移
                    offsetChildren((int) (yDiff / DRAGGING_RESISTANCE));
                    mLastMotionX = ev.getX(pointerIndex);
                    mLastMotionY = ev.getY(pointerIndex);
                }
            } else { // 子组件能向上滚动
                if (isHandler) { // 交给子组件处理手势
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
                    isHandler = false;
                }
                mLastMotionX = x;
                mLastMotionY = y;
            }
        }
    }

//    @Override
//    public boolean onInterceptTouchEvent(MotionEvent ev) {
//        if (!isEnabled() || mContent == null || mHeader == null || canChildScrollUp())
//            return false;
//        final int action = MotionEventCompat.getActionMasked(ev);
//        int pointerIndex;
//        switch (action) {
//            case MotionEvent.ACTION_DOWN:
//                mActivePointerId = ev.getPointerId(0);
//                isBeingDragged = false;
//                canRefresh = !isRefreshing;
//                pointerIndex = ev.findPointerIndex(mActivePointerId);
//                if (pointerIndex < 0) {
//                    return false;
//                }
//                break;
//            case MotionEvent.ACTION_MOVE:
//                if (mActivePointerId == MotionEvent.INVALID_POINTER_ID) {
//                    return false;
//                }
//                pointerIndex = ev.findPointerIndex(mActivePointerId);
//                if (pointerIndex < 0) {
//                    return false;
//                }
//                final float x = ev.getX(pointerIndex);
//                final float y = ev.getY(pointerIndex);
//                startDragging(x, y);
//                break;
//            case MotionEvent.ACTION_UP:
//            case MotionEvent.ACTION_CANCEL:
//                isBeingDragged = false;
//                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
//                break;
//        }
//        return isBeingDragged;
//    }

//    public boolean onTouchEvent(MotionEvent ev) {
//        if (!isEnabled() || mContent == null || mHeader == null)
//            return false;
//        mLastMotion = ev;
//        final int action = ev.getActionMasked();
//        int pointerIndex;
//        switch (action) {
//            case MotionEvent.ACTION_DOWN:
//                mActivePointerId = ev.getPointerId(0);
//                isBeingDragged = false;
//                break;
//            case MotionEvent.ACTION_MOVE: {
//                pointerIndex = ev.findPointerIndex(mActivePointerId);
//                if (pointerIndex < 0) {
//                    return false;
//                }
//                final float x = ev.getX(pointerIndex);
//                final float y = ev.getY(pointerIndex);
//                startDragging(x, y);
//                if (isBeingDragged) {
//                    final int offset = (int) ((y - mLastMotionY) / DRAGGING_RESISTANCE);
//                    final int targetOffset = mTotalOffset + offset;
//                    if (canRefresh && !isRefreshing && mUIHandler != null)
//                        mUIHandler.onPull(targetOffset >= mRefreshThreshold, targetOffset);
//                    mLastMotionY = y;
//                    if (targetOffset >= 0) {
//                        offsetChildren(offset);
//                    } else {
//                        offsetChildren(-mTotalOffset);
//                        return false;
//                    }
//                }
//                break;
//            }
//            case MotionEvent.ACTION_UP: {
//                pointerIndex = ev.findPointerIndex(mActivePointerId);
//                if (pointerIndex < 0) {
//                    return false;
//                }
//                if (isBeingDragged) {
//                    isBeingDragged = false;
//                    if (isRefreshing) {
//                        if (mTotalOffset > mHeaderHeight)
//                            animateOffsetToRefreshPosition(mTotalOffset);
//                    } else {
//                        if (canRefresh && mTotalOffset >= mRefreshThreshold) {
//                            animateOffsetToRefreshPosition(mTotalOffset);
//                            isRefreshing = true;
//                            if (mListener != null) mListener.onRefresh();
//                            if (mUIHandler != null) mUIHandler.onStart();
//                        } else {
//                            animateOffsetToStartPosition(mTotalOffset);
//                        }
//                    }
//                }
//                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
//                return false;
//            }
//            case MotionEvent.ACTION_CANCEL:
//                return false;
//        }
//
//        return true;
//    }

//    private void startDragging(float x, float y) {
//        final float xDiff = x - mLastMotionX;
//        final float yDiff = y - mLastMotionY;
//        if (Math.abs(xDiff) < mTouchSlop && yDiff >= mTouchSlop && !isBeingDragged) {
//            mLastMotionY = mLastMotionY + (yDiff > 0 ? mTouchSlop : -mTouchSlop);
//            isBeingDragged = true;
//            if (canRefresh && mUIHandler != null) mUIHandler.onPrepare();
//        }
//    }

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

    public boolean canChildScrollUp() {
        return mContent != null && mContent.canScrollVertically(-1);
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
            animateOffsetToRefreshPosition(mTotalOffset);
            isRefreshing = true;
            if (mUIHandler != null) mUIHandler.onStart();
        } else if (isRefreshing && !refreshing) {
            if (mUIHandler != null) mUIHandler.onComplete();
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isBeingDragged) animateOffsetToStartPosition(mTotalOffset);
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
}
