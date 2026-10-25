package com.kefan.app.data.quality

/**
 * 内置敏感表述词库：命中不自动替换，标橙底并给出改写建议。
 * 用户可以在设置里增删词条，自定义词库保存在 DataStore。
 */
object SensitiveDictionary {
    val words: Map<String, List<String>> = linkedMapOf(
        "笨" to listOf("这块内容需要再多练两遍", "这个知识点还需要一点积累"),
        "蠢" to listOf("这个知识点需要多练两遍"),
        "脑子不行" to listOf("这个知识点需要多练两遍"),
        "差" to listOf("这块还有提升空间"),
        "很差" to listOf("这块还有不小的提升空间"),
        "太差" to listOf("这块还有不小的提升空间"),
        "不努力" to listOf("最近在基础知识上还需要再下功夫"),
        "不用心" to listOf("本节课专注度还可以再提高"),
        "态度有问题" to listOf("这两个环节还需再专注一些"),
        "没天赋" to listOf("这个知识点还需要更多练习"),
        "懒" to listOf("课后练习还需要更主动一些"),
        "敷衍" to listOf("作业完成度还可以再提高"),
        "没救了" to listOf("这块需要持续加油"),
        "退步严重" to listOf("这次成绩暂时下滑，我们一起找原因"),
        "注意力缺陷" to listOf("这节课注意力还需要加强")
    )

    /** 默认词库（只取词条，建议仍按内置映射查找） */
    val defaultWords: List<String> get() = words.keys.toList()
}