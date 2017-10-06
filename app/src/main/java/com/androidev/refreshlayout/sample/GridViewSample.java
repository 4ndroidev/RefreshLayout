package com.androidev.refreshlayout.sample;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

public class GridViewSample extends SampleActivity<GridView> {
    @Override
    protected int getLayoutId() {
        return R.layout.grid_view_sample;
    }

    @Override
    protected void bindView(final GridView contentView) {
        contentView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return 40;
            }

            @Override
            public Object getItem(int position) {
                return position;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(GridViewSample.this).inflate(R.layout.grid_view_item, contentView, false);
                }
                TextView textView = (TextView) convertView.findViewById(R.id.text);
                textView.setText("item " + (position + 1));
                return convertView;
            }
        });
        contentView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(GridViewSample.this, "click:" + (position + 1), Toast.LENGTH_SHORT).show();
            }
        });
        contentView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(GridViewSample.this, "long click:" + (position + 1), Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }
}
