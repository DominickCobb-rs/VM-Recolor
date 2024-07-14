package com.VMRecolor;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Model;


@Slf4j
public class ModelRecolorer
{
	private Map<String, Map<Integer, int[][]>> originalColorData = new HashMap<>();
	private Map<String, Map<Integer, int[][]>> recoloredColorData = new HashMap<>();

	private static final List<Integer> GREEN_OBJECTS = Arrays.asList(35966, 35969, 35970, 35975, 35976, 35978, 35979, 36003, 36004, 36005, 36006, 36007, 36008);
	private static final int greenReference = 10758;
	private static final int redReference = 65452;
	private static Color currentColor;
	private static double brightness = 1.0;
	private static boolean onlyLava = false;
	private static boolean hideLava = false;
	public static int hideThisColor = 65530;
	private static int whiteBrightness = 100;

	public ModelRecolorer(String filePath, Color newColor, double brightness, boolean onlyLava, boolean hideLava, int whiteBrightness) throws IOException
	{
		cacheData(filePath);
		recolorData(newColor);
		this.currentColor = newColor;
		this.brightness = brightness;
		this.onlyLava = onlyLava;
		this.hideLava = hideLava;
		this.whiteBrightness = whiteBrightness;
	}

	// creates a hashmap with all the facecolors, IDs and types (gameObject, Groundobject etc.)
	// could be simplified if the .txt gets simplified
	private void cacheData(String filePath) throws IOException
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


	// creates a second hashmap with the recolored values, based of the vanilla hashmap
	public void recolorData(Color newColor)
	{
		currentColor = newColor;
		recoloredColorData.clear();
		originalColorData.forEach((type, models) -> {
			Map<Integer, int[][]> recoloredMap = new HashMap<>();
			models.forEach((id, colors) -> {
				int[][] recoloredColors = new int[colors.length][];
				for (int i = 0; i < colors.length; i++)
				{
					if (VMRecolorPlugin.LAVA_OBJECTS.contains(id) || VMRecolorPlugin.PATH_LAVA == id || id == 7817)
					{
						recoloredColors[i] = recolorLava(colors[i], newColor, newColor, id);
					}
					else if (currentColor != null && (VMRecolorPlugin.THE_BOULDER.contains(id) || VMRecolorPlugin.THE_BOULDER_NPCS.contains(id))) // weird transparency effects we need to preserve
					{
						recoloredColors[i] = recolorBoulder(colors[i], newColor, newColor, id);
					}
					else if (currentColor != null)
					{
						recoloredColors[i] = recolor(colors[i], newColor, newColor, id);
					}
					else
					{
						recoloredColors[i] = colors[i];
					}
				}
				recoloredMap.put(id, recoloredColors);
			});
			recoloredColorData.put(type, recoloredMap);
		});
	}

	// recolors a single array of colors (e.g. facecolors1 of a single model)
	private int[] recolor(int[] originalColors, Color newColor, Color secondaryColor, int id)
	{
		int[] newColors = new int[originalColors.length];
		for (int i = 0; i < originalColors.length; i++)
		{
			newColors[i] = newColorHsb(originalColors[i], newColor, secondaryColor, id);
		}
		return newColors;
	}

	private int[] recolorLava(int[] originalColors, Color newColor, Color secondaryColor, int id)
	{
		int[] newColors = new int[originalColors.length];
		for (int i = 0; i < originalColors.length; i++)
		{
			newColors[i] = newLavaColorHsb(originalColors[i], newColor, secondaryColor, id);
		}
		return newColors;
	}

	private int[] recolorBoulder(int[] originalColors, Color newColor, Color secondaryColor, int id)
	{
		int[] newColors = new int[originalColors.length];
		for (int i = 0; i < originalColors.length; i++)
		{
			newColors[i] = newBoulderColorHsb(originalColors[i], newColor, secondaryColor, id);
		}
		return newColors;
	}

