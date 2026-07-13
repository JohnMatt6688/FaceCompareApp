# MobileFaceNet TFLite 模型占位文件
# 请替换为真实的 mobilefacenet.tflite 模型文件
# 
# 获取方式:
#   InsightFace 官方模型库 → 下载 mobilefacenet.onnx → 转换为 TFLite
#   或从 sirius-ai/MobileFaceNet_TF 仓库下载预转换版本
#
# 模型规格:
#   输入:  [1, 112, 112, 3] float32, 像素范围 [−1, 1]
#   输出:  [1, 128] float32, L2 归一化嵌入向量
#   大小:  ~4.5 MB
#
# 此占位文件确保 assets 目录被 git 跟踪。
# 正式使用前请替换为真实模型文件并提交。
