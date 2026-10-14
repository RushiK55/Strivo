import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ABOUT, DROPPED_BONES, GROUPS, MALE_SPINE_IN_FEMALE, MISFILED_MUSCLES, ZANATOMY_MUSCLES, baseName, classify, isNipple, sideOf, zanatomyName } from './groups.mjs';

const group = (name) => classify(name).group;

test('names are read without left and right', () => {
  assert.equal(baseName('Long head of right biceps femoris'), 'long head of biceps femoris');
  assert.equal(baseName('Peroneus longus (left)'), 'peroneus longus');
  assert.equal(baseName('Left  pectoralis minor'), 'pectoralis minor');
});

test('the muscles people train land in the group a gym-goer expects', () => {
  assert.equal(group('Sternocostal part of right pectoralis major'), 'chest');
  assert.equal(group('Clavicular part of left deltoid'), 'delt-front');
  assert.equal(group('Acromial part of left deltoid'), 'delt-side');
  assert.equal(group('Spinal part of right deltoid'), 'delt-rear');
  assert.equal(group('Short head of left biceps brachii'), 'biceps');
  assert.equal(group('Right brachialis'), 'biceps');
  assert.equal(group('Lateral head of right triceps brachii'), 'triceps');
  assert.equal(group('Right brachioradialis'), 'forearm-extensors');
  assert.equal(group('Left extensor digitorum'), 'forearm-extensors');
  assert.equal(group('Humeral head of right pronator teres'), 'forearm-flexors');
  assert.equal(group('Descending part of left trapezius'), 'traps');
  assert.equal(group('Transverse part of right trapezius'), 'mid-back');
  assert.equal(group('Right rhomboid major'), 'mid-back');
  assert.equal(group('Left longissimus thoracis'), 'erector-spinae');
  assert.equal(group('Right external oblique'), 'obliques');
  assert.equal(group('Left rectus abdominis'), 'abs');
  assert.equal(group('Right latissimus dorsi'), 'lats');
  assert.equal(group('Left internal oblique'), 'internal-oblique');
  assert.equal(group('Right transversus abdominis'), 'transverse-abdominis');
  assert.equal(group('Left quadratus lumborum'), 'quadratus-lumborum');
  assert.equal(group('Right multifidus'), 'multifidus');
  assert.equal(group('Right gluteus maximus'), 'glutes');
  assert.equal(group('Right gluteus medius'), 'glute-med');
  assert.equal(group('Left tensor fasciae latae'), 'glute-med');
  assert.equal(group('Right rectus femoris'), 'quadriceps');
  assert.equal(group('Right vastus lateralis'), 'quadriceps');
  assert.equal(group('Long head of left biceps femoris'), 'hamstrings');
  assert.equal(group('Left semitendinosus'), 'hamstrings');
  assert.equal(group('Right adductor magnus'), 'adductors');
  assert.equal(group('Right gracilis'), 'adductors');
  assert.equal(group('Lateral head of left gastrocnemius'), 'calves');
  assert.equal(group('Right soleus'), 'calves');
  assert.equal(group('Right tibialis anterior'), 'shins');
  assert.equal(group('Left fibularis longus'), 'calves');
});

test('look-alike names do not end up in the wrong group', () => {
  // "biceps femoris" is a hamstring, "biceps brachii" an arm muscle
  assert.notEqual(group('Short head of right biceps femoris'), 'biceps');
  // "extensor digitorum longus" is in the shin, "extensor digitorum" in the forearm
  assert.equal(group('Right extensor digitorum longus'), 'shins');
  assert.equal(group('Right extensor digitorum'), 'forearm-extensors');
  // the thumb and the big toe have small muscles of their own
  assert.equal(group('Right extensor pollicis brevis'), 'forearm-extensors');
  assert.equal(group('Right abductor pollicis brevis'), 'hands');
  assert.equal(group('Oblique head of left adductor hallucis'), 'feet');
  assert.equal(group('Oblique head of left adductor pollicis'), 'hands');
  assert.equal(group('Right adductor longus'), 'adductors');
  // the neck's deep muscles are neck, the back's deep muscles are back
  assert.equal(group('Set of anterior cervical intertransversarii'), 'neck');
  assert.equal(group('Lateral lumbar intertransversarius'), 'deep-back');
  assert.equal(group('Left semispinalis capitis'), 'neck');
  assert.equal(group('Right semispinalis thoracis'), 'deep-back');
  assert.equal(group('Inferior oblique part of left longus colli'), 'neck');
  assert.equal(group('First lumbrical of right foot'), 'feet');
  assert.equal(group('Set of lumbricals of hand'), 'hands');
});

test('what the app does not need is dropped, with a reason', () => {
  for (const name of ['Left superior rectus', 'Right inferior oblique', 'Right levator palpebrae superioris', 'Vocalis', 'Posterior papillary muscle of ventricle',
    'Left pubococcygeus', 'External anal sphincter', 'Diaphragm', 'Innermost intercostal muscle', 'Tendon of left quadriceps femoris']) {
    assert.ok(classify(name).dropped, `${name} should be dropped`);
  }
  assert.equal(classify('External intercostal muscle').group, 'intercostals');
});

