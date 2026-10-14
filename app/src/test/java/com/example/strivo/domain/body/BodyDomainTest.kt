package com.example.strivo.domain.body

import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/** The models and catalogue the app ships: models/anatomy at the repository root (the tests run in the app folder). */
private fun asset(name: String): File = File("../models/anatomy/$name").also { check(it.isFile) { "Run from the app module: ${it.absolutePath} not found" } }

private val catalogue: MuscleCatalogue by lazy { MuscleCatalogue.fromJson(asset("muscles.json").readText())!! }

class MuscleCatalogueTest {
    @Test
    fun theCatalogueIsReadWithEveryMuscleAndBothBodies() {
        assertEquals(34, catalogue.groups.size)
        val lats = catalogue.group("lats")!!
        assertEquals("Lats", lats.label)
        assertEquals("Back", lats.libraryGroup)
        assertEquals(MuscleView.Back, lats.view)
        assertEquals(MuscleLayer.Surface, lats.layer)
        assertTrue(lats.about.length > 40)
        assertEquals(MuscleLayer.Deep, catalogue.group("multifidus")!!.layer)
        for (body in BodyKey.entries) {
            val info = catalogue.body(body)!!
            assertEquals(body.file, info.file)
            assertTrue("${body.key} is about as tall as a person", info.height in 1.5f..2f)
            for (group in catalogue.groups) {
                val place = group.placeOn(body)
                assertNotNull("${group.id} is on the ${body.key} body", place)
                assertTrue(place!!.box.max.y > place.box.min.y)
            }
        }
    }

    @Test
    fun aBadCatalogueIsNothingRatherThanBroken() {
        assertNull(MuscleCatalogue.fromJson("not json"))
        assertNull(MuscleCatalogue.fromJson("{}"))
        assertNull(MuscleCatalogue.fromJson("""{"bodies":{},"groups":[]}"""))
    }

    @Test
    fun aMuscleHasALabelEvenWhenTheCatalogueDoesNotKnowIt() {
        assertEquals("Front delts", catalogue.labelOf("delt-front"))
        assertEquals("Some new muscle", catalogue.labelOf("some-new-muscle"))
    }

    @Test
    fun theBodyShownIsTheAccountsGender() {
        assertEquals(BodyKey.Female, BodyKey.forGender("Female"))
        assertEquals(BodyKey.Female, BodyKey.forGender(" female "))
        assertEquals(BodyKey.Male, BodyKey.forGender("Male"))
        assertEquals(BodyKey.Male, BodyKey.forGender(null))
        assertEquals(BodyKey.Male, BodyKey.forGender("Other"))
        assertEquals(BodyKey.Male, BodyKey.forGender(""))
    }
}

class GlbReaderTest {
    private fun model(body: BodyKey) = GlbReader.read(asset(body.file).readBytes())!!

    @Test
    fun bothBodiesAreReadWithAMuscleForEveryGroupASkeletonAndASkin() {
        for (body in BodyKey.entries) {
            val parts = model(body).parts
            assertTrue("${body.key} has about 200,000 triangles", parts.sumOf { it.triangleCount } > 150_000)
            assertEquals(1, parts.count { it.kind == PartKind.Skeleton })
            assertEquals(1, parts.count { it.kind == PartKind.Skin })
            val muscles = parts.filter { it.kind == PartKind.Muscle }
            for (group in catalogue.groups) {
                assertTrue("${group.id} is in the ${body.key} model", muscles.any { it.group == group.id })
            }
            // every piece of every muscle is in the catalogue, and carries its layer
            assertTrue(muscles.all { catalogue.group(it.group) != null })
            assertTrue(muscles.all { catalogue.group(it.group)!!.layer == it.layer })
            assertTrue(muscles.all { it.side in setOf("L", "R", "C") })
        }
    }

