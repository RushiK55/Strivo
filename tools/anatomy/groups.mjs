// Which gym muscle group each part of the anatomy atlas belongs to, and which parts the app does not need.
// Pure functions (no file access), tested in groups.test.mjs. The build (build.mjs) refuses to run when it meets a muscle
// that is neither in a group nor on the drop list, so nothing disappears silently.

/**
 * The clickable muscle groups. `libraryGroup` is the exercise library's `muscleGroup` it belongs to, `view` the side the
 * camera should show when it zooms in, `layer` whether it is on the surface or under other muscles ("deep": the app can show
 * it by fading the surface muscles), `detail` how much of the source mesh is kept (the share of triangles; hidden or deep
 * muscles need far less than the ones you see in the mirror).
 */
export const GROUPS = [
  // id, label, libraryGroup, view, layer, detail, matches (on the name with left/right removed)
  g('chest', 'Chest', 'Chest', 'front', 'surface', 0.50, /pectoralis major|pectoralis minor|subclavius/),
  g('delt-front', 'Front delts', 'Shoulders', 'front', 'surface', 0.60, /clavicular part of deltoid/),
  g('delt-side', 'Side delts', 'Shoulders', 'both', 'surface', 0.60, /acromial part of deltoid/),
  g('delt-rear', 'Rear delts', 'Shoulders', 'back', 'surface', 0.60, /spinal part of deltoid/),
  g('rotator-cuff', 'Rotator cuff', 'Shoulders', 'back', 'deep', 0.30, /supraspinatus|infraspinatus|teres minor|subscapularis/),
  g('traps', 'Upper traps', 'Back', 'back', 'surface', 0.50, /descending part of trapezius|levator scapulae/),
  g('lats', 'Lats', 'Back', 'back', 'surface', 0.50, /latissimus dorsi/),
  g('mid-back', 'Mid back', 'Back', 'back', 'surface', 0.40, /transverse part of trapezius|ascending part of trapezius|rhomboid|teres major/),
  g('biceps', 'Biceps', 'Biceps', 'front', 'surface', 0.60, /biceps brachii|brachialis|coracobrachialis/),
  g('triceps', 'Triceps', 'Triceps', 'back', 'surface', 0.60, /triceps brachii|anconeus/),
  g('forearm-extensors', 'Forearm extensors', 'Biceps', 'back', 'surface', 0.35,
    /extensor carpi|extensor digitorum$|extensor digiti minimi|extensor indicis|extensor pollicis|abductor pollicis longus|supinator|brachioradialis/),
  g('forearm-flexors', 'Forearm flexors', 'Biceps', 'front', 'surface', 0.35,
    /flexor carpi|palmaris|flexor digitorum (superficialis|profundus)|pronator|flexor pollicis longus/),
  g('hands', 'Hands', 'Full body', 'front', 'surface', 0.12, /of hand$|pollicis brevis|opponens pollicis|adductor pollicis/),
  g('neck', 'Neck', 'Shoulders', 'both', 'surface', 0.07,
    /sternocleidomastoid|scalenus|splenius|platysma|longus capitis|longus colli|rectus capitis|obliquus capitis|semispinalis (capitis|cervicis)|longissimus (capitis|cervicis)|iliocostalis cervicis|cervical|digastric|mylohyoid|geniohyoid|stylohyoid|sternohyoid|sternothyroid|thyrohyoid|omohyoid/),
  g('erector-spinae', 'Lower back', 'Back', 'back', 'surface', 0.20, /iliocostalis|longissimus|^spinalis/),
  g('multifidus', 'Multifidus', 'Back', 'back', 'deep', 0.60, /multifidus/),
  g('deep-back', 'Deep back', 'Back', 'back', 'deep', 0.06, /rotator|interspinal|intertransversari|levatores costarum|semispinalis|serratus posterior/),
  g('serratus-anterior', 'Serratus', 'Core', 'front', 'surface', 0.20, /serratus anterior/),
  g('abs', 'Abs', 'Core', 'front', 'surface', 0.50, /rectus abdominis/),
  g('obliques', 'Obliques', 'Core', 'front', 'surface', 0.18, /external oblique/),
  g('internal-oblique', 'Internal obliques', 'Core', 'front', 'deep', 0.25, /internal oblique/),
  g('transverse-abdominis', 'Transverse abdominis', 'Core', 'front', 'deep', 0.25, /transversus abdominis/),
  g('quadratus-lumborum', 'Quadratus lumborum', 'Core', 'back', 'deep', 0.70, /quadratus lumborum/),
  g('intercostals', 'Intercostals', 'Core', 'front', 'deep', 0.04, /external intercostal/),
  g('glutes', 'Glutes', 'Glutes', 'back', 'surface', 0.80, /gluteus maximus/),
  g('glute-med', 'Glute medius & TFL', 'Glutes', 'back', 'surface', 0.40, /gluteus medius|gluteus minimus|tensor fasciae latae|iliotibial tract/),
  g('hip-rotators', 'Deep hip rotators', 'Glutes', 'back', 'deep', 0.20, /piriformis|gemellus|obturator|quadratus femoris/),
  g('hip-flexors', 'Hip flexors', 'Legs', 'front', 'surface', 0.40, /iliacus|psoas|sartorius/),
  g('quadriceps', 'Quadriceps', 'Legs', 'front', 'surface', 0.60, /rectus femoris|vastus/),
  g('hamstrings', 'Hamstrings', 'Legs', 'back', 'surface', 0.60, /biceps femoris|semitendinosus|semimembranosus/),
  g('adductors', 'Inner thigh', 'Legs', 'front', 'surface', 0.40, /adductor (magnus|longus|brevis|minimus)|gracilis|pectineus/),
  g('shins', 'Shins', 'Legs', 'front', 'surface', 0.50, /tibialis anterior|extensor digitorum longus|extensor hallucis longus|fibularis tertius/),
  g('calves', 'Calves', 'Legs', 'back', 'surface', 0.40,
    /gastrocnemius|soleus|plantaris|popliteus|tibialis posterior|flexor digitorum longus|flexor hallucis longus|fibularis (longus|brevis)/),
  g('feet', 'Feet', 'Full body', 'front', 'surface', 0.10,
    /of foot$|plantar interosseous|flexor accessorius|flexor digitorum brevis|abductor hallucis|flexor hallucis brevis|adductor hallucis|extensor hallucis brevis|extensor digitorum brevis/),
];

