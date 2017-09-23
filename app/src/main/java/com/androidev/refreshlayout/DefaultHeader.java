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


/*
 * 标版默认下拉刷新头部
 */
public class DefaultHeader extends LinearLayout implements RefreshLayout.RefreshHeader {

    private final static int FLIP_DURATION = 150;
    private final static int ROTATE_DURATION = 2000;

    private String pullMessage = "下拉以刷新...";
    private String releaseMessage = "松开以刷新...";
    private String refreshMessage = "正在刷新中...";
    private String completeMessage = "刷新完成";

    private RotateAnimation mFlipAnimation;
    private RotateAnimation mReverseFlipAnimation;
    private RotateAnimation mRotateAnimation;
    private TextView mTitle;
    private ImageView mIndicator;

    private int a;

    public DefaultHeader(Context context) {
        this(context, null);
    }

    public DefaultHeader(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DefaultHeader(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        a = context.getResources().getDisplayMetrics().widthPixels / 60;
        setWillNotDraw(false);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        initViews(context);
        buildAnimation();
    }

    protected void initViews(Context context) {
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ((int) (7.5 * a))));
        LinearLayout childLayout = new LinearLayout(context);
        childLayout.setOrientation(LinearLayout.HORIZONTAL);
        childLayout.setGravity(Gravity.CENTER);
        mIndicator = new ImageView(context);
        mIndicator.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mIndicator.setLayoutParams(new LayoutParams(4 * a, 4 * a));
        childLayout.addView(mIndicator);
        mTitle = new TextView(context);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, 2.33f * a);
        mTitle.setPadding(2 * a, 0, 0, 0);
        mTitle.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        childLayout.addView(mTitle);
        childLayout.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(childLayout);
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
        mTitle.setText(pullMessage);
        mIndicator.setVisibility(VISIBLE);
        mIndicator.setRotation(0);
        mIndicator.setImageResource(R.drawable.refresh_header_arrow);
    }

    @Override
    public void onStart() {
        mTitle.setText(refreshMessage);
        mIndicator.setVisibility(VISIBLE);
        mIndicator.setRotation(0);
        mIndicator.setImageResource(R.drawable.refresh_header_loading);
        mIndicator.clearAnimation();
        mIndicator.startAnimation(mRotateAnimation);
    }

    @Override
    public void onComplete() {
        mTitle.setText(completeMessage);
        mIndicator.clearAnimation();
        mIndicator.setVisibility(GONE);
        refresh = false;
    }

    private boolean refresh;

    @Override
    public void onPull(boolean willRefresh, int offset) {
        if (refresh && !willRefresh) {
            mTitle.setText(pullMessage);
            mIndicator.clearAnimation();
            mIndicator.startAnimation(mReverseFlipAnimation);
        } else if (!refresh && willRefresh) {
            mTitle.setText(releaseMessage);
            mIndicator.clearAnimation();
            mIndicator.startAnimation(mFlipAnimation);
        }
        refresh = willRefresh;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
}
