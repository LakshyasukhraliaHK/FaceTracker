/***
 * Copyright (c) 2012 readyState Software Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.google.android.gms.samples.vision.face.facetracker.uploader;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.JobIntentService;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ProgressEvent;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.google.android.gms.samples.vision.face.facetracker.BuildConfig.S3_ACCESS_KEY;
import static com.google.android.gms.samples.vision.face.facetracker.BuildConfig.S3_BUCKET;
import static com.google.android.gms.samples.vision.face.facetracker.BuildConfig.S3_SECRETKEY;


public class UploadService extends JobIntentService {

	public static final String ARG_FILE_PATH = "path";
	public static final String UPLOAD_STATE_CHANGED_ACTION = "com.hlkt.camorcr.example.UPLOAD_STATE_CHANGED_ACTION";
	public static final String UPLOAD_CANCELLED_ACTION = "com.hlkt.camocr.example.UPLOAD_CANCELLED_ACTION";
	public static final String S3KEY_EXTRA = "s3key";
	public static final String PERCENT_EXTRA = "percent";
	public static final String MSG_EXTRA = "msg";
	private static final int NOTIFY_ID_UPLOAD = 1337;

	private AmazonS3Client s3Client;
	private Uploader uploader;
	private NotificationManager nm;






	@Override
		public void onCreate () {
		super.onCreate();
			s3Client = new AmazonS3Client(
					new BasicAWSCredentials(S3_ACCESS_KEY, S3_SECRETKEY));
			nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

			IntentFilter f = new IntentFilter();
			f.addAction(UPLOAD_CANCELLED_ACTION);
			registerReceiver(uploadCancelReceiver, f);

			IntentFilter fail = new IntentFilter();
			fail.addAction("com.alert.receiver");
			registerReceiver(uploadAlertReceiver, fail);
		}


		public static void enqueueWork(Context context,Intent intent){
		enqueueWork(context,UploadService.class,1000,intent);
		}

		@Override
		protected void onHandleWork (Intent intent){


			Intent intent_start = new Intent("com.progress.receiver");
			intent_start.putExtra("visibility", "visible");
			sendBroadcast(intent_start);

			String filePath = intent.getStringExtra(ARG_FILE_PATH);
			if(filePath==null){return;}
			File fileToUpload = new File(filePath);
			final String s3ObjectKey = "OCR/" + md5(filePath) + ".png";

			String s3BucketName = S3_BUCKET;

			final String msg = "Uploading " + s3ObjectKey + "...";

			// create a new uploader for this file
			uploader = new Uploader(this, s3Client, s3BucketName, s3ObjectKey, fileToUpload);

			// listen for progress updates and broadcast/notify them appropriately
			uploader.setProgressListener(new Uploader.UploadProgressListener() {
				@Override
				public void progressChanged(ProgressEvent progressEvent,
											long bytesUploaded, int percentUploaded) {

					Notification notification = buildNotification(msg, percentUploaded);
					nm.notify(NOTIFY_ID_UPLOAD, notification);
					broadcastState(s3ObjectKey, percentUploaded, msg);
				}
			});

			// broadcast/notify that our upload is starting
			Notification notification = buildNotification(msg, 0);
			nm.notify(NOTIFY_ID_UPLOAD, notification);
			broadcastState(s3ObjectKey, 0, msg);

			try {
				String s3Location = uploader.start(); // initiate the upload
				broadcastState(s3ObjectKey, -1, "File successfully uploaded to " + s3Location);
				Intent intent_success = new Intent("com.alert.receiver");
				intent_success.putExtra("Alert", "Upload Success: " + filePath);
				sendBroadcast(intent_success);


			} catch (UploadIterruptedException uie) {
				broadcastState(s3ObjectKey, -1, "User interrupted");
			} catch (Exception e) {
				Intent intent_fail = new Intent("com.alert.receiver");
				intent_fail.putExtra("Alert", "Upload failed " + filePath);
				sendBroadcast(intent_fail);
				e.printStackTrace();
				broadcastState(s3ObjectKey, -1, "Error: " + e.getMessage());

			}

			if (new File(filePath).getAbsoluteFile().delete()) {
				Intent intent_stop = new Intent("com.progress.receiver");
				intent_stop.putExtra("visibility", "gone");
				sendBroadcast(intent_stop);
			}


//			Intent intent_del=new Intent("com.alert.receiver");
//			intent_del.putExtra("Alert","Delete successful");
//			sendBroadcast(intent_del);
			//}
		}


		@Override
		public void onDestroy () {
			nm.cancel(NOTIFY_ID_UPLOAD);
			unregisterReceiver(uploadCancelReceiver);
			unregisterReceiver(uploadAlertReceiver);
			super.onDestroy();
		}

		private void broadcastState (String s3key,int percent, String msg){
			Intent intent = new Intent(UPLOAD_STATE_CHANGED_ACTION);
			Bundle b = new Bundle();
			b.putString(S3KEY_EXTRA, s3key);
			b.putInt(PERCENT_EXTRA, percent);
			b.putString(MSG_EXTRA, msg);
			intent.putExtras(b);
			sendBroadcast(intent);
		}

		private Notification buildNotification (String msg,int progress){
			NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
//			builder.setWhen(System.currentTimeMillis());
//			builder.setTicker(msg);
//			builder.setSmallIcon(R.drawable.ic_launcher_background);
//			builder.setContentTitle(getString(R.string.app_name));
//			builder.setContentText(msg);
//			builder.setOngoing(true);
//			builder.setProgress(100, progress, false);
//
//			Intent notificationIntent = new Intent(this, MainActivity.class);
//			notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
//			builder.setContentIntent(contentIntent);

			return builder.build();
		}

		private BroadcastReceiver uploadCancelReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (uploader != null) {
					uploader.interrupt();
				}
			}
		};

		private BroadcastReceiver uploadAlertReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Bundle alertBundle = intent.getExtras();
				if (alertBundle != null) {
					Toast.makeText(context, (String) alertBundle.get("Alert"), Toast.LENGTH_SHORT).show();
					//String delPath = intent.getStringExtra(ARG_FILE_PATH);
					//new File(delPath).getAbsoluteFile().delete();
				} else {
					Toast.makeText(context, "No alert recieved", Toast.LENGTH_SHORT).show();
				}

			}
		};


		private String md5 (String s){
			try {
				// create MD5 Hash
				MessageDigest digest = MessageDigest.getInstance("MD5");
				digest.update(s.getBytes());
				byte messageDigest[] = digest.digest();

				// create Hex String
				StringBuffer hexString = new StringBuffer();
				for (int i = 0; i < messageDigest.length; i++)
					hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
				return hexString.toString();

			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
				return null;
			}
		}


}
