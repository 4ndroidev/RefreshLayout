package com.androidev.refreshlayout.sample;


import android.support.v4.widget.NestedScrollView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class NestedScrollViewSample extends SampleActivity<NestedScrollView> {
    @Override
    protected int getLayoutId() {
        return R.layout.nested_scroll_view_sample;
    }

    @Override
    protected void bindView(NestedScrollView contentView) {
        final LinearLayout content = (LinearLayout) contentView.getChildAt(0);
        for (int i = 1; i <= 20; i++) {
            Button button = new Button(NestedScrollViewSample.this);
            button.setText("ITEM " + i);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(NestedScrollViewSample.this, "click:" + (content.indexOfChild(v) + 1), Toast.LENGTH_SHORT).show();
                }
            });
            button.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(NestedScrollViewSample.this, "long click:" + (content.indexOfChild(v) + 1), Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48 * 3);
            layoutParams.setMargins(18, 18, 18, 18);
            content.addView(button, layoutParams);
        }
    }
}
