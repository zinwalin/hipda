package net.jejer.hipda.job;

import android.graphics.Bitmap;

import net.jejer.hipda.async.UploadImgHelper;
import net.jejer.hipda.ui.HiApplication;
import net.jejer.hipda.utils.ImageFileInfo;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by GreenSkinMonster on 2016-04-01.
 */
public class ImageUploadJob extends BaseJob implements UploadImgHelper.UploadImgListener {

    private String mUid;
    private String mHash;
    private Collection<ImageFileInfo> mPhotos;
    private Collection<ImageUploadEvent> mHoldEvents = new ArrayList<>();

    public ImageUploadJob(String sessionId, String uid, String hash, Collection<ImageFileInfo> photos) {
        super(sessionId);
        mUid = uid;
        mHash = hash;
        mPhotos = photos;
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onRun() throws Throwable {
        new UploadImgHelper(this, mUid, mHash, mPhotos).upload();

        ImageUploadEvent event = new ImageUploadEvent();
        event.mSessionId = mSessionId;
        event.mType = ImageUploadEvent.ALL_DONE;
        event.holdEvents = mHoldEvents;
        EventBus.getDefault().postSticky(event);
    }

    @Override
    public void updateProgress(int total, int current, int percentage) {
        ImageUploadEvent event = new ImageUploadEvent();
        event.mSessionId = mSessionId;
        event.mType = ImageUploadEvent.UPLOADING;
        event.mTotal = total;
        event.mCurrent = current;
        event.mPercentage = percentage;
        EventBus.getDefault().postSticky(event);
    }

    @Override
    public void itemComplete(String uri, int total, int current, String currentFileName, String message, String detail, String imgId, Bitmap thumbtail) {
        UploadImage image = new UploadImage();
        image.setFileName(currentFileName);
        image.setImgId(imgId);
        image.setPath(uri);
        image.setThumb(thumbtail);

        ImageUploadEvent event = new ImageUploadEvent();
        event.mSessionId = mSessionId;
        event.mType = ImageUploadEvent.ITEM_DONE;
        event.mTotal = total;
        event.mCurrent = current;
        event.mMessage = message;
        event.mDetail = detail;
        event.mImage = image;

        if (HiApplication.isAppVisible()) {
            EventBus.getDefault().post(event);
        } else {
            mHoldEvents.add(event);
        }
    }

}
