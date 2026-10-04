package dev.romoslayer.elapsed.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {
	private static List<String> read(String text, ElapsedConfig into) throws Toml.ParseException {
		return ConfigBinder.read(Toml.parse(text), into);
	}

	@Test
	void hugeExponentIsReportedNotThrown() throws Exception {
		ElapsedConfig config = new ElapsedConfig();
		List<String> problems = read("[general]\nmaxCatchupHours = 1e309\n", config);
		assertEquals(24.0, config.general.maxCatchupHours);
		assertTrue(problems.stream().anyMatch(problem -> problem.contains("maxCatchupHours")), problems.toString());
	}

	@Test
	void integerTooBigForALongIsClampedToItsRange() throws Exception {
		ElapsedConfig config = new ElapsedConfig();
		List<String> problems = read("[performance]\nchunksPerTick = 99999999999999999999\n", config);
		assertEquals(1024, config.performance.chunksPerTick);
		assertEquals(1, problems.size(), problems.toString());
	}

	@Test
	void negativeAndOutOfRangeValuesAreClamped() throws Exception {
		ElapsedConfig config = new ElapsedConfig();
		read("[general]\nmaxCatchupHours = -5\n[chickens]\nmaxEggsPerCatchup = 1000\n", config);
		assertEquals(0.01, config.general.maxCatchupHours);
		assertEquals(64, config.chickens.maxEggsPerCatchup);
	}

	@Test
	void wrongKindOfValueKeepsTheDefault() throws Exception {
		ElapsedConfig config = new ElapsedConfig();
		List<String> problems = read("[general]\nenabled = \"no\"\n", config);
		assertTrue(config.general.enabled);
		assertEquals(1, problems.size());
	}

	@Test
	void stringsWithControlCharactersSurviveARoundTrip() throws Exception {
		String text = "line one\nline two\ttab \"quoted\" back\\slash \u0001";
		Map<List<String>, Map<String, Object>> parsed = Toml.parse("key = " + Toml.quote(text) + "\n");
		assertEquals(text, parsed.get(List.of()).get("key"));
	}

	@Test
	void tableNamesWithEscapesSurviveARoundTrip() throws Exception {
		ElapsedConfig config = new ElapsedConfig();
		ElapsedConfig.AgeBlock block = new ElapsedConfig.AgeBlock();
		block.growthChance = 0.25;
		config.ageBasedBlocks.put("mod:odd\"name", block);
		String written = ConfigBinder.write(config, "header");
		ElapsedConfig back = new ElapsedConfig();
		List<String> problems = ConfigBinder.read(Toml.parse(written), back);
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(0.25, back.ageBasedBlocks.get("mod:odd\"name").growthChance);
	}

	@Test
	void writtenDefaultsReadBackWithoutProblems() throws Exception {
		ElapsedConfig back = new ElapsedConfig();
		List<String> problems = ConfigBinder.read(Toml.parse(ConfigBinder.write(new ElapsedConfig(), "header\nline")), back);
		assertTrue(problems.isEmpty(), problems.toString());
	}

	@Test
	void aKeySetTwiceIsAnError() {
		assertThrows(Toml.ParseException.class, () -> Toml.parse("[general]\nenabled = true\nenabled = false\n"));
	}

	@Test
	void aBrokenFileDuringALiveReloadKeepsTheSettingsInUse(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("elapsed.toml");
		Files.writeString(file, "[general]\nenabled = false\n", StandardCharsets.UTF_8);
		ElapsedConfig.load(dir);
		assertFalse(ElapsedConfig.get().general.enabled);

		Files.writeString(file, "[general\nenabled = true\n", StandardCharsets.UTF_8);
		List<String> problems = ElapsedConfig.reload();
		assertFalse(problems.isEmpty());
		assertFalse(ElapsedConfig.get().general.enabled, "a typo must not switch catching up back on");
		assertEquals("[general\nenabled = true\n", Files.readString(file, StandardCharsets.UTF_8), "the broken file is left alone");
	}
}
