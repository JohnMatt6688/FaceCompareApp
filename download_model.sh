#!/bin/bash
# =============================================================================
# MobileFaceNet TFLite 模型下载脚本
#
# 用法:
#   bash download_model.sh
#
# 模型来源: InsightFace MobileFaceNet (ONNX → TFLite 转换)
#
# 模型规格:
#   输入:  [1, 112, 112, 3] float32, 像素归一化 [−1, 1]
#   输出:  [1, 128] float32, 已 L2 归一化的人脸嵌入向量
#   大小:  ~4.5 MB
# =============================================================================

MODEL_URL="https://github.com/sirius-ai/MobileFaceNet_TF/raw/master/weights/mobilefacenet.tflite"
OUTPUT_DIR="app/src/main/assets"
OUTPUT_FILE="$OUTPUT_DIR/mobilefacenet.tflite"

set -e

mkdir -p "$OUTPUT_DIR"

echo ">>> 下载 MobileFaceNet TFLite 模型..."
echo "    URL: $MODEL_URL"

if command -v wget &>/dev/null; then
    wget -O "$OUTPUT_FILE" "$MODEL_URL"
elif command -v curl &>/dev/null; then
    curl -L -o "$OUTPUT_FILE" "$MODEL_URL"
else
    echo "错误: 未找到 wget 或 curl，请手动下载模型。"
    echo ""
    echo "备选方案:"
    echo "  1. 访问 InsightFace 官方模型仓库下载 mobilefacenet.onnx"
    echo "  2. 使用 tools/onnx2tf.py 或 onnx2tflite 工具转换为 TFLite"
    echo "  3. 将 .tflite 文件放到 $OUTPUT_DIR/"
    exit 1
fi

SIZE=$(stat -f%z "$OUTPUT_FILE" 2>/dev/null || stat -c%s "$OUTPUT_FILE" 2>/dev/null)
echo ""
echo ">>> 下载完成: $OUTPUT_FILE ($SIZE bytes)"
echo ">>> 现在可以用 Android Studio 打开项目并构建。"
