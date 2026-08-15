#!/usr/bin/env python3
"""Generate Android launcher icon PNGs for the AI Agent app.

Creates raster PNG fallbacks for all density buckets.
Design: dark navy background with a stylized AI robot face in blue-to-purple gradient.
"""

import os
import math
from PIL import Image, ImageDraw, ImageFont

# --- Configuration ---
RES_BASE = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "android-agent", "app", "src", "main", "res"
)

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

BG_COLOR = (13, 27, 42)       # #0D1B2A - dark navy
GRAD_TOP = (74, 158, 255)     # #4A9EFF - bright blue
GRAD_BOT = (124, 131, 255)    # #7C83FF - purple
EYE_COLOR = (255, 255, 255)   # white eyes for contrast
ANTENNA_GLOW = (130, 180, 255)  # soft blue glow on antenna tip


def lerp_color(c1, c2, t):
    """Linearly interpolate between two RGB colors."""
    return tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))


def create_gradient(size, top_color, bottom_color, horizontal=False):
    """Create a vertical (or horizontal) gradient image."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for i in range(size):
        t = i / max(size - 1, 1)
        color = lerp_color(top_color, bottom_color, t)
        if horizontal:
            for x in range(size):
                t2 = x / max(size - 1, 1)
                c = lerp_color(top_color, bottom_color, t2)
                img.putpixel((x, i), (*c, 255))
        else:
            for x in range(size):
                img.putpixel((x, i), (*color, 255))
    return img


def apply_gradient_mask(shape_img, grad_img):
    """Apply a gradient to a shape using the shape as an alpha mask."""
    # shape_img: RGBA with shape filled white, transparent elsewhere
    # grad_img: RGBA gradient
    result = grad_img.copy()
    # Use the alpha channel of shape_img as the mask for result
    shape_alpha = shape_img.split()[3]
    result.putalpha(shape_alpha)
    return result


def draw_rounded_rect(draw, bbox, radius, fill=None, outline=None, width=1):
    """Draw a rounded rectangle. bbox = (x1, y1, x2, y2)."""
    x1, y1, x2, y2 = bbox
    r = min(radius, (x2 - x1) // 2, (y2 - y1) // 2)
    if fill:
        draw.rectangle([x1 + r, y1, x2 - r, y2], fill=fill)
        draw.rectangle([x1, y1 + r, x2, y2 - r], fill=fill)
        draw.pieslice([x1, y1, x1 + 2 * r, y1 + 2 * r], 180, 270, fill=fill)
        draw.pieslice([x2 - 2 * r, y1, x2, y1 + 2 * r], 270, 360, fill=fill)
        draw.pieslice([x1, y2 - 2 * r, x1 + 2 * r, y2], 90, 180, fill=fill)
        draw.pieslice([x2 - 2 * r, y2 - 2 * r, x2, y2], 0, 90, fill=fill)
    if outline:
        draw.arc([x1, y1, x1 + 2 * r, y1 + 2 * r], 180, 270, fill=outline, width=width)
        draw.arc([x2 - 2 * r, y1, x2, y1 + 2 * r], 270, 360, fill=outline, width=width)
        draw.arc([x1, y2 - 2 * r, x1 + 2 * r, y2], 90, 180, fill=outline, width=width)
        draw.arc([x2 - 2 * r, y2 - 2 * r, x2, y2], 0, 90, fill=outline, width=width)
        draw.line([x1 + r, y1, x2 - r, y1], fill=outline, width=width)
        draw.line([x1 + r, y2, x2 - r, y2], fill=outline, width=width)
        draw.line([x1, y1 + r, x1, y2 - r], fill=outline, width=width)
        draw.line([x2, y1 + r, x2, y2 - r], fill=outline, width=width)


def generate_icon(size):
    """Generate an icon of the given size. Returns a PIL Image."""
    img = Image.new("RGBA", (size, size), (*BG_COLOR, 255))
    draw = ImageDraw.Draw(img)

    s = size  # shorthand
    # Padding / margin ratios
    margin = s * 0.15  # 15% margin
    inner = s - 2 * margin

    # We'll build the robot icon on a separate layer for gradient effect
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer_draw = ImageDraw.Draw(layer)

    # ---- Antenna ----
    ant_width = max(int(s * 0.025), 1)
    ant_x = s // 2
    ant_top = int(margin + inner * 0.02)
    ant_bottom = int(margin + inner * 0.18)
    layer_draw.line([(ant_x, ant_top), (ant_x, ant_bottom)], fill="white", width=ant_width)

    # Antenna tip (small circle with glow)
    tip_r = max(int(s * 0.04), 2)
    for i in range(tip_r + 2, 0, -1):
        alpha = int(255 * (1 - i / (tip_r + 3)) * 0.5)
        layer_draw.ellipse(
            [ant_x - i, ant_top - i, ant_x + i, ant_top + i],
            fill=(*ANTENNA_GLOW, alpha)
        )
    layer_draw.ellipse(
        [ant_x - tip_r, ant_top - tip_r, ant_x + tip_r, ant_top + tip_r],
        fill="white"
    )

    # ---- Head (rounded rectangle) ----
    head_left = int(margin + inner * 0.1)
    head_right = int(s - margin - inner * 0.1)
    head_top = int(margin + inner * 0.18)
    head_bottom = int(margin + inner * 0.82)
    head_r = int(inner * 0.15)
    head_bbox = (head_left, head_top, head_right, head_bottom)

    draw_rounded_rect(layer_draw, head_bbox, head_r, fill="white")

    # ---- Eyes ----
    eye_r = max(int(inner * 0.09), 2)
    eye_y = int(head_top + inner * 0.35)
    left_eye_x = int(head_left + inner * 0.28)
    right_eye_x = int(head_right - inner * 0.28)

    # Eye background circles (cut out of head - use BG_COLOR to "punch" holes)
    # Actually, we want white eyes on the gradient head. Let's draw eyes separately.
    # First, punch eye holes in the head layer
    layer_draw.ellipse(
        [left_eye_x - eye_r - 2, eye_y - eye_r - 2,
         left_eye_x + eye_r + 2, eye_y + eye_r + 2],
        fill=(0, 0, 0, 0)
    )
    layer_draw.ellipse(
        [right_eye_x - eye_r - 2, eye_y - eye_r - 2,
         right_eye_x + eye_r + 2, eye_y + eye_r + 2],
        fill=(0, 0, 0, 0)
    )

    # ---- Mouth ----
    mouth_width = max(int(inner * 0.28), 3)
    mouth_height = max(int(inner * 0.04), 1)
    mouth_x = s // 2
    mouth_y = int(head_top + inner * 0.62)
    # Small rounded mouth bar - punch it out too
    layer_draw.rounded_rectangle(
        [mouth_x - mouth_width, mouth_y - mouth_height,
         mouth_x + mouth_width, mouth_y + mouth_height],
        radius=max(mouth_height, 1),
        fill=(0, 0, 0, 0)
    )

    # ---- Apply gradient to the robot layer ----
    grad = create_gradient(size, GRAD_TOP, GRAD_BOT)
    gradient_layer = apply_gradient_mask(layer, grad)

    # ---- Ears / side panels ----
    ear_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ear_draw = ImageDraw.Draw(ear_layer)
    ear_w = max(int(inner * 0.04), 1)
    ear_h = max(int(inner * 0.18), 2)
    ear_y = int(head_top + inner * 0.28)
    # Left ear
    ear_draw.rounded_rectangle(
        [head_left - ear_w - 1, ear_y,
         head_left - 1, ear_y + ear_h],
        radius=max(ear_w // 2, 1), fill="white"
    )
    # Right ear
    ear_draw.rounded_rectangle(
        [head_right + 1, ear_y,
         head_right + ear_w + 1, ear_y + ear_h],
        radius=max(ear_w // 2, 1), fill="white"
    )
    ear_grad = create_gradient(size, GRAD_TOP, GRAD_BOT)
    gradient_ears = apply_gradient_mask(ear_layer, ear_grad)

    # ---- Compose final image ----
    # Start with background
    final = Image.new("RGBA", (size, size), (*BG_COLOR, 255))
    final = Image.alpha_composite(final, gradient_layer)
    final = Image.alpha_composite(final, gradient_ears)

    # ---- Draw eyes on top (white circles with colored pupils) ----
    final_draw = ImageDraw.Draw(final)

    # Eye glow
    for eye_x in [left_eye_x, right_eye_x]:
        glow_r = eye_r + max(int(s * 0.03), 1)
        for i in range(glow_r, eye_r, -1):
            alpha = int(80 * (1 - (i - eye_r) / max(glow_r - eye_r, 1)))
            final_draw.ellipse(
                [eye_x - i, eye_y - i, eye_x + i, eye_y + i],
                fill=(*GRAD_TOP, alpha)
            )

    # Eye whites
    for eye_x in [left_eye_x, right_eye_x]:
        final_draw.ellipse(
            [eye_x - eye_r, eye_y - eye_r, eye_x + eye_r, eye_y + eye_r],
            fill=EYE_COLOR
        )
        # Pupil
        pupil_r = max(int(eye_r * 0.55), 1)
        pupil_offset_x = max(int(eye_r * 0.1), 0)
        pupil_offset_y = max(int(eye_r * 0.1), 0)
        # Gradient pupil color (mix of the two colors)
        pupil_color = lerp_color(GRAD_TOP, GRAD_BOT, 0.5)
        final_draw.ellipse(
            [eye_x + pupil_offset_x - pupil_r, eye_y + pupil_offset_y - pupil_r,
             eye_x + pupil_offset_x + pupil_r, eye_y + pupil_offset_y + pupil_r],
            fill=pupil_color
        )
        # Pupil highlight
        hl_r = max(int(pupil_r * 0.35), 1)
        hl_offset = max(int(pupil_r * 0.3), 1)
        final_draw.ellipse(
            [eye_x + pupil_offset_x - hl_offset - hl_r,
             eye_y + pupil_offset_y - hl_offset - hl_r,
             eye_x + pupil_offset_x - hl_offset + hl_r,
             eye_y + pupil_offset_y - hl_offset + hl_r],
            fill=(255, 255, 255, 220)
        )

    # Mouth - small glowing line/arc
    mouth_half = max(int(inner * 0.12), 2)
    mouth_y = int(head_top + inner * 0.62)
    mouth_thickness = max(int(s * 0.015), 1)
    mouth_color = lerp_color(GRAD_TOP, GRAD_BOT, 0.7)
    # Simple curved smile
    smile_points = []
    for angle_deg in range(200, 341, 5):
        angle = math.radians(angle_deg)
        px = s // 2 + int(mouth_half * math.cos(angle))
        py = mouth_y + int(mouth_half * 0.5 * math.sin(angle))
        smile_points.append((px, py))
    if len(smile_points) >= 2:
        final_draw.line(smile_points, fill=mouth_color, width=mouth_thickness + 1)

    # Convert to RGB (no transparency needed for launcher icons)
    return final.convert("RGB")


def main():
    os.makedirs(RES_BASE, exist_ok=True)

    for folder, size in DENSITIES.items():
        dir_path = os.path.join(RES_BASE, folder)
        os.makedirs(dir_path, exist_ok=True)

        icon = generate_icon(size)

        out_path = os.path.join(dir_path, "ic_launcher.png")
        icon.save(out_path, "PNG")
        print(f"  Generated: {out_path}  ({size}x{size})")

        # Also save round variant (same image, Android masks it)
        out_round = os.path.join(dir_path, "ic_launcher_round.png")
        icon.save(out_round, "PNG")
        print(f"  Generated: {out_round}  ({size}x{size})")

    print("\nDone! All icon PNGs generated.")


if __name__ == "__main__":
    main()
