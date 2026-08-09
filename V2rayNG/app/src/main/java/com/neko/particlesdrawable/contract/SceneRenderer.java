package com.neko.particlesdrawable.contract;

import com.neko.particlesdrawable.KeepAsApi;
import com.neko.particlesdrawable.model.Scene;

import androidx.annotation.NonNull;

@KeepAsApi
public interface SceneRenderer {

    void drawScene(@NonNull Scene scene);
}
