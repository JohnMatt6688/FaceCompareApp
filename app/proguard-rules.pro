# ML Kit & TensorFlow Lite 必须保留
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.support.**

# 保持 TFLite 模型文件不压缩
-keepdirectories assets/**/*.tflite
