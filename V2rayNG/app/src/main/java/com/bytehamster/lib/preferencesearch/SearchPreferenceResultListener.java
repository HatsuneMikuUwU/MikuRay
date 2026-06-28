package com.bytehamster.lib.preferencesearch;

import androidx.annotation.NonNull;

public interface SearchPreferenceResultListener {
    void onSearchResultClicked(@NonNull SearchPreferenceResult result);
}