	public int newBoulderColorHsb(int faceColor, Color newColor, Color secondaryColor, int id)
	{
		if (faceColor == -1 || faceColor == 0)
		{
			return faceColor;
		}
		if (faceColor <= 1000)
		{
			return lavaHueShift(faceColor, newColor, faceColor, .5);
		}
		return lavaHueShift(faceColor, newColor, faceColor, 1);    // if the referenceColor equals the faceColor, the Hue of the newColor will be applied
	}

	public int newLavaColorHsb(int faceColor, Color newColor, Color secondaryColor, int id)
	{
		if (onlyLava && id != 31039) //whirlpool
		{
			int newHue = extractHsbValues(faceColor, 6, 11);
			int defaultLavaHue = extractHsbValues(9139, 6, 11);
			if (extractHsbValues(faceColor, 3, 8) > 2 && extractHsbValues(faceColor, 7, 1) < 30)
			{
				if (defaultLavaHue - newHue > 2)
				{
					return faceColor;
				}
			}
		}

		if (hideLava && id != VMRecolorPlugin.LAVA_BEAST)
		{
			if (id == 31039)
			{
				return -1;
			}
			// Not even going to try dynamically finding them
			if (faceColor >= 5964 || isWhite(faceColor))
			{
				return -1;
			}
		}

		if (faceColor == 2)
		{
			return faceColor;
		}
		if (faceColor <= 1000)
		{
			return lavaHueShift(faceColor, newColor, faceColor, .5);
		}
		return lavaHueShift(faceColor, newColor, faceColor, 1);    // if the referenceColor equals the faceColor, the Hue of the newColor will be applied
	}

	private boolean isWhite(int faceColor)
	{
		int faceHue = extractHsbValues(faceColor, 6, 11);
		int faceSat = extractHsbValues(faceColor, 3, 8);
		int faceBri = extractHsbValues(faceColor, 7, 1);
		return faceHue == 0 && faceSat == 0;
	}

	public int lavaHueShift(int faceColor, Color newColor, int referenceColor, double aggression)
	{
		// If people want the default VM look but not so bright
		if (newColor == null)
		{
			int hueFace = extractHsbValues(faceColor, 6, 11);
			int saturationFace = extractHsbValues(faceColor, 3, 8);
			int brightnessFace = extractHsbValues(faceColor, 7, 1);

			// Apply the brightness multiplier
			int newBrightness = (int) (brightnessFace * (brightness / aggression));
			// Ensure the brightness stays within the valid range (0-127)
			newBrightness = Math.max(1, Math.min(127, newBrightness));
			return (hueFace << 10) + (saturationFace << 7) + newBrightness;
		}

		int newColorHsb = colorToRs2hsb(newColor);

		// values of the facecolor
		int hueFace = extractHsbValues(faceColor, 6, 11);
		int saturationFace = extractHsbValues(faceColor, 3, 8);
		int brightnessFace = extractHsbValues(faceColor, 7, 1);
		// value of the new reference color
		int hueRef = extractHsbValues(newColorHsb, 6, 11);
		// value for the current reference color
		int referenceHue = extractHsbValues(referenceColor, 6, 11);

		int hueDiff = referenceHue - hueFace;
		int newHue = hueRef - hueDiff;
		// Pure white colors are behaving oddly
		int newBrightness;

		newBrightness = (int) (brightnessFace * ((brightness/100.0)/ aggression));
		newBrightness = Math.max(1, Math.min(127, newBrightness));

		if (isWhite(faceColor))
		{
			if (whiteBrightness!=-1)
				return (hueRef << 10) + (7 << 7) + whiteBrightness;
			return -1;
		}

		return (newHue << 10) + (saturationFace << 7) + newBrightness;
	}

