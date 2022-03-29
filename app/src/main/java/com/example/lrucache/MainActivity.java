package com.example.lrucache;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "DiskLruCache";
    private static final String DISK_CACHE_SUBDIR = "bitmap";
    private static final int DISK_CACHE_VALUE_COUNT = 1;
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; //磁盘缓存的大小为10MB
    private static final String URL = "https://avatars2.githubusercontent.com/u/9552155?v=3&u=0d33d696e757f6f529674aeb3f532f6e20f28a4b&s=140";
    private static final int DISK_CACHE_INDEX = 0;
    private DiskLruCache mDiskLruCache;
    private ImageView mImageView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageView = findViewById(R.id.iv);
        Button btn = findViewById(R.id.btn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new SetTask(mDiskLruCache, MainActivity.this).execute(URL);
            }
        });
        initCache();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mDiskLruCache != null) {
            try {
                mDiskLruCache.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void initCache() {
        try {
            File cacheDir = getDiskCacheDir(this, "bitmap");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            mDiskLruCache = DiskLruCache.open(cacheDir, getAppVersion(this), DISK_CACHE_VALUE_COUNT, DISK_CACHE_SIZE);
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    private int getAppVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    private void setTaskFinish(Boolean success) {
        Toast.makeText(this, success ? "已经写入磁盘" : "出错！！！", Toast.LENGTH_SHORT).show();
        if (success) {
            new GetTask(mDiskLruCache, mImageView).execute(URL);
        }
    }

    private static class SetTask extends AsyncTask<String, Integer, Boolean>{
        private DiskLruCache mDiskLruCache;
        private WeakReference<MainActivity> mActivity;
        public SetTask(DiskLruCache cache, MainActivity activity) {
            mDiskLruCache = cache;
            mActivity = new WeakReference<>(activity);
        }

        @Override
        protected Boolean doInBackground(String... urls) {
            boolean result = false;
            String key = Utils.hashKeyFromUrl(urls[0]);
            try {
                DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
                if (snapshot != null) {
                    Log.d(TAG, "snapshot");
                    return true;
                }
                DiskLruCache.Editor editor =  mDiskLruCache.edit(key);
                if (editor != null) {
                    // 产生一个输出流
                    OutputStream out = editor.newOutputStream(DISK_CACHE_INDEX);
                    // 从这个url下载图片，将editor的输出流传入
                    if (DownloadUtil.downloadUrlToStream(urls[0], out)) {
                        // 提交，写入磁盘中
                        editor.commit();
                        Log.d(TAG, "commit");
                        result = true;
                    } else {
                        // 出错，撤销操作
                        editor.abort();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return result;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            Log.d(TAG, "onPostExecute: " + result);
            MainActivity activity = mActivity.get();
            if (activity != null) {
                activity.setTaskFinish(result);
            }
        }
    }

    private static class GetTask extends AsyncTask<String, Integer, Bitmap>{
        private DiskLruCache mDiskLruCache;
        private WeakReference<ImageView> mImageView;
        public GetTask(DiskLruCache cache, ImageView imageView) {
            mDiskLruCache = cache;
            mImageView = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            Bitmap bitmap = null;
            String key = Utils.hashKeyFromUrl(urls[0]);
            try {
                DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
                if (snapshot != null) {
                    InputStream is = snapshot.getInputStream(DISK_CACHE_INDEX);
                    bitmap = BitmapFactory.decodeStream(is);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            ImageView imageView = mImageView.get();
            if (imageView != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

}