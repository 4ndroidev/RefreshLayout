package com.androidev.refreshlayout;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;

/**
 * refer to {@link android.support.v4.widget.SwipeRefreshLayout}
 */

public class RefreshLayout extends ViewGroup {

    private static final float DRAGGING_RESISTANCE = 2f;
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final float RATIO_OF_HEADER_HEIGHT_TO_REFRESH = 1.2f;

    private View mContent;
    private View mHeader;

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
        updateLayout();
    }

    private void updateLayout() {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        if (mHeader != null) {
            int left = paddingLeft;
            int top = mTotalOffset + paddingTop - mHeaderHeight;
            int right = left + mHeader.getMeasuredWidth();
            int bottom = top + mHeaderHeight;
            mHeader.layout(left, top, right, bottom);
        }
        if (mContent != null) {
            int left = paddingLeft;
            int top = mTotalOffset + paddingTop;
            int right = left + mContent.getMeasuredWidth();
            int bottom = top + mContent.getMeasuredHeight();
            mContent.layout(left, top, right, bottom);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled() || mContent == null || mHeader == null || canChildScrollUp())
            return false;
        final int action = MotionEventCompat.getActionMasked(ev);
        int pointerIndex;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                isBeingDragged = false;
                canRefresh = !isRefreshing;
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                mLastMotionX = ev.getX(pointerIndex);
                mLastMotionY = ev.getY(pointerIndex);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == MotionEvent.INVALID_POINTER_ID) {
                    return false;
                }
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);
                startDragging(x, y);
                break;

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isBeingDragged = false;
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
        }
        return isBeingDragged;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled() || mContent == null || mHeader == null)
            return false;
        final int action = MotionEventCompat.getActionMasked(ev);
        int pointerIndex;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                isBeingDragged = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);
                startDragging(x, y);
                if (isBeingDragged) {
                    final int offset = (int) ((y - mLastMotionY) / DRAGGING_RESISTANCE);
                    final int targetOffset = mTotalOffset + offset;
                    if (canRefresh && !isRefreshing && mUIHandler != null)
                        mUIHandler.onPull(targetOffset >= mRefreshThreshold, targetOffset);
                    mLastMotionY = y;
                    if (targetOffset >= 0) {
                        setOffsetTopAndBottom(offset);
                    } else {
                        setOffsetTopAndBottom(-mTotalOffset);
                        return false;
                    }
                }
                break;
            }
            case MotionEventCompat.ACTION_POINTER_DOWN: {
                pointerIndex = MotionEventCompat.getActionIndex(ev);
                if (pointerIndex < 0) {
                    return false;
                }
                mActivePointerId = ev.getPointerId(pointerIndex);
                mLastMotionY = ev.getY(pointerIndex);
                mLastMotionX = ev.getX(pointerIndex);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                if (isBeingDragged) {
                    isBeingDragged = false;
                    if (isRefreshing) {
                        if (mTotalOffset > mHeaderHeight)
                            animateOffsetToRefreshPosition(mTotalOffset);
                    } else {
                        if (canRefresh && mTotalOffset >= mRefreshThreshold) {
                            animateOffsetToRefreshPosition(mTotalOffset);
                            isRefreshing = true;
                            if (mListener != null) mListener.onRefresh();
                            if (mUIHandler != null) mUIHandler.onStart();
                        } else {
                            animateOffsetToStartPosition(mTotalOffset);
                        }
                    }
                }
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                return false;
            }
            case MotionEvent.ACTION_CANCEL:
                return false;
        }

        return true;
    }

    private void startDragging(float x, float y) {
        final float xDiff = x - mLastMotionX;
        final float yDiff = y - mLastMotionY;
        if (Math.abs(xDiff) < mTouchSlop && yDiff >= mTouchSlop && !isBeingDragged) {
            mLastMotionY = mLastMotionY + (yDiff > 0 ? mTouchSlop : -mTouchSlop);
            isBeingDragged = true;
            if (canRefresh && mUIHandler != null) mUIHandler.onPrepare();
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    private void setOffsetTopAndBottom(int offset) {
        mTotalOffset += offset;
        if (offset == 0) return;
        mHeader.bringToFront();
        ViewCompat.offsetTopAndBottom(mHeader, offset);
        ViewCompat.offsetTopAndBottom(mContent, offset);
        updateLayout();
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
        setOffsetTopAndBottom(offset);
    }

    private void moveToRefresh(float interpolatedTime) {
        int targetTop = (mFrom + (int) ((mHeaderHeight - mFrom) * interpolatedTime));
        int offset = targetTop - mContent.getTop();
        setOffsetTopAndBottom(offset);
    }

    public boolean canChildScrollUp() {
        if (mContent == null) return false;
        if (android.os.Build.VERSION.SDK_INT < 14) {
            if (mContent instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) mContent;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                        .getTop() < absListView.getPaddingTop());
            } else {
                return ViewCompat.canScrollVertically(mContent, -1) || mContent.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(mContent, -1);
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        if ((android.os.Build.VERSION.SDK_INT < 21 && mContent != null && mContent instanceof AbsListView)
                || (mContent != null && !ViewCompat.isNestedScrollingEnabled(mContent))) {
        } else {
            super.requestDisallowInterceptTouchEvent(b);
        }
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

    public View getHeaderView() {
        return mHeader;
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
                    isRefreshing = refreshing;
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