test('a muscle nobody planned for is reported, not lost', () => {
  assert.deepEqual(classify('Some new muscle'), { unknown: true });
});

test('the side comes from the name, then from where the part is', () => {
  assert.equal(sideOf('Left pectoralis minor', -0.1), 'L');
  assert.equal(sideOf('Right pectoralis minor', 0.1), 'R');
  assert.equal(sideOf('External intercostal muscle', 0.09), 'L');
  assert.equal(sideOf('external intercostal muscle', -0.09), 'R');
  assert.equal(sideOf('Linea alba', 0.001), 'C');
});

test('the outer-thigh band is a muscle group, and the misfiled muscles are claimed from the skeleton', () => {
  assert.equal(group('Right iliotibial tract'), 'glute-med');
  for (const name of ['Right tibialis anterior', 'Left fibularis brevis', 'Right subscapularis', 'Left levator scapulae', 'Right iliotibial tract']) {
    assert.match(name, MISFILED_MUSCLES);
  }
  assert.doesNotMatch('Right tibia', MISFILED_MUSCLES);
  assert.doesNotMatch('Right fibula', MISFILED_MUSCLES);
  assert.doesNotMatch('Left scapula', MISFILED_MUSCLES);
});

test('the skeleton keeps its bones and drops what is hidden or not wanted', () => {
  for (const name of ['compact bone tissue', 'trabecular bone tissue', 'Right anterior cruciate ligament', 'Articular disk of knee joint', 'Thyroid cartilage', 'Upper incisor tooth']) {
    assert.match(name, DROPPED_BONES, name);
  }
  for (const name of ['Left femur', 'Right first rib', 'Left first costal cartilage', 'Sacrum', 'Atlas', 'thoracic vertebra 3', 'fused sacrum', 'Intervertebral disk of axis', 'Left scapula', 'Right patella']) {
    assert.doesNotMatch(name, DROPPED_BONES, name);
  }
});

test('the female skeleton drops the male spine but keeps her own', () => {
  for (const name of ['First lumbar vertebra', 'Twelfth thoracic vertebra', 'Seventh cervical vertebra', 'Sacrum', 'Atlas', 'Axis', 'Intervertebral disk of fifth lumbar vertebra']) {
    assert.match(name, MALE_SPINE_IN_FEMALE, name);
  }
  for (const name of ['lumbar vertebra 1', 'thoracic vertebra 12', 'mammalian cervical vertebra 3', 'vertebral bone 1', 'fused sacrum', 'coccyx', 'Right hip bone', 'Body of sternum']) {
    assert.doesNotMatch(name, MALE_SPINE_IN_FEMALE, name);
  }
});

test('the muscles taken from Z-Anatomy are named so the grouping places them', () => {
  assert.equal(zanatomyName('ta2-2231.l'), 'Left latissimus dorsi');
  assert.equal(zanatomyName('ta2-2357.r'), 'Right rectus abdominis');
  assert.equal(zanatomyName('ta2-2231.m'), null);
  assert.equal(zanatomyName('ta2-9999.l'), null);
  assert.equal(zanatomyName('ta2-2231'), null);
  for (const [id, name] of Object.entries(ZANATOMY_MUSCLES)) {
    for (const side of ['l', 'r']) {
      const place = classify(zanatomyName(`ta2-${id}.${side}`));
      assert.ok(place.group, `${name} should be in a group`);
    }
  }
  // the six that BodyParts3D lacks each get a group of their own
  assert.equal(new Set(Object.values(ZANATOMY_MUSCLES).map((name) => classify(name).group)).size, 6);
});

test('only where the nipples are is used (to place the bust contour of the skin); nothing else of the breast', () => {
  assert.ok(isNipple('left nipple'));
  assert.ok(isNipple('right nipple'));
  for (const name of ['Interlobar adipose tissue of left mammary gland', 'mammary lobe', 'Left female areola', 'areolar tubercle', 'Suspensory ligament of left breast', 'main lactiferous duct', 'Left ovary', 'body of uterus']) {
    assert.ok(!isNipple(name), name);
  }
});

test('every group says in a sentence or two what it is', () => {
  for (const item of GROUPS) {
    assert.ok(ABOUT[item.id] && ABOUT[item.id].length > 40 && ABOUT[item.id].length < 400, `${item.id} needs a short description`);
  }
  assert.deepEqual(Object.keys(ABOUT).sort(), GROUPS.map((item) => item.id).sort());
});

test('every group has what the app needs to use it', () => {
  const ids = new Set();
  for (const item of GROUPS) {
    assert.ok(!ids.has(item.id), `duplicate id ${item.id}`);
    ids.add(item.id);
    assert.match(item.id, /^[a-z]+(-[a-z]+)*$/);
    assert.ok(item.label && item.libraryGroup && ['front', 'back', 'both'].includes(item.view) && ['surface', 'deep'].includes(item.layer));
    assert.ok(item.detail > 0 && item.detail <= 1);
  }
});
