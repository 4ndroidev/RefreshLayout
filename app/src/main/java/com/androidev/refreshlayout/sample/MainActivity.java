package com.androidev.refreshlayout.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.androidev.refreshlayout.RefreshLayout;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final RefreshLayout refreshLayout = (RefreshLayout) findViewById(R.id.refresh_layout);
        final RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        recyclerView.setAdapter(new RecyclerView.Adapter<TextViewHolder>() {
            @Override
            public TextViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                return new TextViewHolder(inflater.inflate(R.layout.recycler_item, recyclerView, false));
            }

            @Override
            public void onBindViewHolder(TextViewHolder holder, int position) {
                holder.textView.setText("item " + (position + 1));
            }

            @Override
            public int getItemCount() {
                return 20;
            }
        });
        refreshLayout.setOnRefreshListener(new RefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshLayout.setRefreshing(false);
                    }
                }, 2000);
            }
        });
    }

    private class TextViewHolder extends RecyclerView.ViewHolder {
        private TextView textView;

        private TextViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(MainActivity.this, "click:" + getAdapterPosition(), Toast.LENGTH_SHORT).show();
                }
            });
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(MainActivity.this, "long click:" + getAdapterPosition(), Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
            textView = (TextView) itemView.findViewById(R.id.text);
        }

    }
}
