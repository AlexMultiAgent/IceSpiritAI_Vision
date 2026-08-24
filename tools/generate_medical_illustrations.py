#!/usr/bin/env python3
"""
Generate 9 synthetic "案例还原示意图" images for medical violation cases.

Each image:
- Has a clear "案例还原示意图" watermark
- Visually represents the type of violation (store sign, billboard, poster, screenshot, etc.)
- Shows the specific违规用语 matching the real case description
- Is clearly NOT a real photo (so the 来源 field can be marked honestly)

Generated as part of Task 3 (medical bucket) of plan 2026-08-24-doubletap-fix-and-violation-cases.md.
"""

from PIL import Image, ImageDraw, ImageFont
import os
import sys

# Paths
OUT_DIR = r"d:\GitHub\IceSpiritAI_Vision\违规案例"
FONT_HEI = r"C:\Windows\Fonts\simhei.ttf"
FONT_YAHEI = r"C:\Windows\Fonts\msyh.ttc"
FONT_KAI = r"C:\Windows\Fonts\simkai.ttf"


def load_font(path, size):
    """Load a TrueType font; fallback to default if not found."""
    if os.path.exists(path):
        return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def add_watermark(img, text="案例还原示意图  根据公开通报绘制  非原始现场照"):
    """Add diagonal watermark indicating this is an illustration."""
    overlay = Image.new("RGBA", img.size, (255, 255, 255, 0))
    draw = ImageDraw.Draw(overlay)
    w, h = img.size
    font = load_font(FONT_HEI, 16)
    # Repeat watermark across the image
    step_x, step_y = 280, 140
    for y in range(-h, h * 2, step_y):
        for x in range(-w, w * 2, step_x):
            draw.text((x, y), text, fill=(255, 100, 100, 60), font=font)
    return Image.alpha_composite(img.convert("RGBA"), overlay)


def draw_signboard(filename, store_name, violation_text, bg_color, text_color):
    """Generate a pharmacy / clinic storefront signboard style image."""
    W, H = 800, 500
    img = Image.new("RGB", (W, H), (245, 240, 230))
    draw = ImageDraw.Draw(img)

    # Top banner: pharmacy cross + store name
    draw.rectangle([(0, 0), (W, 110)], fill=(180, 30, 30))
    font_name = load_font(FONT_YAHEI, 56)
    draw.text((30, 25), store_name, fill=(255, 255, 255), font=font_name)

    # Pharmacy cross (medical symbol) on right
    cx, cy = W - 70, 55
    draw.rectangle([(cx - 12, cy - 38), (cx + 12, cy + 38)], fill=(255, 255, 255))
    draw.rectangle([(cx - 38, cy - 12), (cx + 38, cy + 12)], fill=(255, 255, 255))

    # Door (right side)
    draw.rectangle([(W - 220, 130), (W - 30, H - 30)], fill=(90, 130, 170))
    draw.rectangle([(W - 210, 150), (W - 40, 380)], fill=(140, 180, 220))
    font_glass = load_font(FONT_HEI, 26)
    draw.text((W - 200, 200), violation_text[:8], fill=(220, 30, 30), font=font_glass)

    # Yellow/red promotional poster on the wall (left)
    poster_w, poster_h = 360, 240
    px, py = 30, 140
    draw.rectangle([(px, py), (px + poster_w, py + poster_h)], fill=bg_color,
                    outline=(120, 0, 0), width=4)

    # Poster title
    font_title = load_font(FONT_YAHEI, 32)
    draw.text((px + 18, py + 14), "【专治】", fill=text_color, font=font_title)

    # Violation phrases on poster
    font_v = load_font(FONT_HEI, 24)
    lines = violation_text.split("|")
    for i, line in enumerate(lines):
        y_off = py + 60 + i * 32
        draw.text((px + 24, y_off), "• " + line, fill=text_color, font=font_v)

    # Bottom banner with regulatory seal
    draw.rectangle([(0, H - 50), (W, H)], fill=(50, 50, 50))
    font_seal = load_font(FONT_HEI, 18)
    draw.text((20, H - 42), "（市场监管部门现场查处）", fill=(255, 220, 100), font=font_seal)

    img = add_watermark(img)
    img.convert("RGB").save(os.path.join(OUT_DIR, filename), "JPEG", quality=88)


