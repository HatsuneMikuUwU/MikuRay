package com.miku.ray.preferencesearch.ui

import android.os.Parcel
import android.os.Parcelable

class RevealAnimationSetting(
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    val height: Int,
    val colorAccent: Int
) : Parcelable {

    private constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(centerX)
        dest.writeInt(centerY)
        dest.writeInt(width)
        dest.writeInt(height)
        dest.writeInt(colorAccent)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<RevealAnimationSetting> {
        override fun createFromParcel(parcel: Parcel): RevealAnimationSetting {
            return RevealAnimationSetting(parcel)
        }

        override fun newArray(size: Int): Array<RevealAnimationSetting?> {
            return arrayOfNulls(size)
        }
    }
}