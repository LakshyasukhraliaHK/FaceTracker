package com.google.android.gms.samples.vision.face.facetracker;



import android.os.Environment;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.Deflater;

import static com.google.android.gms.samples.vision.face.facetracker.BuildConfig.S3_ACCESS_KEY;
import static com.google.android.gms.samples.vision.face.facetracker.BuildConfig.S3_BUCKET;
import static com.google.android.gms.samples.vision.face.facetracker.BuildConfig.S3_SECRETKEY;

/**
 * Utils for saving files
 */
public final class FileUtils {
  private FileUtils() {
  }

  /**
   * Save binary data to public image dirr
   *
   * @param content data to save
   * @return newly created file
   * @throws IOException
   */
  public static File saveImage(byte[] content, String extension) throws IOException {
    // Create an image file name
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    File storageDir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES);
    File image = new File(storageDir, timeStamp + "." + extension);
    FileOutputStream out = null;
    try {
      out = new FileOutputStream(image);
      out.write(content);
      out.close();
    } finally {
      if (out != null) {
        out.close();
      }
    }
    return image;
  }

  public static void uploadToS3(final String OBJECT_KEY, final byte[] bis, AmazonS3Client s3Client)  {
    InputStream is = new ByteArrayInputStream(bis);

    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentType("image/jpeg");
    /*Out of memory can be caused if you don't specify minimum metadata of Content Length of your inputstream*/
    Long contentLength = Long.valueOf(bis.length);
    metadata.setContentLength(contentLength);
    PutObjectRequest putObjectRequest = new PutObjectRequest(S3_BUCKET,
        OBJECT_KEY,/*key name*/
        is,/*input stream*/
        metadata);
    try {
      PutObjectResult putObjectResult = s3Client.putObject(putObjectRequest);
    } catch (AmazonServiceException ase) {
      System.out.println("Error Message:    " + ase.getMessage());
      System.out.println("HTTP Status Code: " + ase.getStatusCode());
      System.out.println("AWS Error Code:   " + ase.getErrorCode());
      System.out.println("Error Type:       " + ase.getErrorType());
      System.out.println("Request ID:       " + ase.getRequestId());
    } catch (AmazonClientException ace) {
      System.out.println("Error Message: " + ace.getMessage());
    } finally {
      try {
        is.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private static byte[] compressData(byte[] bytes){
    Deflater deflater = new Deflater();
    deflater.setInput(bytes);
    deflater.finish();

    ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length);
    byte[] buffer = new byte[1024];

    while(!deflater.finished())
    {
      int bytesCompressed = deflater.deflate(buffer);
      bos.write(buffer,0,bytesCompressed);
    }

    try
    {
      bos.close();
    }
    catch(IOException ioe)
    {
      System.out.println("Error while closing the stream : " + ioe);
    }

    return bos.toByteArray();

  }
}