    @Test
    fun everyPiecesNumbersAreSound() {
        for (body in BodyKey.entries) for (part in model(body).parts) {
            assertEquals(part.name, part.positions.size, part.normals.size)
            assertEquals(part.name, 0, part.indices.size % 3)
            assertTrue(part.name, part.indices.all { it in 0 until part.vertexCount })
            assertTrue(part.name, part.positions.all { !it.isNaN() && abs(it) < 10f })
            // the normals are unit length (to within the rounding of the file)
            for (v in 0 until minOf(part.vertexCount, 50)) {
                val length = kotlin.math.sqrt(part.normals[v * 3] * part.normals[v * 3] + part.normals[v * 3 + 1] * part.normals[v * 3 + 1] + part.normals[v * 3 + 2] * part.normals[v * 3 + 2])
                assertEquals(part.name, 1f, length, 0.02f)
            }
        }
    }

    @Test
    fun theSkinWrapsTheMusclesAndTheBodyIsAPersonTall() {
        for (body in BodyKey.entries) {
            val parts = model(body).parts
            val skin = parts.single { it.kind == PartKind.Skin }
            fun top(p: GlbPart) = (0 until p.vertexCount).maxOf { p.positions[it * 3 + 1] }
            fun bottom(p: GlbPart) = (0 until p.vertexCount).minOf { p.positions[it * 3 + 1] }
            assertEquals("${body.key} skin is a person tall", 1.7f, top(skin) - bottom(skin), 0.2f)
            val muscles = parts.filter { it.kind == PartKind.Muscle }
            assertTrue("the skin reaches at least as high as the muscles", top(skin) >= muscles.maxOf { top(it) } - 0.02f)
            assertTrue("and as low", bottom(skin) <= muscles.minOf { bottom(it) } + 0.02f)
        }
    }

    @Test
    fun theFemaleBodyDiffersFromTheMaleOne() {
        val male = model(BodyKey.Male).parts.single { it.kind == PartKind.Skin }
        val female = model(BodyKey.Female).parts.single { it.kind == PartKind.Skin }
        fun width(p: GlbPart) = (0 until p.vertexCount).let { v -> v.maxOf { p.positions[it * 3] } - v.minOf { p.positions[it * 3] } }
        assertFalse("the two skins are different shapes", male.positions.contentEquals(female.positions))
        assertTrue(width(male) > 0.5f && width(female) > 0.5f)
    }

    @Test
    fun aFileThatIsNotABodyIsNothing() {
        assertNull(GlbReader.read(ByteArray(0)))
        assertNull(GlbReader.read(ByteArray(100)))
        assertNull(GlbReader.read("not a model at all, just some text of a decent length".toByteArray()))
        val truncated = asset("male.glb").readBytes().copyOf(5_000)
        assertNull(GlbReader.read(truncated))
    }
}

class BodyCameraTest {
    private val body = catalogue.body(BodyKey.Male)!!.box
    private val box = Box(Vec3(0.15f, 1.15f, -0.04f), Vec3(0.29f, 1.4f, 0.04f)) // the biceps, about

    private fun distanceOf(spot: CameraSpot) = spot.distance

    @Test
    fun theCameraLooksAtTheMiddleOfAMuscleFromTheSideItIsBestSeenFrom() {
        val front = BodyCamera.cameraFor(box, MuscleView.Front, aspect = 0.5f)
        assertEquals(0.22f, front.target.x, 1e-4f)
        assertEquals(1.275f, front.target.y, 1e-4f)
        assertTrue(front.position.z > front.target.z)
        assertTrue(BodyCamera.cameraFor(box, MuscleView.Back, aspect = 0.5f).let { it.position.z < it.target.z })
        val side = BodyCamera.cameraFor(box, MuscleView.Both, aspect = 0.5f)
        assertTrue(abs(side.position.x - side.target.x) > abs(side.position.z - side.target.z))
    }

    @Test
    fun theCameraStandsBackForABiggerMuscleButNeverCloserThan45Cm() {
        val thigh = Box(Vec3(0.02f, 0.45f, -0.07f), Vec3(0.2f, 0.95f, 0.09f))
        val tiny = Box(Vec3(0f, 1f, 0f), Vec3(0.01f, 1.01f, 0.01f))
        assertTrue(distanceOf(BodyCamera.cameraFor(thigh, MuscleView.Front, aspect = 0.5f)) > distanceOf(BodyCamera.cameraFor(box, MuscleView.Front, aspect = 0.5f)))
        assertEquals(0.45f, distanceOf(BodyCamera.cameraFor(tiny, MuscleView.Front, aspect = 0.5f)), 1e-4f)
        // a narrower view (a phone held upright) needs the camera further back for the same muscle
        assertTrue(distanceOf(BodyCamera.cameraFor(box, MuscleView.Front, aspect = 0.45f)) > distanceOf(BodyCamera.cameraFor(box, MuscleView.Front, aspect = 2f)))
    }

