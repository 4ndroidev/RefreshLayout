# RefreshLayout

## 介绍 

支持：`RecyclerView`，`NestedScrollView`，`AbsListView`， `ScrollView`

优势：`fling`效果比较好，下拉刷新整体表现自然

## 思路

> Android 5.0 后出现了嵌套滑动，支持嵌套滑动的组件，可让父组件拦截消耗滑动事件；父组件消耗滑动事件时，将下拉头部进行显示和隐藏，实现下拉刷新。不支持嵌套滑动的组件，在分发触摸事件时做类似嵌套滑动的操作，从而实现下拉刷新。

## 样例

下载：[sample.apk](https://github.com/4ndroidev/RefreshLayout/blob/master/sample/sample.apk)

## 截图

![recyclerview_sample.gif](https://github.com/4ndroidev/RefreshLayout/blob/master/screenshot/recyclerview_sample.gif)![nestedscrollview_sample.gif](https://github.com/4ndroidev/RefreshLayout/blob/master/screenshot/nestedscrollview_sample.gif)

## 使用

### 第一步

添加依赖 :

	compile 'com.androidev:refreshlayout:1.5.0'

### 第二步

修改布局文件，如下 :

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.androidev.refreshlayout.RefreshLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/refresh_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <android.support.v7.widget.RecyclerView
        android:id="@+id/content_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>

</com.androidev.refreshlayout.RefreshLayout>
```

设置下拉刷新监听：

```java
RefreshLayout refreshLayout = (RefreshLayout) findViewById(R.id.refresh_layout);
refreshLayout.setOnRefreshListener(new RefreshLayout.OnRefreshListener() {
    @Override
    public void onRefresh() {
        refreshLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshLayout.setRefreshing(false);
            }
        }, 5000);
    }
});
```

设置下拉头部（可选，自带默认头部）：

- 需要实现`RefreshLayout.RefreshHeader`接口
- 通过`RefreshLayout.setHeader`方法设置自定义头部