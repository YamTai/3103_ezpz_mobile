package DatabaseStructure;

import android.os.Parcel;
import android.os.Parcelable;

public class FileMetadata implements Parcelable {

    public static final String NAME = "fileName";
    public static final String TYPE = "fileType";
    public static final String URL = "downloadUrl";
    public static final String DIRECTORY = "fileDirectory";
    public static final String SIZE = "size";
    public static final String DELETE_ON_DC = "deleteOnDisconnect";

    public final String fileName, fileType, downloadUrl;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    public String fileDirectory;
    public final long size;
    public final boolean deleteOnDisconnect;

    public FileMetadata(String name, String type, String url, String fileDirectory, long size, boolean deleteOnDc){
        this.fileName = name;
        this.fileType = type;
        this.downloadUrl = url;
        this.fileDirectory = fileDirectory;
        this.size = size;
        this.deleteOnDisconnect = deleteOnDc;
    }

    private FileMetadata(Parcel in) {
        fileName = in.readString();
        fileType = in.readString();
        downloadUrl = in.readString();
        size = in.readLong();
        deleteOnDisconnect = in.readByte() != 0x00;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(fileName);
        dest.writeString(fileType);
        dest.writeString(downloadUrl);
        dest.writeLong(size);
        dest.writeByte((byte) (deleteOnDisconnect ? 0x01 : 0x00));
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<FileMetadata> CREATOR = new Parcelable.Creator<FileMetadata>() {
        @Override
        public FileMetadata createFromParcel(Parcel in) {
            return new FileMetadata(in);
        }

        @Override
        public FileMetadata[] newArray(int size) {
            return new FileMetadata[size];
        }
    };
}