    @Test
    fun theWholeBodyFitsTheViewHoweverWideOrNarrow() {
        for (key in BodyKey.entries) {
            val size = catalogue.body(key)!!.box
            for (aspect in listOf(0.4f, 0.5f, 0.9f, 1.6f)) {
                val spot = BodyCamera.bodyCamera(size.let { it }, front = true, aspect = aspect)
                val seenHeight = 2f * spot.distance * kotlin.math.tan(BodyCamera.FOV_DEGREES * Math.PI.toFloat() / 360f)
                assertTrue("${key.key} height at aspect $aspect", size.size.y < seenHeight)
                assertTrue("${key.key} width at aspect $aspect", size.size.x < seenHeight * aspect)
            }
        }
        assertTrue(BodyCamera.bodyCamera(body, front = true, aspect = 0.5f).position.z > 1.5f)
        assertTrue(BodyCamera.bodyCamera(body, front = false, aspect = 0.5f).position.z < -1.5f)
    }

    @Test
    fun aFlightStartsWhereItIsAndEndsWhereItIsGoingAndGoesRoundTheBody() {
        val front = BodyCamera.bodyCamera(body, front = true, aspect = 0.5f)
        val lats = BodyCamera.cameraFor(catalogue.group("lats")!!.male!!.box, MuscleView.Back, aspect = 0.5f)
        val start = BodyCamera.blend(front, lats, 0f)
        val end = BodyCamera.blend(front, lats, 1f)
        assertEquals(front.position.x, start.position.x, 1e-3f)
        assertEquals(front.position.z, start.position.z, 1e-3f)
        assertEquals(lats.position.x, end.position.x, 1e-3f)
        assertEquals(lats.position.z, end.position.z, 1e-3f)
        for (i in 0..20) {
            val spot = BodyCamera.blend(front, lats, i / 20f)
            assertTrue("never through the body at step $i", hypot(spot.position.x, spot.position.z) > 0.6f)
        }
    }

    @Test
    fun turningKeepsTheDistanceAndNeverGoesOverThePoles() {
        val start = BodyCamera.bodyCamera(body, front = true, aspect = 0.5f)
        val turned = BodyCamera.orbit(start, 1.2f, 0.3f)
        assertEquals(start.distance, turned.distance, 1e-3f)
        assertEquals(start.target, turned.target)
        assertTrue(abs(turned.position.x - start.position.x) > 0.5f)
        val high = BodyCamera.orbit(start, 0f, 5f)
        assertTrue(high.position.y - high.target.y < high.distance) // just short of straight up
        assertEquals(start.distance, high.distance, 1e-3f)
        val low = BodyCamera.orbit(start, 0f, -5f)
        assertTrue(low.position.y < low.target.y)
    }

    @Test
    fun zoomingStaysWithinTheLimitsAndPanningMovesTheViewWithoutTurningIt() {
        val start = BodyCamera.bodyCamera(body, front = true, aspect = 0.5f)
        assertEquals(BodyCamera.MIN_DISTANCE, BodyCamera.dolly(start, 0.0001f).distance, 1e-4f)
        assertEquals(BodyCamera.MAX_DISTANCE, BodyCamera.dolly(start, 1000f).distance, 1e-4f)
        assertEquals(start.distance * 0.5f, BodyCamera.dolly(start, 0.5f).distance, 1e-3f)
        val panned = BodyCamera.pan(start, 0f, 0.1f)
        assertEquals(start.distance, panned.distance, 1e-3f)
        assertTrue(panned.target.y > start.target.y)
        assertEquals(panned.target.y - start.target.y, panned.position.y - start.position.y, 1e-4f) // both moved together
        assertEquals(start, BodyCamera.pan(start, 0f, 50f)) // not off the body
    }
}

