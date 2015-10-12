package com.gobeike.library.clock;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Random;

/**
 * Created by xff on 2015/10/11.
 */
public class AlarmBean implements Parcelable {

    public long clockTime = 0l;
    public String id;
    public String title;
    public int serid= new Random().nextInt(1000)+50;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(clockTime);
        dest.writeLong(serid);
        dest.writeString(id);
        dest.writeString(title);
    }

    public static final Creator<AlarmBean> CREATOR = new Creator<AlarmBean>() {
        @Override
        public AlarmBean createFromParcel(Parcel source) {
            AlarmBean bean = new AlarmBean();
            bean.clockTime = source.readLong();
            bean.serid = source.readInt();
            bean.id = source.readString();
            bean.title = source.readString();
            return bean;
        }

        @Override
        public AlarmBean[] newArray(int size) {
            return new AlarmBean[size];
        }
    };
}
