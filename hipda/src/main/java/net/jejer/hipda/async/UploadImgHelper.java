package net.jejer.hipda.async;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.text.TextUtils;

import com.bumptech.glide.Glide;

import net.jejer.hipda.bean.HiSettingsHelper;
import net.jejer.hipda.okhttp.OkHttpHelper;
import net.jejer.hipda.ui.HiApplication;
import net.jejer.hipda.utils.HiUtils;
import net.jejer.hipda.utils.ImageFileInfo;
import net.jejer.hipda.utils.Logger;
import net.jejer.hipda.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadImgHelper {

    private final static int MAX_QUALITY = 90;
    private static final int THUMB_SIZE = 256;

    private int mMaxImageFileSize = 800 * 1024;
    private int mMaxPixels = 2560 * 2560;

    private UploadImgListener mListener;

    private String mUid;
    private String mHash;
    private Collection<ImageFileInfo> mPhotos;

    private String mMessage = "";
    private String mDetail = "";
    private Bitmap mThumb;
    private int mTotal;
    private int mCurrent;
    private String mCurrentFileName = "";

    public UploadImgHelper(UploadImgListener v, String uid, String hash, Collection<ImageFileInfo> photos) {
        mListener = v;
        mUid = uid;
        mHash = hash;
        mPhotos = photos;

        int maxUploadSize = HiSettingsHelper.getInstance().getMaxUploadFileSize();
        if (maxUploadSize > 0 && mMaxImageFileSize > maxUploadSize) {
            mMaxPixels = (int) (0.6 * mMaxPixels);
            mMaxImageFileSize = maxUploadSize;
        }
    }

    public interface UploadImgListener {
        void updateProgress(int total, int current, int percentage);

        void itemComplete(String uri, int total, int current, String currentFileName, String message, String detail, String imgId, Bitmap thumbnail);
    }

    public void upload() {
        Map<String, String> post_param = new HashMap<>();

        post_param.put("uid", mUid);
        post_param.put("hash", mHash);

        mTotal = mPhotos.size();

        int i = 0;
        for (ImageFileInfo photo : mPhotos) {
            mCurrent = i++;
            mListener.updateProgress(mTotal, mCurrent, -1);
            String imgId = uploadImage(HiUtils.UploadImgUrl, post_param, photo);
            mListener.itemComplete(photo.getFilePath(), mTotal, mCurrent, mCurrentFileName, mMessage, mDetail, imgId, mThumb);
        }
    }

    private String uploadImage(String urlStr, Map<String, String> param, ImageFileInfo imageFileInfo) {
        mMessage = "";
        mCurrentFileName = "";

        mCurrentFileName = imageFileInfo.getFileName();

        ByteArrayOutputStream baos = getImageStream(imageFileInfo);
        if (baos == null) {
            if (TextUtils.isEmpty(mMessage))
                mMessage = "处理图片发生错误";
            return null;
        }

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);
        for (String key : param.keySet()) {
            builder.addFormDataPart(key, param.get(key));
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd_HHmm", Locale.US);
        String fileName = "Hi_" + formatter.format(new Date()) + "." + Utils.getImageFileSuffix(imageFileInfo.getMime());
        RequestBody requestBody = RequestBody.create(MediaType.parse(imageFileInfo.getMime()), baos.toByteArray());
        builder.addFormDataPart("Filedata", fileName, requestBody);

        Request request = new Request.Builder()
                .url(urlStr)
                .post(builder.build())
                .build();

        String imgId = null;
        try {
            Response response = OkHttpHelper.getInstance().getClient().newCall(request).execute();
            if (!response.isSuccessful())
                throw new IOException(OkHttpHelper.ERROR_CODE_PREFIX + response.networkResponse().code());

            String responseText = response.body().string();
            // DISCUZUPLOAD|0|1721652|1
            if (responseText.contains("DISCUZUPLOAD")) {
                String[] s = responseText.split("\\|");
                if (s.length < 3 || s[2].equals("0")) {
                    mMessage = "无效上传图片ID";
                    mDetail = "原图限制：" + Utils.toSizeText(HiSettingsHelper.getInstance().getMaxUploadFileSize())
                            + "\n压缩目标：" + Utils.toSizeText(mMaxImageFileSize)
                            + "\n实际大小：" + Utils.toSizeText(baos.size())
                            + "\n" + responseText;
                } else {
                    imgId = s[2];
                }
            } else {
                mMessage = "无法获取图片ID";
                mDetail = "原图限制：" + Utils.toSizeText(HiSettingsHelper.getInstance().getMaxUploadFileSize())
                        + "\n压缩目标：" + Utils.toSizeText(mMaxImageFileSize)
                        + "\n实际大小：" + Utils.toSizeText(baos.size())
                        + "\n" + responseText;
            }
        } catch (Exception e) {
            Logger.e(e);
            mMessage = OkHttpHelper.getErrorMessage(e).getMessage();
            mDetail = "原图限制：" + Utils.toSizeText(HiSettingsHelper.getInstance().getMaxUploadFileSize())
                    + "\n压缩目标：" + Utils.toSizeText(mMaxImageFileSize)
                    + "\n实际大小：" + Utils.toSizeText(baos.size())
                    + "\n" + e.getMessage();
        } finally {
            try {
                baos.close();
            } catch (IOException ignored) {
            }
        }
        return imgId;
    }

    private ByteArrayOutputStream getImageStream(ImageFileInfo imageFileInfo) {
        if (imageFileInfo.isGif()
                && imageFileInfo.getFileSize() > HiSettingsHelper.getInstance().getMaxUploadFileSize()) {
            mMessage = "GIF图片大小不能超过 " + Utils.toSizeText(HiSettingsHelper.getInstance().getMaxUploadFileSize());
            return null;
        }

        //gif or very long/wide image or small image or filePath is null
        if (isDirectUploadable(imageFileInfo)) {
            mThumb = getThumbnail(imageFileInfo.getFilePath());
            return readFileToStream(imageFileInfo.getFilePath());
        }

        return compressFile(imageFileInfo);
    }

    private ByteArrayOutputStream compressFile(ImageFileInfo imageFileInfo) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFileInfo.getFilePath(), opts);

        int width = opts.outWidth;
        int height = opts.outHeight;

        //inSampleSize is needed to avoid OOM
        int be = width * height / mMaxPixels;
        if (be <= 0)
            be = 1; //be=1表示不缩放
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        newOpts.inJustDecodeBounds = false;
        newOpts.inSampleSize = be;

        Bitmap newbitmap = BitmapFactory.decodeFile(imageFileInfo.getFilePath(), newOpts);

        width = newbitmap.getWidth();
        height = newbitmap.getHeight();

        //scale bitmap so later compress could run less times, once is the best result
        //rotate if needed
        if (width * height > mMaxPixels
                || imageFileInfo.getOrientation() > 0) {

            float scale = 1.0f;
            if (width * height > mMaxPixels) {
                scale = (float) Math.sqrt(mMaxPixels * 1.0 / (width * height));
            }

            Matrix matrix = new Matrix();
            if (imageFileInfo.getOrientation() > 0)
                matrix.postRotate(imageFileInfo.getOrientation());
            if (scale < 1)
                matrix.postScale(scale, scale);

            Bitmap scaledBitmap = Bitmap.createBitmap(newbitmap, 0, 0, newbitmap.getWidth(),
                    newbitmap.getHeight(), matrix, true);

            newbitmap.recycle();
            newbitmap = scaledBitmap;
        }

        int quality = MAX_QUALITY;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        newbitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        while (baos.size() > mMaxImageFileSize) {
            quality -= 10;
            if (quality <= 50) {
                mMessage = "无法压缩图片至指定大小 " + Utils.toSizeText(mMaxImageFileSize);
                return null;
            }
            baos.reset();
            newbitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        }

        mThumb = ThumbnailUtils.extractThumbnail(newbitmap, THUMB_SIZE, THUMB_SIZE);
        newbitmap.recycle();
        newbitmap = null;

        return baos;
    }

    private boolean isDirectUploadable(ImageFileInfo imageFileInfo) {
        if (imageFileInfo.isOriginal())
            return true;

        long fileSize = imageFileInfo.getFileSize();
        int w = imageFileInfo.getWidth();
        int h = imageFileInfo.getHeight();

        int orientation = getOrientationFromExif(imageFileInfo.getFilePath());
        imageFileInfo.setOrientation(orientation);
        if (orientation > 0)
            return false;

        //gif image
        if (imageFileInfo.isGif() && fileSize <= HiSettingsHelper.getInstance().getMaxUploadFileSize())
            return true;

        //very long or wide image
        if (w > 0 && h > 0 && fileSize <= HiSettingsHelper.getInstance().getMaxUploadFileSize()) {
            if (Math.max(w, h) * 1.0 / Math.min(w, h) >= 3)
                return true;
        }

        //normal image
        return fileSize <= mMaxImageFileSize && w * h <= mMaxPixels;
    }

    private static ByteArrayOutputStream readFileToStream(String file) {
        FileInputStream fileInputStream = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            fileInputStream = new FileInputStream(file);
            int readedBytes;
            byte[] buf = new byte[1024];
            while ((readedBytes = fileInputStream.read(buf)) > 0) {
                bos.write(buf, 0, readedBytes);
            }
            return bos;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (fileInputStream != null)
                    fileInputStream.close();
            } catch (Exception ignored) {

            }
        }
    }

    private static int getOrientationFromExif(String path) {
        int orientation = -1;
        try {
            ExifInterface exif = new ExifInterface(path);
            String orientString = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
            orientation = orientString != null ? Integer.parseInt(orientString) : ExifInterface.ORIENTATION_NORMAL;

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    orientation = 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    orientation = 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    orientation = 270;
            }
        } catch (Exception e) {
            Logger.e(e);
        }
        return orientation;
    }

    private static Bitmap getThumbnail(String filepath) {
        try {
            return Glide.with(HiApplication.getAppContext())
                    .asBitmap()
                    .load(filepath)
                    .centerCrop()
                    .override(THUMB_SIZE, THUMB_SIZE)
                    .submit()
                    .get();
        } catch (Exception e) {
            Logger.e(e);
        }
        return null;
    }

}