/** What each group is and what it does, in plain words: shown next to the 3D body in the dashboard and the app. */
export const ABOUT = {
  'chest': 'The big fan-shaped muscle on the front of the ribcage (pectoralis major, with the smaller pectoralis minor beneath it). It pushes the arm forward and across the body, as in every press and fly.',
  'delt-front': 'The front part of the shoulder muscle (anterior deltoid). It lifts the arm forward and helps in all pressing movements.',
  'delt-side': 'The middle of the shoulder cap (lateral deltoid). It lifts the arm out to the side and gives the shoulders their width.',
  'delt-rear': 'The back of the shoulder (posterior deltoid). It pulls the arm backwards and outwards, and works hard in rows and reverse flyes.',
  'rotator-cuff': 'Four small muscles around the shoulder joint (supraspinatus, infraspinatus, teres minor and subscapularis) that hold the arm bone in its socket and rotate the arm. Mostly under the other shoulder muscles.',
  'traps': 'The upper trapezius and the levator scapulae, from the neck to the shoulder. They lift the shoulders (a shrug) and tilt the head.',
  'lats': 'The latissimus dorsi, the broad wing-shaped muscle of the back that runs from the spine and pelvis to the upper arm. It pulls the arm down and back in pull-ups, pulldowns and rows, and gives the back its width.',
  'mid-back': 'The middle and lower trapezius, the rhomboids and the teres major between the shoulder blades. They pull the shoulder blades together and down and keep the shoulders from rounding forward.',
  'biceps': 'The muscles at the front of the upper arm (biceps brachii, brachialis and coracobrachialis). They bend the elbow and turn the forearm palm-up.',
  'triceps': 'The three-headed muscle at the back of the upper arm, with the small anconeus. It straightens the elbow and works in every pressing movement.',
  'forearm-extensors': 'The muscles on the back and thumb side of the forearm, including the brachioradialis. They lift the wrist and fingers back and help in hammer curls.',
  'forearm-flexors': 'The muscles on the palm side of the forearm. They bend the wrist and fingers and turn the forearm palm-down: your grip strength.',
  'hands': 'The small muscles of the palm and thumb that move the fingers. They are trained with grip work.',
  'neck': 'The muscles that hold and move the head: the sternocleidomastoid, the scalenes, the splenius, and the small muscles under the jaw and along the neck bones.',
  'erector-spinae': 'Three columns of muscle (iliocostalis, longissimus and spinalis) on either side of the spine. They straighten the back and keep it upright in deadlifts, rows and back extensions. Often called the lower back.',
  'multifidus': 'Short deep muscles that link each vertebra to the ones above and stabilise the spine. They lie deep under the erector spinae and matter for back health.',
  'deep-back': 'Small deep muscles between the vertebrae and the ribs (rotatores, interspinales, serratus posterior and others). They fine-tune the position of the spine.',
  'serratus-anterior': 'The finger-like muscle along the side of the ribs that holds the shoulder blade against the chest. It pushes the arm forward, as at the end of a push-up.',
  'abs': 'The rectus abdominis, the "six-pack": the vertical muscle at the front of the abdomen. It bends the spine forward in a crunch and keeps the pelvis from tipping forward.',
  'obliques': 'The external obliques on the sides of the abdomen. They rotate and bend the trunk sideways and brace it in twists and side bends.',
  'internal-oblique': 'The internal obliques, a layer under the external obliques. They rotate and bend the trunk and brace the abdomen.',
  'transverse-abdominis': 'The deepest abdominal muscle, wrapping around the waist like a corset. It braces the abdomen and supports the spine in planks and heavy lifts.',
  'quadratus-lumborum': 'A flat four-sided muscle between the lowest rib and the pelvis, beside the lower spine. It bends the trunk sideways and steadies the pelvis in side planks and carries.',
  'intercostals': 'The thin muscles between the ribs. They raise and lower the ribs when you breathe.',
  'glutes': 'The gluteus maximus, the largest muscle in the body and the shape of the buttock. It extends the hip in squats, deadlifts, hip thrusts and running uphill.',
  'glute-med': 'The gluteus medius and minimus on the side of the hip, with the tensor fasciae latae and the iliotibial band down the outer thigh. They lift the leg out to the side and keep the pelvis level when you stand on one leg.',
  'hip-rotators': 'Deep muscles under the glutes (piriformis, the gemelli, the obturators and quadratus femoris). They turn the thigh outward and steady the hip joint.',
  'hip-flexors': 'The iliacus and psoas (deep) and the sartorius, the long strap across the thigh. They lift the thigh toward the trunk in leg raises, running and climbing.',
  'quadriceps': 'The four muscles on the front of the thigh (rectus femoris and the three vastus muscles). They straighten the knee in squats, leg presses and lunges.',
  'hamstrings': 'The muscles on the back of the thigh (biceps femoris, semitendinosus and semimembranosus). They bend the knee and extend the hip in leg curls and Romanian deadlifts.',
  'adductors': 'The inner-thigh muscles (the adductors, gracilis and pectineus). They pull the leg toward the middle and help in squats and deadlifts.',
  'shins': 'The muscles on the front of the lower leg (tibialis anterior and the toe extensors). They lift the foot and toes up toward the shin.',
  'calves': 'The gastrocnemius and soleus at the back of the lower leg, with the deeper muscles beside them. They point the foot down in calf raises, walking and jumping.',
  'feet': 'The small muscles of the sole and the top of the foot that move the toes and support the arch.',
};


