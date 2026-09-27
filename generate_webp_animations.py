import os
import math
from PIL import Image, ImageEnhance, ImageOps

SRC_DIR = "app/src/main/res/drawable"
OUT_DIR = "app/src/main/res/raw"
CANVAS_SIZE = (384, 384)

os.makedirs(OUT_DIR, exist_ok=True)

def load_sprite(char_name, emotion, fallback="idle"):
    path = f"{SRC_DIR}/{char_name}_{emotion}.png"
    if not os.path.exists(path):
        path = f"{SRC_DIR}/{char_name}_{fallback}.png"
    im = Image.open(path).convert("RGBA")
    return im.resize(CANVAS_SIZE, Image.Resampling.LANCZOS)

def create_idle_animation(char_name):
    base = load_sprite(char_name, "idle")
    hover = load_sprite(char_name, "hover", "idle")
    
    frames = []
    fps = 15
    total_frames = 36 # 2.4 seconds
    
    for i in range(total_frames):
        t = i / float(total_frames)
        # Gentle floating bob
        dy = int(math.sin(t * 2 * math.pi) * 10)
        # Breathing scale
        scale_x = 1.0 + 0.025 * math.cos(t * 2 * math.pi)
        scale_y = 1.0 - 0.015 * math.cos(t * 2 * math.pi)
        
        # Blink around frame 18-20
        is_blink = 18 <= i <= 20
        curr_img = hover if is_blink else base
        
        # Apply transformation
        w, h = CANVAS_SIZE
        tw = int(w * scale_x)
        th = int(h * scale_y)
        scaled = curr_img.resize((tw, th), Image.Resampling.LANCZOS)
        
        frame = Image.new("RGBA", CANVAS_SIZE, (0, 0, 0, 0))
        ox = (w - tw) // 2
        oy = (h - th) // 2 + dy
        frame.paste(scaled, (ox, oy), scaled)
        frames.append(frame)
        
    save_anim(frames, f"{OUT_DIR}/{char_name}_idle_anim.webp", duration=66)

def create_thinking_animation(char_name):
    think = load_sprite(char_name, "thinking")
    curious = load_sprite(char_name, "curious", "thinking")
    
    frames = []
    total_frames = 36 # 2.4s
    
    for i in range(total_frames):
        t = i / float(total_frames)
        # Thoughtful head tilt / wobble (-4 to +4 degrees)
        angle = math.sin(t * 2 * math.pi) * 4.5
        dy = int(math.sin(t * 4 * math.pi) * 4)
        
        # Switch between thinking and curious
        curr_img = curious if (10 <= i <= 24) else think
        
        # Periodic blink
        if i in (14, 15, 30, 31):
            scale_y = 0.88
        else:
            scale_y = 1.0
            
        w, h = CANVAS_SIZE
        rotated = curr_img.rotate(angle, resample=Image.Resampling.BICUBIC, expand=False)
        tw = w
        th = int(h * scale_y)
        scaled = rotated.resize((tw, th), Image.Resampling.LANCZOS)
        
        frame = Image.new("RGBA", CANVAS_SIZE, (0, 0, 0, 0))
        ox = (w - tw) // 2
        oy = (h - th) // 2 + dy
        frame.paste(scaled, (ox, oy), scaled)
        frames.append(frame)
        
    save_anim(frames, f"{OUT_DIR}/{char_name}_thinking_anim.webp", duration=66)

def create_walking_animation(char_name):
    walk1 = load_sprite(char_name, "walk", "idle")
    walk2 = load_sprite(char_name, "walking_you", "idle")
    idle = load_sprite(char_name, "idle")
    
    frames = []
    steps = [walk1, idle, walk2, idle]
    total_frames = 24
    
    for i in range(total_frames):
        t = i / float(total_frames)
        step_idx = int((t * len(steps) * 2) % len(steps))
        curr_img = steps[step_idx]
        
        # Step bounce and wobble
        dy = -int(abs(math.sin(t * 4 * math.pi)) * 12)
        tilt = math.sin(t * 4 * math.pi) * 3.0
        
        w, h = CANVAS_SIZE
        rotated = curr_img.rotate(tilt, resample=Image.Resampling.BICUBIC, expand=False)
        frame = Image.new("RGBA", CANVAS_SIZE, (0, 0, 0, 0))
        frame.paste(rotated, (0, dy), rotated)
        frames.append(frame)
        
    save_anim(frames, f"{OUT_DIR}/{char_name}_walking_anim.webp", duration=80)

def create_jump_animation(char_name):
    jump = load_sprite(char_name, "jump")
    excited = load_sprite(char_name, "excited", "jump")
    idle = load_sprite(char_name, "idle")
    
    frames = []
    total_frames = 24 # ~1.6s
    
    for i in range(total_frames):
        t = i / float(total_frames)
        w, h = CANVAS_SIZE
        
        if t < 0.15: # Anticipation squash
            curr_img = idle
            dy = 10
            scale_x, scale_y = 1.15, 0.85
        elif t < 0.60: # In air jump
            curr_img = jump
            progress = (t - 0.15) / 0.45
            dy = -int(math.sin(progress * math.pi) * 35)
            scale_x, scale_y = 0.92, 1.12
        elif t < 0.80: # Landing squash
            curr_img = excited
            dy = 12
            scale_x, scale_y = 1.18, 0.82
        else: # Recover to idle
            curr_img = idle
            dy = 0
            scale_x, scale_y = 1.0, 1.0
            
        tw, th = int(w * scale_x), int(h * scale_y)
        scaled = curr_img.resize((tw, th), Image.Resampling.LANCZOS)
        frame = Image.new("RGBA", CANVAS_SIZE, (0, 0, 0, 0))
        ox = (w - tw) // 2
        oy = (h - th) // 2 + dy
        frame.paste(scaled, (ox, oy), scaled)
        frames.append(frame)
        
    save_anim(frames, f"{OUT_DIR}/{char_name}_jump_anim.webp", duration=70)

