#!/usr/bin/env python3
"""生成一张仿真课表图片，用来测 OCR 导入流程（不用真人课表也能验证解析）。

用法：
    python3 tools/make_sample_timetable.py out.png
"""

import sys
from PIL import Image, ImageDraw, ImageFont

FONT_PATH = "/usr/share/fonts/google-noto-sans-mono-cjk-vf-fonts/NotoSansMonoCJK-VF.ttc"
W, H = 1240, 1700
HEADER_H = 90
ROW_H = 118
LABEL_W = 130
COLS = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
ROWS = 12

# (星期 1-7, 起始节, 结束节, 课程名, 地点, 周次文字)
CLASSES = [
    (1, 1, 2, "高等数学A", "教三201", "1-16周"),
    (1, 3, 4, "大学英语", "外语楼305", "1-16周"),
    (1, 9, 10, "程序设计基础", "信息楼A402", "3-18周"),
    (2, 1, 2, "线性代数", "教二108", "1-16周"),
    (2, 5, 6, "大学物理", "理科楼215", "2-17周"),
    (3, 1, 2, "高等数学A", "教三201", "1-16周"),
    (3, 3, 4, "体育", "操场", "1-16周"),
    (3, 7, 8, "数据结构", "信息楼B301", "1-16周(单)"),
    (4, 5, 6, "大学物理实验", "实验楼C102", "4-18周"),
    (5, 1, 2, "大学英语", "外语楼305", "1-16周"),
    (5, 3, 4, "思想道德与法治", "文科楼101", "1-12周"),
]


def main() -> None:
    out = sys.argv[1] if len(sys.argv) > 1 else "sample_timetable.png"
    img = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(img)

    def font(size: int):
        return ImageFont.truetype(FONT_PATH, size)

    f_title = font(40)
    f_head = font(30)
    f_body = font(26)
    f_small = font(21)

    d.text((W // 2 - 300, 20), "2026-2027学年 第一学期 课表", fill="black", font=f_title)

    top = 100
    # 表头
    d.rectangle([0, top, W, top + HEADER_H], outline="black", width=3)
    d.text((20, top + 28), "节次", fill="black", font=f_head)
    for i, name in enumerate(COLS):
        x = LABEL_W + i * ((W - LABEL_W) // 7)
        d.text((x + 40, top + 28), name, fill="black", font=f_head)
        d.line([x, top, x, top + HEADER_H + ROWS * ROW_H], fill="black", width=2)

    # 节次行
    for r in range(ROWS):
        y = top + HEADER_H + r * ROW_H
        d.rectangle([0, y, W, y + ROW_H], outline="black", width=2)
        d.text((20, y + 44), f"第{r + 1}节", fill="black", font=f_body)

    # 课程
    for day, start, end, name, place, weeks in CLASSES:
        x = LABEL_W + (day - 1) * ((W - LABEL_W) // 7)
        y = top + HEADER_H + (start - 1) * ROW_H
        h = (end - start + 1) * ROW_H
        d.rectangle([x + 2, y + 2, x + (W - LABEL_W) // 7 - 2, y + h - 2], outline="black", width=1)
        d.text((x + 14, y + 14), name, fill="black", font=f_body)
        d.text((x + 14, y + 14 + 34), place, fill="black", font=f_small)
        d.text((x + 14, y + 14 + 62), weeks, fill="black", font=f_small)

    img.save(out)
    print(out)


if __name__ == "__main__":
    main()
