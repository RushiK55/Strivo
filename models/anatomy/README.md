# Anatomy models

The 3D body the app and the dashboard show: a skin layer over the muscles. Hover a muscle to light it up; click it and the skin
fades away and the muscle glows (the rest step back); read what it does and see its exercises.

The Android app packs this folder into its assets (`app/build.gradle.kts`) and draws it natively with OpenGL ES, not in a web view: the
male or female body by the account's gender, with the muscles trained on the chosen day glowing. The credit is shown in the Body screen.

| File | What it is |
|---|---|
| `male.glb`, `female.glb` | glTF 2.0 binary, about 3.6 MB each, 200,000 triangles (133,000 muscle, 30,000 skeleton, 40,000 skin). Plain glTF: no compression extension, so any viewer reads it |
| `muscles.json` | The catalogue: every muscle group, its label and description, which exercise-library group it belongs to, the side the camera should show, whether it is on the surface or deep, and where it is (a bounding box per body and per side, for zooming) |
| `ATTRIBUTION.md` | The credit and licence terms. **Read it, and show the credit in the app** |

Units are metres, Y is up, the body faces +Z, +X is the body's left. Both bodies stand on y = 0 and are centred, so the
camera can stay where it is when you switch between them.

## What is in a file

```
Male body / Female body
├── Muscles         34 groups, one node per side:  chest.L  chest.R  lats.L  lats.R  abs.L  abs.R  erector-spinae.C ...
├── Skeleton        one mesh (context; not clickable)
└── Skin            one mesh: a smooth layer around everything (not clickable)
```

A node's `extras` say what it is (`{ "group": "lats", "side": "L", "layer": "surface" }`; `{ "group": "skeleton" }`; `{ "group": "skin" }`).
The two sides of a group share one material (`muscle-lats`), so highlighting one highlights both; the nodes are separate so the
camera can zoom to one side.

**The skin** is drawn see-through (about 40 % opaque, a pale blue-grey) over the muscles, and a viewer fades it out when a muscle is
picked. It is not part of picking: a click on the skin lands on the muscle under it. It is smooth (no muscle detail, no face, no
fingers: the hands are mittens). The female skin has a **soft bust contour**: a gentle swelling, with no breast, areola or nipple
drawn (see below). The muscle layer has none.

**The 34 groups.** Surface: chest, front / side / rear delts, upper traps, lats, mid back, biceps, triceps, forearm flexors
and extensors, hands, neck, lower back (erector spinae), serratus, abs (rectus abdominis), obliques (external), glutes, glute
medius & TFL, hip flexors, quadriceps, hamstrings, inner thigh, shins, calves, feet. Deep (under the surface muscles, so the
viewer must fade the surface layer to show them): rotator cuff, multifidus, deep back, internal obliques, transverse
abdominis, quadratus lumborum, intercostals, deep hip rotators. `muscles.json` gives each group's `layer` and maps it to the
exercise library's `muscleGroup` (Chest, Back, Shoulders, Biceps, Triceps, Legs, Glutes, Core, ...).

## Where each part comes from

- **Most muscles, and the bones:** BodyParts3D, through the Human-Atlas (male) and female-atlas projects.
- **Six muscles BodyParts3D does not have** (lats, abs, internal oblique, transverse abdominis, quadratus lumborum,
  multifidus): the Z-Anatomy-derived model `lucgrn3/anatomy-models` (`muscles.glb`). It uses the same coordinates as
  BodyParts3D, so they fit the male body as they are. Each was identified by drawing it over the body (the file names parts
  only by anatomy ID), and `catalogue.source` says which groups they are (`"z-anatomy"`).
- **The female body:** the female atlas's muscles are the male ones reshaped to a female body (about 4 % shorter, with her own
  hips, waist and glutes), so every group exists in both bodies. The six extra muscles are moved the same way, by the average
  movement of the muscle vertices around them. Test: hiding a real muscle and fitting its male version from the others gets
  within 0.4 to 2.4 mm of the real female muscle (60 to 77 mm if left unfitted).
- **What makes her read as female:** that shape, the female-fitted spine and pelvis, and a soft bust in the skin. The bust is
  two smooth bumps (about 13 cm wide and 2.6 cm high, easing to nothing at the edge) centred where the atlas's nipples are and
  lying on the real front of the body there, which the skin is then grown around and smoothed over, so it is a hint of a shape,
  not a detailed one (`BUST` in `build.mjs`: make `out` smaller or larger to change it). **No breast, areola or nipple is in the
  file as a mesh**, and the uterus, ovaries and the rest of the reproductive system are left out.
- **The skin:** built by `tools/anatomy` around the muscles and the skeleton (and, for the female, the two bust bumps): points are
  scattered over them, put in a 5 mm grid, grown by 10 mm (skin and fat), filled and blurred, and the surface of that is
  simplified to 40,000 triangles. The head is closed over first, so there are no eye sockets.

## Limits

- **Not included:** the muscles of the face and scalp (masseter, temporalis, frontalis ...). Neither source model has them
  in a usable form, and the body has no head muscles.
- **Female shoulders and upper arms** are slightly jagged: a flaw of the reshaping in the source data.
- **Deep muscles are hidden by the surface ones.** A viewer shows them by fading the surface layer (the dashboard has a switch,
  and turns it on when you pick a deep muscle).
- **The skin** bridges small gaps (between an arm and the body, under the arms) with a thin web, because it is grown evenly
  around everything. It is see-through, so this hardly shows.
- **Licence:** because of the Z-Anatomy parts the two `.glb` files are CC BY-SA 4.0 (share-alike): see `ATTRIBUTION.md`.

## Also left out

The eye, throat, tongue, palate and heart muscles, the pelvic floor, the muscles inside the chest wall (they cannot be seen),
teeth, the ear bones, the larynx cartilages, the inside of the bones, the knee's ligaments, the whole breast (only where the nipples are is used, to place the bust contour of the skin), and in the female file the second (male) spine
the source contains.

## Rebuild

The sources are not in this repository: the Human-Atlas and female-atlas projects, and `muscles.glb` from
https://github.com/lucgrn3/anatomy-models (download it into any folder).

```
cd tools/anatomy
npm install
node build.mjs <path to Human-Atlas> <path to female-atlas> <folder with muscles.glb>   # about 30 seconds
npm test            # the grouping rules, the female fitting, and the skin maths
```

Change what is kept, or how much detail each group gets, in `tools/anatomy/groups.mjs`. The build stops, and says which
muscle, when an atlas holds one that is neither in a group nor on the drop list.
