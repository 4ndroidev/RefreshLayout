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

import com.androidev.refreshlayout.sample.R;

/*
 * 标版默认下拉刷新头部
 */
public class DefaultHeader extends LinearLayout implements RefreshLayout.RefreshHeader {

    private final static int FLIP_DURATION = 150;
    private final static int ROTATE_DURATION = 2000;

    private final static String PULL_MESSAGE = "下拉刷新";
    private final static String RELEASE_MESSAGE = "松开刷新";
    private final static String REFRESH_MESSAGE = "正在刷新";
    private final static String COMPLETE_MESSAGE = "刷新完成";

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
        buildAnimation();
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
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 0, dp2px(5), 0);
        mIndicator.setLayoutParams(layoutParams);
        addView(mIndicator);
        mTitle = new TextView(context);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mTitle.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(mTitle);
    }

    private void buildAnimation() {
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
        mTitle.setText(PULL_MESSAGE);
        mIndicator.setVisibility(VISIBLE);
        mIndicator.setRotation(0);
        mIndicator.setImageResource(R.drawable.refresh_header_arrow);
    }

    @Override
    public void onStart() {
        mTitle.setText(REFRESH_MESSAGE);
        mIndicator.setVisibility(VISIBLE);
        mIndicator.setRotation(0);
        mIndicator.setImageResource(R.drawable.refresh_header_loading);
        mIndicator.clearAnimation();
        mIndicator.startAnimation(mRotateAnimation);
    }

    @Override
    public void onComplete() {
        mTitle.setText(COMPLETE_MESSAGE);
        mIndicator.clearAnimation();
        mIndicator.setVisibility(GONE);
        mWillRefresh = false;
    }

    @Override
    public void onPull(boolean willRefresh, int offset) {
        if (mWillRefresh && !willRefresh) {
            mTitle.setText(PULL_MESSAGE);
            mIndicator.clearAnimation();
            mIndicator.startAnimation(mReverseFlipAnimation);
        } else if (!mWillRefresh && willRefresh) {
            mTitle.setText(RELEASE_MESSAGE);
            mIndicator.clearAnimation();
            mIndicator.startAnimation(mFlipAnimation);
        }
        mWillRefresh = willRefresh;
    }

    private int dp2px(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

}