def create_celebrating_animation(char_name):
    celeb = load_sprite(char_name, "celebrating")
    happy = load_sprite(char_name, "happy", "celebrating")
    jump = load_sprite(char_name, "jump", "celebrating")
    
    frames = []
    total_frames = 28 # ~2.0s
    sprites = [celeb, jump, happy, celeb]
    
    for i in range(total_frames):
        t = i / float(total_frames)
        idx = int((t * len(sprites)) % len(sprites))
        curr_img = sprites[idx]
        
        # Happy victory hop
        dy = -int(abs(math.sin(t * 3 * math.pi)) * 18)
        tilt = math.sin(t * 3 * math.pi) * 5.0
        
        w, h = CANVAS_SIZE
        rotated = curr_img.rotate(tilt, resample=Image.Resampling.BICUBIC, expand=False)
        frame = Image.new("RGBA", CANVAS_SIZE, (0, 0, 0, 0))
        frame.paste(rotated, (0, dy), rotated)
        frames.append(frame)
        
    save_anim(frames, f"{OUT_DIR}/{char_name}_celebrating_anim.webp", duration=75)

def create_working_animation(char_name):
    work = load_sprite(char_name, "working")
    tools = load_sprite(char_name, "with_tools", "working")
    power = load_sprite(char_name, "power", "working")
    
    frames = []
    total_frames = 24
    sprites = [work, tools, power, tools]
    
    for i in range(total_frames):
        t = i / float(total_frames)
        idx = int((t * len(sprites)) % len(sprites))
        curr_img = sprites[idx]
        
        # Energetic rapid vibration / rhythm
        dy = int(math.sin(t * 6 * math.pi) * 5)
        scale = 1.0 + 0.03 * math.cos(t * 6 * math.pi)
        
        w, h = CANVAS_SIZE
        tw = int(w * scale)
        th = int(h * scale)
        scaled = curr_img.resize((tw, th), Image.Resampling.LANCZOS)
        
        frame = Image.new("RGBA", CANVAS_SIZE, (0, 0, 0, 0))
        ox = (w - tw) // 2
        oy = (h - th) // 2 + dy
        frame.paste(scaled, (ox, oy), scaled)
        frames.append(frame)
        
    save_anim(frames, f"{OUT_DIR}/{char_name}_working_anim.webp", duration=75)

def create_sleeping_animation(char_name):
    sleep = load_sprite(char_name, "sleep")
    tired = load_sprite(char_name, "tired", "sleep")
    
    frames = []
    total_frames = 36 # ~3s slow sleep cycle
    
    for i in range(total_frames):
        t = i / float(total_frames)
        # Deep slow breathing
        scale_y = 1.0 + 0.05 * math.sin(t * 2 * math.pi)
        scale_x = 1.0 - 0.02 * math.sin(t * 2 * math.pi)
        dy = int(math.sin(t * 2 * math.pi) * 6)
        
        curr_img = sleep if t < 0.75 else tired
        
        w, h = CANVAS_SIZE
        tw, th = int(w * scale_x), int(h * scale_y)
        scaled = curr_img.resize((tw, th), Image.Resampling.LANCZOS)
        
        frame = Image.new("RGBA", CANVAS_SIZE, (0, 0, 0, 0))
        ox = (w - tw) // 2
        oy = (h - th) // 2 + dy
        frame.paste(scaled, (ox, oy), scaled)
        frames.append(frame)
        
    save_anim(frames, f"{OUT_DIR}/{char_name}_sleeping_anim.webp", duration=85)

def create_sad_animation(char_name):
    sad = load_sprite(char_name, "sad")
    
    frames = []
    total_frames = 30
    
    for i in range(total_frames):
        t = i / float(total_frames)
        # Slow droop
        dy = int(math.sin(t * 2 * math.pi) * 8) + 8
        scale_y = 0.95 - 0.03 * math.sin(t * 2 * math.pi)
        
        w, h = CANVAS_SIZE
        tw, th = w, int(h * scale_y)
        scaled = sad.resize((tw, th), Image.Resampling.LANCZOS)
        
        frame = Image.new("RGBA", CANVAS_SIZE, (0, 0, 0, 0))
        ox = (w - tw) // 2
        oy = (h - th) // 2 + dy
        frame.paste(scaled, (ox, oy), scaled)
        frames.append(frame)
        
    save_anim(frames, f"{OUT_DIR}/{char_name}_sad_anim.webp", duration=80)

def save_anim(frames, out_path, duration=70):
    frames[0].save(
        out_path,
        format="WEBP",
        save_all=True,
        append_images=frames[1:],
        duration=duration,
        loop=0,
        quality=85,
        method=4
    )
    size_kb = os.path.getsize(out_path) / 1024
    print(f"✨ Generated: {os.path.basename(out_path)} ({size_kb:.1f} KB)")

def main():
    characters = ["aether", "lumy"]
    for char in characters:
        print(f"\n🎨 Generating animated WebP loops for {char.upper()}...")
        create_idle_animation(char)
        create_thinking_animation(char)
        create_walking_animation(char)
        create_jump_animation(char)
        create_celebrating_animation(char)
        create_working_animation(char)
        create_sleeping_animation(char)
        create_sad_animation(char)

if __name__ == "__main__":
    main()
