package com.androidev.refreshlayout.sample;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.androidev.refreshlayout.RefreshLayout;

public abstract class SampleActivity<T> extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getClass().getSimpleName());
        setContentView(getLayoutId());
        final RefreshLayout refreshLayout = (RefreshLayout) findViewById(R.id.refresh_layout);
        refreshLayout.setOnRefreshListener(new RefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshLayout.setRefreshing(false);
                    }
                }, 10000);
            }
        });
        bindView((T) findViewById(R.id.content_view));
    }

    protected abstract int getLayoutId();

    protected abstract void bindView(final T contentView);
}