	// applies the colors to a model
	public void applyColor(Model model, int[] f1, int[] f2, int[] f3)
	{
		// The boulder models for some reason null :)
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
			log.debug("FaceColor has the wrong length.");
		}
	}

	// returns the new color in the rs2hsb format
	public int newColorHsb(int faceColor, Color newColor, Color secondaryColor, int id)
	{
		if (onlyLava)
		{
			return faceColor;
		}
		// > 60k are mostly the very bright colors.
		if (faceColor > 60000)
		{
			if (!secondaryColor.equals(newColor))
			{
				return brightColors(faceColor, secondaryColor);
			}
			return brightColors(faceColor, newColor);
		}

		return hueShift(faceColor, newColor, faceColor);
	}

	// Method is functional, but has a lot of variables. Will likely be adressed in a future iteration.
	//
	// General Idea: calculate the distance of the vanilla facecolor to a reference color (65452) and then apply that distance
	// to the new (reference) color, to get a similar shifted color.
	public int brightColors(int faceColor, Color newColor)
	{
		int newColorHsb = colorToRs2hsb(newColor);

		// values of the facecolor
		int hueFace = extractHsbValues(faceColor, 6, 11);
		int saturationFace = extractHsbValues(faceColor, 3, 8);
		int brightnessFace = extractHsbValues(faceColor, 7, 1);
		// values of the new reference color
		int hueRef = extractHsbValues(newColorHsb, 6, 11);
		int saturationRef = extractHsbValues(newColorHsb, 3, 8);
		int brightnessRef = extractHsbValues(newColorHsb, 7, 1);
		// pre-calculated values for the current reference color (65452)
		int referenceHue = 63;
		int referenceSat = 7;
		int referenceBright = 44;

		int hueDiff = referenceHue - hueFace;
		int satDiff = referenceSat - saturationFace;
		int brightDiff = referenceBright - brightnessFace;

		int newHue = hueRef - hueDiff;
		newHue = (newHue % 64 + 64) % 64;

		int newSat = saturationRef - satDiff;
		newSat = (newSat % 8 + 8) % 8;

		int newBright = brightnessRef - brightDiff / 4;     // reducing the brightness difference before applying it, to prevent complete white/black results
		newBright -= Math.min(newSat, newBright / 2);
		// making sure that the new brightness is never below 0 or above 127
		if (newBright < 0)
		{
			newBright = 0;
		}
		if (newBright > 127)
		{
			newBright = 127;
		}

		return (newHue << 10) + (newSat << 7) + newBright;
	}

	// same concept as brightColors, but only shifts Hue
	public int hueShift(int faceColor, Color newColor, int referenceColor)
	{
		if (newColor == null) // No idea how this is getting here but really can't be bothered to work it out
		{
			return faceColor;
		}

		int newColorHsb = colorToRs2hsb(newColor);

		// values of the facecolor
		int hueFace = extractHsbValues(faceColor, 6, 11);
		int saturationFace = extractHsbValues(faceColor, 3, 8);
		int brightnessFace = extractHsbValues(faceColor, 7, 1);
		// value of the new reference color
		int hueRef = extractHsbValues(newColorHsb, 6, 11);
		// value for the current reference color
		int referenceHue = extractHsbValues(referenceColor, 6, 11);

		int hueDiff = referenceHue - hueFace;

		int newHue = hueRef - hueDiff;
		// newHue = (newHue % 64 + 64) % 64;

		return (newHue << 10) + (saturationFace << 7) + brightnessFace;
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

	public void applyWallColors(Model model, Color newColor, boolean lavaWall)
	{
		int[] f1 = model.getFaceColors1();
		int[] f2 = model.getFaceColors2();
		int[] f3 = model.getFaceColors3();

		if (f1 != null && f2 != null && f3 != null)
		{
			if (lavaWall)
			{
				applyColor(model, recolorLava(f1, newColor, newColor, 0), recolorLava(f2, newColor, newColor, 0), recolorLava(f3, newColor, newColor, 0));
			}
			else
			{
				applyColor(model, recolor(f1, newColor, newColor, 0), recolor(f2, newColor, newColor, 0), recolor(f3, newColor, newColor, 0));
			}
		}
	}

	public void update(Color newColor, double brightness, boolean onlyLava, boolean hideLava, int whiteBrightness)
	{
		this.currentColor = newColor;
		this.brightness = brightness;
		this.onlyLava = onlyLava;
		this.hideLava = hideLava;
		this.whiteBrightness= whiteBrightness;
		recolorData(newColor);
	}

	// deletes the hashmaps
	public void cleanUp()
	{
		originalColorData.clear();
		recoloredColorData.clear();
	}
}