package com.leejlredstar.redefinencm.kmp.util

import coil3.Image

/**
 * 从 Coil 加载结果中提取专辑主题色（原版 ImageParser.imageThemeColor 的 KMP 版）。
 *
 * @param preferStyle 0=muted，1=vibrant，2=dominant（与原版一致，muted 优先）
 * @return 0xAARRGGBB；null 表示该平台未实现或提取失败（调用方保持默认色）
 *
 * - Android actual：androidx.palette（muted → vibrant → dominant 回退链，与原版相同）
 * - JVM actual：Skia bitmap 像素采样平均色（近似）
 * - iOS actual：暂未实现，返回 null
 */
expect fun themeColorFromCoilImage(image: Image, preferStyle: Int = 0): Long?
