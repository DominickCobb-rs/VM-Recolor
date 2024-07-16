package com.VMRecolor;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("VMRecolor")
public interface VMRecolorConfig extends Config
{
	enum BoulderTypes
	{
		Default,
		Brightness,
		HueShift,
		Star
	}

	enum GlobalColor
	{
		Default,
		Brightness,
		HueShift
	}

	enum LavaOptions
	{
		Default,
		Brightness,
		HueShift,
		Hidden
	}

	enum PlatformOptions
	{
		Default,
		Brightness,
		HueShift,
		MatchLava
	}

	@ConfigSection(
		name = "Global",
		description = "Global color settings",
		position = 0,
		closedByDefault = false
	)
	String globalOpt = "globalOpt";

	@ConfigSection(
		name = "Options",
		description = "All the options for coloring",
		position = 1,
		closedByDefault = false
	)
	String options = "options";

	@ConfigSection(
		name = "Custom colors",
		description = "Global color settings",
		position = 2,
		closedByDefault = false
	)
	String color = "color";

	@ConfigItem(
		keyName = "syncColors",
		name = "Sync colors",
		description = "Sync custom colors when changing -- takes effect on your next color change. Reopen settings to see synced colors.",
		section = globalOpt
	)
	default boolean syncColors()
	{
		return true;
	}

	@ConfigItem(
		keyName = "Boulder",
		name = "Boulder",
		description = "Recolor the boulder",
		section = options
	)
	default BoulderTypes boulder()
	{
		return BoulderTypes.Default;
	}

	@ConfigItem(
		keyName = "lavaBeast",
		name = "Lava Beast",
		description = "Recolor lava beast",
		section = options
	)
	default GlobalColor lavaBeast()
	{
		return GlobalColor.Default;
	}

	@ConfigItem(
		keyName = "lava",
		name = "Lava",
		description = "Removes all objects with visible lava",
		section = options
	)
	default LavaOptions lava()
	{
		return LavaOptions.Default;
	}

	// Unsure. Think this needs to be in config per item as well.
	@ConfigItem(
		keyName = "Brightness",
		name = "Brightness",
		description = "The brightness percentage applied to lava facing visuals",
		section = globalOpt
	)
	@Range(
		max = 10000,
		min = 1
	)
	default int brightness()
	{
		return 100;
	}

	// Still kind of necessary? Maybe enum to match chosen color, recolor to match lava
	@ConfigItem(
		keyName = "whiteBrightness",
		name = "White brightness",
		description = "Change the brightness of white colors (-1 hides it completely)",
		section = globalOpt
	)
	@Range(
		max = 127,
		min = -1
	)
	default int whiteBrightness()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "wall",
		name = "Walls",
		description = "Wall colors",
		section = options
	)
	default GlobalColor wall()
	{
		return GlobalColor.Default;
	}

	@ConfigItem(
		keyName = "lowerLevelFloor",
		name = "Lower Floor",
		description = "Lower level floor option",
		section = options
	)
	default GlobalColor lowerLevelFloor()
	{
		return GlobalColor.Default;
	}

	@ConfigItem(
		keyName = "upperLevelFloor",
		name = "Upper Floor",
		description = "Upper level floor option",
		section = options
	)
	default GlobalColor upperLevelFloor()
	{
		return GlobalColor.Default;
	}

	@ConfigItem(
		keyName = "platform",
		name = "Platforms",
		description = "Platform option",
		section = options
	)
	default PlatformOptions platform()
	{
		return PlatformOptions.Default;
	}

	@ConfigItem(
		keyName = "wallCustomColor",
		name = "Walls",
		description = "Walls color",
		section = color
	)
	default Color wallColor()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "lowerLevelFloorCustomColor",
		name = "Lower Floor",
		description = "Lower level floor color",
		section = color
	)
	default Color lowerLevelFloorColor()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "upperLevelFloorCustomColor",
		name = "Upper Floor",
		description = "Upper level floor color",
		section = color
	)
	default Color upperLevelFloorColor()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "platformCustomColor",
		name = "Platforms",
		description = "Color for the platforms",
		section = color
	)
	default Color platformColor()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "BoulderCustomColor",
		name = "Boulder",
		description = "Color for the boulder",
		section = color
	)
	default Color boulderColor()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "lavaBeastCustomColor",
		name = "Lava Beast",
		description = "Color for the Lava Beast",
		section = color
	)
	default Color lavaBeastColor()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "lavaColor",
		name = "Lava",
		description = "Color for the lava",
		section = color
	)
	default Color lavaColor()
	{
		return Color.RED;
	}
}
