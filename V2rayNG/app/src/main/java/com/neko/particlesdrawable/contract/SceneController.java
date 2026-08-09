package com.neko.particlesdrawable.contract;

import androidx.annotation.Keep;

@Keep
public interface SceneController {

    void nextFrame();

    void makeFreshFrame();

    void makeFreshFrameWithParticlesOffscreen();

}