function g(id, label, libraryGroup, view, layer, detail, match) {
  return { id, label, libraryGroup, view, layer, detail, match };
}

/**
 * Muscles the BodyParts3D atlas does not have, taken from the Z-Anatomy-derived model (lucgrn3/anatomy-models, muscles.glb,
 * whose parts are named by Terminologia Anatomica 2 id: "ta2-2231.l"). It is a male body in the same coordinates as
 * BodyParts3D, so the muscles fit; for the female body they are fitted with the same reshaping the female atlas applies to
 * its own muscles (see warp.mjs). Each was identified by drawing it over the body and checking its shape and place.
 */
export const ZANATOMY_MUSCLES = {
  2231: 'latissimus dorsi',
  2357: 'rectus abdominis',
  2373: 'internal oblique',
  2375: 'transversus abdominis',
  2382: 'quadratus lumborum',
  2285: 'multifidus',
};

/** "ta2-2231.l" -> "Left latissimus dorsi" (a name the grouping understands); null for a part we do not use. */
export function zanatomyName(node) {
  const found = /^ta2-(\d+)\.(l|r)$/.exec(node);
  const muscle = found && ZANATOMY_MUSCLES[found[1]];
  return muscle ? `${found[2] === 'l' ? 'Left' : 'Right'} ${muscle}` : null;
}

