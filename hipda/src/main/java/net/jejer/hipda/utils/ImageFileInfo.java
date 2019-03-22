package net.jejer.hipda.utils;

import com.huantansheng.easyphotos.models.album.entity.Photo;

/**
 * simple bean for selected image file
 * Created by GreenSkinMonster on 2015-04-14.
 */
public class ImageFileInfo {
    private String filePath;
    private int orientation = -1;
    private long fileSize;
    private String mime;
    private int width;
    private int height;
    private boolean original;

    public ImageFileInfo(Photo photo) {
        filePath = photo.path;
        fileSize = photo.size;
        mime = photo.type;
        width = photo.width;
        height = photo.height;
        original = photo.selectedOriginal;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMime() {
        return Utils.nullToText(mime).toLowerCase();
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public String getFileName() {
        if (filePath != null && filePath.contains("/"))
            return filePath.substring(filePath.lastIndexOf("/") + 1);
        return filePath;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean isOriginal() {
        return original;
    }

    public void setOriginal(boolean original) {
        this.original = original;
    }

    public boolean isGif() {
        return getMime().contains("gif");
    }
}
