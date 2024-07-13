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
	@ConfigSection(
		name = "Color",
		description = "Color settings",
		position = 0,
		closedByDefault = false
	)
	String color = "color";

	@ConfigSection(
		name = "Options",
		description = "All the options for coloring",
		position = 1,
		closedByDefault = false
	)
	String options = "options";

	@ConfigItem(
		keyName = "Boulder",
		name = "Boulder",
		description = "Recolor the boulder",
		section = options
	)
	default boolean boulder()
	{
		return false;
	}

	@ConfigItem(
		keyName = "LavaBeastRecolor",
		name = "Lava Beast",
		description = "Recolor VM",
		section = options
	)
	default boolean lavaBeast()
	{
		return false;
	}

	@ConfigItem(
		keyName = "HideLava",
		name = "Hide All Lava",
		description = "Removes all objects with visible lava",
		section = options
	)
	default boolean hideLava()
	{
		return false;
	}

	@ConfigItem(
		keyName = "UseCustomColorScheme",
		name = "Custom Color Scheme",
		description = "Apply a custom color to the entirety of volcanic mine",
		section = color
	)
	default boolean customColor()
	{
		return false;
	}

	@ConfigItem(
		keyName = "Color",
		name = "Color",
		description = "The color to change the lava to",
		section = color
	)
	default Color color()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
		keyName = "Brightness",
		name = "Brightness",
		description = "The brightness percentage applied to lava facing visuals",
		section = color
	)
	@Range(
		max = 2,
		min = 0
	)
	default double brightness()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "onlyLava",
		name = "Only Recolor Lava",
		description = "Don't apply a custom color to the entire cave, just lava.",
		section = color
	)
	default boolean onlyLava()
	{
		return true;
	}
}
