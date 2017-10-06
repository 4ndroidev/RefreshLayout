package com.androidev.refreshlayout.sample;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseArray;
import android.view.View;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static SparseArray<Class> samples = new SparseArray<>();

    static {
        samples.put(R.id.recycler_view_sample, RecyclerViewSample.class);
        samples.put(R.id.list_view_sample, ListViewSample.class);
        samples.put(R.id.grid_view_sample, GridViewSample.class);
        samples.put(R.id.nested_scroll_view_sample, NestedScrollViewSample.class);
        samples.put(R.id.scroll_view_sample, ScrollViewSample.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        for (int i = 0, size = samples.size(); i < size; i++) {
            findViewById(samples.keyAt(i)).setOnClickListener(this);
        }
    }

    private void toSample(Class clazz) {
        Intent intent = new Intent();
        intent.setClass(this, clazz);
        startActivity(intent);
    }

    @Override
    public void onClick(View v) {
        toSample(samples.get(v.getId()));
    }
}
