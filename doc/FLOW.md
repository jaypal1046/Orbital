Bro, let's design this as a real Android Animated Companion app, not just a character moving randomly. 🔥

You will build the character inside your app first, then extend the same character engine to a floating companion that can appear over other apps.

The project will use Kotlin + Jetpack Compose + a reusable character engine + Android overlay functionality.

# 🐾 Project: Living Animated Companion

![AICU秘伝テクニック! CodexアプリとImageGenプラグインによるスプライト生成](https://images.openai.com/static-rsc-4/b5-z1qz3egzH_k4PjE7Wsy6RKiKRkmMvUpHTheOA4TMPJhQZp7wNS-SJma9ziTvpemUNqiyjJAibhV7whoIVFfaJzLlshXDpXQ1fLCSPiOkb-SnY_9w3rtEcobH7pBHv24ZgKBzTHtGEA2RfKsYZs5YKpMgKU8Y3yMvCJhc9SCU?purpose=inline)

![Side Quest - NFC Pets - CHIMPOFM - Dangerous Things Forum](https://images.openai.com/static-rsc-4/2AiPmdmoyqPXYLLTEJ2G7qNLNYo5tKUoXHK5SvEZsfq_kLnayJiEno2FH99xLjCWMYMNQ6uXvacWSId426lFZ7dAt022JTIC_3vEgzymbRVfnjKTcC0MRMvYXpCIOak4fzH43AoMdFFTjZ9Kh9jjpsKUkOLa4uywxXn6sa-7Xwg?purpose=inline)

![Bitzee Mascota Digital
– Toy Planet](https://images.openai.com/static-rsc-4/iai1PsvWQLNEmi7p09PL4Thnhm8waiX8AxX_2RjlFpFStbUzyBWgzWhe2-5zax9DWJgdp0mo5SLEVE0C47toeZvEsfRHk1cL6AI0jKkMxOWzCl7ekB3DQOD9zNMXN1H8MnOGVDkLw2sGROaR_wXT0Z4lbqBy_Zl6z4V2YMms7ug?purpose=inline)

9

## What we are building

## Two modes

Shared engine

Mode 1 — Inside the App

A full-screen animated world where the character walks, jumps, interacts, and reacts to the user.

Mode 2 — Floating Companion

An optional overlay that displays the character above other apps, subject to Android permission and lifecycle restrictions.

Important platform limitation: Android allows apps to create `TYPE_APPLICATION_OVERLAY` windows with the user's `SYSTEM_ALERT_WINDOW` authorization, but it does not guarantee unrestricted, always-running behavior. Background execution, foreground services, battery management, and Android version rules must be handled carefully.

![](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

developer.android.com

+2

# 1. Technology Architecture

We'll separate the project into reusable modules so the character engine can be shared between the in-app experience and the floating mode.

## System architecture

#### Android UI

Jetpack Compose / Overlay View

Character Renderer

Draws the character and animations

Character Engine

Movement, state machine, collisions, and interactions

Character Model & Behavior

Position, velocity, action, and behavior decisions

Character Assets

Rive, sprite sheets, or other animation assets

### Recommended technology stack

|
Component

|

Technology

|
| --- | --- |
|

Language

|

Kotlin

|
|

In-app UI

|

Jetpack Compose

|
|

Animation

|

Rive or sprite-based animation

|
|

Character engine

|

Kotlin

|
|

Movement

|

Custom controller

|
|

App state

|

ViewModel + StateFlow

|
|

In-app rendering

|

Compose / Canvas or custom renderer

|
|

Floating rendering

|

Android custom View

|
|

Overlay

|

WindowManager

|
|

Background behavior

|

Carefully scoped service, if needed

|
|

Build

|

Android Studio + Gradle

|

Design principle: Don't put all movement logic inside a Composable. The engine should be independent of the UI so it can run with either rendering mode.

# 2. Project Folder Structure

Start with a clean architecture that can grow.

```
AnimatedCompanion/
│
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/companion/
│           ├── MainActivity.kt
│           │
│           ├── ui/
│           │   ├── CompanionScreen.kt
│           │   ├── CompanionViewModel.kt
│           │   └── theme/
│           │
│           ├── character/
│           │   ├── CharacterModel.kt
│           │   ├── CharacterEngine.kt
│           │   ├── CharacterState.kt
│           │   ├── CharacterController.kt
│           │   └── MovementController.kt
│           │
│           ├── animation/
│           │   ├── AnimationPlayer.kt
│           │   ├── AnimationState.kt
│           │   └── CharacterAssetLoader.kt
│           │
│           ├── behavior/
│           │   ├── BehaviorController.kt
│           │   ├── IdleBehavior.kt
│           │   └── RandomBehavior.kt
│           │
│           ├── overlay/
│           │   ├── OverlayManager.kt
│           │   ├── OverlayService.kt
│           │   └── CompanionOverlayView.kt
│           │
│           └── data/
│               ├── SettingsRepository.kt
│               └── CharacterPreferences.kt
│
└── shared/
    └── (optional future module)
```

For the first prototype, you can keep everything inside `app` and extract a separate shared module later when the engine stabilizes.

# 3. Phase 1 — Build the Character Inside Your App

Goal: Make one character move smoothly on the screen.

Don't start with overlay functionality yet. First verify the core experience.

![Cute 2D Pixel Cat Sprite Sheet for Games | AI Art Generator | Easy-Peasy.AI](https://images.openai.com/static-rsc-4/iVu2G8CMVrIngxEwXXYByvcljtKUmZ_QECgGIkLJkEa0w5yufFQv9MpI-m7RKbPXI6LJ12CoasKtXoFT1xN0U5UEoZFLBcCuC8Zo6hyI0vTDm0UMi-GOMOxO54C6EnJWyvZsp9-hNUEDCcIh9k-g_7w1aSm4SgkhYx6n41sr3H0?purpose=inline)

![Cat & Dog - Free Sprites | OpenGameArt.org](https://images.openai.com/static-rsc-4/QFh7T3k5rvMakqyCGY7zYA2G21vuDTOkowGOs1Ma8vFCFkuciOthGPq8EvCanXjgaec64clkd4PwziOscfdmm8G1DqRBhMWEhdL5dbOFQjnMcWXWOEqUuNMnzAkP-1P3Fx3-pR0rPf1LWZjyWxmP62qY3bkPCKFve7SauK_2Yro?purpose=inline)

![A simple breakdown of refined character animation in Rive | Rive](https://images.openai.com/static-rsc-4/MW6-aJH_cN5uLP46e9oVmuyLmC4XKs06JPnXBiF3Rh0X37NsYJhV_2_KUB51RwTNDzmSxbDaGZrKhPrc01Bt8-D0bgbPdBnDn7DcKximpUSdK7MtyS1y-mlG3i9dOIvOq5FfOLPth8J_R8YgV1PIuygIAU7dWW_Imrc1Xpbgzlc?purpose=inline)

11

## Features

* Character appears in the center.

* Character walks to random destinations.

* Character turns left and right.

* Character stops at boundaries.

* Character idles between movements.

* User can tap the character.

* Character responds to touch.

### Character model

Kotlin

```
data class CharacterModel(
    val id: String = "default",
    val x: Float = 0f,
    val y: Float = 0f,
    val direction: Direction = Direction.RIGHT,
    val state: CharacterState = CharacterState.IDLE,
    val speed: Float = 120f
)

enum class Direction {
    LEFT,
    RIGHT
}

enum class CharacterState {
    IDLE,
    WALKING,
    JUMPING,
    REACTING,
    SLEEPING
}
```

This is a starting model. You can add velocity, target position, animation identifiers, and other properties as your engine evolves.

# 4. Phase 2 — Movement Engine

This is the heart of your app.

The character should not jump randomly from one position to another. It should move smoothly over time.

## Movement logic

#### Example movement cycle

1. Character is idle at `(100, 500)`.

2. Behavior controller selects destination `(650, 500)`.

3. Movement controller starts walking.

4. Character moves at a speed of `120 dp/s`.

5. Character stops when the destination is reached.

6. Character plays idle animation.

### Important: Use delta time

Your movement should depend on elapsed time, not the number of frames rendered.

Kotlin

```
data class Position(
    val x: Float,
    val y: Float
)

class MovementController {

    fun moveTowards(
        current: Position,
        target: Position,
        speed: Float,
        deltaSeconds: Float
    ): Position {

        val dx = target.x - current.x
        val dy = target.y - current.y

        val distance = kotlin.math.sqrt(
            dx * dx + dy * dy
        )

        if (distance <= 1f) {
            return target
        }

        val step = minOf(
            speed * deltaSeconds,
            distance
        )

        return Position(
            x = current.x + (dx / distance) * step,
            y = current.y + (dy / distance) * step
        )
    }
}
```

This function is a basic movement helper. A production engine should also handle screen bounds, coordinate spaces, and update scheduling.

### Why delta time matters

If you use a fixed movement amount on every frame, the character can move at different speeds on devices with different frame rates. Delta time makes movement more consistent.

# 5. Phase 3 — Character Behavior System

We want the character to feel alive.

Instead of only walking forever, it should decide what to do next.

## State machine

### Character state machine

IDLE

Wait, blink, look around

DECIDE ACTION

Select the next behavior

WALKING

Move to target

JUMPING

Play jump

REACTING

Respond to touch

SLEEPING

Rest state

IDLE

Return after the action completes

### Behavior rules

Start with deterministic behavior rather than AI.

|
Behavior

|

Example trigger

|
| --- | --- |
|

Walk

|

Random destination selected

|
|

Jump

|

Periodic decision or user tap

|
|

React

|

User taps character

|
|

Sleep

|

Idle for a long time

|
|

Wave

|

User interacts repeatedly

|
|

Follow

|

User sets a target point

|

You can add an AI model later for dialogue and higher-level decisions, but the animation and movement system should remain predictable.

# 6. Phase 4 — Animation System

You need two separate concepts:

1. Movement: Where the character is located.

2. Animation: What the character is doing visually.

For example, the character might be walking left, but its animation system determines which walking frames are shown.

## Animation states

Walk

Loop

Idle

Loop

Jump

One-shot

Wave

One-shot

Sleep

Loop

### Asset options

![Rive Editor](https://images.openai.com/static-rsc-4/5SaKMGdUIJjonhRXLBm-kNxOZA9Sc8hFWIIq7PR7tK0_DqhKke1qdAH0wwQ2RbBJOxzZpGwT7iXWTSDS4bOeyvch986voPTSdVWPFekTB1Nwick3MafVM39jLpVj61gLjXSTLr-d5EVo3OLlDwRix2TYb63fqdv2J80O7CawNbc?purpose=inline)

Rive

Good for rigged 2D characters and state-machine-driven animation. Use one character asset with multiple animation states.

![Gender-Neutral Pixel Art Base Model Sprite Sheet | AI Art Generator | Easy-Peasy.AI](https://images.openai.com/static-rsc-4/clKAcCYuhUaFrEtTIlkHnucI3DWkrpUaJOypc2-STZNGTX9y8kySWGs-3mcu-VznVHAXYABW-8p7Mpeb0x0Qq0Q27tjn5fY8qfIOQr3NJ2LlFU9cm5yWGp0QCvol-r3GCTt6Amlx3R6v5jD66A9pTbj5gSJWQ_Z2Sib-KFrqZK0?purpose=inline)

Sprite sheet

Good for pixel art and frame-based animation. You manage frame timing and direction yourself.

For your first prototype, choose one animation format and keep the renderer interface abstract. That makes it easier to change the asset technology later.

# 7. Phase 5 — Build the In-App Scene

Now we connect the engine to Jetpack Compose.

## Screen layout

![【117日続いた理由】歩くだけで育成×ポイ活『えみぅ』が毎日続くワケは？｜おりけん🐶移動ポイ活](https://images.openai.com/static-rsc-4/MmKutrjD4k-MQaA3gT0E87sPmFEzea7JFiXa0pyDQllL8xXFmCn8-CMcE-AS1G7iou-LvaOZUDgXtNdH79kKHIS-bnC5Xy12DQnO_A9ANf1-AskHAC_Q9JZ50KfZbt-DNSrq2tplS5MDewgguMRZJViob1KBzwJGvf0IF3NGbAc?purpose=inline)

Your app screen could include:

* Animated background.

* Character roaming area.

* Character status or speech bubble.

* Pause and play controls.

* Character customization.

* Button to enable floating mode.

### Suggested Compose structure

Kotlin

```
@Composable
fun CompanionScreen(
    state: CharacterUiState,
    onCharacterTap: () -> Unit,
    onToggleFloating: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        // Background scene

        CharacterCanvas(
            state = state,
            onCharacterTap = onCharacterTap
        )

        // Optional speech bubble

        // Settings / floating mode button
    }
}
```

This is a structural example, not a complete renderer. The actual implementation depends on whether you choose Compose Canvas, a custom View, or Rive.

### Coordinate considerations

Your in-app scene should have its own coordinate system. Don't assume screen pixels equal density-independent pixels.

* Compose layout dimensions are generally expressed in dp.

* Android Canvas uses pixel coordinates.

* Overlay windows have their own screen and window coordinate considerations.

Create a coordinate conversion layer when necessary.

# 8. Phase 6 — Floating Character Across Other Apps

This is the advanced stage.

You want the same character to appear above other applications, for example:

![Hellopet - Cute cats dogs and other unique pets for Android -  Download](https://images.openai.com/static-rsc-4/OaKN-fgjI8BcY1lGWbpaV6j0lC5BkGTIShfcRfTlYDX66X_CPn2ikUdfy6-vY9NtZQ5MDQEmSni8z2nhCy5Kkmg9GvcKaMapHSHVJADKzRN3ui31v5jJ0sAcePcb5hON5lbxkFzvf3hNCGqrkEfoJa-oty_k_JDe_PD_Cu5n4zs?purpose=inline)

### How it works

### Floating architecture

User enables floating mode

App asks for overlay permission

OverlayManager

Checks permission and creates/removes the window

WindowManager

Displays the custom overlay View

Character Renderer + Engine

Draws and animates the character

### Overlay permission

You need to declare:

XML

```
<uses-permission
    android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

Then send the user to the appropriate settings screen:

Kotlin

```
val intent = Intent(
    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
    Uri.parse("package:$packageName")
)

startActivity(intent)
```

Check permission:

Kotlin

```
Settings.canDrawOverlays(context)
```

Android documents this permission as enabling windows of type `TYPE_APPLICATION_OVERLAY`. The user must explicitly authorize it on supported Android versions.

![](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

developer.android.com

+1

### Create the overlay window

A basic `WindowManager` setup would use:

Kotlin

```
val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    PixelFormat.TRANSLUCENT
)
```

Then attach your custom character View:

Kotlin

```
windowManager.addView(
    characterView,
    params
)
```

Note: This is only the basic window concept. Production code must handle removal, configuration changes, permission revocation, touch behavior, lifecycle, and exceptions.

# 9. Floating Service and Background Lifecycle

This part is important because you want the character to remain visible when switching apps.

A foreground service may be useful for managing longer-lived user-visible behavior, but it is not a guarantee that your character can run forever without interruption.

### Android requirements to plan for

* Background foreground-service start restrictions.

* Foreground-service type declarations when applicable.

* User-visible notification requirements.

* Battery optimization and vendor-specific background behavior.

* Permission handling.

* Start and stop controls.

* Android version compatibility.

Android 14 requires foreground service types for apps targeting that version, and Android 15 narrows a specific background-start exemption for apps using overlay permission: the app must have a visible overlay window when relying on that exemption.

![](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

developer.android.com

+2

Design recommendation: Make floating mode user-controlled. Start it from an explicit action in the app, display a clear control to stop it, and don't assume the OS will preserve an indefinite animation loop.

For the first floating prototype, you can create an overlay without immediately adding a persistent service. Once that works, evaluate whether a service is actually needed.

# 10. Phase 7 — Shared Engine for Both Modes

This is the key architectural decision.

Don't create two separate movement engines.

Use one shared engine and two renderers.

### Shared character engine

CharacterEngine

Movement + state machine + behavior

In-App Renderer

Compose / Canvas

Overlay Renderer

Custom Android View

### Example interface

Kotlin

```
interface CharacterRenderer {
    fun render(state: CharacterModel)
    fun dispose()
}
```

Your engine should not depend on a specific UI framework. Both renderers can consume the character state.

For more advanced designs, define a rendering state that contains position, direction, animation ID, and other information, then use a consistent update contract.

# 11. Phase 8 — User Interactions

Make the character more fun after basic movement is stable.

![Cute cartoon mouse waving | Free Photo Illustration - rawpixel](https://images.openai.com/static-rsc-4/_b9fN03oxx4vbMDZMmHrH-_7pq_K3QdCTjbe0qTyCPmFZsvvaC-UrLNtnpUjzf7Ksyu5QG7HFyIm2kXWgjGoKa-L70xV8_uo92ggvGn85IbV2YgtfMfoAfskYZSPNDlVj_36AEpII570K7zYO9z3D6KKv0qEpmIU0mAicsjsp9c?purpose=inline)

Tap to React

Tap the character → wave, jump, or show a reaction.

![Businessman pushing a target on white background. Hand drawn doodle man. Vector stock illustration](https://images.openai.com/static-rsc-4/6BiAy-j1Ge2qfC94fCL59cvnNncvi1x2ZeBYEfeK_wpVuFYQ6--Z5oLF4_M6DthJFCs793pCh_Fe6EbWZierACG6ggPGY2F1O7N23VVZh073fsQhw5-LsMW1HJj-MFQZ3fReOKLIMbj4dEWKgeja1ugeS12Oli6g2iH-VdLw_EgGKhdoP9nSp7Gjm5EDsV85?purpose=inline)

Tap to Move

User selects a location, and the character walks toward it.

![Mascot With Speech Bubble - Blue Cartoon Character With Speech Bubble PNG](https://images.openai.com/static-rsc-4/cVJAChfeilTo7O-4oLlJQdfTgowFKyon6PH4yc7Xw1eFjYzRf92nQneGSsmfe_T86qPxz055f1QvrEkY_t1oeDwDFqwd3zXfOmSXRU5eEKyHBo4BJXb00zNefPvwROL6hki7b9QIUjNEcK4DWktdjixa9MDRBK93CpTuGmDHQQ8?purpose=inline)

Speech Bubble

Character displays messages or reacts to user actions.

![Fluvsies Pocket World Review | GamesXF](https://images.openai.com/static-rsc-4/ed-8MPW1dySJeuSho1SFsRsBBosqsF0hLH1mjIb03BvzwdlVhhpr8gKdRD-hINhjjl6Nb6HaS8iWTOZXmZ88eh-tzV9nSlKOvcBpztqJMwRPmrwX1Yu3UqJckQi2RgoPBnqG2TpCNUnQaripqX2bqgdLHXlO8eyT4UJNRdmkh4s?purpose=inline)

Character Customization

Change appearance, costumes, colors, or accessories.

# 12. Phase 9 — Optional AI Features

Once your character is working, you can add AI capabilities.

AI should control high-level decisions, not every animation frame.

### Example architecture

User message

"Come here!"

AI / Intent Parser

Converts the request into a safe action

CharacterController

Validates and executes the movement

Animation

Walk toward the selected target

Possible AI features:

* Chat with the character.

* Character personality.

* Generate reactions.

* Suggest activities.

* User-defined commands.

For cost control, use deterministic behavior for normal roaming and invoke an LLM only for dialogue or occasional higher-level actions.

# 13. Phase 10 — Testing Plan

You need to test the engine independently from the UI.

|
Test

|

What to verify

|
| --- | --- |
|

Movement

|

Character reaches the target

|
|

Boundary

|

Character stays inside allowed area

|
|

Direction

|

Character faces the correct direction

|
|

State transitions

|

Idle → Walk → Idle

|
|

Touch

|

Tap triggers the intended behavior

|
|

Animation

|

Correct animation plays for the state

|
|

Overlay permission

|

Correct behavior when permission is denied

|
|

Overlay lifecycle

|

Window removed and re-added safely

|
|

Background

|

App handles service restrictions

|
|

Performance

|

No unnecessary CPU/GPU work when idle

|

### Performance rules

* Don't run an unrestricted animation loop when the character is idle.

* Use a bounded update rate appropriate to your animation needs.

* Pause or reduce updates when the character is not visible.

* Avoid unnecessary object allocations inside the frame-update loop.

* Test on your realme device as well as an emulator.

# 14. Complete Development Roadmap

## Build checklist

0/9

Project setup

Kotlin, Compose, basic architecture, and Git repository.

Character renderer

Display one animated character inside the app.

Movement engine

Position, targets, delta time, boundaries, and direction.

Behavior state machine

Idle, walking, jumping, and reacting.

Interaction

Touch, speech bubble, and pause controls.

Floating prototype

Overlay permission, WindowManager, and custom overlay View.

Shared engine

Reuse character logic between in-app and overlay modes.

Testing and optimization

Lifecycle, performance, device testing, and error handling.

Advanced features

AI dialogue, customization, settings, and polish.

# 15. Claude Code Master Prompt

Copy this into your Claude Code session. It is designed to guide the implementation step by step rather than generate the entire project blindly.

## Animated Companion Builder

Prompt

Use this as your main project implementation instruction.

You are a senior Android Kotlin engineer and software architect.I am building a native Android animated companion app using Kotlin and Jetpack Compose.

## Product Goal

Create an animated character that can roam inside my Android app and later appear as an optional floating companion over other apps.

## Architecture

* Kotlin

* Jetpack Compose for the in-app experience

* Reusable character engine

* Separate character model, movement, behavior, and renderer

* ViewModel and StateFlow where appropriate

* Custom Android View for the overlay renderer

* WindowManager for floating mode

* Avoid unnecessary dependencies

## Development Rules

1. Inspect the existing codebase before modifying files.

2. Understand the existing UI framework and architecture.

3. Do not rewrite the project without justification.

4. Implement features incrementally.

5. Explain the design before major implementation changes.

6. Keep movement logic independent of the UI framework.

7. Add tests for movement and state transitions.

8. Consider lifecycle, performance, and Android permission requirements.

## Milestones

Milestone 1: In-app animated character. Milestone 2: Smooth movement engine. Milestone 3: State machine and interactions. Milestone 4: Floating overlay prototype. Milestone 5: Shared engine between both modes. Milestone 6: Performance and lifecycle testing.

## First Task

Analyze my existing project.Determine:

* Compose or XML

* Current architecture

* Gradle modules

* Minimum SDK and target SDK

* Existing animation dependencies

* Suitable location for the character engine

Do not modify any files in the first step.Return:

1. Codebase summary

2. Proposed architecture

3. File structure

4. Dependencies needed

5. Risks and Android limitations

6. Step-by-step implementation plan

Copy master prompt

## My recommendation for your first build

Start with Phase 1 → Phase 4 and get a character walking inside your app before working on the overlay.

The first meaningful milestone is:

> A character appears on your Android screen, walks naturally to random locations, turns around, idles, and reacts when you tap it.

Once that works, we can reuse the same movement engine for floating mode.

Next step: Tell Claude Code to inspect your existing Kotlin project. Don't let it add libraries or rewrite your architecture until it has explained the existing codebase.
