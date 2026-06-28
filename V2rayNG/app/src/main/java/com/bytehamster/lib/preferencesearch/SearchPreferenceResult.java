package com.bytehamster.lib.preferencesearch;

import com.v2ray.ang.R;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

public class SearchPreferenceResult {
    private final String key;
    private final int file;
    private final String screen;

    SearchPreferenceResult(String key, int file, String screen) {
        this.key = key;
        this.file = file;
        this.screen = screen;
    }

    /**
     * Returns the key of the preference pressed
     * @return The key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the file in which the result was found
     * @return The file in which the result was found
     */
    public int getResourceFile() {
        return file;
    }

    /**
     * Returns the screen in which the result was found
     * @return The screen in which the result was found
     */
    public String getScreen() {
        return screen;
    }

    /**
     * Highlight the preference that was found
     * @param prefsFragment Fragment that contains the preference
     */
    public void highlight(final PreferenceFragmentCompat prefsFragment) {
        new Handler().post(() -> doHighlight(prefsFragment));
    }

    private void doHighlight(final PreferenceFragmentCompat prefsFragment) {
        final Preference prefResult = prefsFragment.findPreference(getKey());

        if (prefResult == null) {
            Log.e("doHighlight", "Preference not found on given screen");
            return;
        }
        final RecyclerView recyclerView = prefsFragment.getListView();
        final RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter instanceof PreferenceGroup.PreferencePositionCallback) {
            PreferenceGroup.PreferencePositionCallback callback = (PreferenceGroup.PreferencePositionCallback) adapter;
            final int position = callback.getPreferenceAdapterPosition(prefResult);
            if (position != RecyclerView.NO_POSITION) {
                recyclerView.scrollToPosition(position);
                recyclerView.postDelayed(() -> {
                    RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
                    if (holder != null) {
                        Drawable oldBackground = holder.itemView.getBackground();
                        int color = getColorFromAttr(prefsFragment.getContext(), android.R.attr.textColorPrimary);
                        holder.itemView.setBackgroundColor(color & 0xffffff | 0x33000000);
                        new Handler().postDelayed(() -> holder.itemView.setBackgroundDrawable(oldBackground), 1000);
                        return;
                    }
                    highlightFallback(prefsFragment, prefResult);
                }, 200);
                return;
            }
        }
        highlightFallback(prefsFragment, prefResult);
    }

    /**
     * Alternative highlight method if accessing the view did not work
     */
    private void highlightFallback(PreferenceFragmentCompat prefsFragment, final Preference prefResult) {
        final Drawable oldIcon = prefResult.getIcon();
        final boolean oldSpaceReserved = prefResult.isIconSpaceReserved();
        Drawable arrow = AppCompatResources.getDrawable(prefsFragment.getContext(), R.drawable.uwu_arrow);
        int color = getColorFromAttr(prefsFragment.getContext(), android.R.attr.textColorPrimary);
        arrow.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        prefResult.setIcon(arrow);
        prefsFragment.scrollToPreference(prefResult);
        new Handler().postDelayed(() -> {
            prefResult.setIcon(oldIcon);
            prefResult.setIconSpaceReserved(oldSpaceReserved);
        }, 1000);
    }

    private int getColorFromAttr(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(attr, typedValue, true);
        TypedArray arr = context.obtainStyledAttributes(typedValue.data, new int[]{
                android.R.attr.textColorPrimary});
        int color = arr.getColor(0, 0xff3F51B5);
        arr.recycle();
        return color;
    }

    /**
     * Closes the search results page
     * @param activity The current activity
     */
    public void closeSearchPage(AppCompatActivity activity) {
        FragmentManager fm = activity.getSupportFragmentManager();
        fm.beginTransaction().remove(fm.findFragmentByTag(SearchPreferenceFragment.TAG)).commit();
    }
}