class MuscleLookTest {
    private val none = DrawState()

    @Test
    fun withNothingPickedAndNothingTrainedEverythingIsSolid() {
        for (layer in MuscleLayer.entries) assertEquals(Look(1f, 0f, 0f, true), MuscleLook.lookOf("lats", layer, none))
    }

    @Test
    fun theTrainedMusclesGlowTheMainOnesMoreAndTheRestAreDarker() {
        val trained = DrawState(trained = TrainedMuscles(setOf("delt-side"), setOf("triceps")))
        val main = MuscleLook.lookOf("delt-side", MuscleLayer.Surface, trained)
        val helper = MuscleLook.lookOf("triceps", MuscleLayer.Surface, trained)
        val other = MuscleLook.lookOf("calves", MuscleLayer.Surface, trained)
        assertTrue(main.glow > helper.glow && helper.glow > 0f)
        assertEquals(0f, other.glow)
        assertTrue(other.dim > 0f && main.dim == 0f && helper.dim == 0f)
        assertTrue(main.opacity == 1f && other.opacity == 1f)
    }

    @Test
    fun aPickedMuscleGlowsAndTheOthersStepBack() {
        val picked = DrawState(selected = "lats", trained = TrainedMuscles(setOf("chest"), emptySet()))
        val lats = MuscleLook.lookOf("lats", MuscleLayer.Surface, picked)
        assertEquals(1f, lats.opacity)
        assertEquals(MuscleLook.GLOW_SELECTED, lats.glow)
        val chest = MuscleLook.lookOf("chest", MuscleLayer.Surface, picked)
        assertTrue(chest.opacity < 0.5f)
        assertEquals(0f, chest.glow) // the trained glow gives way to the picked muscle
        assertTrue(MuscleLook.lookOf("multifidus", MuscleLayer.Deep, picked).opacity < 0.5f)
    }

    @Test
    fun fadingTheSurfaceShowsTheDeepMusclesAndStopsTapsLandingOnWhatIsFaded() {
        val faded = DrawState(fade = true)
        assertTrue(MuscleLook.lookOf("lats", MuscleLayer.Surface, faded).opacity < 0.3f)
        assertFalse(MuscleLook.lookOf("lats", MuscleLayer.Surface, faded).pickable)
        assertEquals(Look(1f, 0f, 0f, true), MuscleLook.lookOf("multifidus", MuscleLayer.Deep, faded))
        val pickedDeep = DrawState(selected = "multifidus", fade = true)
        assertTrue(MuscleLook.lookOf("lats", MuscleLayer.Surface, pickedDeep).opacity < MuscleLook.lookOf("lats", MuscleLayer.Surface, faded).opacity)
        assertTrue(MuscleLook.isPickable(MuscleLayer.Surface, false))
        assertFalse(MuscleLook.isPickable(MuscleLayer.Surface, true))
        assertTrue(MuscleLook.isPickable(MuscleLayer.Deep, true))
    }

    @Test
    fun eachMuscleHasItsOwnSmallSteadyDifferenceInLightness() {
        assertEquals(MuscleLook.lightnessShift("lats"), MuscleLook.lightnessShift("lats"), 0f)
        val shifts = catalogue.groups.map { MuscleLook.lightnessShift(it.id) }
        assertTrue(shifts.all { it in -0.06f..0.06f })
        assertTrue(shifts.toSet().size > 5)
    }
}

class TrainedMusclesFinderTest {
    private fun exercise(name: String) = Exercise(planId = 1, name = name, weight = "0.0", sets = "3", reps = "10", notes = "")
    private fun lib(name: String, group: String, primary: List<String> = emptyList(), secondary: List<String> = emptyList()) =
        LibraryExercise(name.lowercase().replace(' ', '-'), name, group, "", "", "", "", 3, 10, 0.0, "", true, 0, primary, secondary)

