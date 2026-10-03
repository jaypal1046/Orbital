# UI/UX redesign plan

## Findings

- Dense bordered cards create noise and hide action hierarchy.
- Drawer gives setup, support, legal, and chat history equal emphasis.
- Provider setup saves each keystroke, exposes keys, and puts diagnostics beside first-run tasks.
- Onboarding does not state progress or distinguish optional setup from required access.
- Activities each define their own dark palette and typography.

## System

- One dark, cool-neutral palette with violet as the sole action color.
- 4/8/12/16/24/32/48 spacing scale; 10/16/24 radius scale.
- Type roles: 30 display, 24 page, 20 title, 16 card, 14 body, 12 metadata.
- Surface hierarchy: background, surface, raised surface, selected surface. Borders only delimit controls.
- Motion: Material drawer and content transitions only; status changes use short fade/expand feedback.

## Flow

1. Provider list shows connection state and one clear Configure action.
2. Configure reveals key entry, validation, then model selection. Keys remain masked.
3. Quotas, signals, and routing live in secondary tabs after configuration.
4. Drawer prioritizes New chat, recent chats, then grouped workspace and support destinations.
5. Onboarding marks optional provider setup and keeps the next step explicit.
