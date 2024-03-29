package com.coder.zzq.version_updater.tasks.query_progress;

import android.app.DownloadManager;
import android.database.Cursor;
import android.net.Uri;

import com.coder.zzq.version_updater.bean.DownloadProgress;
import com.coder.zzq.version_updater.bean.ReadableVersionInfo;
import com.coder.zzq.version_updater.bean.download_event.DownloadFailed;
import com.coder.zzq.version_updater.bean.download_event.DownloadInProgress;
import com.coder.zzq.version_updater.communication.DownloadEventNotifier;
import com.coder.zzq.version_updater.tasks.TaskScheduler;
import com.coder.zzq.version_updater.util.UpdateUtil;

import java.util.TimerTask;

import static android.app.DownloadManager.STATUS_FAILED;
import static android.app.DownloadManager.STATUS_RUNNING;
import static android.app.DownloadManager.STATUS_SUCCESSFUL;

public abstract class QueryProgressTask extends TimerTask {

    private final long mDownloadId;

    private ReadableVersionInfo mNewVersionInfo;

    private final DownloadManager mDownloadManager;


    public QueryProgressTask(long downloadId, ReadableVersionInfo newVersionInfo) {
        mDownloadId = downloadId;
        mDownloadManager = UpdateUtil.getDownloadManager();
        mNewVersionInfo = newVersionInfo;
    }


    @Override
    public void run() {
        if (mDownloadId != -1) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(mDownloadId);
            try (Cursor cursor = mDownloadManager.query(query)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    String uriStr = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    long sizeSoFar = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long totalSize = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    long percentage = (sizeSoFar * 100 / totalSize);

                    switch (status) {
                        case STATUS_RUNNING:
                            if (!DownloadEventNotifier.get().isFilteringIntermediateProgress()) {
                                DownloadEventNotifier.get().notifyEvent(new DownloadInProgress(new DownloadProgress(sizeSoFar, totalSize, percentage + "%", false), mNewVersionInfo, null));
                            }
                            break;
                        case STATUS_FAILED:
                            int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                            switch (reason) {
                                case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                                    DownloadEventNotifier.get().notifyEvent(new DownloadFailed(DownloadFailed.FAILED_REASON_SD_CARD_NOT_FOUND));
                                    break;
                                case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                                    DownloadEventNotifier.get().notifyEvent(new DownloadFailed(DownloadFailed.FAILED_REASON_INSUFFICIENT_SPACE));
                                    break;
                            }
                            cancelDownloadService();
                            mDownloadManager.remove(mDownloadId);
                            break;
                        case STATUS_SUCCESSFUL:
                            DownloadEventNotifier.get().notifyEvent(
                                    new DownloadInProgress(
                                            new DownloadProgress(sizeSoFar, totalSize, percentage + "%", true),
                                            mNewVersionInfo,
                                            UpdateUtil.createApkInstaller(Uri.parse(uriStr), true)
                                    )
                            );
                            cancelDownloadService();
                            break;
                    }

                    if (status != STATUS_SUCCESSFUL && status != STATUS_FAILED) {
                        TaskScheduler.queryDownloadProgressDelay(this);
                    }
                }
            }
        }

    }

    protected abstract void cancelDownloadService();
}