/**
 * Muscles that are in the atlas but not wanted in the app: the eye, throat, tongue and palate, the heart, the pelvic floor,
 * and the inside of the chest wall (hidden by the ribs and the muscles over them). Each is listed with the reason.
 */
export const DROPPED = [
  [/^(superior|inferior|lateral|medial) rectus$|^(superior|inferior) oblique$|levator palpebrae/, 'eye muscle'],
  [/arytenoid|aryepiglott|cricothyroid|thyro-arytenoid|vocalis|veli palatini|uvular|genioglossus|hyoglossus/, 'throat, tongue or palate'],
  [/papillary muscle/, 'heart'],
  [/coccygeus|puborectalis|superficial perineal|external anal sphincter/, 'pelvic floor'],
  [/^diaphragm$|internal intercostal|innermost intercostal|transversus thoracis/, 'inside the chest wall, never visible'],
  [/tendon of .*quadriceps femoris/, 'misplaced beside the knee in the female atlas'],
];

/**
 * The nipples of the female atlas. Only where they are is used: the female skin gets a soft, smooth bust contour centred there
 * (see build.mjs). Neither they nor anything else of the breast is ever drawn.
 */
export const isNipple = (name) => /\bnipple\b/i.test(name);

/** The name without left/right, lower case: "Long head of right biceps femoris" -> "long head of biceps femoris". */
export const baseName = (name) => name.toLowerCase().replace(/\((left|right)\)/g, ' ').replace(/\b(left|right)\b/g, ' ').replace(/\s+/g, ' ').trim();

/** A muscle's place: `{ group }`, `{ dropped: reason }` or `{ unknown: true }`. */
export function classify(name) {
  const base = baseName(name);
  const dropped = DROPPED.find(([re]) => re.test(base));
  if (dropped) return { dropped: dropped[1] };
  const found = GROUPS.find((group) => group.match.test(base));
  return found ? { group: found.id } : { unknown: true };
}

/** "L" or "R" from the name, else from where the part is (x is positive on the body's left); "C" for the middle. */
export function sideOf(name, centreX) {
  if (/\bleft\b/i.test(name)) return 'L';
  if (/\bright\b/i.test(name)) return 'R';
  if (centreX > 0.01) return 'L';
  if (centreX < -0.01) return 'R';
  return 'C';
}

/** Parts the atlas files under the skeleton (or under connective tissue) but that are muscles or the band of the outer thigh. */
export const MISFILED_MUSCLES = /tibialis|fibularis|subscapularis|levator scapulae|iliotibial tract/i;

/**
 * Skeleton parts the app does not need: teeth and the tiny ear bones, the cartilages of the larynx and nose, the inside of
 * the bones (compact and trabecular tissue) and the knee's ligaments and discs, which are hidden inside the joint.
 */
export const DROPPED_BONES = /teeth|tooth|gingiva|incisor|canine|molar|premolar|malleus|incus|stapes|cricoid|arytenoid|corniculate|cuneiform cartilage|thyroid cartilage|alar cartilage|trabecular bone|compact bone|cruciate|anterolateral ligament|articular disk of knee/i;

/**
 * The female atlas holds two spines: the male one it started from (first to twelfth thoracic vertebra and so on, with a
 * sacrum, an atlas and an axis, and a disc between each) and one shaped for the female body ("thoracic vertebra 1",
 * "fused sacrum" ...), which has the natural curve and fits her ribs and pelvis. Only the second is wanted.
 */
export const MALE_SPINE_IN_FEMALE = /^(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth) (thoracic|lumbar|cervical) vertebra$|^sacrum$|^atlas$|^axis$|intervertebral disk/i;
