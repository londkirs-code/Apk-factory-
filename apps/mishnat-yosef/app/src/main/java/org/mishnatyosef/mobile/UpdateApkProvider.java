package org.mishnatyosef.mobile;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
public class UpdateApkProvider extends ContentProvider {
 @Override public boolean onCreate(){return true;}
 @Override public String getType(Uri uri){return "application/vnd.android.package-archive";}
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
  if(!"r".equals(mode))throw new FileNotFoundException("Read only");
  File dir=getContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
  File f=dir==null?new File(getContext().getFilesDir(),"mishnat-yosef-update.apk"):new File(dir,"mishnat-yosef-update.apk");
  if(!f.isFile())throw new FileNotFoundException("APK not downloaded");
  return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);
 }
 @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
 @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}