# 🎨 Orbital Avatar Character Generation Prompts (Google Flow / Imagen / Midjourney)

Use these prompts with **Google Flow**, **ImageFX / Imagen 3**, **Midjourney v6**, or **DALL-E 3** to generate consistent, expressive companion mascot sprites for Orbital.

---

## 💎 Master Style Anchor
*Copy & append this style block to keep character proportions, lighting, and textures 100% consistent across all emotions:*

```text
Cute 3D stylized game mascot companion, high-end Pixar and Riot Games character render, chibi proportions, glossy smooth textures, expressive anime-styled luminous eyes, volumetric soft studio lighting, vibrant colors, isolated character on pure solid white background, full body centered, game sprite asset, no background, 8k render, Unreal Engine 5 style.
```

---

## 🌌 1. Aether (Cosmic AI Spirit Bot)
* **Identity**: Sleek cosmic cybernetic fairy bot, floating cyber-antennas, big glowing sapphire blue eyes, gentle violet/cyan aura.

### 1. `aether_idle.png` (Idle Floating)
```text
Cute 3D stylized game mascot companion Aether, sleek cosmic cybernetic fairy bot, floating gently in mid-air, relaxed friendly smile, big luminous sapphire blue eyes looking forward, gentle glowing purple-blue aura rings, clean solid white background, full body centered, Pixar 3D render, game asset.
```

### 2. `aether_thinking.png` (Deep Thought / Brainstorming)
```text
Cute 3D stylized game mascot companion Aether, celestial cybernetic fairy bot, thinking pose, cute tiny hand on chin, head tilted to the side in deep curiosity, blinking glowing eyes looking upwards, holographic glowing question mark and glowing particle dots floating above head, clean solid white background, full body centered, Pixar 3D render.
```

### 3. `aether_walk.png` (Walking / Roaming)
```text
Cute 3D stylized game mascot companion Aether, sleek cosmic bot, cheerful walking side-angle action pose, one foot forward, gentle hovering motion with arms swinging happily, energetic cheerful expression, clean solid white background, full body centered, Pixar 3D render.
```

### 4. `aether_jump.png` (Playful Jump / Greeting)
```text
Cute 3D stylized game mascot companion Aether, cosmic cyber bot, dynamic joyful jumping high in mid-air pose, arms stretched wide, ecstatic beaming face, sparkling energy trail beneath feet, dynamic squash and stretch 3D perspective, clean solid white background, full body centered, Pixar 3D render.
```

### 5. `aether_celebrating.png` (Victory & Success)
```text
Cute 3D stylized game mascot companion Aether, cosmic bot, celebrating victory pose, holding tiny golden star trophy, cheerful winking eye, colorful glowing confetti and sparks exploding around, joyful wide smile, clean solid white background, full body centered, Pixar 3D render.
```

### 6. `aether_working.png` (Tool Execution & Coding)
```text
Cute 3D stylized game mascot companion Aether, cosmic cyber bot, holding mini futuristic glowing hologram tablet and tiny glowing magic wrench, determined focused adorable face, neon cyber code symbols floating around, clean solid white background, full body centered, Pixar 3D render.
```

### 7. `aether_sleep.png` (Sleeping / Inactivity)
```text
Cute 3D stylized game mascot companion Aether, cosmic bot, curled up peacefully floating on a soft glowing cloud, eyes softly closed with a sweet sleepy smile, tiny glowing "Zzz" letters floating upwards, soft dim night glow, clean solid white background, full body centered, Pixar 3D render.
```

### 8. `aether_sad.png` (Error / Apology)
```text
Cute 3D stylized game mascot companion Aether, cosmic cyber bot, cute sad apologetic expression, head tilted downwards, droopy ears, big teary glossy eyes, holding a tiny broken red heart/gear, soft muted glow, clean solid white background, full body centered, Pixar 3D render.
```

---

## 🔥 2. Lumy (Warm Star / Ember Fox)
* **Identity**: Warm golden-orange star fox spirit, fluffy warm chest fur, glowing amber/crimson eyes, floating ember sparks.

### 1. `lumy_idle.png` (Idle Floating)
```text
Cute 3D stylized game mascot companion Lumy, golden ember star fox spirit, fluffy warm fur, floating gently, warm glowing amber eyes, sweet welcoming smile, tiny floating golden ember sparks, clean solid white background, full body centered, Pixar 3D render.
```

### 2. `lumy_thinking.png` (Curious / Thinking)
```text
Cute 3D stylized game mascot companion Lumy, golden star fox spirit, thinking pose with finger on cheek, head cocked sideways in wonder, sparkling curious amber eyes looking up, glowing golden thought bubble floating above, clean solid white background, full body centered, Pixar 3D render.
```

### 3. `lumy_walk.png` (Walking)
```text
Cute 3D stylized game mascot companion Lumy, golden star spirit, lively walking pose, bouncing lightly on tiptoes with fluffy tail swaying, happy enthusiastic expression, clean solid white background, full body centered, Pixar 3D render.
```

### 4. `lumy_jump.png` (Joyful Bounce)
```text
Cute 3D stylized game mascot companion Lumy, golden star fox, energetic jumping pose in mid-air, ears perked up, joyful beaming face, golden stardust trail below, clean solid white background, full body centered, Pixar 3D render.
```

### 5. `lumy_celebrating.png` (Victory & Joy)
```text
Cute 3D stylized game mascot companion Lumy, golden star fox, jumping high in joy with arms raised, big joyful open mouth smile, golden star sparkles and festive confetti bursts around, clean solid white background, full body centered, Pixar 3D render.
```

### 6. `lumy_working.png` (Power / Task)
```text
Cute 3D stylized game mascot companion Lumy, golden star spirit, focused energetic pose with glowing magical fireball/tools floating between paws, sparks flying, confident cute face, clean solid white background, full body centered, Pixar 3D render.
```

### 7. `lumy_sleep.png` (Sleeping)
```text
Cute 3D stylized game mascot companion Lumy, golden star spirit, curled up like a sleeping puppy in a golden glowing sphere, deep peaceful sleep, floating glowing "Zzz", cozy warm ambient light, clean solid white background, full body centered, Pixar 3D render.
```

### 8. `lumy_sad.png` (Sad / Error)
```text
Cute 3D stylized game mascot companion Lumy, golden star fox spirit, cute drooping ears, sad puppy eyes, sitting down with head lowered, soft warm tear drop, clean solid white background, full body centered, Pixar 3D render.
```

---

## ⚡ Next Steps Workflow

1. **Generate**: Paste the prompts into Google Flow / ImageFX / Midjourney (upload existing `idle.png` as image reference for exact face & color matching).
2. **Remove Background**: Ensure character has a clean transparent background (using `rembg` or automatic background remover).
3. **Save**: Save transparent PNG files into `app/src/main/res/drawable/` matching the filenames above.
4. **Compile to Looping WebP**:
   ```bash
   python generate_webp_animations.py
   ```
   *This automatically generates 3–5s looping transparent Animated WebPs inside `app/src/main/res/raw/` which the overlay loads automatically!*
