package com.miku.ray.particlesdrawable

@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FILE,
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD
)
annotation class KeepAsApi