def draw_outdoor_billboard(filename, hospital_name, violation_text):
    """Generate an outdoor billboard for民营医院 advertising."""
    W, H = 900, 600
    img = Image.new("RGB", (W, H), (135, 180, 220))  # sky blue
    draw = ImageDraw.Draw(img)

    # Sky gradient
    for y in range(H):
        c = int(135 + (220 - 135) * (y / H))
        draw.line([(0, y), (W, y)], fill=(c, c + 30, 220))

    # Pole
    draw.rectangle([(W // 2 - 15, 100), (W // 2 + 15, H)], fill=(120, 100, 80))

    # Billboard frame
    bx, by, bw, bh = 50, 80, W - 100, 380
    draw.rectangle([(bx, by), (bx + bw, by + bh)], fill=(255, 240, 240),
                    outline=(80, 0, 0), width=6)

    # Hospital name
    font_h = load_font(FONT_YAHEI, 50)
    draw.text((bx + 30, by + 24), hospital_name, fill=(180, 30, 30), font=font_h)

    # Big violation text
    font_v = load_font(FONT_HEI, 36)
    lines = violation_text.split("|")
    for i, line in enumerate(lines):
        y_off = by + 110 + i * 56
        # Red highlight bar
        draw.rectangle([(bx + 20, y_off - 8), (bx + bw - 20, y_off + 44)],
                        fill=(255, 220, 80))
        draw.text((bx + 40, y_off), "★ " + line, fill=(180, 0, 0), font=font_v)

    # Bottom phone number
    font_phone = load_font(FONT_YAHEI, 36)
    draw.text((bx + 30, by + bh - 70), "健康热线:  400-888-6666",
              fill=(0, 80, 160), font=font_phone)

    # Ground line
    draw.rectangle([(0, H - 40), (W, H)], fill=(110, 90, 70))

    img = add_watermark(img)
    img.convert("RGB").save(os.path.join(OUT_DIR, filename), "JPEG", quality=88)


def draw_poster(filename, title, body_lines, accent_color=(200, 30, 30)):
    """Generate a print advertisement poster (印刷品)."""
    W, H = 700, 950
    img = Image.new("RGB", (W, H), (255, 252, 240))
    draw = ImageDraw.Draw(img)

    # Top banner
    draw.rectangle([(0, 0), (W, 130)], fill=accent_color)
    font_title = load_font(FONT_YAHEI, 60)
    # Measure title for centering
    bbox = draw.textbbox((0, 0), title, font=font_title)
    tw = bbox[2] - bbox[0]
    draw.text(((W - tw) // 2, 35), title, fill=(255, 255, 255), font=font_title)

    # Subtitle
    font_sub = load_font(FONT_HEI, 28)
    draw.text((40, 155), "【专家推荐】  不打针不吃药 一周期见效",
              fill=(120, 0, 0), font=font_sub)

    # Image placeholder (capsule shape)
    draw.ellipse([(W // 2 - 100, 200), (W // 2 + 100, 400)], fill=(220, 200, 160),
                 outline=(180, 100, 50), width=4)
    draw.text((W // 2 - 70, 280), "祖传", fill=(120, 50, 0),
              font=load_font(FONT_HEI, 40))
    draw.text((W // 2 - 50, 330), "秘方", fill=(120, 50, 0),
              font=load_font(FONT_HEI, 40))

    # Body lines (the violation text)
    font_body = load_font(FONT_HEI, 26)
    for i, line in enumerate(body_lines):
        y_off = 430 + i * 48
        # Star bullets
        draw.text((40, y_off), "★", fill=accent_color,
                  font=load_font(FONT_HEI, 32))
        draw.text((80, y_off), line, fill=(40, 40, 40), font=font_body)

    # Bottom red banner
    draw.rectangle([(0, H - 110), (W, H)], fill=(255, 220, 80))
    font_bot = load_font(FONT_YAHEI, 36)
    draw.text((40, H - 90), "无效退款！签订治疗保证书！",
              fill=(180, 0, 0), font=font_bot)

    # QR code placeholder (right)
    qx, qy = W - 130, H - 230
    draw.rectangle([(qx, qy), (qx + 110, qy + 110)], fill=(255, 255, 255),
                    outline=(0, 0, 0), width=3)
    # Pseudo QR pattern
    for r in range(11):
        for c in range(11):
            if (r * 7 + c * 11) % 3 == 0:
                draw.rectangle([(qx + 8 + c * 9, qy + 8 + r * 9),
                                 (qx + 15 + c * 9, qy + 15 + r * 9)], fill=(0, 0, 0))

    img = add_watermark(img)
    img.convert("RGB").save(os.path.join(OUT_DIR, filename), "JPEG", quality=88)


def draw_phone_screenshot(filename, store_name, post_text, contact_line):
    """Generate a 手机微信 / 朋友圈 screenshot style."""
    W, H = 540, 1080
    img = Image.new("RGB", (W, H), (240, 240, 240))
    draw = ImageDraw.Draw(img)

    # Phone frame
    draw.rectangle([(0, 0), (W, H)], fill=(20, 20, 20))
    draw.rectangle([(8, 8), (W - 8, H - 8)], fill=(255, 255, 255))

    # Top status bar
    draw.rectangle([(8, 8), (W - 8, 50)], fill=(230, 230, 230))
    font_status = load_font(FONT_HEI, 20)
    draw.text((30, 18), "14:08  4G  微信", fill=(50, 50, 50), font=font_status)

    # 微信 header
    draw.rectangle([(8, 50), (W - 8, 110)], fill=(245, 245, 245))
    font_h = load_font(FONT_HEI, 24)
    draw.text((30, 70), "朋友圈", fill=(40, 40, 40), font=font_h)

    # Avatar + name
    draw.ellipse([(30, 130), (90, 190)], fill=(180, 180, 180))
    font_name = load_font(FONT_HEI, 22)
    draw.text((110, 140), store_name, fill=(50, 50, 50), font=font_name)
    font_time = load_font(FONT_HEI, 18)
    draw.text((110, 175), "2小时前", fill=(150, 150, 150), font=font_time)

    # Post content
    font_post = load_font(FONT_HEI, 22)
    lines = post_text.split("|")
    for i, line in enumerate(lines):
        draw.text((30, 220 + i * 36), line, fill=(40, 40, 40), font=font_post)

    # Post image (red banner style)
    banner_y = 220 + len(lines) * 36 + 30
    draw.rectangle([(30, banner_y), (W - 30, banner_y + 240)],
                    fill=(220, 30, 30))
    font_big = load_font(FONT_YAHEI, 38)
    draw.text((50, banner_y + 20), "【特大喜讯】", fill=(255, 255, 255), font=font_big)
    draw.text((50, banner_y + 80), "根治糖尿病",
              fill=(255, 220, 80), font=load_font(FONT_YAHEI, 44))
    draw.text((50, banner_y + 140), "当晚见效！签约治疗！",
              fill=(255, 240, 200), font=load_font(FONT_HEI, 28))

    # Like bar
    like_y = banner_y + 270
    draw.rectangle([(8, like_y), (W - 8, like_y + 30)], fill=(245, 245, 245))
    draw.text((30, like_y + 5), "❤ 32  💬 18",
              fill=(100, 100, 100), font=font_time)

    # Bottom contact
    font_contact = load_font(FONT_HEI, 20)
    draw.text((30, like_y + 50), contact_line, fill=(60, 100, 180), font=font_contact)

    # Bottom nav bar
    draw.rectangle([(8, H - 60), (W - 8, H - 8)], fill=(250, 250, 250))
    draw.text((30, H - 45), "🏠 微信 通讯录 发现 我",
              fill=(100, 100, 100), font=load_font(FONT_HEI, 18))

    img = add_watermark(img)
    img.convert("RGB").save(os.path.join(OUT_DIR, filename), "JPEG", quality=88)


def draw_led_screen(filename, brand, slogan_lines, contact):
    """Generate an LED outdoor screen style image."""
    W, H = 1000, 500
    img = Image.new("RGB", (W, H), (15, 15, 25))
    draw = ImageDraw.Draw(img)

    # LED matrix effect (subtle horizontal banding)
    for y in range(0, H, 6):
        draw.rectangle([(0, y), (W, y + 3)], fill=(25, 25, 35))

    # Frame
    draw.rectangle([(15, 15), (W - 15, H - 15)],
                    outline=(120, 80, 30), width=8)

    # Brand name
    font_brand = load_font(FONT_YAHEI, 56)
    draw.text((40, 40), brand, fill=(255, 220, 80), font=font_brand)

    # Underline
    draw.rectangle([(40, 110), (W - 40, 120)], fill=(255, 220, 80))

    # Slogan (violation text)
    font_sl = load_font(FONT_HEI, 32)
    colors = [(255, 80, 80), (255, 255, 100), (100, 255, 200), (255, 180, 100)]
    for i, line in enumerate(slogan_lines):
        c = colors[i % len(colors)]
        y_off = 150 + i * 60
        draw.text((60, y_off), "★ " + line, fill=c, font=font_sl)

    # Contact at bottom
    font_c = load_font(FONT_YAHEI, 30)
    draw.text((40, H - 90), contact, fill=(255, 255, 255), font=font_c)

    # Timestamp / disclaimer
    font_ts = load_font(FONT_HEI, 16)
    draw.text((40, H - 45), "（现场查处拍摄·LED 显示屏）",
              fill=(180, 180, 180), font=font_ts)

    img = add_watermark(img)
    img.convert("RGB").save(os.path.join(OUT_DIR, filename), "JPEG", quality=88)


def draw_promo_paper(filename, title, claim_lines):
    """Generate a 传单 / 宣传单页 style image."""
    W, H = 720, 1000
    img = Image.new("RGB", (W, H), (255, 250, 235))
    draw = ImageDraw.Draw(img)

    # Top red banner
    draw.rectangle([(0, 0), (W, 100)], fill=(190, 0, 0))
    font_t = load_font(FONT_YAHEI, 42)
    draw.text((30, 28), title, fill=(255, 255, 255), font=font_t)

    # Body
    font_b = load_font(FONT_HEI, 26)
    for i, line in enumerate(claim_lines):
        y = 140 + i * 44
        # alternating highlight bars
        if i % 2 == 0:
            draw.rectangle([(20, y - 8), (W - 20, y + 30)],
                            fill=(255, 245, 200))
        draw.text((40, y), "✦ " + line, fill=(160, 0, 0), font=font_b)

    # Decorative box at bottom (军方徽章 or 营养师证)
    box_y = H - 280
    draw.rectangle([(40, box_y), (W - 40, H - 40)],
                    outline=(160, 100, 50), width=4)
    font_dec = load_font(FONT_HEI, 22)
    draw.text((60, box_y + 20), "★ 中国营养保健协会推荐产品",
              fill=(80, 80, 80), font=font_dec)
    draw.text((60, box_y + 60), "★ 解放军总医院联合研制",
              fill=(80, 80, 80), font=font_dec)
    draw.text((60, box_y + 100), "★ 通过国家药监局严格审批",
              fill=(80, 80, 80), font=font_dec)
    draw.text((60, box_y + 140), "★ 适合三高、糖尿病、心血管疾病人群",
              fill=(180, 0, 0), font=font_b)

    # Star seal
    draw.ellipse([(W - 160, box_y + 20), (W - 50, box_y + 130)],
                  fill=(220, 30, 30), outline=(180, 0, 0), width=3)
    font_seal = load_font(FONT_HEI, 16)
    draw.text((W - 145, box_y + 40), "权威认证", fill=(255, 255, 255), font=font_seal)
    draw.text((W - 145, box_y + 70), "国食健字", fill=(255, 255, 255), font=font_seal)
    draw.text((W - 145, box_y + 95), "G2016xxxx", fill=(255, 255, 255), font=font_seal)

    img = add_watermark(img)
    img.convert("RGB").save(os.path.join(OUT_DIR, filename), "JPEG", quality=88)


def draw_news_broadcast(filename, headline, subhead, body_lines):
    """Generate a news-form 报道式广告 style."""
    W, H = 800, 1100
    img = Image.new("RGB", (W, H), (245, 245, 250))
    draw = ImageDraw.Draw(img)

    # Newspaper-like header
    draw.rectangle([(0, 0), (W, 80)], fill=(255, 255, 255))
    font_paper = load_font(FONT_YAHEI, 44)
    draw.text((40, 18), "健康时报", fill=(180, 0, 0), font=font_paper)
    draw.text((W - 240, 30), "2024-09-15  第37版",
              fill=(80, 80, 80), font=load_font(FONT_HEI, 18))

    # Header line
    draw.rectangle([(0, 82), (W, 86)], fill=(180, 0, 0))

    # Headline (news-form phrasing)
    font_head = load_font(FONT_YAHEI, 36)
    # Simulate bold by drawing twice with slight offset
    draw.text((42, 122), headline, fill=(0, 0, 0), font=font_head)
    draw.text((40, 120), headline, fill=(0, 0, 0), font=font_head)

    font_sub = load_font(FONT_HEI, 22)
    draw.text((40, 180), subhead, fill=(120, 0, 0), font=font_sub)

    # Divider
    draw.line([(40, 220), (W - 40, 220)], fill=(180, 180, 180), width=2)

    # Body
    font_body = load_font(FONT_YAHEI, 22)
    y = 240
    for line in body_lines:
        draw.text((40, y), "▌" + line, fill=(40, 40, 40), font=font_body)
        y += 70

    # Bottom: doctor / expert image placeholder
    box_y = H - 220
    draw.rectangle([(40, box_y), (W - 40, H - 30)],
                    outline=(200, 200, 200), width=2)
    draw.ellipse([(60, box_y + 20), (160, box_y + 120)], fill=(180, 200, 220))
    font_dr = load_font(FONT_HEI, 22)
    draw.text((180, box_y + 30), "专家简介:", fill=(60, 60, 60), font=font_dr)
    draw.text((180, box_y + 60), "某主任医师  ××中医院院长",
              fill=(60, 60, 60), font=font_dr)
    draw.text((180, box_y + 90), "享受国务院特殊津贴",
              fill=(60, 60, 60), font=font_dr)
    draw.text((180, box_y + 120), "咨询热线: 400-666-8888",
              fill=(180, 0, 0), font=font_dr)

    img = add_watermark(img)
    img.convert("RGB").save(os.path.join(OUT_DIR, filename), "JPEG", quality=88)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    # Case 2: 民营医院户外广告 - 治愈率 (Tengchong 2017)
    draw_outdoor_billboard(
        "medical_hospital_02.jpg",
        hospital_name="腾冲东方男科医院",
        violation_text="治愈率高达 99.8%|永不复发|100% 全面修复|安全有保障",
    )

    # Case 3: 中医诊所门头 - 祖传秘方 (玉溪骨伤医院 2016 case style)
    draw_signboard(
        "medical_clinic_03.jpg",
        store_name="济世堂中医门诊",
        violation_text="祖传秘方|彻底治愈|一贴见效|签约治疗|无效退款",
        bg_color=(255, 230, 100),
        text_color=(180, 0, 0),
    )

    # Case 4: 互联网医疗广告 (Shanghai Changning 2022 / 30 stores case)
    draw_phone_screenshot(
        "medical_internet_04.jpg",
        store_name="健康养生堂",
        post_text="【重大突破】|逆转糖尿病新技术|不吃药不打针|60天断根",
        contact_line="📞 扫码咨询: 微信 138-0011-2233",
    )

    # Case 5: 医疗美容 - 100%有效 (Hangzhou Yumeiren 2023 case style)
    draw_led_screen(
        "medical_beauty_05.jpg",
        brand="虞美人医疗美容医院",
        slogan_lines=[
            "瓷娃娃打针  刺激细胞皮肤变年轻",
            "额头坑坑洼洼  一次打平",
            "签约治疗  100% 有效",
        ],
        contact="📞 VIP专线 400-888-1234",
    )

    # Case 6: 药品广告 - 无效退款承诺 (Shaoyang Boren 2020 case style)
    draw_poster(
        "medical_drug_06.jpg",
        title="独家专利 鼻炎一喷灵",
        body_lines=[
            "彻底告别鼻塞流涕",
            "一喷 5 分钟 通气",
            "签约治疗 无效全额退款",
            "不复发 不反复",
            "国家专利 ZL2018xxxxxxx",
        ],
        accent_color=(190, 30, 30),
    )

    # Case 7: 保健品 - 替代药物 (Hunan Liling 2024 "调理糖尿病" case style)
    draw_promo_paper(
        "medical_health_07.jpg",
        title="无糖粉丝  调理糖尿病",
        claim_lines=[
            "不用吃药  不打胰岛素",
            "可停用一切西药",
            "3个月指标恢复正常",
            "签约治疗 无效退款",
            "100% 纯天然食品级",
        ],
    )

    # Case 8: 冒用军队医院名义 (PLA-misuse cases)
    draw_signboard(
        "medical_army_08.jpg",
        store_name="解放军第×中心医院 风湿骨病专科",
        violation_text="军医专家|治愈率98%|部队首长|签约治疗",
        bg_color=(0, 100, 0),
        text_color=(255, 255, 255),
    )

    # Case 9: 药品对比断言 (Foshan / various cure rate case style)
    draw_poster(
        "medical_compare_09.jpg",
        title="××胶囊 同类药中疗效最好",
        body_lines=[
            "经三甲医院临床验证",
            "治愈率比同类药品高 40%",
            "全国销量领先",
            "安全性最高  无副作用",
            "权威专家一致推荐",
        ],
        accent_color=(50, 80, 180),
    )

    # Case 10: 新闻报道形式医疗广告 (TCM clinic 2024 case style)
    draw_news_broadcast(
        "medical_newsform_10.jpg",
        headline="百年传承  中医攻克糖尿病  治愈率突破 95%",
        subhead="—— 记××堂糖尿病专科 ××主任医师的传奇医术",
        body_lines=[
            "本报记者  ×××  报道: 近日,记者在××堂糖尿病专科采访发现,经××主任",
            "医师采用祖传秘方治疗的糖尿病患者 治愈率已突破 95%, 不少多年服药患者",
            "已完全停药,指标恢复正常。这项突破为我国糖尿病治疗开辟了新路径。",
            "据悉,该诊所采用纯中药配方,签约治疗,无效退款,已为数数万名患者带来福音。",
        ],
    )

    print("Generated 9 synthetic illustrations in", OUT_DIR)


if __name__ == "__main__":
    main()