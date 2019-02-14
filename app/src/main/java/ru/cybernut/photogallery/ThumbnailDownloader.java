package ru.cybernut.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ThumbnailDownloader<T> extends HandlerThread {

    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;

    private boolean hasQuit = false;
    private Handler requestHandler;
    private ConcurrentHashMap<T, String> requestMap = new ConcurrentHashMap<>();
    private Handler responseHandler;
    private ThumbnailDownloadListener<T> thumbnailDownloadListener;

    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> thumbnailDownloadListener) {
        this.thumbnailDownloadListener = thumbnailDownloadListener;
    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        this.responseHandler = responseHandler;
    }

    @Override
    protected void onLooperPrepared() {
        requestHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if(msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    Log.i(TAG, "Got a request for URL: " + requestMap.get(target));
                    handleRequest(target);
                }
            }
        };

    }

    private void handleRequest(final T target) {
        try {
            final String url = requestMap.get(target);
            if(url == null) {
                return;
            }
            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            Log.i(TAG, "Bitmap created.");

            responseHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(requestMap.get(target) != url || hasQuit) {
                        return;
                    }
                    requestMap.remove(target);
                    thumbnailDownloadListener.onThumbnailDownloaded(target, bitmap);
                }
            });

        } catch (IOException ioe) {
            Log.e(TAG, "Error downloading image.", ioe);
        }
    }

    @Override
    public boolean quit() {
        hasQuit = true;
        return super.quit();
    }

    public void clearQueue() {
        requestHandler.removeMessages(MESSAGE_DOWNLOAD);
        requestMap.clear();
    }

    public void queueThumbnail(T target, String url) {
        Log.i(TAG, "Got a URL: " + url);
        if(url == null) {
            requestMap.remove(target);
        } else {
            requestMap.put(target, url);
            requestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }
    }
}
