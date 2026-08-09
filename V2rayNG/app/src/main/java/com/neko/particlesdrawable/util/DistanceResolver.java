package com.neko.particlesdrawable.util;

import com.neko.particlesdrawable.KeepAsApi;

@KeepAsApi
public final class DistanceResolver {

    public static float distance(final float ax, final float ay,
                                 final float bx, final float by) {
        return (float) Math.sqrt(
                (ax - bx) * (ax - bx) +
                        (ay - by) * (ay - by)
        );
    }
}
