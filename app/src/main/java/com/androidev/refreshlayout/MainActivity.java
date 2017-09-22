package com.androidev.refreshlayout;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ScrollView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final RefreshLayout refreshLayout = (RefreshLayout) findViewById(R.id.refresh_layout);
        final ScrollView scrollView = (ScrollView) findViewById(R.id.scrollview);
//        scrollView.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                scrollView.smoothScrollTo(0, 200);
//            }
//        }, 1000);
        refreshLayout.setOnRefreshListener(new RefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshLayout.setRefreshing(false);
                    }
                }, 3000);
            }
        });
    }
}
