package com.androidev.refreshlayout;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DefaultHeader extends LinearLayout implements RefreshLayout.RefreshHeader {

    private final static int FLIP_DURATION = 150;
    private final static int ROTATE_DURATION = 2000;

    private RotateAnimation mFlipAnimation;
    private RotateAnimation mReverseFlipAnimation;
    private RotateAnimation mRotateAnimation;
    private TextView mTitle;
    private ImageView mIndicator;
    private boolean mWillRefresh;


    public DefaultHeader(Context context) {
        this(context, null);
    }

    public DefaultHeader(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DefaultHeader(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setWillNotDraw(false);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        initViews(context);
        createAnimation();
    }

    protected void initViews(Context context) {
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER);
        int paddingHorizontal = 0;
        int paddingVertical = dp2px(12);
        setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
        mIndicator = new ImageView(context);
        mIndicator.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mIndicator.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mIndicator);
        mTitle = new TextView(context);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        mTitle.setPadding(dp2px(8), 0, 0, 0);
        mTitle.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(mTitle);
    }

    private void createAnimation() {
        mFlipAnimation = new RotateAnimation(0, -180, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        mFlipAnimation.setInterpolator(new LinearInterpolator());
        mFlipAnimation.setDuration(FLIP_DURATION);
        mFlipAnimation.setFillAfter(true);
        mReverseFlipAnimation = new RotateAnimation(-180, 0, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        mReverseFlipAnimation.setInterpolator(new LinearInterpolator());
        mReverseFlipAnimation.setDuration(FLIP_DURATION);
        mReverseFlipAnimation.setFillAfter(true);
        mRotateAnimation = new RotateAnimation(0, 360, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        mRotateAnimation.setInterpolator(new LinearInterpolator());
        mRotateAnimation.setDuration(ROTATE_DURATION);
        mRotateAnimation.setRepeatCount(Animation.INFINITE);
        mRotateAnimation.setRepeatMode(Animation.INFINITE);
        mRotateAnimation.setFillAfter(false);
    }

    @Override
    public void onPrepare() {
        mTitle.setText(R.string.pull_message);
        mIndicator.setRotation(0);
        mIndicator.setImageResource(R.drawable.refresh_header_arrow);
    }

    @Override
    public void onStart() {
        mTitle.setText(R.string.refresh_message);
        mIndicator.setRotation(0);
        mIndicator.setImageResource(R.drawable.refresh_header_loading);
        mIndicator.clearAnimation();
        mIndicator.startAnimation(mRotateAnimation);
    }

    @Override
    public void onComplete() {
        mTitle.setText(R.string.complete_message);
        mIndicator.setImageResource(R.drawable.refresh_header_done);
        mIndicator.clearAnimation();
        mWillRefresh = false;
    }

    @Override
    public void onPull(boolean willRefresh, int offset) {
        if (mWillRefresh && !willRefresh) {
            mTitle.setText(R.string.pull_message);
            mIndicator.clearAnimation();
            mIndicator.startAnimation(mReverseFlipAnimation);
        } else if (!mWillRefresh && willRefresh) {
            mTitle.setText(R.string.release_message);
            mIndicator.clearAnimation();
            mIndicator.startAnimation(mFlipAnimation);
        }
        mWillRefresh = willRefresh;
    }

    private int dp2px(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

}
