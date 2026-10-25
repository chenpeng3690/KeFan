package com.kefan.app.data.remote.prompts

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * 手动框选后，用视觉模型识别裁剪区域里的全部题目。
 * 输出格式复用错题提取的 items JSON。
 */
object MistakeQuestionPrompt {

    private val SYSTEM_PROMPT = """
你在识别一张用户框选的题目照片。用户已经确认框选范围内是错题，不需要你判断对错，也不需要筛选，框选区域里有几道题就全部保留几道。

【分题规则】
- 这张图里可能有 1~5 道题，也可能只有一道。
- 从每道题的题号或题干开始，到这道题的选项、作答区域或下一题开始前结束。
- 用户框了两道题，items 就输出两项；框了三道题，就输出三项。不要合并，不要漏题。
- 不要因为某道题看起来做对了就丢弃，用户框选的题目全部保留。

【文字规则】
- stemText 不要带题号，直接从题干文字开始，例如不要写“3.”、“(2)”、“①”。
- 如果是选择题，必须保留题干和 A/B/C/D 选项，每个选项单独一行，不要只留题干。
- 如果是填空题，必须保留空格下划线，统一写成 ______，不要删掉下划线。
- 公式用 $...$ 包裹，内部只使用：^、_、\frac{分子}{分母}、\sqrt{内容}、\le、\ge、\ne、\times、\div、\pm、\pi、\alpha、\beta、\theta、\angle、\triangle、\perp、\parallel、\infty。
- 不支持的复杂公式照原样写，不要强行改写。

【输出严格 JSON，不要任何多余文字】
{
  "items": [
    {
      "stemText": "题干文字，不带题号；选择题保留选项，填空题保留 ______；公式用 $...$ 包裹",
      "studentAnswer": "学生写的答案，没写则为空字符串",
      "correctAnswer": "正确答案，看不出则为空字符串，不许猜",
      "knowledgeTags": ["1~3个知识点，用教材通用叫法"],
      "errorType": "概念不清|审题失误|计算失误|方法不会|粗心遗漏（选最接近的一个）",
      "difficulty": 1到5的整数,
      "hasFigure": true 或 false,
      "figureNote": "hasFigure 为 true 时必填：图里有什么",
      "figureBox": "hasFigure 为 true 时必填，只框这道题的图形本身，格式 [左, 上, 宽, 高]，取值 0~1，相对这张裁剪图。不要包含题干文字",
      "pageIndex": 1,
      "confidence": 0.0到1.0的小数,
      "cropHint": ""
    }
  ]
}

【硬约束】
- stemText 必须是图片里真实存在的文字，不许补全、不许编造数字。
- 看不清的地方留空或降低 confidence，不要猜。
- 没有图形、表格、统计图、几何图时 hasFigure 填 false，figureBox 留空数组。
- items 最多 5 项；只输出 JSON。
""".trimIndent()

    suspend fun buildMessagesJson(image: File): String = withContext(Dispatchers.IO) {
        val bytes = image.readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        buildJsonArray {
            addJsonObject {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            }
            addJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    addJsonObject {
                        put("type", "text")
                        put("text", "请识别这张裁剪图里的全部题目，逐题输出，不要判断对错，并按系统提示输出 JSON。")
                    }
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:image/jpeg;base64,$base64")
                        }
                    }
                })
            }
        }.toString()
    }
}
