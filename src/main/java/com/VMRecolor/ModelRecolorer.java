/*
	BSD 2-Clause License

	Copyright (c) 2024, denaelc

	Redistribution and use in source and binary forms, with or without
	modification, are permitted provided that the following conditions are met:

	1. Redistributions of source code must retain the above copyright notice, this
	   list of conditions and the following disclaimer.

	2. Redistributions in binary form must reproduce the above copyright notice,
	   this list of conditions and the following disclaimer in the documentation
	   and/or other materials provided with the distribution.

	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
	AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
	IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
	DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
	FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
	DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
	SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
	CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
	OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
	OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// A lot of code used from the gauntlet recolor plugin.
// This is an adaptation(abomination) of the previously created modelprocessor.
// The name was changed specifically because the it was easier for me to keep track of things as I went along.

package com.VMRecolor;

import static com.VMRecolor.VMRecolorPlugin.GRAPHICS_OBJECTS;
import static com.VMRecolor.VMRecolorPlugin.LAVA;
import static com.VMRecolor.VMRecolorPlugin.LAVA_BEAST;
import static com.VMRecolor.VMRecolorPlugin.LOWER_LEVEL_FLOOR;
import static com.VMRecolor.VMRecolorPlugin.LOWER_LEVEL_INTERACTABLES;
import static com.VMRecolor.VMRecolorPlugin.PLATFORMS;
import static com.VMRecolor.VMRecolorPlugin.UPPER_LEVEL_FLOOR;
import static com.VMRecolor.VMRecolorPlugin.WALL_OBJECTS;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Model;

@Slf4j
public class ModelRecolorer
{

	@Inject
	private VMRecolorConfig config;

	private Map<String, Map<Integer, int[][]>> originalColorData = new HashMap<>();
	private Map<String, Map<Integer, int[][]>> recoloredColorData = new HashMap<>();

	static final int DARK_PURPLE = 48;
	static final int PURPLE = 49;
	static final int YELLOW = 8;
	static final int ORANGE = 5;
	static final int GREEN = 28;

	// creates a hashmap with all the facecolors, IDs and types (gameObject, Groundobject etc.)
	// could be simplified if the .txt gets simplified
	public void cacheData(String filePath) throws IOException
	{
		try (InputStream inputStream = getClass().getResourceAsStream(filePath))
		{
			if (inputStream == null)
			{
				throw new FileNotFoundException("Resource not found: " + filePath);
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream)))
			{
				String line;
				String currentType = null;
				int currentId = -1;
				int[][] colors = new int[3][];

				while ((line = reader.readLine()) != null)
				{
					if (line.trim().isEmpty())
					{
						continue;
					}
					if (line.contains(" ID: "))
					{
						if (currentType != null && currentId != -1)
						{
							originalColorData.computeIfAbsent(currentType, k -> new HashMap<>()).put(currentId, colors);
						}
						currentType = line.split(" ")[0];
						currentId = Integer.parseInt(line.split(": ")[1].split(" ")[0]);
						colors = new int[3][];
					}
					else if (line.startsWith("FaceColors"))
					{
						int index = Integer.parseInt(line.substring(10, 11)) - 1;
						colors[index] = Arrays.stream(line.split(": ")[1].replace("[", "").replace("]", "").split(",")).mapToInt(Integer::parseInt).toArray();
					}
				}
				if (currentType != null && currentId != -1)
				{
					originalColorData.computeIfAbsent(currentType, k -> new HashMap<>()).put(currentId, colors);
				}
			}
		}
	}


	// This is great and all but it's not super convenient for replacing individual IDs which is this plugin's need
	// creates a second hashmap with the recolored values, based of the vanilla hashmap
	public void recolorData()
	{
		recoloredColorData.clear();
		originalColorData.forEach((type, models) -> {
			Map<Integer, int[][]> recoloredMap = new HashMap<>();
			models.forEach((id, colors) -> {
				int[][] recoloredColors = new int[colors.length][];
				for (int i = 0; i < colors.length; i++)
				{
					recoloredColors[i] = recolor(colors[i], id);
				}
				recoloredMap.put(id, recoloredColors);
			});
			recoloredColorData.put(type, recoloredMap);
		});
	}

	private boolean isBoulder(int id)
	{
		return VMRecolorPlugin.THE_BOULDER.contains(id) || VMRecolorPlugin.THE_BOULDER_NPCS.contains(id);
	}

	// recolors a single array of colors (e.g. facecolors1 of a single model)
	private int[] recolor(int[] originalColors, int id)
	{
		int[] newColors = new int[originalColors.length];
		for (int i = 0; i < originalColors.length; i++)
		{
			if (id == 0 || WALL_OBJECTS.contains(id))
			{
				newColors[i] = newColorHsbEnumHandler(originalColors[i], config.wallColor(), id, config.wall());
			}
			else if (isBoulder(id))
			{
				newColors[i] = newBoulderColorHsb(originalColors[i], config.boulderColor(), id, config.boulder());
			}
			else if (LOWER_LEVEL_FLOOR.contains(id) || GRAPHICS_OBJECTS.contains(id) || (config.lava()== VMRecolorConfig.LavaOptions.Hidden && (id==660 || id ==659))) // lava projectiles and splats
			{
				newColors[i] = newColorHsbEnumHandler(originalColors[i], config.lowerLevelFloorColor(), id, config.lowerLevelFloor());
			}
			else if (UPPER_LEVEL_FLOOR.contains(id))
			{
				newColors[i] = newColorHsbEnumHandler(originalColors[i], config.upperLevelFloorColor(), id, config.upperLevelFloor());
			}
			else if (PLATFORMS.contains(id))
			{
				newColors[i] = newColorPlatformHandler(originalColors[i], config.platformColor(), id, config.platform());
			}
			else if (id == LAVA_BEAST || id == 1403)
			{
				newColors[i] = newColorHsbEnumHandler(originalColors[i], config.lavaBeastColor(), id, config.lavaBeast());
			}
			else if (LAVA.contains(id))
			{
				newColors[i] = newLavaColorHsb(originalColors[i], id);
			}
			else
			{
				newColors[i] = originalColors[i];
			}
		}
		return newColors;
	}

	public int newBoulderColorHsb(int faceColor, Color newColor, int id, VMRecolorConfig.BoulderTypes boulderType)
	{
		if (faceColor == -1 || faceColor == 0)
		{
			return faceColor;
		}

		switch (boulderType)
		{
			case Brightness:
			{
				int hueFace = extractHsbValues(faceColor, 6, 11);
				int saturationFace = extractHsbValues(faceColor, 3, 8);
				int brightnessFace = extractHsbValues(faceColor, 7, 1);

				// Apply the brightness multiplier
				int newBrightness = (int) (brightnessFace * (config.brightness() / 100.0));
				// Ensure the brightness stays within the valid range (0-127)
				newBrightness = Math.max(1, Math.min(127, newBrightness));
				return (hueFace << 10) + (saturationFace << 7) + newBrightness;
			}

			case HueShift:
			{
				return hueShift(faceColor, config.boulderColor());
			}
			// I like the way stars look but hate mining them
			case Star:
			// No good way to recolor each stage of the boulder. It would involve going through the arrays individually and creating entirely new palletes.
			// If there's a way to edit rsmodels with their natural hsbs I would love to do this and just export as needed, but currently is not viable.
			// When it breaks, it reveals the model of the next boulder but without the
			// custom colors applied
			{
				int hue = getHue(faceColor);
				if (hue == YELLOW)
				{
					return hsbTors2(PURPLE, 1, 30);
				}
				if (hue == ORANGE)
				{
					if (getBrightness(faceColor) > 25)
					{
						return hsbTors2(DARK_PURPLE, 1, 20);
					}
					else
					{
						return hsbTors2(GREEN, getSaturation(faceColor), getBrightness(faceColor) / 3);
					}
				}
				// Reds
				if (hue == 1)
				{
					return hsbTors2(GREEN + 1, getSaturation(faceColor), getBrightness(faceColor) / 3);
				}
				if (hue == 2)
				{
					return hsbTors2(GREEN, getSaturation(faceColor), getBrightness(faceColor) / 3);
				}
				if (hue == 3)
				{
					return hsbTors2(GREEN - 1, getSaturation(faceColor), getBrightness(faceColor) / 3);
				}
				return faceColor;
			}
			default:
				return faceColor;
		}
	}

	private int hsbTors2(int h, int s, int b)
	{
		return (h << 10) + (s << 7) + b;
	}

	public int newLavaColorHsb(int faceColor, int id)
	{
		if (faceColor == 2)
		{
			return faceColor;
		}
		switch (config.lava())
		{
			case Default:
				return faceColor;

			case Brightness:
			{
				int hueFace = extractHsbValues(faceColor, 6, 11);
				int saturationFace = extractHsbValues(faceColor, 3, 8);
				int brightnessFace = extractHsbValues(faceColor, 7, 1);

				// Apply the brightness multiplier
				int newBrightness = (int) (brightnessFace * (config.brightness() / 100.0));
				// Ensure the brightness stays within the valid range (0-127)
				newBrightness = Math.max(1, Math.min(127, newBrightness));
				return (hueFace << 10) + (saturationFace << 7) + newBrightness;
			}

			case HueShift:
			{
				return lavaHueShift(faceColor, config.lavaColor(), faceColor, 1);
			}

			case Hidden:
			{
				if (id == VMRecolorPlugin.LAVA_BEAST)
				{
					return faceColor;
				}
				if (id == 31039)
				{
					return -1;
				}
				// Not even going to try dynamically finding them
				if ((faceColor > 5900 && faceColor < 6000) || faceColor > 9000 || isWhite(faceColor))
				{
					return -1;
				}
				return faceColor;
			}

			default:
				return -1; //how
		}
	}

	public int newColorPlatformHandler(int faceColor, Color newColor, int id, VMRecolorConfig.PlatformOptions globalColor)
	{
		if (faceColor == 2)
		{
			return faceColor;
		}
		switch (globalColor)
		{
			case Default:
				return faceColor;

			case Brightness:
			{
				int hueFace = extractHsbValues(faceColor, 6, 11);
				int saturationFace = extractHsbValues(faceColor, 3, 8);
				int brightnessFace = extractHsbValues(faceColor, 7, 1);

				// Apply the brightness multiplier
				int newBrightness = (int) (brightnessFace * (config.brightness() / 100.0));
				// Ensure the brightness stays within the valid range (0-127)
				newBrightness = Math.max(1, Math.min(127, newBrightness));
				return (hueFace << 10) + (saturationFace << 7) + newBrightness;
			}

			case HueShift:
			{
				return hueShift(faceColor, newColor);
			}

			case MatchLava:
			{
				if (config.lava() == VMRecolorConfig.LavaOptions.Hidden)
				{
					return faceColor;
				}
				return newLavaColorHsb(faceColor, id);
			}
			default:
				return -1; //how
		}
	}

	public int newColorHsbEnumHandler(int faceColor, Color newColor, int id, VMRecolorConfig.GlobalColor globalColor)
	{
		if (faceColor == 2)
		{
			return faceColor;
		}
		// Ignore lava beast projectiles, don't make anything on lava beasts invisible, and don't hide gas hole animations
		if ((faceColor > 9000 || isWhite(faceColor))
			&& !(id == 1403 || ((config.lava() == VMRecolorConfig.LavaOptions.Hidden) && id == LAVA_BEAST) || LOWER_LEVEL_INTERACTABLES.contains(id) || id == 1407))
		{
			return newLavaColorHsb(faceColor, id);
		}
		switch (globalColor)
		{
			case Default:
				return faceColor;

			case Brightness:
			{
				int hueFace = extractHsbValues(faceColor, 6, 11);
				int saturationFace = extractHsbValues(faceColor, 3, 8);
				int brightnessFace = extractHsbValues(faceColor, 7, 1);

				// Apply the brightness multiplier
				int newBrightness = (int) (brightnessFace * (config.brightness() / 100.0));
				// Ensure the brightness stays within the valid range (0-127)
				newBrightness = Math.max(1, Math.min(127, newBrightness));
				return (hueFace << 10) + (saturationFace << 7) + newBrightness;
			}

			case HueShift:
			{
				return hueShift(faceColor, newColor);
			}

			default:
				return -1; //how
		}
	}

	private boolean isWhite(int faceColor)
	{
		int faceHue = extractHsbValues(faceColor, 6, 11);
		int faceSat = extractHsbValues(faceColor, 3, 8);
		return faceHue == 0 && faceSat == 0;
	}

	public int lavaHueShift(int faceColor, Color newColor, int referenceColor, double aggression)
	{
		int newColorHsb = colorToRs2hsb(newColor);

		// values of the facecolor
		int saturationFace = extractHsbValues(faceColor, 3, 8);
		int brightnessFace = extractHsbValues(faceColor, 7, 1);
		// value of the new reference color
		int hueRef = extractHsbValues(newColorHsb, 6, 11);

		// This is a WIP
		int newBrightness;

		newBrightness = (int) (brightnessFace * ((config.brightness() / 100.0) / aggression));
		newBrightness = Math.max(1, Math.min(127, newBrightness));

		if (isWhite(faceColor))
		{
			if (config.whiteBrightness() != -1)
			{
				return (hueRef << 10) + (7 << 7) + config.whiteBrightness();
			}
			return -1;
		}

		return (hueRef << 10) + (saturationFace << 7) + newBrightness;
	}

	// applies the colors to a model
	public void applyColor(Model model, int[] f1, int[] f2, int[] f3)
	{
		int[] faceColors, faceColors2, faceColors3;
		try
		{
			faceColors = model.getFaceColors1();
			faceColors2 = model.getFaceColors2();
			faceColors3 = model.getFaceColors3();
		}
		catch (NullPointerException e)
		{
			return;
		}

		if (f1.length <= faceColors.length && f2.length <= faceColors2.length && f3.length <= faceColors3.length)
		{
			System.arraycopy(f1, 0, faceColors, 0, f1.length);
			System.arraycopy(f2, 0, faceColors2, 0, f2.length);
			System.arraycopy(f3, 0, faceColors3, 0, f3.length);
		}
		else
		{
			log.info("FaceColor has the wrong length.");
		}
	}

	// same concept as brightColors, but only shifts Hue
	public int hueShift(int faceColor, Color newColor)
	{
		int newColorHsb = colorToRs2hsb(newColor);

		// values of the facecolor
		int hueFace = extractHsbValues(faceColor, 6, 11);
		int saturationFace = extractHsbValues(faceColor, 3, 8);
		int brightnessFace = extractHsbValues(faceColor, 7, 1);
		// value of the new reference color
		int hueRef = extractHsbValues(newColorHsb, 6, 11);
		// value for the current reference color

		//int referenceHue = extractHsbValues(referenceColor, 6, 11);

		//int hueDiff = referenceHue - hueFace;

		//int newHue = hueRef - hueDiff;

		// newHue = (newHue % 64 + 64) % 64;

		return (hueRef << 10) + (saturationFace << 7) + brightnessFace;
	}


	static int getBrightness(int faceColor)
	{
		return (((1 << 7) - 1) & (faceColor));
	}

	static int getSaturation(int faceColor)
	{
		return (((1 << 3) - 1) & (faceColor >> (8 - 1)));
	}

	static int getHue(int faceColor)
	{
		return (((1 << 6) - 1) & (faceColor >> (11 - 1)));
	}

	// Returns the hsb values
	static int extractHsbValues(int hsbColor, int k, int p)
	{
		return (((1 << k) - 1) & (hsbColor >> (p - 1)));
	}

	// not my method, I don't know who to give credit for it, but I took it from AnkouOSRS, https://github.com/AnkouOSRS/cox-light-colors/blob/master/src/main/java/com/coxlightcolors/CoxLightColorsPlugin.java
	private int colorToRs2hsb(Color color)
	{
		float[] hsbVals = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
		// "Correct" the brightness level to avoid going to white at full saturation, or having a low brightness at
		// low saturation
		hsbVals[2] -= Math.min(hsbVals[1], hsbVals[2] / 2);
		int encode_hue = (int) (hsbVals[0] * 63);
		int encode_saturation = (int) (hsbVals[1] * 7);
		int encode_brightness = (int) (hsbVals[2] * 127);
		return (encode_hue << 10) + (encode_saturation << 7) + (encode_brightness);
	}

	// applies either the vanilla or the recolored hashmap data to a given model
	public void applyColors(int objectId, String type, Model model, boolean useRecolored)
	{
		Map<Integer, int[][]> data = useRecolored ? recoloredColorData.getOrDefault(type, Collections.emptyMap()) : originalColorData.getOrDefault(type, Collections.emptyMap());
		int[][] colors = data.get(objectId);
		if (colors != null && colors[0] != null && colors[1] != null && colors[2] != null)
		{
			applyColor(model, colors[0], colors[1], colors[2]);
		}
	}

	// Calls to this method are so computationally heavy they result in significant client hitching
	// this is alleviated by not calling them synchronously (should only be a problem if a user changes config
	// when rendering?) This is noteable when crossing any loading line (if called synchronously).
	public void applyColorsDirectly(Model model, int id)
	{
		int[] f1 = model.getFaceColors1();
		int[] f2 = model.getFaceColors2();
		int[] f3 = model.getFaceColors3();

		if (f1 != null && f2 != null && f3 != null)
		{
			applyColor(model, recolor(f1, id), recolor(f2, id), recolor(f3, id));
		}
	}

	// deletes the hashmaps
	public void cleanUp()
	{
		originalColorData.clear();
		recoloredColorData.clear();
	}
}