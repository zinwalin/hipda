package net.jejer.hipda.job;

import android.graphics.Bitmap;

/**
 * Created by GreenSkinMonster on 2016-04-13.
 */
public class UploadImage {

    private String mImgId;
    private String mPath;
    private String mFileName;
    private Bitmap mThumb;
    private int mStatus;
    private String mMessage;

    public String getFileName() {
        return mFileName;
    }

    public void setFileName(String fileName) {
        mFileName = fileName;
    }

    public String getImgId() {
        return mImgId;
    }

    public void setImgId(String imgId) {
        mImgId = imgId;
    }

    public String getPath() {
        return mPath;
    }

    public void setPath(String path) {
        mPath = path;
    }

    public int getStatus() {
        return mStatus;
    }

    public void setStatus(int status) {
        mStatus = status;
    }

    public Bitmap getThumb() {
        return mThumb;
    }

    public void setThumb(Bitmap thumb) {
        mThumb = thumb;
    }

    public String getMessage() {
        return mMessage;
    }

    public void setMessage(String message) {
        mMessage = message;
    }
}