    private val library = listOf(
        lib("Pull-Up", "Back", listOf("lats"), listOf("biceps", "mid-back")),
        lib("Lateral Raise", "Shoulders", listOf("delt-side")),
        lib("Overhead Press", "Shoulders", listOf("delt-front", "delt-side"), listOf("triceps")),
        lib("Mystery Back Move", "Back"), // no muscles set on it
    )

    @Test
    fun aDaysMusclesComeFromTheLibraryByExerciseName() {
        val trained = TrainedMusclesFinder.find(listOf(exercise("Lateral Raise"), exercise("overhead press"), exercise("PULL UP")), library, catalogue)
        assertEquals(setOf("delt-side", "delt-front", "lats"), trained.main)
        assertEquals(setOf("triceps", "biceps", "mid-back"), trained.helper)
    }

    @Test
    fun aMuscleThatIsMainInOneExerciseAndAHelperInAnotherIsMain() {
        val trained = TrainedMusclesFinder.find(listOf(exercise("Overhead Press"), exercise("Lateral Raise")), library + lib("Dip", "Triceps", listOf("triceps")), catalogue)
        assertEquals(setOf("delt-front", "delt-side"), trained.main)
        val both = TrainedMusclesFinder.find(listOf(exercise("Overhead Press"), exercise("Dip")), library + lib("Dip", "Triceps", listOf("triceps")), catalogue)
        assertTrue("triceps" in both.main && "triceps" !in both.helper)
    }

    @Test
    fun anExerciseKnownOnlyByItsBroadGroupLightsThatGroupsSurfaceMusclesAsHelpers() {
        val trained = TrainedMusclesFinder.find(listOf(exercise("Mystery Back Move")), library, catalogue)
        assertTrue(trained.main.isEmpty())
        assertTrue("lats" in trained.helper && "traps" in trained.helper)
        assertTrue("multifidus" !in trained.helper) // a deep muscle is not guessed at
        assertTrue(trained.helper.all { catalogue.group(it)!!.libraryGroup == "Back" })
    }

    @Test
    fun anExerciseTheLibraryDoesNotKnowSaysNothing() {
        assertTrue(TrainedMusclesFinder.find(listOf(exercise("My own made-up move")), library, catalogue).isEmpty)
        assertTrue(TrainedMusclesFinder.find(emptyList(), library, catalogue).isEmpty)
        assertTrue(TrainedMusclesFinder.find(listOf(exercise("Pull-Up")), emptyList(), catalogue).isEmpty)
    }

    @Test
    fun theLibraryEntryThatSaysWhichMusclesItTrainsWinsOverOneWithTheSameNameThatDoesNot() {
        val twins = listOf(lib("Row", "Back"), lib("row", "Back", listOf("lats")))
        assertEquals(setOf("lats"), TrainedMusclesFinder.find(listOf(exercise("Row")), twins, catalogue).main)
    }

    @Test
    fun theExercisesThatTrainAMuscleAreListedTheMainOnesFirst() {
        val day = listOf(exercise("Lateral Raise"), exercise("Overhead Press"), exercise("Pull-Up"), exercise("Unknown"))
        val uses = TrainedMusclesFinder.usesOf("delt-side", day, library, catalogue)
        assertEquals(listOf("Lateral Raise", "Overhead Press"), uses.map { it.exercise.name })
        assertTrue(uses.all { it.role == MuscleRole.Main })
        val triceps = TrainedMusclesFinder.usesOf("triceps", day, library, catalogue)
        assertEquals(listOf("Overhead Press"), triceps.map { it.exercise.name })
        assertEquals(MuscleRole.Helper, triceps.single().role)
        val mixed = TrainedMusclesFinder.usesOf("biceps", listOf(exercise("Pull-Up")), library + lib("Curl", "Biceps", listOf("biceps")), catalogue)
        assertEquals(MuscleRole.Helper, mixed.single().role)
        val first = TrainedMusclesFinder.usesOf("biceps", listOf(exercise("Pull-Up"), exercise("Curl")), library + lib("Curl", "Biceps", listOf("biceps")), catalogue)
        assertEquals(listOf("Curl", "Pull-Up"), first.map { it.exercise.name })
        assertTrue(TrainedMusclesFinder.usesOf("calves", day, library, catalogue).isEmpty())
    }
}
