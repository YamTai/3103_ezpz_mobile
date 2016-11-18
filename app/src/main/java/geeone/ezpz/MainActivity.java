package geeone.ezpz;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import DatabaseStructure.FileMetadata;

@TargetApi(Build.VERSION_CODES.M)
public class MainActivity extends AppCompatActivity {

    private FirebaseServices mService;
    private ServiceConnection mConnection;
    private boolean mBound = false;

    // ext storage permission
    private final int READ_EXT_STORAGE_PERMISSION_REQUEST_CODE = 0;
    private int readExtStoragePermission;

    private final int IMAGE_PICKER_REQUEST_CODE = 1;


    private ArrayList<FileMetadata> mFileMetadataList;
    private FileListViewAdapter mAdapter;
    private ProgressDialog uploadPd;
    private int pdTimeout = 20000;  //20 seconds progress dialog timeout

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.main_title);

        ListView mFileList = (ListView) findViewById(R.id.listView_main_fileList);
        mFileMetadataList = new ArrayList<>();
        mAdapter = new FileListViewAdapter(this, mFileMetadataList, R.layout.file_listview_item);
        mFileList.setAdapter(mAdapter);

        //  service connection to FirebaseServices
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mService = ((FirebaseServices.LocalBinder)service).getService();
                fetchList();
                mBound = true;
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                mService = null;
                mBound = false;
            }
        };

        //  checks permission for api level >=23
        if (needsPermission()){
            readExtStoragePermission = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
        //  binds activity to FirebaseService
        if (!mBound){
            Intent i = new Intent(this, FirebaseServices.class);
            startService(i);
            bindService(i, mConnection, BIND_AUTO_CREATE);
        }
    }


    /*
        description: opens gallery for image selection for upload. executes when upload FAB is pressed.
    */
    @SuppressWarnings("UnusedParameters")
    public void upload(View v){
        if (needsPermission()){
            if (readExtStoragePermission == PackageManager.PERMISSION_GRANTED){
                Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(i, IMAGE_PICKER_REQUEST_CODE);
            }else{
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXT_STORAGE_PERMISSION_REQUEST_CODE);
            }
        }else{
            Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(i, IMAGE_PICKER_REQUEST_CODE);
        }
    }

    /*
        description: fetches list of files from Firebase database
    */
    private void fetchList(){
        mService.startMetadataListener(new MainReceiver(this, new Handler()));
    }

    /*
        description: uploads selected image to Firebase storage using image's uri
     */
    private void upload(Uri imageUri, boolean removeOnDC){
        mService.upload(imageUri, removeOnDC, new MainReceiver(this, new Handler()));
        uploadPd = new ProgressDialog(this);
        uploadPd.setIndeterminate(true);
        uploadPd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        uploadPd.setCancelable(false);
        uploadPd.setMessage(getString(R.string.main_progressDialog_uploading));
        uploadPd.show();
        //  provides timeout for progress dialog
        new Handler().postDelayed(new Runnable() {
            public void run() {
                if (uploadPd.isShowing()){
                    uploadPd.dismiss();
                    Toast.makeText(MainActivity.this, R.string.main_toast_upload_fail, Toast.LENGTH_SHORT).show();
                }
            }
        }, pdTimeout);
    }

    /*
        description: checks if device requires runtime permission (api >= 23)
        returns: true if api level >=23, else false
     */
    private boolean needsPermission(){
        return android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        mService.logout();
    }

    /*
        description: callback method for image gallery, returns image uri of selected image
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case IMAGE_PICKER_REQUEST_CODE: {
                    if (data != null) {
                        final Uri imageUri = data.getData();
                        if (imageUri != null) {
                            AlertDialog shareConfigDialog = new AlertDialog.Builder(this).create();
                            shareConfigDialog.setTitle(R.string.main_shareConfigDialog_title);
                            shareConfigDialog.setMessage(getString(R.string.main_shareConfigDialog_content));
                            shareConfigDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.main_shareConfigDialog_button_positive), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    upload(imageUri, true);
                                }
                            });
                            shareConfigDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.main_shareConfigDialog_button_negative), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    upload(imageUri, false);
                                }
                            });
                            shareConfigDialog.show();
                        }
                    }
                    break;
                }
                default: {
                    //  do nothing
                }
            }
        }
    }

    /*
        description: callback method of permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case READ_EXT_STORAGE_PERMISSION_REQUEST_CODE:{
                if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    readExtStoragePermission = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE);
                }
                break;
            }
            default:{
                //  do nothing
            }
        }
    }

    /*
        description: custom listview adapter
     */
    private class FileListViewAdapter extends BaseAdapter{
        private final Context context;
        private final ArrayList<FileMetadata> fileList;
        private final int itemLayoutResourceId;

        @SuppressWarnings("SameParameterValue")
        private FileListViewAdapter(Context c, ArrayList<FileMetadata> input, int layoutId){
            this.context = c;
            this.fileList = input;
            this.itemLayoutResourceId = layoutId;
        }
        public int getCount() {
            return fileList.size();
        }
        public Object getItem(int position) {
            return fileList.get(position);
        }
        public long getItemId(int position) {
            return position;
        }
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = getLayoutInflater();
            if (convertView == null){
                convertView = inflater.inflate(itemLayoutResourceId, parent, false);
            }
            TextView name = (TextView) convertView.findViewById(R.id.textView_fileName);
            TextView fileMetadata = (TextView) convertView.findViewById(R.id.textView_fileMeta);
            ImageView delete = (ImageView) convertView.findViewById(R.id.imageView_delete);
            delete.setColorFilter(0xFF888888);

            FileMetadata item = fileList.get(position);
            if (item != null) {
                final String fileName = item.fileName;
                final String fileType = item.fileType;
                final String downloadUrl = item.downloadUrl;
                final long fileSize = item.size;

                if ((fileName != null) && (fileType != null) && (downloadUrl != null) && (fileSize > 0)) {
                    name.setText(fileName);
                    fileMetadata.setText(fileType + ", " + String.valueOf(fileSize) + " bytes");

                    delete.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            AlertDialog delDialog = new AlertDialog.Builder(context).create();
                            delDialog.setTitle(R.string.main_stopSharingDialog_title);
                            delDialog.setMessage(getString(R.string.main_stopSharingDialog_content, fileName));
                            delDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.main_stopSharingDialog_button_negative), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            delDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.main_stopSharingDialog_button_positive), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mService.delete(fileName, new MainReceiver(context, new Handler()));
                                }
                            });
                            delDialog.show();
                        }
                    });
                    convertView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            AlertDialog shareDialog = new AlertDialog.Builder(context).create();
                            shareDialog.setTitle(R.string.main_shareDialog_title);
                            shareDialog.setMessage(fileName + "\n" + fileType + "\n" + downloadUrl);
                            shareDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.main_shareDialog_button_close), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            shareDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.main_shareDialog_button_share) , new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent i = new Intent(android.content.Intent.ACTION_SEND);
                                    i.setType("text/plain");
                                    i.putExtra(android.content.Intent.EXTRA_SUBJECT, R.string.main_share_subject);
                                    i.putExtra(android.content.Intent.EXTRA_TEXT, downloadUrl);
                                    startActivity(Intent.createChooser(i, getString(R.string.main_share_title)));
                                }
                            });
                            shareDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.main_shareDialog_button_copy), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                    ClipData clip = ClipData.newPlainText(getString(R.string.main_clipboard_link), downloadUrl);
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(context, R.string.main_toast_copied, Toast.LENGTH_SHORT).show();
                                }
                            });
                            shareDialog.show();
                        }
                    });
                }
            }
            return convertView;
        }
    }

    /*
        description: custom resultreceiver for receiving results from FirebaseServices
     */
    private class MainReceiver extends ResultReceiver {
        private final Context context;
        private MainReceiver(Context c, Handler h){
            super(h);
            context = c;
        }
        @Override
        public void onReceiveResult(int resultCode, Bundle resultData) {
            switch(resultCode){
                case FirebaseServices.UPLOAD_RESULT_CODE:{
                    if (resultData.getBoolean(FirebaseServices.UPLOAD_RESULT_DATA, false)){
                        Toast.makeText(context, R.string.main_toast_upload_success, Toast.LENGTH_SHORT).show();
                        uploadPd.dismiss();
                    }else{
                        Toast.makeText(context, R.string.main_toast_upload_fail, Toast.LENGTH_SHORT).show();
                        uploadPd.dismiss();
                    }
                    break;
                }
                case FirebaseServices.METADATA_FETCH_RESULT_CODE:{
                    if (resultData != null){
                        ArrayList<FileMetadata> fileMetaList = resultData.getParcelableArrayList(FirebaseServices.METADATA_FETCH_RESULT_DATA);
                        if (fileMetaList != null){
                            mFileMetadataList.clear();
                            mFileMetadataList.addAll(fileMetaList);
                            mAdapter.notifyDataSetChanged();
                        }
                    }else{
                        Toast.makeText(context, R.string.main_toast_fetchList_fail, Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
                case FirebaseServices.DELETE_RESULT_CODE:{
                    if (resultData != null){
                        if (resultData.getBoolean(FirebaseServices.DELETE_RESULT_DATA, false)){
                            Toast.makeText(context, R.string.main_toast_remove_success, Toast.LENGTH_SHORT).show();
                        }else{
                            Toast.makeText(context, R.string.main_toast_remove_fail, Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
                }
                default:{
                    //  do nothing
                }
            }
        }
    }
}
