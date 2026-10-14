# Attribution and licence

The geometry in `male.glb` and `female.glb` is adapted from the datasets below, which ask for credit and, for the first two,
for share-alike. **Show this credit in the app (for example under Profile or an "About" screen) and keep this file with the
models.**

- **Z-Anatomy** — *The libre 3D atlas of anatomy*, Gauthier Kervyn and Marcin Zielinski, **CC BY-SA 4.0**.
  https://github.com/Z-Anatomy/Models-of-human-anatomy. Used, through the adapted meshes of `lucgrn3/anatomy-models`
  (https://github.com/lucgrn3/anatomy-models, CC BY-SA 4.0), for five muscles: latissimus dorsi, rectus abdominis, internal
  oblique, transversus abdominis and quadratus lumborum. That repository's own credits file lists the multifidus as
  BodyParts3D (below).
- **BodyParts3D**, © The Database Center for Life Science. https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html.
  Mitsuhashi et al. (2009), https://doi.org/10.1093/nar/gkn613. The male bones and muscles, and (reshaped for a female body) the
  female bones and muscles. **The licence is stated two ways:** the Human-Atlas and female-atlas projects give CC BY 4.0, the
  `lucgrn3/anatomy-models` credits give CC BY-SA 2.1 Japan. Check the BodyParts3D licence page before you publish, and follow
  the stricter one (share-alike) until you have.
- **Human Reference Atlas / HuBMAP**, *3D Reference Organ Set for Female*, Kristen Browne and Heidi Schlehlein, CC BY 4.0.
  https://lod.humanatlas.io/ref-organ/united-female/v1.5. The female-shaped spine in the female skeleton, and the place of the nipples, used
  only to centre the soft bust contour of the female skin (nothing of the breast is drawn).

## What share-alike means here

Because the files contain CC BY-SA material, **`male.glb`, `female.glb` and `muscles.json` are shared under CC BY-SA 4.0**
(https://creativecommons.org/licenses/by-sa/4.0/): anyone may use and change them, including commercially, if they give this
credit, say what they changed, and share their changed versions of these models under the same licence. The app's own code is
a separate work and is not covered by that; if you plan to sell the app or want certainty about where the line is, have a
lawyer read the licence once.

## What was changed

Meshes were taken from the Human-Atlas and female-atlas projects (MIT licensed, which had already simplified them) and from the
`lucgrn3/anatomy-models` muscles. For this app: the muscles were joined into 34 groups, vertices merged, each group simplified,
smooth normals recomputed, the Z-Anatomy muscles moved onto the female body by the female atlas's own reshaping, and both bodies
put on the same ground and centre line. A smooth skin layer was built around the muscles and the skeleton (see `README.md`), and
Parts the app does not need were left out (see
`README.md`), including the reproductive organs and the breast (only where the nipples are is used, to place a smooth bump in the female skin). The build is `tools/anatomy/build.mjs`.

Educational use only; not a clinical tool.
