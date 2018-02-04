package com.coder.zzq.versionupdaterlib.service;

import android.app.DownloadManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.support.annotation.Nullable;

import com.coder.zzq.versionupdaterlib.MessageSender;
import com.coder.zzq.versionupdaterlib.bean.DownloadEvent;
import com.coder.zzq.versionupdaterlib.bean.DownloadFileInfo;
import com.coder.zzq.versionupdaterlib.bean.LastDownloadInfo;
import com.coder.zzq.versionupdaterlib.bean.UpdaterSetting;
import com.coder.zzq.versionupdaterlib.util.Utils;

import java.io.File;
import java.util.Date;

/**
 * Created by 朱志强 on 2018/1/27.
 */

public class DownloadService extends IntentService {
    public static final String UPDATER_SETTING = "updater_setting";


    public DownloadService() {
        super("DownloadApkService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        UpdaterSetting updaterSetting = intent.getParcelableExtra(UPDATER_SETTING);

        LastDownloadInfo lastDownloadInfo = LastDownloadInfo.fetch(this);

        if (lastDownloadInfo.getVersionCode() == updaterSetting.getRemoteVersionCode()) {
            DownloadFileInfo downloadFileInfo = Utils.getInfoOfDownloadFile(this, lastDownloadInfo.getDownloadId());
            switch (downloadFileInfo.getDownloadStatus()) {
                case DownloadManager.STATUS_PENDING:
                case DownloadManager.STATUS_RUNNING:
                    MessageSender.sendMsg(new DownloadEvent(DownloadEvent.DOWNLOAD_IN_PROGRESS));
                    break;
                case DownloadManager.STATUS_PAUSED:
                    MessageSender.sendMsg(new DownloadEvent(DownloadEvent.DOWNLOAD_PAUSED, downloadFileInfo.getReason()));
                    break;
                case DownloadManager.STATUS_FAILED:
                    switch (downloadFileInfo.getReason()) {
                        case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                        case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                            MessageSender.sendMsg(new DownloadEvent(DownloadEvent.DOWNLOAD_FAILED, downloadFileInfo.getReason()));
                            Utils.getDownloadManager(this).remove(lastDownloadInfo.getDownloadId());
                            LastDownloadInfo.clear(this);
                            break;
                        case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                            updaterSetting.setSavedApkName(updaterSetting.getBaseApkName() + new Date().toString());
                            downloadAgain(updaterSetting, lastDownloadInfo);
                            break;
                        default:
                            downloadAgain(updaterSetting, lastDownloadInfo);
                            break;
                    }
                case DownloadManager.STATUS_SUCCESSFUL:
                    File file = new File(downloadFileInfo.getUri().getEncodedPath());
                    if (file.exists() && file.length() == downloadFileInfo.getFileSizeBytes()) {
                        Utils.installApk(this,downloadFileInfo.getUri());
                    } else {
                        downloadAgain(updaterSetting, lastDownloadInfo);
                    }
                    break;
                case DownloadFileInfo.STATUS_NO_EXISTS:
                    downloadAgain(updaterSetting, lastDownloadInfo);
                    break;
            }

        } else {
            downloadAgain(updaterSetting, lastDownloadInfo);
        }
    }

    private void downloadAgain(UpdaterSetting updaterSetting, LastDownloadInfo lastDownloadInfo) {
        if (lastDownloadInfo.getDownloadId() != -1) {
            Utils.getDownloadManager(this).remove(0);
            LastDownloadInfo.clear(this);
        }

        DownloadManager.Request request = new DownloadManager.Request(updaterSetting.getRemoteApkUri())
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, updaterSetting.getSavedApkName())
                .setNotificationVisibility(updaterSetting.getNotificationVisibilityMode())
                .setTitle(updaterSetting.getNotificationTitle());
        long downloadId = Utils.getDownloadManager(this).enqueue(request);

        LastDownloadInfo.update(this).downloadId(downloadId).versionCode(updaterSetting.getRemoteVersionCode()).store();
    }


    public static void start(Context context, UpdaterSetting updaterSetting) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.putExtra(UPDATER_SETTING, updaterSetting);
        context.startService(intent);
    }
}